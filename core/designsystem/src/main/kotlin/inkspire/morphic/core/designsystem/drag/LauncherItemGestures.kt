package inkspire.morphic.core.designsystem.drag

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
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
 * @param onShowMenu the long-press fired: open the item's context menu, anchored to the **rectangle this modifier
 *   is attached to** — which is the item's visible extent, since that is where the gestures are hung (see the
 *   scope note above). Reported from here rather than reconstructed by each surface, so a menu can never be
 *   anchored to something other than what the user pressed; L1 rebuilt that rectangle three different ways.
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
    onShowMenu: (anchorInRoot: Rect) -> Unit,
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

    // **Taking the menu down is the contract's, not the caller's.** Every path that ends a menu — a drag lifting
    // out of it, a cancelled gesture — is decided by the machine, so wiring it here means no surface can forget
    // it and leave a menu hanging over a drag in flight. Same reasoning as the surface lock above: wiring that
    // cannot be forgotten belongs in the one place every caller already goes through. There was, correspondingly,
    // an `onDismissMenu` parameter, and every call site in the tree passed `{}`.
    val menuHost = LocalMenuHost.current

    // **Every callback is read through `rememberUpdatedState`, and that is a correctness fix rather than a habit.**
    //
    // `pointerInput(config, edgeActions)` restarts its block only when those two change — which is almost never —
    // so the block captures whatever lambdas the *first* composition passed and keeps calling them forever. A
    // caller whose callback closes over state that arrives later is then silently frozen at the value it had
    // before that state existed. That is not hypothetical: home builds its item menu inside `onShowMenu`, and the
    // "Resize" row is offered only once the grid has published its measured geometry — which happens a frame after
    // the first composition, so the row could never appear.
    //
    // Keying the `pointerInput` on the callbacks instead would restart the gesture whenever one was re-created,
    // which for lambdas rebuilt every recomposition means tearing down an in-flight drag.
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnEdgeAction by rememberUpdatedState(onEdgeAction)
    val currentOnShowMenu by rememberUpdatedState(onShowMenu)
    val currentOnBeginDrag by rememberUpdatedState(onBeginDrag)
    val currentOnDragTo by rememberUpdatedState(onDragTo)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnCancelDrag by rememberUpdatedState(onCancelDrag)

    onGloballyPositioned { coordinates = it }
        .pointerInput(config, edgeActions) {
            val machine = ItemGestureMachine(config, edgeActions)

            fun rootOf(local: Offset): Offset = coordinates?.localToRoot(local) ?: local

            /**
             * This node's rectangle in root coordinates — the menu's anchor.
             *
             * `positionInRoot() + size`, never `boundsInRoot()`: that call clips to every ancestor, so an item
             * inside a scroller that is half off the top reports half of itself and one below the fold reports an
             * empty rectangle. A menu anchored to an empty rect would silently jump to the corner. (The same rule
             * the APPS category card learnt the hard way — see CLAUDE.md.)
             */
            fun anchorInRoot(): Rect {
                val c = coordinates ?: return Rect.Zero
                return Rect(c.positionInRoot(), c.size.toSize())
            }

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
                    ItemGestureEffect.OpenItem -> currentOnOpen()
                    is ItemGestureEffect.EdgeAction -> currentOnEdgeAction(effect.direction)
                    // The long-press has fired: from here the finger is this item's, whether it ends as a menu or
                    // becomes a drag. A drag arrives as `[DismissMenu, BeginDrag, …]` in one list, so the claim is
                    // taken again immediately after being dropped — which a count absorbs, and which nothing can
                    // observe in between since this whole loop runs synchronously off the pointer thread.
                    ItemGestureEffect.ShowMenu -> { claimSurface(); currentOnShowMenu(anchorInRoot()) }
                    ItemGestureEffect.DismissMenu -> { releaseSurface(); menuHost?.dismiss() }
                    ItemGestureEffect.BeginDrag -> { claimSurface(); currentOnBeginDrag(rootOf(local)) }
                    is ItemGestureEffect.DragTo -> currentOnDragTo(rootOf(local))
                    ItemGestureEffect.Drop -> { releaseSurface(); currentOnDrop() }
                    ItemGestureEffect.CancelDrag -> { releaseSurface(); currentOnCancelDrag() }
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
                                    // Read *before* the event, because `Up` resets the machine to `Idle` — and what
                                    // matters is whether the item owned the finger at the moment it was released.
                                    val claimed = machine.phase.ownsFinger
                                    perform(machine.onEvent(ItemGestureEvent.Up), local)
                                    // Consuming the release is what tells an **embedded View** the gesture was
                                    // taken from it: a widget's `AppWidgetHostView` is a second consumer of these
                                    // same touches, and without a cancel it fires the widget's own click as the
                                    // user lifts out of the long-press menu. See [ItemGesturePhase.ownsFinger] —
                                    // suppressing our own `OpenItem` never could have stopped that, because the
                                    // tap belongs to the hosted view rather than to us.
                                    if (claimed) change.consume()
                                    break
                                }
                                if (!change.pressed) {
                                    perform(machine.onEvent(ItemGestureEvent.Cancel), local)
                                    break
                                }
                                // **Ignoring consumption, deliberately** — the twin of `changedToUpIgnoreConsumed`
                                // above. Where the finger *is* is never in dispute; whether this item acts on it is
                                // the machine's decision, and it has a phase for exactly that
                                // (`ReleasedToParent`). Asking `positionChanged()` instead reads consumption as
                                // "the finger did not move", and the surface pan claims at the platform slop (~8dp)
                                // while this item needs 20 — so a swipe begun on an icon went straight from `Down`
                                // to `Up` with no `Move` in between, left the machine in `Pressed`, and **launched
                                // the app**. It cost nothing only while the pan ran on the Main pass and could not
                                // consume ahead of this node.
                                if (change.positionChangedIgnoreConsumed()) {
                                    perform(machine.onEvent(ItemGestureEvent.Move(local - down.position)), local)
                                    // Once the item owns the finger, stop anything else reacting to the same
                                    // movement — a parent pager or scroller, and an embedded View below.
                                    if (machine.phase.ownsFinger) change.consume()
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
