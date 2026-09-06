package inkspire.morphic.feature.apps.layout.alphabet

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AlphabetStripStyle
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The **A–Z index strip**: the letters a surface's content is ordered by, down its trailing edge, scrubbed with a
 * finger.
 *
 * **It scrolls; it does not filter.** Running a finger down it takes the content to that letter and dims everything
 * outside its run, and lifting leaves the content where it arrived — so the gesture is for *finding a place*, and
 * what was found is still there once the finger is gone. Niagara Launcher's behavior, and deliberately not L1's:
 * L1's strip replaced the surface with the letter's apps and put the surface back on release, which makes the whole
 * gesture a preview nobody can act on.
 *
 * **Only letters that lead somewhere.** [labels] is the caller's, and comes from the same buckets it scrolls by, so
 * the strip never offers one that nothing starts with. The finger cannot land on a dead letter, and a shorter list
 * is a bigger target for each of the rest.

 * **Strings, not characters, and reported by position.** The alphabet is the platform's for the user's locale, whose
 * labels can be more than one character — and whose two ends are both labelled `…`, so only a position says which
 * of them a finger is on.
 *
 * **Every letter is a slot of equal height, and the slots are the whole strip.** Both styles depend on that: the
 * finger's y divides straight into an index with no hit list, and the curve reads a letter's distance from the
 * finger in *slots*, so the bow covers the same number of letters on a short strip as on a tall one.
 *
 * @param labels the letters to draw, top to bottom. Empty draws nothing at all rather than an empty rail.
 * @param onLetter which of [labels] is under the finger, and null when it lifts. Fires when that *changes* rather
 *   than on every frame of a drag — the caller scrolls on it, and a scroll per frame is a scroll per frame.
 */
@Composable
internal fun AlphabetStrip(
    labels: List<String>,
    style: AlphabetStripStyle,
    onLetter: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val colors = LocalMorphicColors.current
    var heightPx by remember { mutableStateOf(0) }
    // Where the finger is, in strip-local px, or null when it is not down — the one thing this composable owns.
    // *Which letter* that means is derived rather than stored, so there is no second copy to keep in step.
    var fingerY by remember { mutableStateOf<Float?>(null) }

    val slotPx = if (heightPx == 0) 0f else heightPx.toFloat() / labels.size
    val active = fingerY?.let { y -> (y / slotPx).toInt().coerceIn(labels.indices) }

    // Reported from composition rather than from the pointer loop, which is what makes this a change and not a
    // stream: the loop below runs on every frame of a slow drag, and the same letter re-sent is a `scrollToItem`
    // fighting the one still animating.
    LaunchedEffect(active) { active?.let(onLetter) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            // Wide enough to aim at — the letters themselves are small, and this is the whole target. A fixed width
            // rather than the letters' own, so the rail does not shift under a finger when the glyphs change.
            .width(28.dp)
            .onSizeChanged { heightPx = it.height }
            .pointerInput(labels) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Consumed, and it has to be: this rail sits beside a scroller, and a down left unconsumed here
                    // is one the list happily starts a fling with.
                    down.consume()
                    fingerY = down.position.y
                    try {
                        // The condition carries both ways out — the pointer lifting, and the gesture being taken
                        // away — rather than a `break` for each, which is the same loop written twice.
                        var change = awaitPointerEvent().changes.firstOrNull()
                        while (change != null && change.pressed) {
                            fingerY = change.position.y
                            change.consume()
                            change = awaitPointerEvent().changes.firstOrNull()
                        }
                    } finally {
                        fingerY = null
                        onLetter(null)
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == active) colors.content else colors.contentMuted,
                        modifier = Modifier.graphicsLayer {
                            val bow = bowAt(index, fingerY, slotPx, style)
                            // Toward the content, furthest at the finger. Negative because the rail is on the
                            // trailing edge — the one line a leading-edge strip would have to flip.
                            translationX = -72.dp.toPx() * bow
                            val scale = 1f + bow
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                }
            }
        }
        // **The badge, and only on the curved style.** A standard strip answers with the letter itself, which is
        // enough while nothing has moved; a bowed one has pulled that letter out from under the finger, so it needs
        // somewhere to say what is selected that the finger is not sitting on. Placed by the finger rather than by
        // the letter, since the finger is the thing the eye is already following.
        if (style == AlphabetStripStyle.CURVED) {
            fingerY?.let { y ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(x = -116.dp.roundToPx(), y = (y - 44.dp.toPx() / 2f).roundToInt()) }
                        .size(44.dp)
                        .background(colors.surfaceElevated, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = active?.let(labels::get).orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.content,
                    )
                }
            }
        }
    }
}

/**
 * How near the finger the letter at [index] is — 1 under it, falling smoothly to 0 either side, and 0 whenever there
 * is no finger or the style does not bow.
 *
 * **A Gaussian rather than a linear ramp, and the difference is visible.** Under a ramp the letters next to the peak
 * change size at the same rate as the ones ten away, which reads as a wedge; a bell flattens at the peak and at the
 * tails, which is what makes the rail look bent rather than folded. The width is measured in *slots* — four of them
 * to the point where it has all but vanished — so the bow spans the same number of letters however tall the strip is.
 *
 * Read inside a `graphicsLayer` block, so it runs at draw time and a moving finger redraws without recomposing.
 */
private fun bowAt(index: Int, fingerY: Float?, slotPx: Float, style: AlphabetStripStyle): Float {
    if (style != AlphabetStripStyle.CURVED || fingerY == null || slotPx <= 0f) return 0f
    val slots = ((index + 0.5f) * slotPx - fingerY) / slotPx / 4f
    return exp(-slots * slots)
}
