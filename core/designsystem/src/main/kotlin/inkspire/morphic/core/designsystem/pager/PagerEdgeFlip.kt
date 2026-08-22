package inkspire.morphic.core.designsystem.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** How long between flips while the finger stays at the edge. */
private const val EDGE_FLIP_DWELL_MS = 450L

private enum class FlipEdge { LEFT, RIGHT }

/**
 * **Carrying a dragged item to another page**: while the finger is held near the left or right edge of
 * [viewport], flip [pagerState] one page per dwell, stopping at the ends.
 *
 * The counterpart of [launcherPagerSwipe] for a drag rather than a swipe — and it exists *because* of that
 * modifier: a paged drag surface gates page-swipe off while an item is in flight, so the two gestures never fight,
 * which leaves the edge dwell as the only way to change pages mid-drag.
 *
 * **Settling is the whole subtlety.** Moving off the edge changes this effect's key and so cancels it — and
 * canceling inside `animateToPage` stops the animation exactly where it is, parking the pager between two pages
 * with both half on screen. Nothing else would finish the job: swipe is gated off, and the loop that would have
 * continued is the thing that was canceled. So "no edge" is an explicit branch that settles to the nearest page,
 * not an early return. It is a no-op when the pager is already on a boundary ([LauncherPagerState.settleToNearestPage]
 * compares before animating), and `Animatable`'s mutator mutex makes the hand-off from the canceled animation safe.
 *
 * That bug shipped in three hand-rolled copies of this loop — home's paged main area, the APPS pager, and the
 * pager-drag harness — and had to be fixed in each. This is those three collapsed into one.
 *
 * @param viewport the pager's bounds in root coordinates, or null before it has been measured.
 * @param fingerInRoot where the dragged finger is, or **null when no drag concerns this pager** — which is how a
 *   caller says "not mine". On a shared coordinator that matters: a drag inside an open folder must not flip the
 *   pages behind it, so the caller passes null unless its own zone is the active one.
 */
@Composable
fun EdgeFlipEffect(
    pagerState: LauncherPagerState,
    viewport: Rect?,
    fingerInRoot: Offset?,
    edgeWidth: Dp = 44.dp,
    dwellMillis: Long = EDGE_FLIP_DWELL_MS,
) {
    val edgePx = with(LocalDensity.current) { edgeWidth.toPx() }
    val edge: FlipEdge? = when {
        viewport == null || fingerInRoot == null -> null
        fingerInRoot.x < viewport.left + edgePx -> FlipEdge.LEFT
        fingerInRoot.x > viewport.right - edgePx -> FlipEdge.RIGHT
        else -> null
    }

    LaunchedEffect(edge) {
        if (edge == null) {
            pagerState.settleToNearestPage()
            return@LaunchedEffect
        }
        while (true) {
            delay(dwellMillis.milliseconds)
            val target = pagerState.currentPage + if (edge == FlipEdge.LEFT) -1 else 1
            if (target < 0 || target >= pagerState.pageCount) break
            pagerState.animateToPage(target)
        }
    }
}
