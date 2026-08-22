package inkspire.morphic.core.designsystem.drag

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class ScrollEdge { TOP, BOTTOM }

/**
 * **Reaching content that is off-screen while dragging**: while the finger is held near the top or bottom of a
 * scrolling viewport, scroll the content under it.
 *
 * The vertical counterpart of `EdgeFlipEffect`, and it exists for the same reason: a surface that scrolls has to
 * gate its own scroll gesture off while an item is in flight, or the drag and the scroll fight over one finger —
 * which leaves the content past the fold unreachable unless something else moves it.
 *
 * **Speed ramps with depth** rather than switching on at a fixed rate: crossing into the band nudges the content,
 * pressing to the very edge runs at [maxPerSecond]. A fixed rate makes the boundary feel like a trapdoor, and it
 * has to be slow enough to be controllable, which then makes a long list tedious. Paced by [withFrameNanos] and
 * multiplied by the real frame delta, so the speed is in dp per *second* and doesn't change with frame rate.
 *
 * Stops at the ends: when the scroll consumes nothing there is nowhere left to go, so the loop exits rather than
 * spinning a frame callback for the rest of the drag.
 *
 * @param scrollState any [ScrollableState] — a `verticalScroll`'s `ScrollState` (the category pager's pages) or a lazy
 *   list/grid state (the category card's grid). Deliberately the interface rather than one concrete state: all this
 *   needs is `scrollBy`, and the two callers happen to scroll by different mechanisms.
 * @param bounds the scrolling viewport in root coordinates, or null before it is measured.
 * @param fingerInRoot where the dragged finger is, or **null when no drag concerns this scroller** — a page that
 *   isn't the one being dragged over must not scroll itself, so the caller passes null unless the drag is its own.
 *   This mirrors how `EdgeFlipEffect` is told whose drag it is.
 */
@Composable
fun DragAutoScrollEffect(
    scrollState: ScrollableState,
    bounds: Rect?,
    fingerInRoot: Offset?,
    edge: Dp = 72.dp,
    maxPerSecond: Dp = 900.dp,
) {
    val density = LocalDensity.current
    val edgePx = with(density) { edge.toPx() }
    val maxPxPerSecond = with(density) { maxPerSecond.toPx() }

    val active: ScrollEdge? = when {
        bounds == null || fingerInRoot == null -> null
        fingerInRoot.y < bounds.top + edgePx -> ScrollEdge.TOP
        fingerInRoot.y > bounds.bottom - edgePx -> ScrollEdge.BOTTOM
        else -> null
    }

    // How far into the band the finger is, 0f at the inner boundary and 1f at the edge. Read live inside the loop
    // rather than keyed into the effect: it changes on every finger move, and restarting the scroll each time would
    // reset the frame clock and stutter.
    val depth by rememberUpdatedState(
        when {
            bounds == null || fingerInRoot == null || active == null -> 0f
            active == ScrollEdge.TOP -> ((bounds.top + edgePx - fingerInRoot.y) / edgePx).coerceIn(0f, 1f)
            else -> ((fingerInRoot.y - (bounds.bottom - edgePx)) / edgePx).coerceIn(0f, 1f)
        },
    )

    LaunchedEffect(active, scrollState) {
        val direction = when (active) {
            null -> return@LaunchedEffect
            ScrollEdge.TOP -> -1f
            ScrollEdge.BOTTOM -> 1f
        }
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = (frame - previousFrame) / NANOS_PER_SECOND
            previousFrame = frame
            val delta = direction * maxPxPerSecond * depth * seconds
            // Nothing consumed means the end of the content: stop, rather than asking for a frame callback per
            // frame for the rest of the gesture.
            if (delta != 0f && scrollState.scrollBy(delta) == 0f) break
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
