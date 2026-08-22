package inkspire.morphic.core.designsystem.pager

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import inkspire.morphic.core.designsystem.surface.LocalItemSwipeClaim
import inkspire.morphic.core.model.swipeDirectionOf
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drives a [LauncherPager] from horizontal swipes. Claims the gesture only once it is decided to be horizontal
 * (past touch slop, x-dominant, and not already consumed by a child), then scrolls the [state] and flings on
 * release. Because it defers to already-consumed events, a child that has claimed the pointer — an item drag or
 * a vertical gesture — wins, and the pager stays out of its way.
 *
 * **It asks the pressed item before claiming**, exactly as the surface pan does and for the same reason. The
 * consumed-event hand-off below is a *repair*: the pager claims at the platform slop where an item needs
 * 20dp, so a page visibly scrolled and then snapped back to where it started every time an item took a
 * LEFT/RIGHT swipe of its own. Asking `ItemSwipeClaim` up front means there is nothing to undo — the item
 * published what it would handle at the down, so this is a lookup rather than a race the pager always wins.
 *
 * The repair stays for what cannot be predicted at the down: a long-press **drag** claims the pointer 400ms
 * in, by which time the pager may legitimately be scrolling.
 *
 * @param enabled gate the pager swipe off when it shouldn't run (e.g. `{ !coordinator.isDragging }` so an item
 *   drag isn't fighting a page swipe).
 */
fun Modifier.launcherPagerSwipe(
    state: LauncherPagerState,
    enabled: () -> Boolean = { true },
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val itemClaim = LocalItemSwipeClaim.current
    pointerInput(state, itemClaim) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled()) return@awaitEachGesture
            val startPage = state.currentPage
            scope.launch { state.stopAllAnimations() }

            var decided = false
            var horizontal = false
            var flung = false
            var dx = 0f
            var dy = 0f
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPointerInputChange(change)

                // If we already claimed a horizontal swipe but a child has since consumed the gesture (an item
                // drag, or an item's registered edge swipe), hand off: snap back to the page we started on and
                // stop. This undoes the small scroll that sneaks in between the pager's touch slop and the
                // item's larger slop.
                if (horizontal && change.isConsumed) {
                    scope.launch { state.animateToPage(startPage) }
                    flung = true
                    break
                }

                if (!change.pressed) {
                    if (horizontal) {
                        change.consume()
                        scope.launch { state.flingHorizontal(tracker.calculateVelocity().x) }
                        flung = true
                    }
                    break
                }

                val delta = change.positionChange()
                if (!decided) {
                    if (change.isConsumed) break // a child claimed the gesture — leave it alone
                    dx += delta.x
                    dy += delta.y
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        // The item's swipe wins outright; leaving it alone here is what stops the page from
                        // scrolling a few dp and bouncing back.
                        if (itemClaim?.claims(swipeDirectionOf(dx, dy)) == true) break
                        if (abs(dx) > abs(dy) && enabled()) {
                            decided = true
                            horizontal = true
                        } else {
                            break // vertical — not ours
                        }
                    }
                }
                if (decided) {
                    change.consume()
                    scope.launch { state.dragHorizontalBy(delta.x) }
                }
            }

            // We froze any in-flight settle on the down. If this gesture didn't take it over with a fling —
            // a tap, or a vertical/other gesture — re-settle to the nearest page so the pager never stops
            // mid-scroll. (No-op when already resting on a page.)
            if (!flung) scope.launch { state.settleToNearestPage() }
        }
    }
}
