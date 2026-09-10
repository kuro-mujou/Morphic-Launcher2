package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.graphics.wallpaper.WallpaperMorph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The studio's swipe — a scrub through a prepared morph where there is one, and the discrete re-roll it used to be
 * everywhere else.
 *
 * **Two gestures behind one movement, and which one it is settled at touch-down.** A design with a plan seam and a
 * scrub already built drags the picture continuously; anything else keeps the old behaviour, where a swipe past a
 * threshold re-rolls the seed and the new render fades over the old. Deciding once, at the start, is what stops a
 * scrub that was prepared mid-drag from taking over halfway.
 *
 * **This holds the scrub's view state, which the recipe has no room for.** There is no seed meaning "62% of the way
 * to the next window", so how far a finger has come is the screen's to keep — and keeping it here rather than in the
 * ViewModel's state is also what stops sixty recompositions a second moving one float that only the draw pass reads.
 * The decisions the *studio* owns stay with it: what a scrub is at all, and what committing one does to the recipe.
 */
@Stable
internal class ShuffleSwipe(
    private val scope: CoroutineScope,
    private val onCommit: () -> Unit,
    private val onShuffle: () -> Unit,
) {

    /** How far across the morph the finger has come, `0` being the window on screen and `1` the one it is going to. */
    val progress = Animatable(0f)

    /**
     * The morph being drawn, or null when the preview is showing its bitmap.
     *
     * **It outlives the prepared scrub it came from.** Committing moves the recipe on and clears what was prepared,
     * but the bitmap underneath is still the window the swipe *started* from until its replacement is rendered, so
     * dropping this at the commit would play the whole morph backwards in a single frame.
     */
    var live by mutableStateOf<WallpaperMorph?>(null)
        private set

    /** Whether a finger is driving the morph — false for a plain swipe on a design that cannot be scrubbed. */
    var scrubbing by mutableStateOf(false)
        private set

    /** What a touch-down would find prepared; pushed in each composition, read only when a gesture starts. */
    var ready: WallpaperScrub? = null

    private var travelled = 0f

    fun start() {
        travelled = 0f
        val prepared = ready
        scrubbing = prepared != null
        if (prepared == null) return
        live = prepared.morph
        scope.launch { progress.snapTo(0f) }
    }

    /**
     * Carries the finger's [amount] into the morph, [reach] being the swipe that would finish one.
     *
     * **The distance drives it, not the direction.** A shuffle has no sides — either way is the same next window —
     * so dragging back the way you came takes the morph back with you, and a drag that crosses its own start keeps
     * going forwards rather than turning round.
     */
    fun drag(amount: Float, reach: Float) {
        travelled += amount
        if (scrubbing) scope.launch { progress.snapTo((abs(travelled) / reach).coerceIn(0f, 1f)) }
    }

    /** Finishes the morph or puts it back, [threshold] being how far a *discrete* swipe must go to count. */
    fun end(threshold: Float) {
        when {
            !scrubbing -> if (abs(travelled) > threshold) onShuffle()
            progress.value >= CommitAt -> scope.launch {
                progress.animateTo(1f)
                // Committed only once the picture has actually arrived at the window being committed to, so the
                // recipe and the frame on screen never disagree, even for the length of an animation.
                onCommit()
                scrubbing = false
            }
            else -> cancel()
        }
    }

    fun cancel() {
        if (!scrubbing) return
        scope.launch {
            progress.animateTo(0f)
            scrubbing = false
        }
    }

    /** Lets the morph go once neither a finger nor a landing is holding it — see [WallpaperStudioState.landing]. */
    fun settle(landing: Boolean) {
        if (!scrubbing && !landing) live = null
    }

    private companion object {

        /**
         * How far a scrub must have come, as a share of the swipe that finishes one, for letting go to finish it
         * rather than to put it back.
         *
         * **Below this the gesture is cancelled, which is what the reference does too.** Probing it on 2026-08-30
         * found that a sub-threshold drag left the design exactly as it was, and that reading was filed at the time
         * as evidence *against* there being a scrub at all — the wrong conclusion from the right observation, since
         * what it had actually found was this threshold. A drag with no way to change your mind would be the odd one
         * out among every other gesture on the device.
         *
         * A little under half, so a decisive drag that stops short of the middle still reads as having meant it.
         */
        const val CommitAt = 0.4f
    }
}

/**
 * A [ShuffleSwipe] bound to [state], committing and shuffling through the studio.
 *
 * The morph is let go in a [LaunchedEffect] rather than at the commit, because what releases it is a *render* landing
 * — an event the gesture has no part in and cannot wait for.
 */
@Composable
internal fun rememberShuffleSwipe(
    state: WallpaperStudioState,
    onCommit: () -> Unit,
    onShuffle: () -> Unit,
): ShuffleSwipe {
    val scope = rememberCoroutineScope()
    val swipe = remember(scope) { ShuffleSwipe(scope, onCommit, onShuffle) }
    SideEffect { swipe.ready = state.scrub }
    LaunchedEffect(swipe.scrubbing, state.landing) { swipe.settle(state.landing) }
    return swipe
}

/**
 * Drives [swipe] from a horizontal drag over this element.
 *
 * **A swipe across the whole element is a whole morph**, which is the reference's own pacing — its slow swipe took
 * nineteen seconds and the picture kept up the entire way. The threshold below is the *other* gesture's, the discrete
 * re-roll, and stays the shorter travel it always was: a re-roll shows nothing on the way, so asking for a long drag
 * would only make it feel unresponsive.
 */
internal fun Modifier.shuffleSwipe(swipe: ShuffleSwipe): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { swipe.start() },
        onDragEnd = { swipe.end(110.dp.toPx()) },
        onDragCancel = { swipe.cancel() },
    ) { _, amount -> swipe.drag(amount, size.width.toFloat()) }
}
