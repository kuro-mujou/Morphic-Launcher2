package inkspire.morphic.core.designsystem.surface

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import inkspire.morphic.core.model.HomeEdge
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far past [slop] this accumulated travel reached — the drag the pan owes on the event it claims.
 *
 * Zero rather than a negative when the axis never crossed slop itself, which is the ordinary case for the axis a
 * diagonal swipe did *not* lock to: subtracting a slop it never paid would push the pan backwards.
 */
private fun Float.pastSlop(slop: Float): Float = when {
    this > slop -> this - slop
    this < -slop -> this + slop
    else -> 0f
}

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
 * **It runs on [PointerEventPass.Initial], and the hand-off does not work on any other pass.** A parent on the Main
 * pass sees its children's events *after* them, and `positionChange()` returns `Offset.Zero` once a change has been
 * consumed — so over any scrolling content the accumulated delta stays at zero, slop is never crossed, and none of
 * the logic below ever executes. Initial reverses that: this gesture gets first refusal on the raw delta, decides,
 * and **consumes only if it claims**. Declining costs the child nothing, because nothing was consumed and the event
 * reaches it on Main exactly as it would have. Taking the default pass here is what leaves `AT_EDGE` inert.
 *
 * The other side of owning first refusal is that a claim must never be idle: a swipe pressed against a bound the pan
 * is already clamped at would consume the finger and move nothing, so the claim is gated on the swipe actually
 * returning toward HOME.
 *
 * **The question is asked once, at slop, and the answer stands for the whole gesture.** A finger that crosses slop
 * while the content can still scroll belongs to the content until it lifts — scrolling a list to the bottom and
 * carrying straight on does *not* start panning; a second swipe from the bottom does. That is the honest limit of a
 * hand-off decided by a claim rather than by leftover-delta plumbing: continuing would
 * mean real `nestedScroll` and a scroll connection on every surface. Two fingers skip the question entirely.
 *
 * **[enabled] is consulted twice, and the second time is the one that matters.** At touch-down it catches a reason
 * that already existed — an open folder, say. But the reason may *arrive* mid-gesture: an item's long-press fires
 * 400ms after the finger lands, and by then this loop is long past its down. So it is asked again at the moment of
 * claiming, which is the last point at which handing the swipe back is free. Once claimed the pan keeps it: aborting a
 * pan already under way would read as the launcher stuttering, and the events are consumed from that point anyway.
 *
 * **A claimant at slop postpones the decision; it does not end the gesture.** Being disabled at slop reads as *"not
 * yet"*, so the loop keeps accumulating and decides on the first event at which nothing is claiming. That is the one
 * qualification to the paragraph above, and it is what [EmbeddedViewTouchFrame] needs: an embedded Android View cannot
 * be *asked* whether it wants a gesture — Compose hands it moves on the `Final` pass, after this one has decided — so
 * it claims at the **down** and hands the claim back once it has declined. A pan that broke off at slop instead would
 * make every widget a dead zone for surface switching; waiting costs one event of latency and nothing else. Nothing
 * else observes the difference, because every other claimant holds its claim until its own gesture ends.
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
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
            var justClaimed = false
            var settled = false
            var twoFinger = false
            var accX = 0f
            var accY = 0f
            val tracker = VelocityTracker()

            // **The pan is driven by one coroutine that drains a running total, not by one launch per event** —
            // and that is what a slow swipe was stalling on.
            //
            // `dragXBy` is `suspend` because `Animatable.snapTo` takes the animatable's `MutatorMutex`. Launched
            // per pointer event, a dozen of them contend for that lock; each loser suspends and is resumed through
            // the composition dispatcher, which hands out its queue at frame boundaries. So *n* touch events became
            // roughly *n* frames of backlog: the surface sat still while the queue drained and then landed on the
            // finger all at once. A flick is two or three events and never showed it; a slow drag is dozens, which
            // is why this got worse the slower you moved.
            //
            // Summing instead of queueing also makes the backlog self-correcting: several events arriving inside
            // one frame collapse into a single `snapTo` of their total, which is the only value that frame could
            // have drawn anyway.
            var pending = 0f
            var pump: Job? = null
            fun pumpPending() {
                if (pump?.isActive == true) return
                pump = scope.launch {
                    // Re-read each turn: more may have arrived while the previous `snapTo` held the lock.
                    while (pending != 0f) {
                        val delta = pending
                        pending = 0f
                        when (axis) {
                            PanAxis.HORIZONTAL -> state.dragXBy(delta)
                            PanAxis.VERTICAL -> state.dragYBy(delta)
                            null -> Unit
                        }
                    }
                }
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
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
                    // Re-asked here, not just at the down: see the notes on `enabled`. A claim taken between the two —
                    // an item's long-press, a folder opening under the finger, an embedded View that may want the
                    // gesture — postpones the decision rather than ending it, so the accumulation above carries on and
                    // a claim that is dropped mid-gesture still leaves a pan possible.
                    if ((abs(accX) > touchSlop || abs(accY) > touchSlop) && enabled()) {
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
                            // **Only a swipe that actually returns toward HOME is ours.** The finger has to travel
                            // toward the edge the open surface came in from — dragging the *other* way is pressed
                            // against a bound the pan is already clamped at, so it would move nothing while
                            // consuming the finger. On the Initial pass that is not merely wasteful: it would eat
                            // every forward page-swipe and downward scroll the open surface's own content needs.
                            val delta = if (activeAxis == PanAxis.HORIZONTAL) accX else accY
                            val returning =
                                if (openEdge == HomeEdge.LEFT || openEdge == HomeEdge.TOP) delta < 0f else delta > 0f
                            if (!returning) break
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
                        justClaimed = claimed
                    }
                }

                if (claimed) {
                    pressed.forEach { it.consume() }
                    // **The first drag carries the travel that recognizing the swipe cost**, not just the delta of
                    // the event that happened to cross slop. Those are the same number only for a flick, where one
                    // event jumps the whole distance; a slow swipe reaches slop over a dozen small events, and
                    // applying the last of them alone threw the other eleven away — so the surface began a fixed
                    // ~8dp behind the finger and stayed there for the rest of the drag. It is exactly the lag that
                    // gets worse the slower you move, which is the opposite of what a threshold should feel like.
                    //
                    // The slop itself is deliberately *not* carried: it is the distance spent deciding this is a
                    // swipe at all, and paying it back would start the pan with a visible jump. What is owed is the
                    // overshoot past it — Compose's own `detectDragGestures` hands callers the same quantity.
                    val step = when (axis) {
                        PanAxis.HORIZONTAL -> if (justClaimed) accX.pastSlop(touchSlop) else dx
                        PanAxis.VERTICAL -> if (justClaimed) accY.pastSlop(touchSlop) else dy
                        null -> 0f
                    }
                    justClaimed = false
                    pending += step
                    pumpPending()
                }
            }

            if (claimed) {
                val velocity = tracker.calculateVelocity()
                // Flip screen velocity into pan-space: a finger flinging right (+x) opens LEFT, i.e. pan
                // decreasing, so the settle sees a negative velocity.
                when (axis) {
                    // Each settle waits for the pump to finish: a spring started while a `snapTo` is still queued
                    // would animate from a position the finger has already left, and the drain would then jump the
                    // value out from under it.
                    PanAxis.HORIZONTAL -> {
                        settled = true
                        scope.launch { pump?.join(); state.settleX(startX, -velocity.x) }
                    }

                    PanAxis.VERTICAL -> {
                        settled = true
                        scope.launch { pump?.join(); state.settleY(startY, -velocity.y) }
                    }

                    null -> Unit
                }
            }

            // A release that never claimed (a tap, or a gesture handed to a child) re-settles both axes so the
            // pan never rests mid-transition.
            if (!settled) scope.launch { pump?.join(); state.settleToNearest() }
        }
    }
}
