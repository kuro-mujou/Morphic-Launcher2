package inkspire.morphic.core.designsystem.menu

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock

/**
 * How much longer than an item's long-press a surface long-press waits. See [surfaceMenuGestures] — this margin is
 * what makes "the item wins" a fact rather than a coin toss.
 */
private const val SurfaceMenuMarginMs = 120L

/**
 * **Long-press on empty space → the surface's own menu.** The companion of
 * [inkspire.morphic.core.designsystem.drag.launcherItemGestures], and the thing the "an item's touch target is its
 * visible extent, never its cell" rule has been reserving space for: the slack around every icon exists precisely so
 * there is somewhere left to press and hold on a full page.
 *
 * Applied to a **surface's root**, above its content, so it sees every press the surface receives — including
 * presses that land on an item, since `launcherItemGestures` never consumes a down. Which is the whole difficulty,
 * and it is answered twice over:
 *
 * 1. **The item is given a head start.** This waits [ItemGestureConfig.longPressTimeoutMillis] *plus*
 *    [SurfaceMenuMarginMs], so when a press is on an icon the item's own long-press has already fired. Both timers
 *    start on the same event, so without a margin which one won would be genuinely undefined.
 * 2. **And then it asks.** [inkspire.morphic.core.designsystem.surface.SurfaceGestureLock] is exactly "something on
 *    screen owns this finger" — an item that has opened its menu or begun a drag holds it, and so does an open menu
 *    — so a claim is the signal to stand down. It is the same question the surface *swipe* asks, which is the point:
 *    one answer, not a second one invented here. It also settles a case nobody had to write down: an **open folder**
 *    (or category expansion) already holds the lock, so pressing its backdrop cannot open the menu of the surface
 *    buried underneath it.
 *
 * L1 had no equivalent because its home ran a **single root recognizer** that resolved the cell under the finger and
 * branched (`isOnIcon` → the item menu, otherwise the surface menu). That works, but it makes the surface responsible
 * for knowing where every item drew its icon — and this codebase deliberately hands that decision down to the cell's
 * content instead. Asking "did anything claim the finger?" needs no geometry at all, which is why it can be one
 * modifier that every surface applies identically.
 *
 * The press is reported in **root** coordinates, the space [MenuAnchor.Press] and the drag coordinator both work in.
 *
 * @param config the launcher's shared item timings — read rather than restated so the margin above stays relative to
 *   whatever the item long-press is tuned to.
 * @param enabled false suppresses the menu without changing the layout — a surface still being panned onto the
 *   screen, say. The gesture is still installed, so nothing about hit-testing changes.
 * @param onOpenMenu a long-press survived, in root coordinates.
 */
@Composable
fun Modifier.surfaceMenuGestures(
    config: ItemGestureConfig,
    enabled: Boolean = true,
    onOpenMenu: (rootPosition: Offset) -> Unit,
): Modifier {
    val lock = LocalSurfaceGestureLock.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    return onGloballyPositioned { coordinates = it }
        .pointerInput(config, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                // **`AwaitPointerEventScope`'s own `withTimeoutOrNull`, not `kotlinx.coroutines`'.** That scope is
                // `@RestrictsSuspension`, so the general one does not compile here — which is the compiler saying
                // something true: a pointer loop may only suspend on pointer events. It takes plain millis, so this
                // is the one place the codebase's "`delay` takes a `Duration`" rule does not apply.
                //
                // It returns null when the press *survived* the wait, which is the success case here: everything
                // inside the block is a reason to give up, and each one returns to say so.
                val gaveUp = withTimeoutOrNull(config.longPressTimeoutMillis + SurfaceMenuMarginMs) {
                    while (true) {
                        val event = awaitPointerEvent()
                        // A second finger is the surface pan's gesture, not a press.
                        if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull
                        if (!change.pressed) return@withTimeoutOrNull
                        // Consumed by a child means something below took this gesture — a page swipe, a scroll —
                        // and a long-press is no longer what is happening.
                        if (change.isConsumed) return@withTimeoutOrNull
                        // Travel measured from the down, against the launcher's shared slop, so a press that
                        // wandered is a swipe here for the same distance it is one on an item.
                        if ((change.position - down.position).getDistance() > config.touchSlopPx) {
                            return@withTimeoutOrNull
                        }
                    }
                }

                if (gaveUp == null && lock?.isLocked != true) {
                    onOpenMenu(coordinates?.localToRoot(down.position) ?: down.position)
                }
            }
        }
}
