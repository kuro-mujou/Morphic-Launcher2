package inkspire.morphic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.GridReflow
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.settleDock
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.layout.reconcileReportedOrder
import inkspire.morphic.data.layout.PlacedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen-level state holder for the home surface: assembles the render [state] and owns the write path. Kept out
 * of the composable so the surface stays declarative and this logic is unit-testable (no view-model logic in the
 * UI). Plain MVVM — the UI reads one immutable [state] flow and calls typed methods ([launch], [applyChanges]);
 * there is deliberately no sealed-intent/reducer layer, which is ceremony this small surface does not need.
 *
 * As an [androidx.lifecycle.ViewModel] it is scoped to the hosting screen's `ViewModelStore`, so it survives
 * configuration changes (rotation) and its [viewModelScope] coroutines are cancelled automatically when the
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
    private val appRepository: AppRepository,
    private val appLauncher: AppLauncher,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val placements = MutableStateFlow<Map<GridItem, PlacedItem>>(emptyMap())

    /** The device the surface reports, or null until it does. Null keeps [iconSizings] empty rather than guessing. */
    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    /**
     * Each zone's resolved icon sizing, by slot.
     *
     * Two entries, because the pager and the dock are two grids with two blueprints — which is what makes their
     * icon config independent. Before this landed they shared one value only because nothing provided any and both
     * inherited `LocalIconMetrics`' default.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val iconSizings: Flow<Map<GridSlot, IconSizing>> =
        device.flatMapLatest { current ->
            if (current == null) {
                flowOf(emptyMap())
            } else {
                combine(
                    ZoneSlots.map { slot -> settingsRepository.iconSizing(slot, current).map { slot to it } },
                ) { pairs -> pairs.toMap() }
            }
        }

    /**
     * The main area's grid for the reported device — **the user's size**, resolved from `HOME_MAIN`'s blueprint with
     * any override applied, exactly as the dock's is.
     *
     * A `StateFlow` rather than a plain flow because two things besides the UI read it: the first-run seed needs a
     * grid to lay apps out in, and [fitDockTo] needs one to spill onto. `Eagerly` so both can await it without a
     * subscriber having arrived.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainConfig: StateFlow<GridConfig?> =
        device.flatMapLatest { current ->
            if (current == null) flowOf(null) else settingsRepository.gridConfig(GridSlot.HOME_MAIN, current)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The dock's stored size for the reported device — its height, and the counts it divides that height into.
     *
     * Two flows rather than one because the extent and the dimensions are two settings with two stores, joined here
     * because a `StateFlow` of one state object is what the surface reads. Null until a device is reported, exactly
     * as [iconSizings] is empty until then.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dockSizing: Flow<DockSizing?> =
        device.flatMapLatest { current ->
            if (current == null) {
                flowOf(null)
            } else {
                combine(
                    settingsRepository.dockHeight(current),
                    settingsRepository.gridConfig(GridSlot.HOME_DOCK, current),
                ) { heightDp, grid -> DockSizing(heightDp, grid.visualCols, grid.visualRows) }
            }
        }

    /**
     * Everything the settings layer decides about how home is drawn, in one value.
     *
     * Folded together because `combine` takes five flows and the state needs six: three sources of *configuration*
     * behind one, leaving the three sources of *content* (placements, apps, folders) at the top level. It is also the
     * honest grouping — these three change together when the user edits a section, and none of them is content.
     */
    private val sizing: Flow<HomeSizing> = combine(iconSizings, mainConfig, dockSizing, ::HomeSizing)

    val state: StateFlow<HomeState> =
        combine(
            placements,
            appRepository.observeApps(),
            layoutRepository.folders(),
            sizing,
        ) { placed, apps, folders, configured ->
            val infoByComponent = apps.associateBy { it.componentKey }
            val folderById = folders.associateBy { it.id }
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
                        else -> null // widgets / containers get their cells later
                    }
                },
                iconSizing = configured.icon,
                main = configured.main,
                dock = configured.dock,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState(emptyList()))

    /** Layout writes dispatched but not yet seen land — see the store collector in [init]. */
    private var writesInFlight = 0

    init {
        // Seeding, keyed on the **configured** grid rather than on the device report: the device changes once, the
        // grid changes every time the settings screen writes one, and a seed needs to know how big a page is.
        // `StateFlow` conflates equal values, so this runs on a real change and not on a re-report of the same size.
        // The app cache is refreshed once, before the first seed, since a seed with an empty cache places nothing.
        viewModelScope.launch {
            var refreshed = false
            mainConfig.filterNotNull().collect { config ->
                if (!refreshed) {
                    appRepository.refresh()
                    refreshed = true
                }
                seedIfEmpty(config)
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
    }

    /**
     * Supplies the device configuration the surface is drawn on — the UI's one job in this direction.
     *
     * It replaced `setGridConfig(config)`, in which the UI resolved the home blueprint and handed down the product.
     * Reporting the *input* means both grids **and** each zone's icon sizing derive here, so the next device-dependent
     * thing costs nothing — and, more importantly, they derive from the *store* rather than from the blueprint the UI
     * happened to have to hand.
     */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /** Opens the app for [component] (a home tap). Fire-and-forget — [AppLauncher] swallows a stale component. */
    fun launch(component: ComponentKey) = appLauncher.launch(component)

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
     * Deliberately **not** where L1 put it. L1 reflowed inside the `combine` that assembles its home state and
     * launched the persist *from within that transform* — so the write ran as a side effect of reading state, on
     * every emission that happened to find a stray, racing itself. Here the trigger is the config changing, and the
     * write is an ordinary [applyChanges] like any other.
     */
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
        viewModelScope.launch {
            // Nothing is written until the main grid is known — an eviction with nowhere to go would take an item off
            // the dock and leave it placed nowhere. **Awaited rather than skipped**: the two configs resolve from the
            // same store at roughly the same moment, so a `?: return` here would drop the very first fit whenever the
            // dock's answer arrived first, and nothing would call again until something else changed.
            val main = mainConfig.filterNotNull().first()
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
     * drop, so there is genuinely nothing to do. Recognising that here keeps it one no-op instead of an add-and-remove
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
    private fun leaveFolderChanges(folder: HomeItem.Folder, app: ComponentKey): List<LayoutChange> {
        if (app !in folder.folder.apps) return emptyList()
        val remaining = folder.folder.apps.filter { it != app }
        if (remaining.size > 1) return listOf(LayoutChange.RemoveFromFolder(folder.folder.id, app))
        return buildList {
            remaining.singleOrNull()?.let { last ->
                add(LayoutChange.Move(GridItem.App(last), folder.placement, folder.zone))
            }
            add(LayoutChange.RemoveFromGrid(GridItem.Folder(folder.folder.id)))
        }
    }

    /** The placed folder [folderId], or null when it is gone (dissolved, or its definition not resolved yet). */
    private fun folderById(folderId: Long): HomeItem.Folder? =
        state.value.items.filterIsInstance<HomeItem.Folder>().firstOrNull { it.folder.id == folderId }

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
        val draggedApp = (dragged as? GridItem.App)?.component ?: return null
        val target = state.value.items.firstOrNull {
            it.gridItem != dragged && it.placement == targetPlacement && it.zone == zone
        } ?: return null
        return when (target) {
            is HomeItem.App -> listOf(
                LayoutChange.CreateFolder(
                    label = DEFAULT_FOLDER_LABEL,
                    apps = listOf(target.info.componentKey, draggedApp),
                    at = targetPlacement,
                    zone = zone,
                ),
            )
            is HomeItem.Folder -> listOf(LayoutChange.AddToFolder(target.folder.id, draggedApp))
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
        /** The two grids home draws icons in — its paged main area and its dock, each with its own blueprint. */
        private val ZoneSlots = listOf(GridSlot.HOME_MAIN, GridSlot.HOME_DOCK)

        val ORIENTATION = Orientation.PORTRAIT
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_FOLDER_LABEL = "Folder"
    }
}

/**
 * The settings-resolved half of [HomeState], assembled before it joins the content half.
 *
 * Exists because `combine` stops at five flows and home needs six; it is not a second state object. Every field is
 * "not yet" until the surface reports its device, since all three are resolved per device configuration.
 */
private data class HomeSizing(
    val icon: Map<GridSlot, IconSizing>,
    val main: GridConfig?,
    val dock: DockSizing?,
)

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
                else -> Unit // container/folder membership ops don't move grid placements
            }
        }
    }
