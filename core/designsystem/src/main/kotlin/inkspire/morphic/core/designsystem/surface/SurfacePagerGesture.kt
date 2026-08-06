package inkspire.morphic.core.designsystem.surface

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import inkspire.morphic.core.model.HomeEdge
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The axis a surface pan is locked to for the duration of one drag. */
private enum class PanAxis { HORIZONTAL, VERTICAL }

/** Below this |pan|, an axis counts as resting on HOME (matches the settle spring's visibility threshold). */
private const val AXIS_EPSILON = 0.001f

/**
 * Drives a [SurfacePager] from swipes: once movement passes touch slop, decide the swipe's axis and direction,
 * claim it if it opens (or returns from) a surface, then drag that axis and settle/fling on release.
 *
 * **One finger vs two.** Each edge carries a [SurfaceSwipe] policy (see [SurfaceBinding]). An edge whose HOME
 * content owns the one-finger swipe on that axis — an infinite pager horizontally, a vertical list vertically —
 * is [SurfaceSwipe.TWO_FINGER]: a one-finger drag toward it is handed back (broken) so that content gets it,
 * and only a two-finger swipe opens the surface. An edge whose axis is free is [SurfaceSwipe.ONE_OR_TWO_FINGER]
 * and opens on either. This is why the loop tracks *all* pressed fingers and averages their movement — a
 * two-finger swipe must read as one motion.
 *
 * The claim rules keep the pan out of the way of everything else:
 * - **from HOME** (pan at rest): claim only if the swipe points at an edge that has a surface *and* the finger
 *   count satisfies that edge's [EdgeSwipe.open] policy. A swipe toward an empty edge, or a one-finger swipe
 *   where the edge is [OneFingerSwipe.NEVER], is left unclaimed.
 * - **with a surface open**: lock to the open axis, so the only move is dragging back toward HOME (panning
 *   straight from one side surface to another isn't a thing — you return to HOME first). Closing has its own
 *   [EdgeSwipe.close] policy, driven by *this surface's* content: an infinite horizontal pager owns the
 *   one-finger close of a LEFT/RIGHT surface just as HOME's owns its open, so it too needs two fingers.
 *
 * **The nested-scroll hand-off** is what makes [OneFingerSwipe.AT_EDGE] different from [OneFingerSwipe.ALWAYS]: the
 * content being crossed is asked, live, whether it has reached the edge the swipe is pulling it toward
 * ([ScrollEdges]), and only then does the pan claim. Both halves read the same way from opposite ends — opening asks
 * HOME's content about the edge the swipe points at, closing asks the open surface's content about the edge
 * *opposite* the one it sits on, because closing drags it back the way it came.
 *
 * **The question is asked once, at slop, and the answer stands for the whole gesture.** A finger that crosses slop
 * while the content can still scroll belongs to the content until it lifts — scrolling a list to the bottom and
 * carrying straight on does *not* start panning; a second swipe from the bottom does. That is L1's behaviour too, and
 * it is the honest limit of a hand-off decided by a claim rather than by leftover-delta plumbing: continuing would
 * mean real `nestedScroll` and a scroll connection on every surface. Two fingers skip the question entirely.
 *
 * **[enabled] is consulted twice, and the second time is the one that matters.** At touch-down it catches a reason
 * that already existed — an open folder, say. But the reason may *arrive* mid-gesture: an item's long-press fires
 * 400ms after the finger lands, and by then this loop is long past its down. So it is asked again at the moment of
 * claiming, which is the last point at which handing the swipe back is free. Once claimed the pan keeps it: aborting a
 * pan already under way would read as the launcher stuttering, and the events are consumed from that point anyway.
 *
 * @param enabled gate the whole gesture off when it shouldn't run.
 */
fun Modifier.surfacePagerGesture(
    state: SurfacePagerState,
    enabled: () -> Boolean = { true },
): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(state) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled()) return@awaitEachGesture

            // Freeze any in-flight settle so this touch takes over the pan cleanly, and capture the page each
            // axis started on (0, -1, or +1) for the settle rule.
            scope.launch { state.stop() }
            val startX = state.panX.roundToInt().toFloat()
            val startY = state.panY.roundToInt().toFloat()
            // Which axis is already engaged. Read the *raw* pan, not the rounded page: a pan mid-animation (say
            // 0.3 on its way to opening) still counts as engaged. A surface pan is single-axis — while one axis
            // is off HOME the other must stay pinned at zero, or a perpendicular swipe opens a second surface on
            // top of the first. Rounding here was the bug: round(0.3) == 0 read as "resting on HOME", freeing
            // the other axis.
            val activeAxis = when {
                abs(state.panX) > AXIS_EPSILON -> PanAxis.HORIZONTAL
                abs(state.panY) > AXIS_EPSILON -> PanAxis.VERTICAL
                else -> null
            }

            var axis: PanAxis? = null
            var claimed = false
            var settled = false
            var twoFinger = false
            var accX = 0f
            var accY = 0f
            val tracker = VelocityTracker()

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                // Sticky: once a second finger has touched down, this stays a two-finger gesture even if one
                // finger later lifts — so lifting a finger mid-swipe doesn't abort the pan.
                if (pressed.size >= 2) twoFinger = true

                // Track velocity + averaged movement across all pressed fingers, so a two-finger swipe reads as
                // one motion. Prefer the original finger for the velocity lead; fall back if it has lifted.
                val lead = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                tracker.addPointerInputChange(lead)
                val dx = pressed.map { it.positionChange().x }.average().toFloat()
                val dy = pressed.map { it.positionChange().y }.average().toFloat()

                if (!claimed) {
                    accX += dx
                    accY += dy
                    if (abs(accX) > touchSlop || abs(accY) > touchSlop) {
                        // Re-asked here, not just at the down: see the note on `enabled`. A claim taken between the
                        // two — an item's long-press, a folder opening under the finger — hands the swipe back.
                        if (!enabled()) break
                        val horizontal = abs(accX) >= abs(accY)
                        if (activeAxis != null) {
                            // A surface is open (or mid-transition): only a swipe along that axis is ours — it
                            // drags back toward HOME. A perpendicular swipe is left unclaimed, and the release's
                            // settle-to-nearest tidies the in-flight pan.
                            if (horizontal != (activeAxis == PanAxis.HORIZONTAL)) break
                            // Which surface is open (the sign of the pan on this axis), so we can read its close
                            // policy. A two-finger-only close belongs to that surface's own content — hand back.
                            val openEdge = when (activeAxis) {
                                PanAxis.HORIZONTAL -> if (state.panX < 0f) HomeEdge.LEFT else HomeEdge.RIGHT
                                PanAxis.VERTICAL -> if (state.panY < 0f) HomeEdge.TOP else HomeEdge.BOTTOM
                            }
                            val close = state.edgeSwipes[openEdge]?.close ?: OneFingerSwipe.ALWAYS
                            // The nested-scroll hand-off, closing half. The content crossed is *this surface's*, and
                            // the edge it must have reached is the one **opposite** the surface's own — closing the
                            // RIGHT surface drags it back rightward, so its content has to be at its left edge.
                            val sideEdges = state.sideScroll.getValue(openEdge).edges()
                            if (!twoFinger && !close.allows(sideEdges[openEdge.opposite])) break
                            axis = activeAxis
                            claimed = true
                        } else {
                            // Resting on HOME: which edge does this swipe point at, and does it have a surface?
                            val target = if (horizontal) {
                                if (accX > 0f) HomeEdge.LEFT else HomeEdge.RIGHT
                            } else {
                                if (accY > 0f) HomeEdge.TOP else HomeEdge.BOTTOM
                            }
                            val open = state.edgeSwipes[target]?.open ?: break // no surface — hand it back
                            // The nested-scroll hand-off, opening half. The content crossed is HOME's, and the edge
                            // it must have reached is the one the swipe points at: opening the LEFT surface drags
                            // HOME rightward, so HOME's pager has to be on its first page.
                            //
                            // A NEVER edge fails this whatever the content says: that finger belongs to HOME's
                            // infinite content on this axis, which has no edge to hand off from at all.
                            if (!twoFinger && !open.allows(state.centerScroll.edges()[target])) break
                            axis = if (horizontal) PanAxis.HORIZONTAL else PanAxis.VERTICAL
                            claimed = true
                        }
                    }
                }

                if (claimed) {
                    pressed.forEach { it.consume() }
                    when (axis) {
                        PanAxis.HORIZONTAL -> scope.launch { state.dragXBy(dx) }
                        PanAxis.VERTICAL -> scope.launch { state.dragYBy(dy) }
                        null -> Unit
                    }
                }
            }

            if (claimed) {
                val velocity = tracker.calculateVelocity()
                // Flip screen velocity into pan-space: a finger flinging right (+x) opens LEFT, i.e. pan
                // decreasing, so the settle sees a negative velocity.
                when (axis) {
                    PanAxis.HORIZONTAL -> {
                        settled = true
                        scope.launch { state.settleX(startX, -velocity.x) }
                    }

                    PanAxis.VERTICAL -> {
                        settled = true
                        scope.launch { state.settleY(startY, -velocity.y) }
                    }

                    null -> Unit
                }
            }

            // A release that never claimed (a tap, or a gesture handed to a child) re-settles both axes so the
            // pan never rests mid-transition.
            if (!settled) scope.launch { state.settleToNearest() }
        }
    }
}
