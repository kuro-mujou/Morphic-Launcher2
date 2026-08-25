package inkspire.morphic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.mainSlot
import inkspire.morphic.core.model.orientation
import inkspire.morphic.core.model.pagerSlot
import inkspire.morphic.core.model.sideSlot
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.AppShortcuts
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.GridOccupancy
import inkspire.morphic.data.layout.GridReflow
import inkspire.morphic.data.layout.HomeListRepository
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.PlacedItem
import inkspire.morphic.data.layout.WidgetSpan
import inkspire.morphic.data.layout.reconcileReportedOrder
import inkspire.morphic.data.layout.settleDock
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SurfaceRegister
import inkspire.morphic.data.widgets.AppWidgetHostController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen-level state holder for the home surface: assembles the render [state] and owns the write path. Kept out
 * of the composable so the surface stays declarative and this logic is unit-testable (no view-model logic in the
 * UI). Plain MVVM — the UI reads one immutable [state] flow and calls typed methods ([launch], [applyChanges]);
 * there is deliberately no sealed-intent/reducer layer, which is ceremony this small surface does not need.
 *
 * As an [androidx.lifecycle.ViewModel] it is scoped to the hosting screen's `ViewModelStore`, so it survives
 * configuration changes (rotation) and its [viewModelScope] coroutines are canceled automatically when the
 * screen is finally gone — the reason to use the framework type rather than a hand-rolled singleton.
 *
 * **Optimistic placements, over a store that is followed rather than sampled.** The durable store is Room via
 * [LayoutRepository], but the UI reads from an in-memory [placements] flow, updated **immediately** on every
 * [applyChanges] before the write is dispatched — so a drop lands with no round-trip flicker and the write just makes
 * it durable. It is a *lead* on the database rather than a fork of it: the store is collected continuously, and its
 * emissions are taken whenever none of our own writes is in flight. That last part is what stopped being optional —
 * this holder was the sole writer of home placements until the settings grid editor arrived, which writes the
 * displaced `Move`s itself because only the button press knows which edge moved. App metadata streams live from
 * [AppRepository.observeApps] as it always has.
 *
 * **The device comes from the UI.** `currentDeviceConfiguration()` is a `@Composable` read of the window, so the
 * surface reports it via [setDevice] and everything device-dependent is derived here: **both** grids and both zones'
 * icon sizing, each resolved by `data:settings` from its blueprint plus whatever the user changed. Orientation is
 * fixed to [Orientation.PORTRAIT] for now.
 *
 * **Every configured number arrives as a flow, including the main grid — which is what makes the settings screen
 * reach this surface at all.** An earlier cut resolved `HomePagerGrid.toGridConfig(device)` here and again in
 * `HomeScreen`, so home drew its *blueprint* size however the user had resized it: the grid section wrote a size the
 * launcher never read, and (worse) moved the placements to match a grid nothing was drawing, which could leave an app
 * in a column off the edge of the visible lattice. The dock was already resolved this way; the main area now is too,
 * so neither zone has a second idea of its own size.
 */
class HomeViewModel(
    private val layoutRepository: LayoutRepository,
    private val homeListRepository: HomeListRepository,
    private val appRepository: AppRepository,
    private val appLauncher: AppLauncher,
    private val appShortcuts: AppShortcuts,
    private val settingsRepository: SettingsRepository,
    private val widgetHost: AppWidgetHostController,
) : ViewModel() {
    private val placements = MutableStateFlow<Map<GridItem, PlacedItem>>(emptyMap())

    /**
     * The vertical list's order — **optimistic, over a store that is followed rather than sampled**, exactly as
     * [placements] is and for the same reason one step further on.
     *
     * A drop has to land instantly. Without this the surface re-rendered from the store while the write was still in
     * flight, so the dragged row returned to where it started and jumped to its new place a frame or two later —
     * which reads as the drop having failed. The write then merely makes it durable.
     *
     * Emissions are ignored while one of our own writes is in flight, so the echo of an earlier write cannot roll a
     * newer drop back; whatever is skipped is superseded by the emission after the last write lands. That last
     * emission is also the correction: `setOrder` reconciles the reported list against real membership, so an order
     * the UI could not fully render is fixed up as soon as the store answers.
     */
    private val listOrder = MutableStateFlow<List<ComponentKey>>(emptyList())

    /** The device the surface reports, or null until it does. Null keeps [iconSizings] empty rather than guessing. */
    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    /**
     * Which pairing HOME is drawing, from the surface register.
     *
     * A `StateFlow` seeded with the register's own default so the first frame shows an unconfigured launcher rather
     * than nothing, and `Eagerly` because the list seed below awaits it without a UI subscriber.
     */
    private val layout: StateFlow<HomeLayout> =
        settingsRepository.surfaceRegister
            .map { it.homeLayout }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SurfaceRegister.Default.homeLayout)

    /**
     * **Device × layout** — the pair every resolved setting below is keyed on, and the reason it is one value.
     *
     * Both inputs move a grid's identity, not just its numbers: the device picks which override applies, and the
     * layout picks *which grid* is being asked about at all (`HomeLayout.mainSlot`/`sideSlot`). Combining them once
     * means each flow below re-subscribes on either change through a single `flatMapLatest`, rather than every flow
     * having to remember to watch both.
     */
    private val posture: StateFlow<HomePosture?> =
        combine(device, layout) { current, homeLayout -> current?.let { HomePosture(it, homeLayout) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Each zone's resolved icon sizing, by slot.
     *
     * One entry per zone **that draws icons**, because they are separate grids with separate blueprints — which is
     * what makes their icon config independent. The widget area contributes none: its blueprint's `icon` is null, and
     * asking `iconSizing` for it would (rightly) throw, so the filter is what keeps that unrepresentable rather than
     * a runtime hazard.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val iconSizings: Flow<Map<GridSlot, IconSizing>> =
        posture.flatMapLatest { current ->
            val slots = current?.iconSlots.orEmpty()
            if (slots.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    slots.map { slot -> settingsRepository.iconSizing(slot, current!!.device).map { slot to it } },
                ) { pairs -> pairs.toMap() }
            }
        }

    /**
     * **HOME's grid arrangement's own size**, resolved for the reported device — `HOME_MAIN`'s, whichever layout is
     * showing.
     *
     * Deliberately *not* gated on the layout, unlike [mainSizing]. This is the grid the coordinate placements live
     * in, and they live there whichever main area is on screen: the seed lays apps out in it, the dock spills onto
     * it, and the vertical list is seeded by flattening it. A user who first launches on the list layout still needs
     * their apps placed, and this is what places them.
     *
     * A `StateFlow` because three things besides the UI await it, `Eagerly` so they can do so before a subscriber
     * arrives.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pagerConfig: StateFlow<GridConfig?> =
        device.flatMapLatest { current ->
            if (current == null) flowOf(null) else settingsRepository.gridConfig(GridSlot.HOME_MAIN, current)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Whether the main pager's pages wrap around at the ends.
     *
     * Read off the resolved map rather than asked per slot, which is `SettingsRepository.pagerWraps`' shape and its
     * reason. Defaulted to false rather than left nullable: unlike a size, a missing answer here has an honest
     * stand-in — an unconfigured launcher does not wrap — and the blueprint agrees, so no frame ever shows a pager
     * behaving differently from how it will settle.
     *
     * **It is not gated on the reported device**, unlike everything else in this holder, because wrapping has no
     * device dimension. That is why it needs no `flatMapLatest` and can be read straight through.
     */
    private val mainWraps: Flow<Boolean> =
        settingsRepository.pagerWraps.map { it[GridSlot.HOME_MAIN] == true }

    /**
     * What the main area **is**, sized — a grid on one layout and a row height on the other.
     *
     * The `when` is the whole of the layout branch in this holder: everything downstream reads a [HomeMainSizing] and
     * never asks which layout produced it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainSizing: Flow<HomeMainSizing?> =
        posture.flatMapLatest { current ->
            when (current?.layout) {
                null -> flowOf(null)
                // Two settings joined here rather than two fields on the state, because the pager arm is the only
                // thing that can hold either: a wrap toggle belongs to a pager, and the other arm has none.
                HomeLayout.PAGER_WITH_DOCK -> combine(pagerConfig, mainWraps) { config, wraps ->
                    config?.let { HomeMainSizing.Pager(it, wraps) }
                }

                HomeLayout.LIST_WITH_WIDGET_AREA ->
                    settingsRepository.rowHeight(GridSlot.HOME_LIST, current.device).map(HomeMainSizing::List)
            }
        }

    /**
     * The side zone's stored size for the reported device — its extent, and the counts it divides that extent into.
     *
     * Two flows rather than one because the extent and the dimensions are two settings with two stores, joined here
     * because a `StateFlow` of one state object is what the surface reads. **Which** zone is a property of the
     * layout and of nothing else here: the dock and the widget area are the same shape of setting on two slots.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sideSizing: Flow<SideZoneSizing?> =
        posture.flatMapLatest { current ->
            if (current == null) {
                flowOf(null)
            } else {
                combine(
                    settingsRepository.extent(current.sideSlot, current.device),
                    settingsRepository.gridConfig(current.sideSlot, current.device),
                ) { extentDp, grid -> SideZoneSizing(extentDp, grid.visualCols, grid.visualRows) }
            }
        }

    /**
     * Each zone's horizontal padding, resolved per device — the same shape and the same reason as [iconSizings].
     *
     * Both zones, including the widget area: a margin is the one measurement every grid has, which is why it is the
     * one of the three that is asked of both slots unconditionally.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val paddings: Flow<Map<GridSlot, Int>> =
        posture.flatMapLatest { current ->
            if (current == null) {
                flowOf(emptyMap())
            } else {
                combine(
                    current.slots.map { slot ->
                        settingsRepository.horizontalPadding(slot, current.device).map { slot to it }
                    },
                ) { pairs -> pairs.toMap() }
            }
        }

    /**
     * Everything the settings layer decides about how home is drawn, in one value.
     *
     * Folded together because `combine` takes five flows and the state needs more: the sources of *configuration*
     * behind one, leaving the sources of *content* (placements, apps, folders, the list's order) at the top level. It
     * is also the honest grouping — these change together when the user edits a section, and none of them is content.
     */
    private val sizing: Flow<HomeSizing> = combine(iconSizings, mainSizing, sideSizing, paddings, layout, ::HomeSizing)

    /**
     * The definitions a placement resolves *through* — a folder's contents, a widget's provider and label, and each
     * container's arrangement plus what it holds.
     *
     * Folded into one flow for [sizing]'s reason (`combine` takes five and the state wants more), and this is the
     * honest grouping: all four are stores of *what an item is*, where the placement tables say only where it sits. A
     * placement whose definition is missing renders as nothing, which is what the `mapNotNull` below relies on.
     */
    private val definitions: Flow<HomeDefinitions> =
        combine(
            layoutRepository.folders(),
            layoutRepository.widgets(),
            layoutRepository.iconContainers(),
            layoutRepository.widgetContainers(),
            ::HomeDefinitions,
        )

    val state: StateFlow<HomeState> =
        combine(
            placements,
            appRepository.observeApps(),
            definitions,
            listOrder,
            sizing,
        ) { placed, apps, defined, listOrder, configured ->
            val infoByComponent = apps.associateBy { it.componentKey }
            val folderById = defined.folders.associateBy { it.id }
            val widgetById = defined.widgets.associateBy { it.appWidgetId }
            val iconContainerById = defined.iconContainers.associateBy { it.id }
            val widgetContainerById = defined.widgetContainers.associateBy { it.id }
            HomeState(
                items = placed.mapNotNull { (item, at) ->
                    when (item) {
                        is GridItem.App ->
                            infoByComponent[item.component]?.let { HomeItem.App(it, at.placement, at.zone) }

                        is GridItem.Folder -> folderById[item.folderId]?.let { folder ->
                            HomeItem.Folder(
                                folder = folder,
                                apps = folder.apps.mapNotNull(infoByComponent::get),
                                placement = at.placement,
                                zone = at.zone,
                            )
                        }

                        is GridItem.Widget ->
                            widgetById[item.appWidgetId]?.let { HomeItem.Widget(it, at.placement, at.zone) }
                        // Both containers resolve their *contents* here too, for the folder's reason: the cell draws
                        // them, so re-resolving per frame in the UI would put this join in the wrong layer twice.
                        is GridItem.IconContainer -> iconContainerById[item.containerId]?.let { container ->
                            HomeItem.IconContainer(
                                container = container,
                                icons = container.items.mapNotNull { member ->
                                    when (member) {
                                        is IconItem.App -> infoByComponent[member.component]?.let(ContainerIcon::App)
                                        is IconItem.Folder -> folderById[member.folderId]?.let { folder ->
                                            ContainerIcon.Folder(folder, folder.apps.mapNotNull(infoByComponent::get))
                                        }
                                    }
                                },
                                placement = at.placement,
                                zone = at.zone,
                            )
                        }

                        is GridItem.WidgetContainer -> widgetContainerById[item.containerId]?.let { container ->
                            HomeItem.WidgetContainer(
                                container = container,
                                widgets = container.widgetIds.mapNotNull(widgetById::get),
                                placement = at.placement,
                                zone = at.zone,
                            )
                        }
                    }
                },
                layout = configured.layout,
                // Resolved through the same cache the grid items are, and unresolvable entries are dropped for the
                // same reason: an app the cache cannot describe has no icon and no label to draw. The *stored* order
                // keeps them (`HomeListRepository.setOrder` reconciles), so an uninstall-and-reinstall returns them
                // to where they were.
                listApps = listOrder.mapNotNull(infoByComponent::get),
                // Every installed app, not just the placed ones — see [HomeState.catalog]. Already built here as the
                // resolver above, so publishing it costs a field rather than a second pass.
                catalog = infoByComponent,
                iconSizing = configured.icon,
                horizontalPaddingDp = configured.padding,
                main = configured.main,
                side = configured.side,
            )
        }
            // Joined outside the combine above, which is already at its five-argument limit — and correctly so:
            // this is keyed by neither the device nor the layout, so nesting it inside would re-subscribe it on
            // every rotation and every pairing change. The same shape `AppsSectionViewModel` uses for its bound
            // layouts.
            .combine(settingsRepository.homeItemGestures) { base, gestures -> base.copy(itemGestures = gestures) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState(emptyList()))

    /**
     * Performs whatever [gesture] is assigned to on [item], and nothing at all when it is unassigned.
     *
     * **Read at the moment it fires, not held by the cell.** A gesture that was reassigned while the finger was
     * down should do the new thing, and the cell has no reason to carry an action it only hands back.
     *
     * A withdrawn shortcut or an uninstalled app simply does nothing — both commands are fire-and-forget for
     * that reason, and a gesture is exactly as entitled to point at something gone as a home icon is.
     */
    fun runGesture(item: GridItem, gesture: ItemGesture) {
        viewModelScope.launch {
            when (val action = settingsRepository.homeItemGestures.first().actionsOn(item)[gesture]) {
                null -> Unit
                is GestureAction.LaunchApp -> appLauncher.launch(action.component)
                is GestureAction.LaunchShortcut ->
                    appShortcuts.start(action.id, action.packageName, action.userSerial)
            }
        }
    }

    /** Layout writes dispatched but not yet seen land — see the store collector in [init]. */
    private var writesInFlight = 0

    /** The same, for the list's own store. Separate because they are separate stores with separate echoes. */
    private var listWritesInFlight = 0

    init {
        // Seeding, keyed on the **configured** grid rather than on the device report: the device changes once, the
        // grid changes every time the settings screen writes one, and a seed needs to know how big a page is.
        // `StateFlow` conflates equal values, so this runs on a real change and not on a re-report of the same size.
        // The app cache is refreshed once, before the first seed, since a seed with an empty cache places nothing.
        viewModelScope.launch {
            var refreshed = false
            pagerConfig.filterNotNull().collect { config ->
                if (!refreshed) {
                    appRepository.refresh()
                    refreshed = true
                }
                seedIfEmpty(config)
            }
        }
        // **The vertical list's first-run default: the grid, flattened.** Seeded when the layout is *chosen* rather
        // than at startup, because a list seeded on a launcher whose user never opens that layout would be a snapshot
        // going stale in the database — and because the seed has to run after the grid has something in it.
        //
        // `collectLatest` is what makes awaiting the placements safe: switching away cancels the wait rather than
        // leaving it pending against a layout that is no longer showing. Awaiting at all (rather than `?: return`) is
        // the same call `fitDockTo` makes — the grid seed and this one resolve from the same store at roughly the
        // same moment, so a skip would drop the only seed and nothing would ask again.
        viewModelScope.launch {
            layout.collectLatest { current ->
                if (current != HomeLayout.LIST_WITH_WIDGET_AREA) return@collectLatest
                homeListRepository.seedIfEmpty(readingOrder(placements.first { it.isNotEmpty() }))
            }
        }
        // **The store is followed, not read once — because this holder stopped being its only writer.** [placements]
        // is a *lead* on the database, not a fork of it, and it was seeded once at startup on the assumption that
        // nothing else wrote home placements. The grid editor broke that assumption: resizing writes the count **and**
        // the `Move`s it displaces, because only the button press knows which edge moved. A home that read once would
        // keep drawing the old arrangement — and re-reading on the size change would not fix it either, since for a
        // *grown* grid the size is deliberately written first, so that no observer ever sees a grid too small for its
        // contents.
        //
        // Emissions are ignored while one of our own writes is in flight, which is what preserves the optimism: a drop
        // updates the map immediately, and the echo of an *earlier* write must not roll it back for a frame. Anything
        // skipped is either what is already shown or is superseded by the emission after the last write lands.
        viewModelScope.launch {
            layoutRepository.placements(ORIENTATION).collect { stored ->
                if (writesInFlight == 0) placements.value = stored
            }
        }
        // The list's store, followed on the same terms — see [listOrder].
        viewModelScope.launch {
            homeListRepository.order.collect { stored ->
                if (listWritesInFlight == 0) listOrder.value = stored
            }
        }
    }

    /**
     * Supplies the device configuration the surface is drawn on — the UI's one job in this direction.
     *
     * Reporting the *input* rather than a product the UI resolved from the home blueprint means both grids **and**
     * each zone's icon sizing derive here, so the next device-dependent thing costs nothing — and, more importantly,
     * they derive from the *store* rather than from the blueprint the UI happened to have to hand.
     */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /** Opens the app for [component] (a home tap). Fire-and-forget — [AppLauncher] swallows a stale component. */
    fun launch(component: ComponentKey) = appLauncher.launch(component)

    /**
     * Commits the vertical list's new order, as the surface dropped it — **optimistically**, then durably.
     *
     * The optimism is not decoration here. The MovingGap preview lives on the *drag*, so it is gone the instant the
     * finger lifts: with the surface still rendering the stored order, the dropped row visibly returned to where it
     * started and only reached its new place when the write echoed back. Leading the store closes that window, the
     * same way [applyChanges] closes it for a coordinate drop. See [listOrder].
     *
     * The reconciliation against real membership is the repository's, deliberately: the surface reports only the rows
     * it could render, and only the store knows the rest. See `HomeListRepository.setOrder`.
     */
    fun reorderList(order: List<ComponentKey>) {
        listOrder.value = order
        listWritesInFlight++
        viewModelScope.launch {
            try {
                homeListRepository.setOrder(order)
            } finally {
                listWritesInFlight--
            }
        }
    }

    /**
     * Takes the widget [appWidgetId] off HOME — **and gives its id back to the platform**.
     *
     * The second half is why this is not a plain `RemoveFromGrid`. An allocated id is a resource that outlives this
     * process: dropping our rows alone would leave the platform believing the widget still exists, keeping the
     * provider updating something nobody can see, with no way for the user to reach it again. `data:layout` cannot
     * do it — the host belongs to `data:widgets` and the store deliberately only keeps records — so the two halves
     * meet here, which is the same shape as the top-action band's Uninstall (a layout change beside a system call).
     *
     * Order matters only for what a failure would leave behind: the rows go first, so a host that refuses the
     * release leaves an orphaned id rather than a cell drawing a widget the layout no longer knows about.
     */
    fun removeWidget(appWidgetId: Int) {
        applyChanges(listOf(LayoutChange.RemoveFromGrid(GridItem.Widget(appWidgetId))))
        widgetHost.deleteId(appWidgetId)
    }

    /**
     * Places a widget the add flow has just bound and configured, at the first free rect of [span] on [zone]'s grid.
     *
     * **It searches rather than being told a cell**, because the picker is reached from a long-press whose position
     * says where the *menu* opened, not where a widget of an unknown size will fit. It returns null rather than
     * deleting the id silently, so the caller can say so.
     *
     * @return **where it landed**, which the caller needs and not merely whether it worked: the search may file a
     *   widget on a page the user is not looking at, and a surface that cannot say which page has no way to show
     *   them what they just added. Null when nothing of that size fits, in which case **nothing is written** and the
     *   caller still owns the id — a widget half-added is worse than one not added.
     */
    fun placeWidget(widget: WidgetInfo, span: WidgetSpan, zone: HomeZone, config: GridConfig): GridPlacement? {
        val at = freeRect(zone, config, rowSpan = span.rowSpan, colSpan = span.colSpan) ?: return null
        applyChanges(listOf(LayoutChange.PlaceWidget(widget, at, zone)))
        return at
    }

    /**
     * Whether [zone] could take an item of this size **right now** — the picker's question, asked before anything is
     * bound or written.
     *
     * **The same search the add itself runs** ([freeRect]), which is the only thing that makes it trustworthy: a
     * capacity test written separately agrees on the day it is written and then disagrees with the placement it is
     * supposed to predict, and the failure is a user pressing Add and watching nothing happen.
     */
    fun hasRoomFor(rowSpan: Int, colSpan: Int, zone: HomeZone, config: GridConfig): Boolean =
        freeRect(zone, config, rowSpan = rowSpan, colSpan = colSpan) != null

    /**
     * Adds [component] to icon container [containerId] — the "+" picker's commit.
     *
     * One op, because `AddToIconContainer` detaches the app from wherever it was as part of filing it: off the grid,
     * out of a folder, out of another container. So the picker needs no "is it placed?" test and no removal to pair
     * with, which is the same composition the drag path relies on.
     */
    /**
     * Takes [item] out of icon container [containerId] — **membership only, so it goes nowhere**, which is
     * `ContainerSettingsViewModel.removeIcon`'s behavior and its reason: the grid has no cell reserved for it, and
     * inventing one is a placement decision a menu row has no business making. Putting it somewhere is a drag.
     */
    fun removeIconFromContainer(containerId: Long, item: IconItem) {
        applyChanges(listOf(LayoutChange.RemoveFromIconContainer(containerId, item)))
    }

    /**
     * Files [item] into icon container [containerId] at [index] — a drop from outside, which names a place.
     *
     * One op, because `AddToIconContainer` detaches the item from wherever it was as part of filing it: off the
     * grid, out of a folder, out of another container. There is no removal to pair with it.
     */
    fun insertIntoIconContainer(containerId: Long, item: IconItem, index: Int) {
        applyChanges(listOf(LayoutChange.AddToIconContainer(containerId, item, index)))
    }

    /** Sets icon container [containerId]'s contents to [items] — a reorder, so membership is unchanged. */
    fun reorderIconContainer(containerId: Long, items: List<IconItem>) {
        applyChanges(listOf(LayoutChange.ReorderIconContainer(containerId, items)))
    }

    fun addAppToIconContainer(containerId: Long, component: ComponentKey) {
        applyChanges(listOf(LayoutChange.AddToIconContainer(containerId, IconItem.App(component))))
    }

    /**
     * Takes widget container [containerId] off HOME — **with every widget inside it, and their ids**.
     *
     * This is [removeWidget]'s rule applied once per contained widget, and it is not optional: deleting the
     * container row cascades its *membership* rows, but `widget_container_item` has no foreign key to the `widget`
     * table, so each contained widget's definition and its allocated `appWidgetId` would outlive the container with
     * nothing left pointing at them. A leak the user can neither see nor clear — the reason this is one method
     * rather than something each caller assembles.
     *
     * The ids come from the container's **stored membership** rather than from its resolved [HomeItem.WidgetContainer.widgets],
     * deliberately: a widget whose definition the store lost is exactly the one whose id most needs handing back,
     * and it is the one that would be missing from the resolved list.
     */
    fun removeWidgetContainer(containerId: Long) {
        val widgetIds = state.value.items
            .filterIsInstance<HomeItem.WidgetContainer>()
            .firstOrNull { it.container.id == containerId }
            ?.container?.widgetIds
            .orEmpty()
        applyChanges(
            widgetIds.map { LayoutChange.RemoveFromGrid(GridItem.Widget(it)) } +
                LayoutChange.RemoveFromGrid(GridItem.WidgetContainer(containerId)),
        )
        widgetIds.forEach(widgetHost::deleteId)
    }

    /**
     * Adds an **empty icon container** to [zone]'s grid, at the first cell a [ContainerSpan] fits.
     *
     * Empty is the whole of it: a container is created from the widget picker and then filled, by dragging icons in
     * or (once there is an app picker) through its own "+". There is nothing to choose at creation, which is why this
     * takes no items — and nothing to be optimistic about, since the id is autogenerated and the surface has no way
     * to guess it. `applyChanges` re-syncs from the store after a structural op for exactly this case.
     *
     * @return where it landed, or null when nothing of that size fits — [placeWidget]'s contract exactly.
     */
    fun createIconContainer(zone: HomeZone, config: GridConfig): GridPlacement? {
        val at = freeContainerRect(zone, config) ?: return null
        applyChanges(
            listOf(
                LayoutChange.CreateIconContainer(
                    arrangement = IconArrangement.GRID,
                    items = emptyList(),
                    at = at,
                    zone = zone,
                ),
            ),
        )
        return at
    }

    /**
     * Adds an **empty widget container** to [zone]'s grid, on [createIconContainer]'s terms exactly.
     *
     * The default axis is horizontal because that is the direction a finger already swipes on this surface — home's
     * own pages — so a container's pages read as a smaller instance of a gesture the user has, rather than a new one.
     *
     * @return where it landed, or null when nothing of that size fits.
     */
    fun createWidgetContainer(zone: HomeZone, config: GridConfig): GridPlacement? {
        val at = freeContainerRect(zone, config) ?: return null
        applyChanges(
            listOf(
                LayoutChange.CreateWidgetContainer(
                    axis = WidgetContainerAxis.HORIZONTAL,
                    widgetIds = emptyList(),
                    at = at,
                    zone = zone,
                ),
            ),
        )
        return at
    }

    /**
     * The first free [ContainerSpan]-sized rect on [zone]'s grid, or null when the zone is full.
     *
     * Searched rather than given a cell for [placeWidget]'s reason: the picker is reached from a long-press whose
     * position says where the *menu* opened, not where a container will fit.
     */
    private fun freeContainerRect(zone: HomeZone, config: GridConfig): GridPlacement? {
        val span = ContainerSpan * config.cellMultiplier
        return freeRect(zone, config, rowSpan = span, colSpan = span)
    }

    /**
     * The first free rect of this size on [zone]'s grid, or null when there is nowhere to put it.
     *
     * **Whether the search may spill onto a new page is the zone's to answer, not the caller's**, and getting it
     * wrong is silent both ways. A *paged* zone — HOME's main grid under the pager pairing — grows a page rather
     * than refusing, which is what makes a widget picker always succeed there. Every other coordinate zone is a
     * single grid drawn all at once: the dock is a strip, and the widget area is one frame with no pages and a
     * planner that pins `page = 0`. Handing either of those a placement on page 1 does not hide the item, which
     * would be bad enough — `CoordinateDragGrid` never filters by page, so it draws it **on top of** whatever is at
     * the same row and column on page 0. `GridOccupancy.findFreeRectOnPage` exists for exactly this and its own KDoc
     * says so; this is the call site that had been using the wrong one.
     *
     * Read off [layout] rather than passed in, so a surface cannot describe its own zone wrongly: which zones page
     * is a property of the pairing, and the pairing is here.
     */
    private fun freeRect(zone: HomeZone, config: GridConfig, rowSpan: Int, colSpan: Int): GridPlacement? {
        val occupied = state.value.items.filter { it.zone == zone }.map { it.placement }
        val occupancy = GridOccupancy(config, occupied)
        val paged = zone == HomeZone.MAIN && layout.value.pagerSlot != null
        return if (paged) {
            occupancy.findFreeRect(page = 0, row = 0, col = 0, rowSpan = rowSpan, colSpan = colSpan)
        } else {
            occupancy.findFreeRectOnPage(page = 0, row = 0, col = 0, rowSpan = rowSpan, colSpan = colSpan)
        }
    }

    /**
     * What giving [item] the cells [to] would do to everything else: where each occupant it overlaps would be
     * pushed to, or **null when the grid cannot make room** for it at all.
     *
     * **The same engine a drop runs**, which is the point — a resize and a move differ only in which rectangle is
     * being asked for, so `FreeGridPlanner` answers both and there is one definition of "what happens to the
     * things in the way".
     *
     * It is asked twice per resize and that is safe rather than wasteful: the planner is pure, and a preview
     * writes nothing, so the answer the frame previewed and the answer [resizeItem] commits are computed from the
     * same state and cannot disagree. Sharing the *logic* rather than caching a result is the same rule the drag
     * shadow follows — see `FreeGridPlanner`'s own note on how three copies of it diverge.
     */
    fun planResize(
        item: GridItem,
        to: GridPlacement,
        zone: HomeZone,
        config: GridConfig,
    ): Map<GridItem, GridPlacement>? {
        val occupants = state.value.items
            .filter { it.zone == zone && it.gridItem != item && it.placement.page == to.page }
            .associate { it.gridItem to it.placement }
        // `relocate` is what separates this from a drop, and `FreePush` states the reason: a resize claims an
        // *area* and says nothing about direction, so an occupant that cannot slide out sideways should be given
        // free space elsewhere rather than turning the whole expansion red while half the grid is empty.
        val plan = FreeGridPlanner.plan(to, occupants, config, relocate = true)
        return if (plan.intent == DropIntent.INVALID) null else plan.moves
    }

    /**
     * Commits a resize: [item] takes the cells [to], and whatever it now overlaps is pushed aside.
     *
     * @return false when [planResize] cannot make room, in which case **nothing is written** — the overlay is
     *   already drawing that rectangle as refused, and committing a partial push would move neighbors for a
     *   resize that never happened.
     */
    fun resizeItem(item: GridItem, to: GridPlacement, zone: HomeZone, config: GridConfig): Boolean {
        val moves = planResize(item, to, zone, config) ?: return false
        applyChanges(
            moves.map { (moved, at) -> LayoutChange.Move(moved, at, zone) } +
                LayoutChange.Move(item, to, zone),
        )
        return true
    }

    /**
     * Takes [component] off the vertical list — the list's own *Remove*.
     *
     * **It has to be the membership op, and a reorder cannot stand in for it.** The obvious-looking alternative is
     * `reorderList(current - component)`, and that silently does nothing: `HomeListRepository.setOrder` reconciles
     * what the UI reported against real membership on purpose, so an app missing from the reported list is treated
     * as one the surface could not *render* and is appended back at the end. That guard is right — it is what stops
     * an uninstalled-but-unpruned app being deleted because no row could be drawn for it — which is exactly why
     * removal needs an op that says so rather than one that hopes to be inferred.
     *
     * Optimistic on the same terms as [reorderList], and for the same reason: the row should leave under the finger
     * that chose Remove rather than a write later.
     */
    fun removeFromList(component: ComponentKey) {
        listOrder.value = listOrder.value - component
        listWritesInFlight++
        viewModelScope.launch {
            try {
                homeListRepository.remove(component)
            } finally {
                listWritesInFlight--
            }
        }
    }

    /**
     * Adds [components] to the end of the vertical list — the *Add apps* picker's commit.
     *
     * [removeFromList]'s counterpart, and a membership op for the same reason: `setOrder` reconciles a reported
     * order against what is stored, so handing it a longer list writes nothing at all.
     *
     * Optimistic on the same terms, though what it buys here is different. A removal has to leave under the finger;
     * an addition has to **exist before the surface can scroll to it**, and the surface reveals what it added by
     * waiting for the row to appear. Leading the store means that wait is a frame rather than a round-trip.
     *
     * Already-listed apps are dropped here as well as in the store, so the optimistic list and the written one agree
     * about what actually changed rather than briefly disagreeing about a duplicate.
     */
    fun addToList(components: List<ComponentKey>) {
        val added = components.distinct().filterNot { it in listOrder.value }
        if (added.isEmpty()) return
        listOrder.value = listOrder.value + added
        listWritesInFlight++
        viewModelScope.launch {
            try {
                homeListRepository.add(added)
            } finally {
                listWritesInFlight--
            }
        }
    }

    /**
     * Re-homes anything the dock can no longer hold, now that its grid is [dockConfig] — **the settings write and
     * the placement store meeting**.
     *
     * Shrinking the dock (or raising its icon size, which shrinks how many cells fit) swallows cells that may hold
     * items. The strip is a *single page*, so unlike home there is no next page to push them onto: whatever will not
     * fit is evicted and lands on **home's main area** instead, where a page can always be appended.
     *
     * **A command rather than a setter**, and reported by the surface rather than derived here, which is the one
     * exception to "report the input, not the product" ([setDevice]'s rule). The two inputs a dock grid needs — the
     * measured width, and the type scale the label row's height comes from — exist only in the UI, so the surface
     * is the only layer that can answer. It reports the answer; the write stays here.
     *
     * **Idempotent, so the caller does not have to know whether the dock shrank**: a config everything already fits
     * reports no change and writes nothing, which is why this can simply be called whenever the grid changes.
     *
     * Deliberately **not** inside the `combine` that assembles home state. Reflowing there and launching the persist
     * from within that transform makes the write a side effect of *reading* state, running on every emission that
     * happens to find a stray, and racing itself. Here the trigger is the config changing, and the
     * write is an ordinary [applyChanges] like any other.
     */
    /**
     * **Is the posture on screen the one whose arrangement this surface reads?**
     *
     * The gate on both settles, and it exists because they *write*. Placements are keyed by [Orientation] and this
     * surface reads [ORIENTATION] only — home orientation is unbuilt — so re-fitting while the device is the other way
     * up would rewrite a portrait arrangement to fit a screen it is never drawn on, and nothing would undo that on
     * rotating back. It matters most for the dock, which is a **rail** in phone landscape: the transpose of the strip,
     * so almost every item in it would be evicted to home permanently.
     *
     * A grid drawn out of its bounds for as long as a rotation lasts is cosmetic and reverses itself. The write does
     * not, which is why this guards the writes rather than the drawing. It stops being a no-op the day placements are
     * stored per posture, at which point [ORIENTATION] follows the device and this is simply always true.
     */
    private val drawsStoredPlacements: Boolean
        get() = device.value?.orientation == ORIENTATION

    /**
     * Re-homes anything the **main area** can no longer hold, now that its grid is [config] — the pager's half of the
     * rule [fitDockTo] states for the dock.
     *
     * The trigger is usually the dock: its height is the setting, the pager takes what is left, and past a point what
     * is left carries fewer rows than the user's count. It is also reached by an icon-size change, which raises the
     * smallest usable cell and so lowers how many fit — the same clamp from the other side.
     *
     * **Where the strays go is the one difference from the dock, and it follows from the surface rather than being a
     * choice.** Home is paged and can always append one, so `GridReflow`'s default overflow carries them forward;
     * the dock is a single strip with no next page, which is why it evicts to home instead. Nothing is deleted on
     * either path.
     *
     * The row *count* is not written down here. It is clamped where it is read (`CellFit.fitGridConfig` in the
     * surface), so shortening the dock again gives the rows straight back — where writing the reduction would make a
     * temporary shortage permanent. Only the placements move, and only when the reflow reports it needed to.
     */
    fun fitMainTo(config: GridConfig) {
        if (!drawsStoredPlacements) return
        val main = placements.value.filterValues { it.zone == HomeZone.MAIN }.mapValues { it.value.placement }
        val settled = GridReflow.reflow(main, config)
        if (!settled.changed) return
        applyChanges(
            settled.placements
                .filterNot { (item, at) -> main[item] == at }
                .map { (item, at) -> LayoutChange.Move(item, at, HomeZone.MAIN) },
        )
    }

    fun fitDockTo(dockConfig: GridConfig) {
        if (!drawsStoredPlacements) return
        viewModelScope.launch {
            // Nothing is written until the main grid is known — an eviction with nowhere to go would take an item off
            // the dock and leave it placed nowhere. **Awaited rather than skipped**: the two configs resolve from the
            // same store at roughly the same moment, so a `?: return` here would drop the very first fit whenever the
            // dock's answer arrived first, and nothing would call again until something else changed.
            val main = pagerConfig.filterNotNull().first()
            val current = placements.value
            applyChanges(
                settleDock(
                    dock = current.filterValues { it.zone == HomeZone.DOCK }.mapValues { it.value.placement },
                    main = current.filterValues { it.zone == HomeZone.MAIN }.mapValues { it.value.placement },
                    dockConfig = dockConfig,
                    mainConfig = main,
                ),
            )
        }
    }

    /**
     * Applies layout [changes] optimistically to [placements] (so the UI updates now), then persists them.
     *
     * Pure `Move`/`RemoveFromGrid` stay fully optimistic — no round-trip, no drop flicker. Structural ops
     * (e.g. `CreateFolder`) mint ids we can't predict, so once they've persisted the placement map is resynced
     * from the store to pick up the new item and drop the ones folded into it.
     *
     * [writesInFlight] brackets the write so the store collector holds off until it lands: between the optimistic
     * update and the database catching up, the truth is here rather than there. Decremented in a `finally`, so a
     * failed write cannot leave the surface permanently deaf to the store.
     */
    fun applyChanges(changes: List<LayoutChange>) {
        if (changes.isEmpty()) return
        placements.value = placements.value.withApplied(changes)
        writesInFlight++
        viewModelScope.launch {
            try {
                layoutRepository.apply(ORIENTATION, changes)
                if (changes.any { it !is LayoutChange.Move && it !is LayoutChange.RemoveFromGrid }) {
                    placements.value = layoutRepository.placements(ORIENTATION).first()
                }
            } finally {
                writesInFlight--
            }
        }
    }

    /**
     * Reorders folder [folderId] to the arrangement the overlay [reported] on drop.
     *
     * The reported order covers only the members the UI could render, so it is reconciled against the real
     * membership first ([reconcileReportedOrder]) — `ReorderFolder` replaces membership wholesale, and writing the
     * UI's list verbatim would delete anything it couldn't draw.
     */
    fun reorderFolder(folderId: Long, reported: List<ComponentKey>) {
        val known = folderById(folderId)?.folder?.apps ?: return
        applyChanges(listOf(LayoutChange.ReorderFolder(folderId, reconcileReportedOrder(known, reported))))
    }

    /**
     * Adds an app dragged into folder [folderId], where [reported] is the arrangement the overlay dropped (its members
     * *plus* [incoming]): set the membership/order and take [incoming] off the home grid.
     *
     * [incoming] joins the known membership before reconciling, since it is a member as of this drop — otherwise
     * the very app being added would be reconciled away as a non-member.
     *
     * [from] is the folder the app was pulled **out of**, when it came from one rather than off a grid — the
     * folder-to-folder move, committed as a single batch so the app is never briefly in both or neither. Its
     * `RemoveFromGrid` is then a no-op (a folder member has no cell), which is harmless and keeps one code path.
     */
    fun addToFolder(folderId: Long, reported: List<ComponentKey>, incoming: ComponentKey, from: Long? = null) {
        val known = folderById(folderId)?.folder?.apps ?: return
        val leaving = from?.takeIf { it != folderId }?.let(::folderById)
        applyChanges(
            listOf(
                LayoutChange.ReorderFolder(folderId, reconcileReportedOrder(known + incoming, reported)),
                LayoutChange.RemoveFromGrid(GridItem.App(incoming)),
            ) + leaving?.let { leaveFolderChanges(it, incoming) }.orEmpty(),
        )
    }

    /**
     * Commits an app dragged out of folder [folderId] and dropped in [zone] at [plan] (its footprint, plus the
     * occupants of that zone it pushed): the app lands there and leaves the folder.
     *
     * See [leaveFolderChanges] for the removal half (including auto-dissolve), which every landing shares.
     */
    fun dropExtractedApp(folderId: Long, component: ComponentKey, plan: PlacementPlan, zone: HomeZone) {
        val folder = folderById(folderId) ?: return
        val changes = mutableListOf<LayoutChange>()
        // Pushed occupants are already in the drop zone, so they move within it.
        plan.moves.forEach { (moved, to) -> changes += LayoutChange.Move(moved, to, zone) }
        changes += LayoutChange.Move(GridItem.App(component), plan.footprint, zone) // the extracted app lands here
        changes += leaveFolderChanges(folder, component)
        applyChanges(changes)
    }

    /**
     * Commits an app dragged out of folder [folderId] and dropped on the **merge ring** of whatever sits at
     * [targetPlacement] in [zone]: it goes straight into that target — combining with an app to make a new folder, or
     * joining an existing one — and leaves [folderId] in the same batch.
     *
     * This is what stops an extracted app being stranded. Without it a drag out of a folder could only ever land on an
     * empty cell: the drop handler discarded a MERGE outcome, so releasing over another folder (or another app) did
     * nothing and the app snapped back — an app could leave a folder but never move between folders in one gesture.
     *
     * **Dropped back on its own folder is a no-op.** The folder is still sitting on the grid throughout the drag, so
     * its merge ring is a natural "put it back" — and the app is still a member, since nothing is written until the
     * drop, so there is genuinely nothing to do. Recognizing that here keeps it one no-op instead of an add-and-remove
     * pair that cancel out. (Dwelling on that same ring re-opens the folder instead, which is how the app is put back
     * at a *chosen* slot.)
     */
    fun mergeExtractedApp(folderId: Long, component: ComponentKey, targetPlacement: GridPlacement, zone: HomeZone) {
        val folder = folderById(folderId) ?: return
        val target = state.value.items.firstOrNull { it.placement == targetPlacement && it.zone == zone } ?: return
        if (target.gridItem == GridItem.Folder(folderId)) return // back into the folder it came from — it never left
        val merge = mergeChanges(GridItem.App(component), targetPlacement, zone) ?: return
        applyChanges(merge + leaveFolderChanges(folder, component))
    }

    /**
     * The changes that take [app] out of [folder] — the half shared by every way a dragged-out app can land (an empty
     * cell, another folder, a new folder made by merging). Kept as one function so those paths cannot disagree about
     * what leaving a folder means.
     *
     * **Auto-dissolve:** a folder holds ≥ 2 apps, so removing the second-last one would leave a folder of one. Instead
     * the folder is dissolved — the single remaining app takes over the folder's cell and the folder is deleted (its FK
     * cascades drop the membership + placement rows). Otherwise it's a plain remove-from-folder.
     *
     * The dissolve move carries **[folder]'s own zone**, not the drop's: the last app inherits the folder's cell, which
     * is wherever the folder sat. The two differ whenever an app is dragged out of a folder in one zone and dropped in
     * the other.
     *
     * **A non-member yields no changes.** Callers pass the folder a drag *started* in, so the app is normally a member
     * by construction — but membership is read from state that can move underneath a long gesture (an uninstall, a
     * profile going away), and removing an app that isn't there must not also dissolve a folder whose count never fell.
     */
    private fun leaveFolderChanges(folder: HomeItem.Folder, app: ComponentKey): List<LayoutChange> =
        leaveFolderChanges(folder, listOf(app))

    /**
     * The same, for **several apps leaving one folder at once** — the picker's case.
     *
     * **It takes a collection because the single-app form cannot be run twice.** Each call reads `folder.folder.apps`
     * from state, and nothing in a batch updates that state between changes: two apps leaving a three-app folder
     * would each see two remaining, each decide the folder survives, and leave it holding one app — the state
     * auto-dissolve exists to prevent. Asked once about all of them, the count is right.
     */
    private fun leaveFolderChanges(folder: HomeItem.Folder, apps: List<ComponentKey>): List<LayoutChange> {
        val leaving = apps.filter { it in folder.folder.apps }
        if (leaving.isEmpty()) return emptyList()
        val remaining = folder.folder.apps.filterNot { it in leaving }
        if (remaining.size > 1) return leaving.map { LayoutChange.RemoveFromFolder(folder.folder.id, it) }
        return buildList {
            remaining.singleOrNull()?.let { last ->
                add(LayoutChange.Move(GridItem.App(last), folder.placement, folder.zone))
            }
            add(LayoutChange.RemoveFromGrid(GridItem.Folder(folder.folder.id)))
        }
    }

    /**
     * Adds [components] to folder [folderId] — the folder's own *Add apps* picker committing.
     *
     * **`AddToFolder` already carries most of the semantics**, which is why this is not the drag path repeated N
     * times: the repository detaches the app from whatever folder held it, appends it at the end, and deletes its
     * grid placement, so an app arriving from a home page or from another folder is one change either way.
     *
     * What it does *not* carry is **auto-dissolve**. Detaching the second-to-last app from another folder leaves that
     * folder holding one, which is the state a folder is never supposed to rest in — so the departures are collected
     * per source folder and asked once, [leaveFolderChanges]'s collection form. Per source rather than per app for
     * the reason that overload exists: two apps out of one folder, asked separately, both conclude it survives.
     *
     * Apps already in this folder are dropped rather than re-appended, so a picker that offered one by mistake
     * cannot silently reorder the folder.
     */
    fun addAppsToFolder(folderId: Long, components: List<ComponentKey>) {
        val target = folderById(folderId) ?: return
        val added = components.distinct().filterNot { it in target.folder.apps }
        if (added.isEmpty()) return
        val departures = added
            .mapNotNull { app -> folderHolding(app)?.takeIf { it != folderId }?.let { it to app } }
            .groupBy({ it.first }, { it.second })
            .flatMap { (from, apps) -> folderById(from)?.let { leaveFolderChanges(it, apps) }.orEmpty() }
        applyChanges(added.map { LayoutChange.AddToFolder(folderId, it) } + departures)
    }

    /** The placed folder [folderId], or null when it is gone (dissolved, or its definition not resolved yet). */
    private fun folderById(folderId: Long): HomeItem.Folder? =
        state.value.items.filterIsInstance<HomeItem.Folder>().firstOrNull { it.folder.id == folderId }

    /**
     * The folder **on this surface** that currently holds [app], or null when it is loose on a grid or not here at
     * all. Covers both zones, since a dock folder holds an app exactly as a pager one does.
     *
     * This is what a landing asks to know which folder is owed a departure, and it deliberately asks about
     * *membership* rather than about where the drag started (`AppCollectionHostState.dragSourceCollectionId`). For a
     * drag lifted inside one of home's folders the two agree — nothing is written until the drop, so it is a member —
     * but they part company for an app arriving from the APPS drawer, which can already be in a home folder while
     * having been lifted somewhere else entirely. Membership is the question the write actually depends on.
     *
     * Scoped to home's own folders on purpose: an app may sit in an APPS-surface folder *and* on home, which is no
     * contradiction — two surfaces, two arrangements. The duplicate only exists when one surface would show the app
     * both loose on its grid and inside one of its own folders.
     */
    fun folderHolding(app: ComponentKey): Long? =
        state.value.items.filterIsInstance<HomeItem.Folder>().firstOrNull { app in it.folder.apps }?.folder?.id

    /**
     * [folderHolding] for icon containers, and it takes a whole [GridItem] because a container holds folders as
     * well as apps — the two things a drag can lift out of one.
     */
    fun iconContainerHolding(item: GridItem): Long? {
        val member = item.asIconItem() ?: return null
        return state.value.items.filterIsInstance<HomeItem.IconContainer>()
            .firstOrNull { member in it.container.items }?.container?.id
    }

    /**
     * Commits an icon dragged **out** of icon container [containerId] and released on a grid — the container's
     * counterpart of [dropExtractedApp] and [mergeExtractedApp], collapsed into one because the two differ here
     * only in what the landing writes.
     *
     * **The removal goes first, and that ordering is load-bearing.** `RemoveFromIconContainer` deletes by component
     * (or folder id) rather than by *container*, since the store's uniqueness index already says an item is in at
     * most one — so running it after a landing that filed the item into another container would delete the row that
     * landing had just written, and the icon would vanish from both. Leading with it is safe for the same reason:
     * every add detaches first anyway.
     */
    fun dropExtractedIcon(containerId: Long, item: GridItem, plan: PlacementPlan, zone: HomeZone) {
        val member = item.asIconItem() ?: return
        val landing = if (plan.intent == DropIntent.MERGE) {
            // Null means the merge has become impossible (the target is gone). Nothing is written at all then —
            // dropping the removal on its own would strand the icon in neither the container nor the grid.
            mergeChanges(item, plan.footprint, zone) ?: return
        } else {
            plan.moves.map { (moved, to) -> LayoutChange.Move(moved, to, zone) } +
                LayoutChange.Move(item, plan.footprint, zone)
        }
        applyChanges(listOf(LayoutChange.RemoveFromIconContainer(containerId, member)) + landing)
    }

    /**
     * Builds the change for a drop that merged the dragged item onto whatever sits at [targetPlacement] in
     * [zone]: app→app creates a new folder at the target's cell; app→folder appends to it. Returns null when there
     * is no valid merge (target gone, or a combination not yet supported — folder-on-app, widgets and containers
     * arrive with those item types). Kept in the ViewModel so the drop handler stays logic-free.
     *
     * [zone] is part of *identifying the target*, not just of writing the result: each zone has its own coordinate
     * space, so a placement alone matches items in every zone at once.
     */
    fun mergeChanges(dragged: GridItem, targetPlacement: GridPlacement, zone: HomeZone): List<LayoutChange>? {
        val target = state.value.items.firstOrNull {
            it.gridItem != dragged && it.placement == targetPlacement && it.zone == zone
        } ?: return null
        val draggedApp = (dragged as? GridItem.App)?.component
        return when (target) {
            // Nothing merges onto either kind of widget: a widget is not an app and a widget container holds only
            // widgets, so nothing an icon drag carries can go there. Returning null is what makes the drop fall
            // through to an ordinary push, which is the honest outcome — the finger is over something that cannot
            // receive what it is holding. It mirrors `canMerge`, which is what stops the ring being offered at all.
            is HomeItem.Widget, is HomeItem.WidgetContainer -> null
            is HomeItem.App -> draggedApp?.let {
                listOf(
                    LayoutChange.CreateFolder(
                        label = DEFAULT_FOLDER_LABEL,
                        apps = listOf(target.info.componentKey, it),
                        at = targetPlacement,
                        zone = zone,
                    ),
                )
            }

            is HomeItem.Folder -> draggedApp?.let { listOf(LayoutChange.AddToFolder(target.folder.id, it)) }
            // **One op is the whole move**, unlike the folder paths: `AddToIconContainer` detaches the item from
            // wherever it was — its grid cell, its folder, another container — as part of filing it, so there is no
            // removal to pair with it. That detach is the store keeping its own invariant rather than a courtesy to
            // this caller; see `LayoutRepositoryImpl.detachIconItem`.
            is HomeItem.IconContainer ->
                dragged.asIconItem()?.let { listOf(LayoutChange.AddToIconContainer(target.container.id, it)) }
        }
    }

    /**
     * First-run default: with nothing placed, lay the first apps onto the grid in reading order — one app per
     * *visual* cell, left→right, top→bottom. Fills all but the **last visual row**, so the page keeps slack.
     * This matters: the free-placement push engine rearranges by shoving occupants into empty cells, so a
     * 100%-full page can't be rearranged at all (every drop but the origin reads INVALID). Overflowing a truly
     * full page onto the next page is future work; for now the seed simply doesn't pack the grid. Idempotent —
     * a persisted layout short-circuits it.
     *
     * Each app occupies a whole visual cell, which is `cellMultiplier` logical cells on each axis; visual
     * coordinates are scaled by the multiplier so the stored placements are in the grid's logical space (and so
     * a future sub-cell item can sit between them without any migration).
     *
     * **Only [HomeZone.MAIN] is seeded — the dock deliberately starts empty.** An app lives in exactly one place,
     * so seeding the dock would mean carving apps out of this list, and picking *which* apps belong in a dock is a
     * presentation default worth getting right on its own (with a picker) rather than guessing here. Until then the
     * dock is filled by dragging an app into it, which is the flow that needs proving first.
     */
    private suspend fun seedIfEmpty(config: GridConfig) {
        if (layoutRepository.placements(ORIENTATION).first().isNotEmpty()) return
        val mult = config.cellMultiplier
        val seedRows = (config.visualRows - 1).coerceAtLeast(1)
        val apps = appRepository.observeApps().first().take(seedRows * config.visualCols)
        val moves = apps.mapIndexed { index, app ->
            LayoutChange.Move(
                item = GridItem.App(app.componentKey),
                to = GridPlacement(
                    page = 0,
                    row = (index / config.visualCols) * mult,
                    col = (index % config.visualCols) * mult,
                    rowSpan = mult,
                    colSpan = mult,
                ),
                zone = HomeZone.MAIN,
            )
        }
        layoutRepository.apply(ORIENTATION, moves)
    }

    companion object {
        val ORIENTATION = Orientation.PORTRAIT
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_FOLDER_LABEL = "Folder"

        /**
         * A new container's footprint, in **visual** cells on each axis.
         *
         * Two rather than one because one visual cell is an app's footprint, and a group of icons drawn at an app's
         * size is a group of smudges: the container has to be bigger than the things it holds to be worth having.
         * It is a starting size, not a rule — a container is resized like any other placed item.
         */
        /**
         * How many **visual** cells a container takes each way when it is placed — multiplied by the grid's cell
         * multiplier at the point of use.
         *
         * `internal` because the widget picker quotes it: its preview draws a square and labels it "2 × 2", and a
         * literal there would be right today and silently wrong the first time this moved. The number belongs to
         * whatever *places* a container, which is this.
         */
        internal const val ContainerSpan = 2
    }
}

/**
 * The two inputs that decide **which** grids HOME has and how they resolve: the posture it is drawn on, and the
 * pairing it is drawing.
 *
 * A pair rather than two flows because every setting below depends on both, and because [layout] changes the *slot*
 * rather than the value — `HOME_DOCK` and `HOME_WIDGET_AREA` are different grids with different blueprints, so a
 * layout change is not a re-resolve of the same question but a different question.
 */
private data class HomePosture(val device: DeviceConfiguration, val layout: HomeLayout) {

    /** HOME's two grids in this pairing — the main area's and the side zone's. */
    val slots: List<GridSlot> get() = listOf(layout.mainSlot, sideSlot)

    /** The side zone's grid: the dock, or the widget area. */
    val sideSlot: GridSlot get() = layout.sideSlot

    /**
     * The grids that draw **icon cells**, which is the subset that has icon sizing to resolve.
     *
     * The widget area is the one exclusion, and it is a property of the blueprint rather than a name checked here:
     * a grid whose `icon` is null draws something else, and `SettingsRepository.iconSizing` throws for it by design.
     */
    val iconSlots: List<GridSlot> get() = slots.filter { it.blueprint.icon != null }
}

/**
 * The settings-resolved half of [HomeState], assembled before it joins the content half.
 *
 * Exists because `combine` stops at five flows and home needs more; it is not a second state object. Every field but
 * [layout] is "not yet" until the surface reports its device, since all of them are resolved per configuration —
 * [layout] has a real default because the surface must choose what to compose before anything is resolved.
 */
private data class HomeSizing(
    val icon: Map<GridSlot, IconSizing>,
    val main: HomeMainSizing?,
    val side: SideZoneSizing?,
    val padding: Map<GridSlot, Int>,
    val layout: HomeLayout,
)

/**
 * The definition stores a placement resolves through — see `HomeViewModel.definitions`.
 *
 * Two flows in one value rather than two more arguments to a `combine` that has run out of them, exactly as
 * [HomeSizing] groups the configuration sources.
 */
private data class HomeDefinitions(
    val folders: List<Folder>,
    val widgets: List<WidgetInfo>,
    val iconContainers: List<IconContainer>,
    val widgetContainers: List<WidgetContainer>,
)

/**
 * HOME's coordinate placements flattened into a single top-to-bottom order — **the vertical list's seed**.
 *
 * Page, then row, then column — which is what makes switching to the list layout hand the user their apps in the
 * arrangement they already recognize. It runs **once**, into the list's own store; reading it live and writing back
 * through it is what makes a list reorder destroy the grid underneath.
 *
 * Only [HomeZone.MAIN] and only apps: the dock keeps its own contents across a layout change (it is simply not drawn
 * on the other one), and a folder has no row in a list that holds apps only.
 */
private fun readingOrder(placed: Map<GridItem, PlacedItem>): List<ComponentKey> =
    placed.entries
        .filter { (item, at) -> item is GridItem.App && at.zone == HomeZone.MAIN }
        .sortedWith(
            compareBy({ it.value.placement.page }, { it.value.placement.row }, { it.value.placement.col }),
        )
        .map { (item, _) -> (item as GridItem.App).component }

/**
 * Folds coordinate [changes] into a placement map — the in-memory mirror of what the repository persists.
 *
 * A `Move` writes the change's **zone** along with the cell, which is what makes a cross-zone drag (home → dock)
 * optimistic like any other: the item simply re-keys into the other zone's grid. It is also why zone must be part
 * of the mirrored value — carrying only the coordinate would silently keep a moved item in its old zone until the
 * write came back.
 */
private fun Map<GridItem, PlacedItem>.withApplied(changes: List<LayoutChange>): Map<GridItem, PlacedItem> =
    toMutableMap().apply {
        changes.forEach { change ->
            when (change) {
                is LayoutChange.Move -> put(change.item, PlacedItem(change.to, change.zone))
                is LayoutChange.RemoveFromGrid -> remove(change.item)
                // **Filing an icon into a container takes it off the grid**, and mirroring that here is what stops
                // it being drawn twice for the frame or two the write takes: the store's `detachIconItem` deletes
                // the placement, so without this the old cell keeps its icon until the echo arrives. The container's
                // *contents* still wait for the store — they come from a definition flow this map knows nothing
                // about — so the icon briefly disappears rather than briefly duplicating, which is the better of
                // the two and the same trade `CreateFolder` already makes.
                is LayoutChange.AddToIconContainer -> remove(change.item.asGridItem())
                else -> Unit // the remaining membership ops don't move grid placements
            }
        }
    }
