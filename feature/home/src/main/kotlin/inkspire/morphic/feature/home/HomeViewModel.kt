package inkspire.morphic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
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
 * **Grid comes from the UI.** The concrete [GridConfig] is resolved from the home blueprint against the detected
 * device (a `@Composable` read), so the surface pushes it in via [setGridConfig] rather than this holder guessing
 * dimensions. That first call also refreshes the app cache and, if nothing is placed, seeds the first apps so an
 * empty database still shows a populated home. Orientation is fixed to [Orientation.PORTRAIT] for now.
 */
class HomeViewModel(
    private val layoutRepository: LayoutRepository,
    private val appRepository: AppRepository,
    private val appLauncher: AppLauncher,
) : ViewModel() {
    private val placements = MutableStateFlow<Map<GridItem, GridPlacement>>(emptyMap())

    val state: StateFlow<HomeState> =
        combine(placements, appRepository.observeApps(), layoutRepository.folders()) { placed, apps, folders ->
            val infoByComponent = apps.associateBy { it.componentKey }
            val folderById = folders.associateBy { it.id }
            HomeState(
                items = placed.mapNotNull { (item, placement) ->
                    when (item) {
                        is GridItem.App -> infoByComponent[item.component]?.let { HomeItem.App(it, placement) }
                        is GridItem.Folder -> folderById[item.folderId]?.let { folder ->
                            HomeItem.Folder(folder, folder.apps.mapNotNull(infoByComponent::get), placement)
                        }
                        else -> null // widgets / containers get their cells later
                    }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState(emptyList()))

    private var configuredFor: GridConfig? = null

    /**
     * Supplies the [config] resolved for the current device (detected in the UI). Idempotent per config value:
     * the first call refreshes the app cache and seeds a first-run layout, then loads placements; a later call
     * with a *different* config just reloads. Seeding is guarded on an empty store, so it never clobbers an
     * arranged layout.
     */
    fun setGridConfig(config: GridConfig) {
        if (config == configuredFor) return
        val firstConfig = configuredFor == null
        configuredFor = config
        viewModelScope.launch {
            if (firstConfig) appRepository.refresh()
            seedIfEmpty(config)
            // Home renders the MAIN zone only for now; drop the zone, keep the coordinate.
            placements.value = layoutRepository.placements(ORIENTATION).first().mapValues { it.value.placement }
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
                placements.value = layoutRepository.placements(ORIENTATION).first().mapValues { it.value.placement }
            }
        }
    }

    /**
     * Reorders folder [folderId] to the arrangement the overlay [reported] on drop.
     *
     * The reported order covers only the members the UI could render, so it is reconciled against the real
     * membership first ([reconcileFolderOrder]) — `ReorderFolder` replaces membership wholesale, and writing the
     * UI's list verbatim would delete anything it couldn't draw.
     */
    fun reorderFolder(folderId: Long, reported: List<ComponentKey>) {
        val known = folderById(folderId)?.folder?.apps ?: return
        applyChanges(listOf(LayoutChange.ReorderFolder(folderId, reconcileFolderOrder(known, reported))))
    }

    /**
     * Adds an app dragged in from home into folder [folderId], where [reported] is the arrangement the overlay
     * dropped (its members *plus* [incoming]): set the membership/order and take [incoming] off the home grid.
     *
     * [incoming] joins the known membership before reconciling, since it is a member as of this drop — otherwise
     * the very app being added would be reconciled away as a non-member.
     */
    fun addToFolder(folderId: Long, reported: List<ComponentKey>, incoming: ComponentKey) {
        val known = folderById(folderId)?.folder?.apps ?: return
        applyChanges(
            listOf(
                LayoutChange.ReorderFolder(folderId, reconcileFolderOrder(known + incoming, reported)),
                LayoutChange.RemoveFromGrid(GridItem.App(incoming)),
            ),
        )
    }

    /**
     * Commits an app dragged out of folder [folderId] and dropped on the home grid at [plan] (its footprint,
     * plus the home occupants it pushed): the app lands there and leaves the folder.
     *
     * **Auto-dissolve:** a folder holds ≥ 2 apps, so extracting the second-last one would leave a folder of one.
     * Instead the folder is dissolved — the single remaining app takes over the folder's cell and the folder is
     * deleted (its FK cascades drop the membership + placement rows). Otherwise it's a plain remove-from-folder.
     */
    fun dropExtractedApp(folderId: Long, component: ComponentKey, plan: PlacementPlan) {
        val folder = folderById(folderId) ?: return
        val remaining = folder.folder.apps.filter { it != component }
        val changes = mutableListOf<LayoutChange>()
        plan.moves.forEach { (moved, to) -> changes += LayoutChange.Move(moved, to) } // pushed home occupants
        changes += LayoutChange.Move(GridItem.App(component), plan.footprint) // the extracted app lands here
        if (remaining.size <= 1) {
            remaining.singleOrNull()?.let { last ->
                changes += LayoutChange.Move(GridItem.App(last), folder.placement)
            }
            changes += LayoutChange.RemoveFromGrid(GridItem.Folder(folderId)) // deletes folder (cascades rows)
        } else {
            changes += LayoutChange.RemoveFromFolder(folderId, component)
        }
        applyChanges(changes)
    }

    /** The placed folder [folderId], or null when it is gone (dissolved, or its definition not resolved yet). */
    private fun folderById(folderId: Long): HomeItem.Folder? =
        state.value.items.filterIsInstance<HomeItem.Folder>().firstOrNull { it.folder.id == folderId }

    /**
     * Builds the change for a drop that merged the dragged item onto whatever sits at [targetPlacement]:
     * app→app creates a new folder at the target's cell; app→folder appends to it. Returns null when there is no
     * valid merge (target gone, or a combination not yet supported — folder-on-app, widgets and containers
     * arrive with those item types). Kept in the ViewModel so the drop handler stays logic-free.
     */
    fun mergeChanges(dragged: GridItem, targetPlacement: GridPlacement): List<LayoutChange>? {
        val draggedApp = (dragged as? GridItem.App)?.component ?: return null
        val target = state.value.items.firstOrNull { it.gridItem != dragged && it.placement == targetPlacement }
            ?: return null
        return when (target) {
            is HomeItem.App -> listOf(
                LayoutChange.CreateFolder(
                    label = DEFAULT_FOLDER_LABEL,
                    apps = listOf(target.info.componentKey, draggedApp),
                    at = targetPlacement,
                    zone = HomeZone.MAIN,
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
            )
        }
        layoutRepository.apply(ORIENTATION, moves)
    }

    companion object {
        val ORIENTATION = Orientation.PORTRAIT
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_FOLDER_LABEL = "Folder"
    }
}

/** Folds coordinate [changes] into a placement map — the in-memory mirror of what the repository persists. */
private fun Map<GridItem, GridPlacement>.withApplied(changes: List<LayoutChange>): Map<GridItem, GridPlacement> =
    toMutableMap().apply {
        changes.forEach { change ->
            when (change) {
                is LayoutChange.Move -> put(change.item, change.to)
                is LayoutChange.RemoveFromGrid -> remove(change.item)
                else -> Unit // container/folder membership ops don't move grid placements
            }
        }
    }
