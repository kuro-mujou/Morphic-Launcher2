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
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drives a [LauncherPager] from horizontal swipes. Claims the gesture only once it is decided to be horizontal
 * (past touch slop, x-dominant, and not already consumed by a child), then scrolls the [state] and flings on
 * release. Because it defers to already-consumed events, a child that has claimed the pointer — an item drag or
 * a vertical gesture — wins, and the pager stays out of its way.
 *
 * @param enabled gate the pager swipe off when it shouldn't run (e.g. `{ !coordinator.isDragging }` so an item
 *   drag isn't fighting a page swipe).
 */
fun Modifier.launcherPagerSwipe(
    state: LauncherPagerState,
    enabled: () -> Boolean = { true },
): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(state) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled()) return@awaitEachGesture
            scope.launch { state.stopAllAnimations() }

            var decided = false
            var horizontal = false
            var dx = 0f
            var dy = 0f
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPointerInputChange(change)

                if (!change.pressed) {
                    if (horizontal) {
                        change.consume()
                        scope.launch { state.flingHorizontal(tracker.calculateVelocity().x) }
                    }
                    break
                }

                val delta = change.positionChange()
                if (!decided) {
                    if (change.isConsumed) break // a child claimed the gesture — leave it alone
                    dx += delta.x
                    dy += delta.y
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
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
        }
    }
}
