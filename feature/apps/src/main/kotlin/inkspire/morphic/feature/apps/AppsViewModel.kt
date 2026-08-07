package inkspire.morphic.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.category.AppCategorizer
import inkspire.morphic.data.layout.AppsOrderRepository
import inkspire.morphic.data.layout.AppsCategoryChange
import inkspire.morphic.data.layout.AppsPagerChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.reconcileReportedOrder
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * The settings-resolved half of [AppsState], assembled before it joins the content half.
 *
 * Exists because `combine` stops at five flows; it is not a second state object. Every field is "not yet" until the
 * surface reports its device, since all of it is resolved per device configuration.
 */
private data class AppsSizing(
    val icon: Map<GridSlot, IconSizing>,
    val cols: Map<GridSlot, Int>,
    val pager: GridConfig?,
    val listRowHeightDp: Int?,
    val padding: Map<GridSlot, Int>,
    val wraps: Map<GridSlot, Boolean>,
    val card: CardChrome?,
)

/**
 * The four **per-device** halves of [AppsSizing], grouped so the outer `combine` stays within its five flows.
 *
 * Kotlin's `Triple` covered three of them and a fourth arrived with the category card's chrome; a named record is
 * what that becomes rather than nested pairs, and it says what the four have in common — each is re-resolved when the
 * device configuration changes, where the three beside it are not.
 */
private data class PerDevice(
    val icon: Map<GridSlot, IconSizing>,
    val cols: Map<GridSlot, Int>,
    val padding: Map<GridSlot, Int>,
    val card: CardChrome?,
)

/**
 * How many entries fit one page of this grid.
 *
 * On the APPS pager `cellMultiplier` is 1, so logical cells and visual ones are the same count — an entry occupies
 * exactly one cell, which is what makes a page's capacity a plain product.
 */
private val GridConfig.perPage: Int get() = rows * cols

/**
 * Screen-level state holder for the APPS surface: streams the app collection, puts it in display order, keeps the
 * pager's stored arrangement in step with what is installed, and launches on tap. Plain MVVM — the UI reads one
 * immutable [state] flow and calls typed methods, with no sealed-intent/reducer layer.
 *
 * **It is per surface, not per layout.** Every layout renders the same collection, so switching layout must not
 * reload anything — which is also why both shapes in [AppsState] are always maintained, even though a given layout
 * reads only one. Keeping the pager's store synced while the list is on screen is the point, not waste: the
 * arrangement is the user's, and it should be current the moment they switch to it.
 *
 * **Two kinds of order, and only one of them is stored.** The derived layouts are a function of the app cache
 * ([apps], A–Z); the pager is an arrangement the user makes, so it lives in [AppsOrderRepository]. This holder is
 * where the two meet — it hands the repository the A–Z order so newly installed apps append in a sensible place,
 * and hands the UI the resolved pages.
 */
class AppsViewModel(
    private val appRepository: AppRepository,
    private val appsOrderRepository: AppsOrderRepository,
    layoutRepository: LayoutRepository,
    private val appLauncher: AppLauncher,
    private val categorizer: AppCategorizer,
    settingsRepository: SettingsRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    /**
     * The device configuration the surface is drawn on, or null until it reports one.
     *
     * **The one thing the UI knows and this holder cannot**: `currentDeviceConfiguration()` is a `@Composable` read of
     * the window. Everything device-dependent is resolved from it here — every grid's size and every grid's icon
     * sizing, both by `data:settings` from its blueprint plus whatever the user changed — which is why this *replaced*
     * a narrower `setPagerGrid(config)` rather than joining it. Pushing the input down beats pushing a derivative of
     * it: the next thing that needs the device costs nothing.
     */
    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    /**
     * Every installed app in display order — **one subscription, shared by all three readers**.
     *
     * Three things collect this: [state], the pager sync, and the category sync. Without [shareIn] each would open
     * its own Room query and re-run the whole pipeline, so a single install would map the table and sort it three
     * times over; the sort is also [flowOn]'d because a locale-aware [Collator] over a few hundred labels has no
     * business on the frame an install lands — the same reason the classifier below is hopped off the main thread.
     *
     * `WhileSubscribed` rather than `Eagerly` so the query closes with the last reader, and `replay = 1` so a reader
     * that arrives late (the pager's collector waits for a capacity) gets the current list instead of waiting for the
     * next database change.
     */
    private val sortedApps: Flow<List<AppInfo>> =
        appRepository.observeApps()
            .map { apps -> apps.sortedWith(LabelOrder) }
            .flowOn(dispatchers.default)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /**
     * The APPS pager's **stored** grid for the reported device — the user's size, resolved from its blueprint with any
     * override applied.
     *
     * Reaches the state so the *screen* can fit it (see [setPagerFit]); it is deliberately **not** the capacity anything
     * paginates against, because a size chosen at one icon size may no longer be drawable at another.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pagerConfig: Flow<GridConfig?> =
        device.flatMapLatest { current ->
            if (current == null) flowOf(null) else settingsRepository.gridConfig(GridSlot.APPS_PAGER, current)
        }

    /**
     * **The page capacity: the stored grid clamped to what the screen can actually draw**, reported by the surface.
     *
     * A `StateFlow` because three things read it and one of them is not a flow at all: the pages come from it, the
     * install sync writes against it, and [applyPager] needs its capacity at the moment of a drop.
     *
     * **Why the fit has to arrive here rather than being applied in the UI.** Every other grid's stored size only
     * decides what is *drawn*, so a surface can clamp it privately. This one is also the number the **store** is
     * paginated against — `apps_pager_item` rows carry an explicit page and in-page slot — so a pager drawn at one size
     * and paginated at another would put items on pages that do not exist, and a drop would compute its slot against a
     * capacity the store does not apply. The clamp therefore has to be upstream of pagination, which means upstream of
     * here.
     *
     * **Null until the surface reports it, and that gate is load-bearing.** Pagination *writes* (`syncPager`), so a
     * capacity guessed for one frame and corrected on the next would write rows twice and visibly reshuffle the pages.
     * Every reader below already treats null as "not yet" — the same rule home states for its own settle effects: a
     * blueprint fallback is not the user's grid, so nothing may act on it.
     *
     * The narrow input [setDevice] replaced (`setPagerGrid`) pushed the *blueprint's* size down, which put a decision
     * the store owns in the UI. This pushes a **runtime bound** the store cannot know instead — the same shape as
     * `DockViewModel.setHeight` being told its row cap.
     */
    private val pagerFit = MutableStateFlow<GridConfig?>(null)

    /**
     * The **visual column count** of each scrolling APPS grid, by slot — resolved, as the icon sizing is.
     *
     * Columns alone, because these three grids have no row count to resolve: their rows are however many the content
     * reaches. That is the same split the repository draws between `gridConfig` and `gridCols`, and `core:model`
     * between `toGridConfig` and `colsFor`, so this is one more place where the shape of the answer follows the grid's
     * own sizing rather than a convention.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val gridCols: Flow<Map<GridSlot, Int>> =
        device.flatMapLatest { current ->
            if (current == null) {
                flowOf(emptyMap())
            } else {
                combine(
                    ScrollingSlots.map { slot -> settingsRepository.gridCols(slot, current).map { slot to it } },
                ) { pairs -> pairs.toMap() }
            }
        }

    /**
     * The stored pager arrangement, re-subscribed whenever the page capacity changes.
     *
     * Keyed on the **fitted** grid rather than on the device, since it is the grid that decides how many entries fit a
     * page — and it now changes without the device changing, whenever the user resizes it *or* grows the icons past
     * what the stored size can carry.
     *
     * Emits empty (rather than nothing) before that grid is known, so the derived layouts still render on the first
     * frame: a `combine` waits for *every* source, and a list that never appeared because the surface had not yet
     * reported its device would be a blank screen for a layout that does not use the pager at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pagerItems: Flow<List<List<IconItem>>> =
        pagerFit.flatMapLatest { config ->
            if (config == null) flowOf(emptyList())
            else appsOrderRepository.pagerPages(ORIENTATION, config.perPage)
        }

    /**
     * The resolved icon sizing for every grid this surface draws icons in, by slot.
     *
     * Resolved in the repository — blueprint default with any user override merged on top — and merely collected here,
     * so no layout has to know that overrides are sparse or keyed per device configuration. Re-subscribed on a device
     * change, because the resolution is per configuration.
     *
     * Empty before a device is reported, in which case a layout falls back to `LocalIconMetrics`' own default for that
     * frame rather than rendering nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val iconSizings: Flow<Map<GridSlot, IconSizing>> =
        device.flatMapLatest { current ->
            if (current == null) {
                flowOf(emptyMap())
            } else {
                combine(
                    IconSlots.map { slot -> settingsRepository.iconSizing(slot, current).map { slot to it } },
                ) { pairs -> pairs.toMap() }
            }
        }

    /**
     * The APPS list's row height for the reported device, in dp — the one grid whose cell height nothing else can
     * decide, so the user does (see `AppsListGrid`). Null until a device is reported, as everything here is.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val listRowHeight: Flow<Int?> =
        device.flatMapLatest { current ->
            if (current == null) flowOf(null) else settingsRepository.rowHeight(GridSlot.APPS_LIST, current)
        }

    /**
     * Each arrangement's horizontal padding, resolved per device — the same shape as [iconSizings] and [gridCols].
     *
     * Over [PaddedSlots], which is every APPS grid: unlike the other two lists there is no grid to leave out, because
     * a margin is the one measurement that applies whether a grid draws icons, tiles or a single lane. That is also
     * why this is a map rather than one value for "the APPS surface" — five arrangements, five settings, and the
     * screen picks by slot exactly as it already does for icons.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val paddings: Flow<Map<GridSlot, Int>> =
        device.flatMapLatest { current ->
            if (current == null) {
                flowOf(emptyMap())
            } else {
                combine(
                    PaddedSlots.map { slot -> settingsRepository.horizontalPadding(slot, current).map { slot to it } },
                ) { pairs -> pairs.toMap() }
            }
        }

    /**
     * The category card's resolved tile chrome for the reported device — its corner, title scale and two paddings.
     *
     * One value rather than a map, unlike its neighbours: exactly one APPS grid draws tiles, so a slot key would have
     * four meaningless entries. `GridSlot.APPS_CARD` is written out at the one call site instead, which is the same
     * choice [listRowHeight] makes for the one grid that declares a row height.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cardChrome: Flow<CardChrome?> =
        device.flatMapLatest { current ->
            if (current == null) flowOf(null) else settingsRepository.cardChrome(GridSlot.APPS_CARD, current)
        }

    /**
     * Everything the settings layer decides about how this surface is drawn, in one value.
     *
     * Folded together for the reason home's `HomeSizing` is: `combine` stops at five flows and the state needs more.
     * It also groups honestly — these change together when a settings section is edited, and none of them is content.
     */
    private val sizing: Flow<AppsSizing> =
        // Six sources against `combine`'s five, so the three **per-device maps** are grouped first. They are the
        // honest three to fold: each is `posture`-gated and each resolves the same way, where the two below are a
        // single value and a setting with no device dimension at all.
        combine(
            combine(iconSizings, gridCols, paddings, cardChrome, ::PerDevice),
            pagerConfig,
            listRowHeight,
            settingsRepository.pagerWraps,
        ) { perDevice, pager, rowHeight, wraps ->
            AppsSizing(
                icon = perDevice.icon,
                cols = perDevice.cols,
                pager = pager,
                listRowHeightDp = rowHeight,
                padding = perDevice.padding,
                wraps = wraps,
                card = perDevice.card,
            )
        }

    val state: StateFlow<AppsState> =
        combine(
            sortedApps,
            pagerItems,
            layoutRepository.folders(),
            appsOrderRepository.categoryContents(),
            sizing,
        ) { apps, pages, folders, categories, configured ->
            val infoByComponent = apps.associateBy { it.componentKey }
            val folderById = folders.associateBy { it.id }
            AppsState(
                apps = apps,
                pagerPages = pages.map { page ->
                    page.mapNotNull { item ->
                        when (item) {
                            // An entry the app cache can't resolve is dropped rather than drawn blank; the store
                            // still holds it, and `syncPager` below removes it once the cache says it is gone.
                            is IconItem.App -> infoByComponent[item.component]?.let(AppsItem::App)
                            is IconItem.Folder -> folderById[item.folderId]?.let { folder ->
                                AppsItem.Folder(folder, folder.apps.mapNotNull(infoByComponent::get))
                            }
                        }
                    }
                },
                categories = categories.map { contents ->
                    // An app the cache can't resolve is dropped rather than drawn blank, as on the pager; the store
                    // keeps it until `syncCategories` hears it is gone.
                    AppsCategory(contents.category, contents.apps.mapNotNull(infoByComponent::get))
                },
                iconSizing = configured.icon,
                gridCols = configured.cols,
                pagerConfig = configured.pager,
                listRowHeightDp = configured.listRowHeightDp,
                horizontalPaddingDp = configured.padding,
                pagerWraps = configured.wraps,
                cardChrome = configured.card,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsState())

    init {
        // The cache is offline-first, so the surface renders from it immediately and this only tops it up. Done
        // here rather than assumed of whoever ran first, so the surface stands alone (the dev harness can open it
        // without home ever having been shown).
        // TODO(B6 data:apps): once the AppEvent listener keeps the cache live (and prunes uninstalls), warming it
        //  belongs to that listener at startup, not to each screen that reads it.
        viewModelScope.launch { appRepository.refresh() }

        // Keep the stored arrangement in step with reality. One collector covers all three cases that change it —
        // first run (an empty store makes every app "new"), an install or uninstall, and a capacity change — which
        // is why there is no separate seed step to drift out of sync with this one. `syncPager` writes nothing when
        // nothing changed, so the common launch costs a read.
        viewModelScope.launch {
            combine(sortedApps, pagerFit) { apps, config -> apps to config }
                .collect { (apps, config) ->
                    if (config != null) {
                        appsOrderRepository.syncPager(ORIENTATION, config.perPage, apps.map { it.componentKey })
                    }
                }
        }

        // Keep the category arrangement in step with what is installed. Separate from the pager's collector because
        // it needs no capacity — a category is one scrolling list — so it can run from the first app-list emission
        // rather than waiting for the surface to measure a grid.
        //
        // Classification is hopped off the main thread: it is a few hundred string operations per app, which is
        // nothing once but is pointless jank on the frame an install lands. `syncCategories` writes nothing when
        // nothing changed, so a normal launch costs one read.
        viewModelScope.launch {
            sortedApps.collect { apps ->
                val assignments = withContext(dispatchers.default) {
                    // A LinkedHashMap over the A–Z list, because the repository appends new apps in iteration order.
                    apps.associate { it.componentKey to categorizer.categoryIdOf(it) }
                }
                appsOrderRepository.syncCategories(assignments)
            }
        }
    }

    /**
     * Supplies the device configuration the surface is drawn on — the UI's one job in this direction.
     *
     * It replaced `setPagerGrid(config)`, in which the UI resolved a blueprint and handed down the product. Reporting
     * the *input* instead means everything else derives here: the stored page size, and every grid's icon sizing.
     * Idempotent, since setting an equal value on a `MutableStateFlow` emits nothing.
     *
     * The one thing that cannot derive from it is the *fit* of that size, which needs a measured window — see
     * [setPagerFit]. That is a genuinely different input rather than the old one returning: a bound, not a default.
     */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /**
     * Reports the pager grid the surface can actually draw — [AppsState.pagerConfig] clamped by `CellFit.fitGridConfig`
     * to the measured window at the current icon sizing.
     *
     * **This becomes the page capacity**, so it is the number the store is paginated against and the number a drop's
     * slot is computed from; see [pagerFit] for why the clamp cannot stay in the UI. The screen owns the call because
     * the fit needs a measured area and the current type scale, neither of which a state holder has — the same division
     * of labour as `DockViewModel.setHeight`, and the same one every `CellFit` caller follows.
     *
     * **Only ever called with a fit of the *stored* grid**, never of a blueprint fallback: paginating against a
     * placeholder would write pages nobody chose and then rewrite them. Idempotent, so the surface may report on every
     * recomposition.
     */
    fun setPagerFit(config: GridConfig) {
        pagerFit.value = config
    }

    /** Opens the app for [component] (a tap). Fire-and-forget — [AppLauncher] swallows a stale component. */
    fun launch(component: ComponentKey) = appLauncher.launch(component)

    /**
     * Commits a plain pager drop: [item] lands at [toSlot] of [toPage].
     *
     * The repository owns what a move *means* — compacting the source page, cascading overflow forward — so this
     * is a message, not a computation, and the UI never has to duplicate those rules in order to preview them.
     */
    fun movePagerItem(item: IconItem, toPage: Int, toSlot: Int) =
        applyPager(listOf(AppsPagerChange.Move(item, toPage, toSlot)))

    /**
     * Merges [dragged] onto whatever sits at [targetSlot] of [targetPage] — the merge-ring drop.
     *
     * App onto app mints a folder holding both; app onto folder joins it. [target] is the entry the drag layer
     * resolved under the finger, passed by identity — see [mergeInto].
     */
    fun mergePagerItem(dragged: ComponentKey, target: IconItem) = applyPager(mergeInto(target, dragged))

    /**
     * Reorders folder [folderId] to the arrangement its overlay [reported] on drop.
     *
     * Reconciled against real membership first ([reconcileReportedOrder]): `ReorderFolder` replaces membership
     * wholesale, and the overlay can only report members it could render, so writing its list verbatim would
     * *delete* anything unresolvable rather than reorder it.
     */
    fun reorderFolder(folderId: Long, reported: List<ComponentKey>) {
        val known = folderById(folderId)?.folder?.apps ?: return
        applyPager(listOf(AppsPagerChange.ReorderFolder(folderId, reconcileReportedOrder(known, reported))))
    }

    /**
     * Commits an app dropped **into** folder [folderId] at a chosen slot: [reported] is the order the overlay
     * dropped (its members plus [incoming]), and [from] is the folder the drag started in, if any.
     *
     * [incoming] joins the known membership before reconciling, since it is a member as of this drop — otherwise
     * the very app being added would be reconciled away as a non-member. When [from] is another folder the whole
     * folder-to-folder move is one batch, so the app is never briefly in both or neither.
     */
    fun addToFolder(folderId: Long, reported: List<ComponentKey>, incoming: ComponentKey, from: Long?) {
        val known = folderById(folderId)?.folder?.apps ?: return
        val order = reconcileReportedOrder(known + incoming, reported)
        applyPager(
            listOf(
                AppsPagerChange.ReorderFolder(folderId, order),
                // The app is in the folder now, so it must stop occupying a slot. A no-op when it arrived from
                // another folder rather than off a page — one shape for both, instead of a condition to get wrong.
                AppsPagerChange.RemoveFromPager(IconItem.App(incoming)),
            ) + leaveFolderChanges(from, folderId, incoming),
        )
    }

    /** Commits an app dragged **out** of folder [from] onto the pager at [toSlot] of [toPage]. */
    fun dropExtractedApp(from: Long, app: ComponentKey, toPage: Int, toSlot: Int) {
        applyPager(
            listOf(AppsPagerChange.Move(IconItem.App(app), toPage, toSlot)) + leaveFolderChanges(from, null, app),
        )
    }

    /**
     * Commits an app dragged out of folder [from] and dropped on [target]'s **merge ring** — folder→folder, or
     * folder→new-folder, in one gesture.
     *
     * Dropping it back on the folder it came from is a no-op: it is still a member, and nothing was written on the
     * way out, so there is genuinely nothing to do.
     */
    fun mergeExtractedApp(from: Long, app: ComponentKey, target: IconItem) {
        if (target is IconItem.Folder && target.folderId == from) return
        applyPager(mergeInto(target, app) + leaveFolderChanges(from, null, app))
    }

    /**
     * The changes that fold [dragged] into [target]: a new folder when the target is an app, joining it when the
     * target is already a folder.
     *
     * Takes the target by **identity**, so nothing here re-derives it from a position. The caller resolved which
     * entry the finger was on against what was actually on screen, and a slot index would have thrown that away —
     * the page renders a gap-shifted order during a drag, so the same index names different entries to the two
     * sides. It is the same reason `AppsPagerChange.CreateFolder` names a neighbour rather than a slot.
     */
    private fun mergeInto(target: IconItem, dragged: ComponentKey): List<AppsPagerChange> = when (target) {
        is IconItem.App -> listOf(AppsPagerChange.CreateFolder(DEFAULT_FOLDER_LABEL, target.component, dragged))
        is IconItem.Folder -> joinFolder(target.folderId, dragged)
    }

    /**
     * The changes that put [app] into folder [folderId] the quick way — appended at the end, as a merge-ring drop
     * does. Two ops because they are two stores: it joins the folder's contents, and it stops occupying a pager
     * slot. The second is a no-op when the app was never on a page.
     */
    private fun joinFolder(folderId: Long, app: ComponentKey): List<AppsPagerChange> = listOf(
        AppsPagerChange.AddToFolder(folderId, app),
        AppsPagerChange.RemoveFromPager(IconItem.App(app)),
    )

    /**
     * The changes that take [app] out of folder [from] — the half every landing shares, so the paths cannot
     * disagree about what leaving a folder means. Empty when the app didn't come from a folder, or when it is
     * going back into the same one (a re-entry is a reorder, not a move).
     *
     * **Auto-dissolve:** a folder holds ≥ 2 apps, so removing the second-last would leave a folder of one. Instead
     * the folder is dissolved and the survivor takes over its slot on the pager — the ordered-surface equivalent of
     * home's "the last app inherits its cell".
     */
    private fun leaveFolderChanges(from: Long?, into: Long?, app: ComponentKey): List<AppsPagerChange> {
        if (from == null || from == into) return emptyList()
        val folder = folderById(from) ?: return emptyList()
        if (app !in folder.folder.apps) return emptyList()
        val remaining = folder.folder.apps.filter { it != app }
        return if (remaining.size > 1) {
            listOf(AppsPagerChange.RemoveFromFolder(from, app))
        } else {
            listOf(
                AppsPagerChange.RemoveFromFolder(from, app),
                AppsPagerChange.DissolveFolder(from, remaining.singleOrNull()),
            )
        }
    }

    /** The placed folder [folderId], or null when it is gone (dissolved, or not resolved yet). */
    private fun folderById(folderId: Long): AppsItem.Folder? =
        state.value.pagerPages.firstNotNullOfOrNull { page ->
            page.filterIsInstance<AppsItem.Folder>().firstOrNull { it.folder.id == folderId }
        }

    /**
     * Commits a category-pager drop: [app] lands at [toSlot] of [toCategory].
     *
     * One method for both reorder and re-file, because on that surface they are one thing — a page *is* a category,
     * so the destination id carries the difference and there is nothing else to decide here.
     */
    fun moveCategoryItem(app: ComponentKey, toCategory: String, toSlot: Int) {
        viewModelScope.launch {
            appsOrderRepository.applyCategory(listOf(AppsCategoryChange.Move(app, toCategory, toSlot)))
        }
    }

    /**
     * Commits a reorder within [categoryId]: [reported] is the order that category's expansion dropped.
     *
     * **Unreconciled on purpose**, unlike the folder reorders above — and that asymmetry is the point rather than an
     * oversight. A folder's membership reaches this holder intact (it is the folder *definition*), so there is a true
     * list to fold the UI's report onto here; a category's does not — [AppsState.categories] holds only the apps the
     * app cache could resolve, so reconciling against it would compare a filtered list with itself and guard nothing.
     * `AppsCategoryChange.Reorder` therefore does it in the store, where the full membership lives, which also makes
     * the op incapable of changing membership at all.
     */
    fun reorderCategory(categoryId: String, reported: List<ComponentKey>) {
        viewModelScope.launch {
            appsOrderRepository.applyCategory(listOf(AppsCategoryChange.Reorder(categoryId, reported)))
        }
    }

    private fun applyPager(changes: List<AppsPagerChange>) {
        if (changes.isEmpty()) return
        // The **fitted** capacity, which is the one the pages the user just dropped onto were paginated at. Reading the
        // stored size here instead would compact and cascade against a page size nothing drew.
        val perPage = pagerFit.value?.perPage ?: return
        viewModelScope.launch { appsOrderRepository.applyPager(ORIENTATION, perPage, changes) }
    }

    companion object {
        val ORIENTATION = Orientation.PORTRAIT

        /**
         * The APPS grids whose rows come from their content, so only their columns are configurable.
         *
         * [GridSlot.APPS_CARD] belongs here: a card grid's rows are however many its content reaches, so only its lane
         * count is stored. It is in [IconSlots] as well, since its preview slots are icons with sizing of their own.
         */
        private val ScrollingSlots = listOf(GridSlot.APPS_SCROLL, GridSlot.APPS_CATEGORY, GridSlot.APPS_CARD)

        /**
         * Every grid this surface draws — the one list with no exclusions, because every grid has edges.
         *
         * The pager is in it and absent from [ScrollingSlots] (its rows are fixed, so it is sized by `gridConfig`);
         * the card is in both. Padding cares about none of those distinctions, which is why this list is simply all
         * five.
         */
        private val PaddedSlots = listOf(
            GridSlot.APPS_LIST,
            GridSlot.APPS_SCROLL,
            GridSlot.APPS_PAGER,
            GridSlot.APPS_CATEGORY,
            GridSlot.APPS_CARD,
        )

        /**
         * The grids this surface draws icons in — the APPS grids plus [GridSlot.FOLDER], which renders both a pager
         * folder and an expanded category card.
         *
         * **[GridSlot.APPS_CARD] is in it**, which reverses what this said. It was excluded on the grounds that a card
         * is a tile rather than an icon cell — true of the card, and wrong about its *contents*: the four preview slots
         * are icons and now declare their own sizing. Leaving it out did not merely skip a lookup, it silently handed
         * the card someone else's: an unresolved slot falls back to `LocalIconMetrics`, which inside `AppsCategoryCard`
         * is the **folder's**, so the cards drew labels the card grid explicitly turns off while the settings preview
         * drew none. A missing entry here is not a default, it is whatever the ambient value happens to be.
         */
        private val IconSlots = listOf(
            GridSlot.APPS_LIST,
            GridSlot.APPS_SCROLL,
            GridSlot.APPS_PAGER,
            GridSlot.APPS_CATEGORY,
            GridSlot.APPS_CARD,
            GridSlot.FOLDER,
        )
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_FOLDER_LABEL = "Folder"

        /**
         * A–Z by label, **locale-aware**, then by component as a tie-break.
         *
         * A [Collator] rather than L1's `sortedBy { label.lowercase() }`: lowercasing compares raw UTF-16, which
         * puts every accented letter after `Z` (so a Vietnamese or French app list breaks into two alphabets) and
         * gets Turkish dotless-i wrong. The collator sorts by the *current locale's* rules, which is what a user
         * scanning an alphabetical list expects. Default (tertiary) strength on purpose — a primary-strength
         * collator treats `a` and `ă` as equal, which is right for *searching* and wrong for *ordering*.
         *
         * The component tie-break makes the order total: two apps can share a label (a work-profile clone of a
         * personal app is the common one), and without it their relative order would depend on the cache's
         * emission order and could visibly swap between refreshes — including, now, changing where a newly
         * installed app lands on the pager.
         */
        private val LabelOrder: Comparator<AppInfo> = run {
            val collator = Collator.getInstance()
            Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.componentKey.flatten() }
        }
    }
}
