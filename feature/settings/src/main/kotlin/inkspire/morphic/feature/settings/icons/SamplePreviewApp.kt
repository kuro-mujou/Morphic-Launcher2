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
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
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

    /**
     * **A run of consecutive apps** starting at the same roll, for a preview that draws more than one — the category
     * card, whose whole subject is a 2×2 of them.
     *
     * It draws from the installed list rather than from a real category on purpose. A category's contents are the
     * user's, so previewing one would show whatever *that* phone happens to hold — and a category with two apps in it
     * leaves half the slots empty, which is exactly the state in which the padding and spacing sliders show nothing.
     * A preview has to draw the full case for the controls to be legible, so it fills every slot regardless.
     *
     * Wraps by `mod` for [app]'s reason, so a short app list repeats rather than coming up empty; a phone with fewer
     * than [count] apps installed is not a case worth a branch, and repeating is the honest degradation.
     *
     * **Hold the result; do not call this per read.** Each call builds its own `stateIn`, so calling it in composition
     * would start a fresh subscription every recomposition. Its one caller keeps it as a ViewModel property, which is
     * what [app] gets for free by being one.
     */
    fun apps(count: Int): StateFlow<List<AppInfo>> =
        combine(appRepository.observeApps(), roll) { apps, index ->
            if (apps.isEmpty()) emptyList() else List(count) { apps[(index + it).mod(apps.size)] }
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Moves to the next app. Wrapped by `mod`, so it cycles rather than ever running out. */
    fun reroll() {
        roll.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
