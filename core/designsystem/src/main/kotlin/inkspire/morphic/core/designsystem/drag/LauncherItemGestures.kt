package inkspire.morphic.core.designsystem.drag

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Attaches the launcher's item-gesture contract to a composable, driving the shared [ItemGestureMachine] and
 * reporting its effects through these callbacks. One modifier per draggable item, on every surface, so the
 * behaviour is identical everywhere (docs/DRAG_AND_DROP_DESIGN.md §5) — the antidote to L1's four divergent
 * per-surface recognizers.
 *
 * This is the thin I/O shell over the pure machine: it races the [ItemGestureConfig.longPressTimeoutMillis]
 * timer against pointer movement, turns finger changes into the machine's [ItemGestureEvent]s, and dispatches
 * the returned [ItemGestureEffect]s to the callbacks. The drag callbacks receive the finger in **root/window**
 * coordinates — the space the [DragCoordinator] hit-tests in.
 *
 * Scope note: the item's own pointer stream tracks the whole gesture — once the pointer is down the gesture
 * owns it until release, wherever the finger travels, so a drag from one surface can land on another. This
 * holds as long as the item stays composed, so the rule is to **keep a source surface composed while a drag
 * from it is in flight** (a "closing" side surface slides/fades but stays in the tree until drop). A root-level
 * pointer overlay was tried for this and rejected: a full-screen `pointerInput` swallows the items' events
 * (docs/DRAG_AND_DROP_DESIGN.md §5).
 *
 * @param config shared slop + long-press timing.
 * @param edgeActions the swipe directions this item handles as an edge action; swipes in other directions are
 *   released to the parent (pager / surface navigation) instead of being consumed. Empty (default) → the item
 *   claims no swipes, so every swipe flows to the parent.
 * @param onOpen a completed tap.
 * @param onEdgeAction a press-and-swipe in a registered direction (custom action; a toast for now).
 * @param onShowMenu the long-press fired: open the item's context menu.
 * @param onDismissMenu take the menu back down (drag started, or released with no move).
 * @param onBeginDrag lift into a drag; the finger is at the given root position.
 * @param onDragTo the drag moved to the given root position.
 * @param onDrop release the drag.
 * @param onCancelDrag the gesture was cancelled mid-drag.
 */
@SuppressLint("ReturnFromAwaitPointerEventScope", "MultipleAwaitPointerEventScopes")
fun Modifier.launcherItemGestures(
    config: ItemGestureConfig,
    edgeActions: Set<SwipeDirection> = emptySet(),
    onOpen: () -> Unit,
    onEdgeAction: (SwipeDirection) -> Unit,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onBeginDrag: (rootPosition: Offset) -> Unit,
    onDragTo: (rootPosition: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancelDrag: () -> Unit,
): Modifier = composed {
    // Captured so we can turn item-local finger positions into root coordinates for the coordinator.
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // **The surface swipe is claimed for as long as this item owns the finger**, which is from the long-press
    // onward — the menu going up, and the drag it may turn into. Here rather than at each of the five surfaces that
    // wire this modifier, for `LauncherDragCell`'s reason: wiring that cannot be forgotten belongs in the one place
    // every caller already goes through. Stable for the life of the shell, so the `pointerInput` below capturing it
    // once is correct.
    val surfaceLock = LocalSurfaceGestureLock.current

    onGloballyPositioned { coordinates = it }
        .pointerInput(config, edgeActions) {
            val machine = ItemGestureMachine(config, edgeActions)

            fun rootOf(local: Offset): Offset = coordinates?.localToRoot(local) ?: local

            // Whether *this* gesture currently holds the surface-swipe claim, so the release below is idempotent
            // and the `finally` cannot over-release someone else's.
            var holdsSurfaceLock = false
            fun claimSurface() {
                if (!holdsSurfaceLock) { surfaceLock?.acquire(); holdsSurfaceLock = true }
            }
            fun releaseSurface() {
                if (holdsSurfaceLock) { surfaceLock?.release(); holdsSurfaceLock = false }
            }

            fun perform(effects: List<ItemGestureEffect>, local: Offset) {
                for (effect in effects) when (effect) {
                    ItemGestureEffect.OpenItem -> onOpen()
                    is ItemGestureEffect.EdgeAction -> onEdgeAction(effect.direction)
                    // The long-press has fired: from here the finger is this item's, whether it ends as a menu or
                    // becomes a drag. A drag arrives as `[DismissMenu, BeginDrag, …]` in one list, so the claim is
                    // taken again immediately after being dropped — which a count absorbs, and which nothing can
                    // observe in between since this whole loop runs synchronously off the pointer thread.
                    ItemGestureEffect.ShowMenu -> { claimSurface(); onShowMenu() }
                    ItemGestureEffect.DismissMenu -> { releaseSurface(); onDismissMenu() }
                    ItemGestureEffect.BeginDrag -> { claimSurface(); onBeginDrag(rootOf(local)) }
                    is ItemGestureEffect.DragTo -> onDragTo(rootOf(local))
                    ItemGestureEffect.Drop -> { releaseSurface(); onDrop() }
                    ItemGestureEffect.CancelDrag -> { releaseSurface(); onCancelDrag() }
                }
            }

            coroutineScope {
                while (true) {
                    val down = awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                    val pointerId: PointerId = down.id
                    var local = down.position
                    // A claim must not outlive the gesture that took it. Every machine path does release it, but a
                    // cancelled `pointerInput` coroutine (the node leaving the tree mid-drag) takes none of them, and
                    // a leaked claim would lock the surface swipe for the rest of the session.
                    try {
                        perform(machine.onEvent(ItemGestureEvent.Down), local)

                        // The long-press timer runs beside the event loop; the machine ignores it if the gesture
                        // has already moved on (swiped/dragging), so no explicit cancellation race is needed.
                        val longPress = launch {
                            delay(config.longPressTimeoutMillis.milliseconds)
                            perform(machine.onEvent(ItemGestureEvent.LongPress), local)
                        }

                        awaitPointerEventScope {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                                if (change == null) {
                                    perform(machine.onEvent(ItemGestureEvent.Cancel), local)
                                    break
                                }
                                local = change.position
                                if (change.changedToUpIgnoreConsumed()) {
                                    perform(machine.onEvent(ItemGestureEvent.Up), local)
                                    break
                                }
                                if (!change.pressed) {
                                    perform(machine.onEvent(ItemGestureEvent.Cancel), local)
                                    break
                                }
                                if (change.positionChanged()) {
                                    perform(machine.onEvent(ItemGestureEvent.Move(local - down.position)), local)
                                    // Once the gesture is claimed as a swipe or a drag, stop the parent
                                    // (pager/scroller) from also reacting to the same finger movement.
                                    val phase = machine.phase
                                    if (phase is ItemGesturePhase.Swiped || phase == ItemGesturePhase.Dragging) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                        longPress.cancel()
                    } finally {
                        releaseSurface()
                    }
                }
            }
        }
}
