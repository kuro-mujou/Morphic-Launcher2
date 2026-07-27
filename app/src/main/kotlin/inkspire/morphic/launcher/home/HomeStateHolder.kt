package inkspire.morphic.launcher.home

import inkspire.morphic.core.common.scope.ApplicationScope
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Assembles the home render [state] from the two repositories and seeds a starter layout on first run. Kept out
 * of the composable so the surface stays declarative and this logic is testable (the POST_FIX_CLEANUP rule:
 * no view-model logic living in the UI).
 *
 * It joins [LayoutRepository.placements] (where things sit) with [AppRepository.observeApps] (what they are),
 * yielding the [PlacedApp]s the grid draws. On construction it refreshes the app cache and, if nothing is yet
 * placed, drops the first apps onto the grid so an empty database still shows a populated home — real
 * default-layout curation is a later product concern.
 *
 * Orientation is fixed to [Orientation.PORTRAIT] for now; wiring the live orientation is a follow-up.
 */
class HomeStateHolder(
    private val layoutRepository: LayoutRepository,
    private val appRepository: AppRepository,
    private val scope: ApplicationScope,
) {
    val state: StateFlow<HomeState> =
        combine(
            layoutRepository.placements(ORIENTATION),
            appRepository.observeApps(),
        ) { placements, apps ->
            val infoByComponent = apps.associateBy { it.componentKey }
            HomeState(
                apps = placements.mapNotNull { (item, placed) ->
                    val app = item as? GridItem.App ?: return@mapNotNull null
                    val info = infoByComponent[app.component] ?: return@mapNotNull null
                    PlacedApp(info, placed.placement)
                },
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState(emptyList()))

    init {
        scope.launch {
            appRepository.refresh()
            seedIfEmpty()
        }
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
