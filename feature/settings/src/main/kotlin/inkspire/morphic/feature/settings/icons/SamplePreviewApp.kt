package inkspire.morphic.feature.settings.icons

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.data.apps.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * **One installed app for the icon preview to draw**, and a way to ask for a different one — the read-side companion of
 * [IconSizingEdits], held by every section that shows a preview.
 *
 * A real app rather than a drawn placeholder, because that is the preview's whole purpose: an icon's silhouette is what
 * you are judging the size of, and a rounded square would answer a question nobody asked. L1 did the same, injecting its
 * `AppRepository` into each detail screen.
 *
 * **Deterministic per roll, not random per frame.** The pick is an index into the (stable, A–Z) app list rather than a
 * `shuffled().first()` evaluated in composition, which is what L1 did — `remember(apps, reroll) { apps.shuffled()… }`
 * re-picks whenever the app list emits, so its preview could change app while a slider was being dragged. Here [reroll]
 * is the only thing that moves it.
 *
 * Kept as a plain class a ViewModel holds, for the reason [IconSizingEdits] is: four sections need the same two lines,
 * and a base class for two members is how a hierarchy starts.
 */
internal class SamplePreviewApp(
    appRepository: AppRepository,
    scope: CoroutineScope,
) {
    private val roll = MutableStateFlow(0)

    /**
     * The app to draw, or null before the cache has answered — the preview draws its outlines alone until then, which
     * is the half that does not need an app.
     */
    val app: StateFlow<AppInfo?> =
        combine(appRepository.observeApps(), roll) { apps, index ->
            if (apps.isEmpty()) null else apps[index.mod(apps.size)]
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Moves to the next app. Wrapped by `mod`, so it cycles rather than ever running out. */
    fun reroll() {
        roll.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
