package inkspire.morphic.launcher.home

import inkspire.morphic.core.common.scope.ApplicationScope
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.Orientation
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
 * Assembles the home render [state] and owns the write path. Kept out of the composable so the surface stays
 * declarative and this logic is testable (the POST_FIX_CLEANUP rule: no view-model logic in the UI).
 *
 * **Optimistic placements.** The durable store is Room via [LayoutRepository], but the UI reads from an
 * in-memory [placements] flow: seeded once from the database on start, then updated **immediately** on every
 * [applyChanges] before the write is dispatched. Because this holder is the *sole* writer of home placements,
 * the database always converges to what's shown — so a drop lands instantly with no round-trip flicker, and the
 * write just makes it durable. App metadata, by contrast, streams live from [AppRepository.observeApps] (installs
 * and removals should show through).
 *
 * On construction it refreshes the app cache and, if nothing is placed, seeds the first apps so an empty
 * database still shows a populated home. Orientation is fixed to [Orientation.PORTRAIT] for now.
 */
class HomeStateHolder(
    private val layoutRepository: LayoutRepository,
    private val appRepository: AppRepository,
    private val scope: ApplicationScope,
) {
    private val placements = MutableStateFlow<Map<GridItem, GridPlacement>>(emptyMap())

    val state: StateFlow<HomeState> =
        combine(placements, appRepository.observeApps()) { placed, apps ->
            val infoByComponent = apps.associateBy { it.componentKey }
            HomeState(
                apps = placed.mapNotNull { (item, placement) ->
                    val app = item as? GridItem.App ?: return@mapNotNull null
                    infoByComponent[app.component]?.let { PlacedApp(it, placement) }
                },
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState(emptyList()))

    init {
        scope.launch {
            appRepository.refresh()
            seedIfEmpty()
            // Home renders the MAIN zone only for now; drop the zone, keep the coordinate.
            placements.value = layoutRepository.placements(ORIENTATION).first().mapValues { it.value.placement }
        }
    }

    /** Applies layout [changes] optimistically to [placements] (so the UI updates now), then persists them. */
    fun applyChanges(changes: List<LayoutChange>) {
        if (changes.isEmpty()) return
        placements.value = placements.value.withApplied(changes)
        scope.launch { layoutRepository.apply(ORIENTATION, changes) }
    }

    /** First-run default: with nothing placed, lay the first [ROWS] × [COLS] apps onto the grid in reading
     *  order. Idempotent — a persisted layout short-circuits it. */
    private suspend fun seedIfEmpty() {
        if (layoutRepository.placements(ORIENTATION).first().isNotEmpty()) return
        val apps = appRepository.observeApps().first().take(ROWS * COLS)
        val moves = apps.mapIndexed { index, app ->
            LayoutChange.Move(
                item = GridItem.App(app.componentKey),
                to = GridPlacement(page = 0, row = index / COLS, col = index % COLS),
            )
        }
        layoutRepository.apply(ORIENTATION, moves)
    }

    companion object {
        val ORIENTATION = Orientation.PORTRAIT
        const val ROWS = 5
        const val COLS = 4
        private const val STOP_TIMEOUT_MS = 5_000L
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
