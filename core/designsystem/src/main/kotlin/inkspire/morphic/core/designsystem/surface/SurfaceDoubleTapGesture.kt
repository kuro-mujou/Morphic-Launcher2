package inkspire.morphic.core.designsystem.surface

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig

/** How far from the first tap the second may land, in touch slops — a finger never comes down on the same pixel twice. */
private const val SECOND_TAP_REACH_SLOPS = 4f

/**
 * **Double tap on empty space → the surface's own double-tap action.** The tap companion of
 * `surfaceMenuGestures`, applied to the same surface root and for the same reason: it sees every press the surface
 * receives, including presses that land on an item.
 *
 * **So it asks rather than measures.** An item consumes nothing on a tap, so consumption cannot say where a tap
 * landed; instead both downs must find no item pressed ([ItemSwipeClaim.itemPressed], which every item publishes at
 * its own down — ahead of this, since a parent handles an event after its children) and nothing holding
 * [SurfaceGestureLock]. A double tap on an icon is that icon's gesture, never the surface's.
 *
 * A tap is a release inside the launcher's touch slop and before its long-press timeout; the second tap must start
 * inside [ItemGestureConfig.doubleTapWindowMillis] and near the first. Anything else — travel, a scroller consuming, a
 * second finger — ends the attempt without a word, and **nothing is consumed either way**, so every other gesture on
 * the surface proceeds exactly as it would without this one.
 *
 * @param enabled false suppresses it without changing hit-testing — a surface panned off to one side, say.
 */
@Composable
fun Modifier.surfaceDoubleTap(
    config: ItemGestureConfig,
    enabled: Boolean = true,
    onDoubleTap: () -> Unit,
): Modifier {
    val lock = LocalSurfaceGestureLock.current
    val itemClaim = LocalItemSwipeClaim.current
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    return pointerInput(config, enabled) {
        if (!enabled) return@pointerInput
        fun onEmptySpace(): Boolean = itemClaim?.itemPressed != true && lock?.isLocked != true
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            if (!onEmptySpace() || !awaitTap(first, config)) return@awaitEachGesture

            // `AwaitPointerEventScope`'s own timeout, in plain millis — see `surfaceMenuGestures` for why the general
            // one does not compile here.
            val second = withTimeoutOrNull(config.doubleTapWindowMillis) {
                awaitFirstDown(requireUnconsumed = false)
            } ?: return@awaitEachGesture
            val reach = config.touchSlopPx * SECOND_TAP_REACH_SLOPS
            if (!onEmptySpace() || (second.position - first.position).getDistance() > reach) return@awaitEachGesture

            if (awaitTap(second, config)) currentOnDoubleTap()
        }
    }
}

/**
 * Whether [down] ends as a tap: released with one finger, unconsumed, inside the touch slop and before the long-press
 * timeout — past which the press is the surface menu's.
 */
private suspend fun AwaitPointerEventScope.awaitTap(down: PointerInputChange, config: ItemGestureConfig): Boolean =
    withTimeoutOrNull(config.longPressTimeoutMillis) {
        var tapped: Boolean? = null
        while (tapped == null) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            tapped = when {
                change == null || change.isConsumed || event.changes.count { it.pressed } > 1 -> false
                (change.position - down.position).getDistance() > config.touchSlopPx -> false
                !change.pressed -> true
                else -> null
            }
        }
        tapped
    } == true
