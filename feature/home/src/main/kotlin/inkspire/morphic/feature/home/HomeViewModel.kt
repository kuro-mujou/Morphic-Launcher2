package inkspire.morphic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
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
 * **Optimistic placements.** The durable store is Room via [LayoutRepository], but the UI reads from an
 * in-memory [placements] flow: seeded once from the database on start, then updated **immediately** on every
 * [applyChanges] before the write is dispatched. Because this holder is the *sole* writer of home placements,
 * the database always converges to what's shown — so a drop lands instantly with no round-trip flicker, and the
 * write just makes it durable. App metadata, by contrast, streams live from [AppRepository.observeApps] (installs
 * and removals should show through).
 *
 * **The device comes from the UI.** `currentDeviceConfiguration()` is a `@Composable` read of the window, so the
 * surface reports it via [setDevice] and everything device-dependent is derived here: the grid resolved from the
 * home blueprint, and each zone's icon sizing. That first report also refreshes the app cache and, if nothing is
 * placed, seeds the first apps so an empty database still shows a populated home. Orientation is fixed to
 * [Orientation.PORTRAIT] for now.
 */
class HomeViewModel(
    private val layoutRepository: LayoutRepository,
    private val appRepository: AppRepository,
    private val appLauncher: AppLauncher,
    settingsRepository: SettingsRepository,
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

    val state: StateFlow<HomeState> =
        combine(
            placements,
            appRepository.observeApps(),
            layoutRepository.folders(),
            iconSizings,
        ) { placed, apps, folders, iconSizing ->
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
                iconSizing = iconSizing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState(emptyList()))

    private var configuredFor: GridConfig? = null

    /**
     * Supplies the device configuration the surface is drawn on — the UI's one job in this direction.
     *
     * It replaced `setGridConfig(config)`, in which the UI resolved the home blueprint and handed down the product.
     * Reporting the *input* means the grid **and** each zone's icon sizing derive here, so the next device-dependent
     * thing costs nothing. Idempotent per resolved grid: two devices that resolve to the same dimensions do not reseed.
     */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
        val config = HomePagerGrid.toGridConfig(configuration)
        if (config == configuredFor) return
        val firstConfig = configuredFor == null
        configuredFor = config
        viewModelScope.launch {
            if (firstConfig) appRepository.refresh()
            seedIfEmpty(config)
            placements.value = layoutRepository.placements(ORIENTATION).first()
        }
    }

    /** Opens the app for [component] (a home tap). Fire-and-forget — [AppLauncher] swallows a stale component. */
    fun launch(component: ComponentKey) = appLauncher.launch(component)

    /**
     * Applies layout [changes] optimistically to [placements] (so the UI updates now), then persists them.
     *
     * Pure `Move`/`RemoveFromGrid` stay fully optimistic — no round-trip, no drop flicker. Structural ops
     * (e.g. `CreateFolder`) mint ids we can't predict, so once they've persisted the placement map is resynced
     * from the store to pick up the new item and drop the ones folded into it.
     */
    fun applyChanges(changes: List<LayoutChange>) {
        if (changes.isEmpty()) return
        placements.value = placements.value.withApplied(changes)
        viewModelScope.launch {
            layoutRepository.apply(ORIENTATION, changes)
            if (changes.any { it !is LayoutChange.Move && it !is LayoutChange.RemoveFromGrid }) {
                placements.value = layoutRepository.placements(ORIENTATION).first()
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
