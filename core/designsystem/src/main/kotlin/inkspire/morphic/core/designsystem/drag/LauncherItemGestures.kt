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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.surface.LocalItemSwipeClaim
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Attaches the launcher's item-gesture contract to a composable, driving the shared [ItemGestureMachine] and
 * reporting its effects through these callbacks. One modifier per draggable item, on every surface, so the
 * behavior is identical everywhere (docs/DRAG_AND_DROP_DESIGN.md §5) — the antidote to L1's four divergent
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
 * @param doubleTap this item has a double tap assigned, which is what arms the window a release waits out
 *   before [onOpen] fires. False (default) keeps the immediate launch every other item has always had.
 * @param onOpen a completed tap.
 * @param onEdgeAction a press-and-swipe in a registered direction (custom action; a toast for now).
 * @param onDoubleTap a second press inside the window on an item that has one assigned.
 * @param onSwipePull the finger has moved while a claimed swipe is in flight — the raw offset from the down, and
 *   the direction committed to. Null offset means the swipe ended and the item returns to rest, on release and on
 *   cancel alike. How far the item actually moves is the caller's: see [ItemGestureEffect.SwipeProgress].
 * @param onShowMenu the long-press fired: open the item's context menu, anchored to the **rectangle this modifier
 *   is attached to** — which is the item's visible extent, since that is where the gestures are hung (see the
 *   scope note above). Reported from here rather than reconstructed by each surface, so a menu can never be
 *   anchored to something other than what the user pressed; L1 rebuilt that rectangle three different ways.
 * @param onBeginDrag lift into a drag; the finger is at the given root position.
 * @param onDragTo the drag moved to the given root position.
 * @param onDrop release the drag.
 * @param onCancelDrag the gesture was canceled mid-drag.
 */
@SuppressLint("ReturnFromAwaitPointerEventScope", "MultipleAwaitPointerEventScopes")
fun Modifier.launcherItemGestures(
    config: ItemGestureConfig,
    edgeActions: Set<SwipeDirection> = emptySet(),
    doubleTap: Boolean = false,
    onOpen: () -> Unit,
    onEdgeAction: (SwipeDirection) -> Unit,
    onDoubleTap: () -> Unit = {},
    onSwipePull: (direction: SwipeDirection, offsetFromDown: Offset?) -> Unit = { _, _ -> },
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

    // **What this item would take for itself, published at the down so the pan can ask.** The item cannot win a
    // race for a swipe — the pan claims at the platform slop and this contract at 20dp — so it does not race:
    // `edgeActions` is known at composition, and the pan checks it against its own direction before claiming.
    // See `ItemSwipeClaim`.
    val swipeClaim = LocalItemSwipeClaim.current

    // **Taking the menu down is the contract's, not the caller's.** Every path that ends a menu — a drag lifting
    // out of it, a canceled gesture — is decided by the machine, so wiring it here means no surface can forget
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
    val currentOnSwipePull by rememberUpdatedState(onSwipePull)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnShowMenu by rememberUpdatedState(onShowMenu)
    val currentOnBeginDrag by rememberUpdatedState(onBeginDrag)
    val currentOnDragTo by rememberUpdatedState(onDragTo)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnCancelDrag by rememberUpdatedState(onCancelDrag)

    onGloballyPositioned { coordinates = it }
        .pointerInput(config, edgeActions, doubleTap) {
            val machine = ItemGestureMachine(config, edgeActions, doubleTap)

            fun rootOf(local: Offset): Offset = coordinates?.localToRoot(local) ?: local

            /**
             * This node's rectangle in root coordinates — the menu's anchor.
             *
             * `positionInRoot() + size`, never `boundsInRoot()`: that call clips to every ancestor, so an item
             * inside a scroller that is half off the top reports half of itself and one below the fold reports an
             * empty rectangle. A menu anchored to an empty rect would silently jump to the corner. (The same rule
             * the APPS category card learned the hard way — see CLAUDE.md.)
             */
            fun anchorInRoot(): Rect {
                val c = coordinates ?: return Rect.Zero
                return Rect(c.positionInRoot(), c.size.toSize())
            }

            // Whether *this* gesture currently holds the surface-swipe claim, so the release below is idempotent
            // and the `finally` cannot over-release someone else's.
            // The direction of the swipe currently being pulled, so the settle can name it. Reset by the settle
            // itself rather than at the down: a gesture that never claimed a swipe never sets it.
            var lastPull: SwipeDirection? = null

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
                    is ItemGestureEffect.SwipeProgress -> {
                        lastPull = effect.direction
                        currentOnSwipePull(effect.direction, effect.offsetFromDown)
                    }
                    // The direction the pull is being released *from*; the caller animates back to rest whichever
                    // it was, so any value would do — but handing back the wrong one would be a lie in a log.
                    ItemGestureEffect.SwipeSettled -> {
                        lastPull?.let { currentOnSwipePull(it, null) }
                        lastPull = null
                    }
                    ItemGestureEffect.DoubleTapAction -> currentOnDoubleTap()
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
                // **The double-tap window, and it only ever runs on an item that has one assigned.** The
                // machine parks in `AwaitingSecondTap` on release; this races the next press against the
                // timeout, and whichever arrives first decides whether the tap was single or double. Held
                // across iterations of the loop below because the gap between two taps *is* the gap between
                // two gestures — there is no finger down to hang it on.
                var window: Job? = null

                while (true) {
                    val down = awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                    // A press inside the window is the second tap; the machine decides what that means.
                    window?.cancel()

                    val pointerId: PointerId = down.id
                    var local = down.position
                    // A claim must not outlive the gesture that took it. Every machine path does release it, but a
                    // canceled `pointerInput` coroutine (the node leaving the tree mid-drag) takes none of them, and
                    // a leaked claim would lock the surface swipe for the rest of the session.
                    try {
                        // Only while this item has something to take. Publishing an empty set would still be
                        // correct — the pan asks about one direction — but it would make every press overwrite
                        // whatever a previous one left, and the release below already handles that.
                        if (edgeActions.isNotEmpty()) swipeClaim?.claim(edgeActions)
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
                                    // **Consumed already means an `Initial`-pass ancestor took it** — the surface
                                    // pan, which runs ahead of every descendant, so its claim reaches this node here
                                    // on `Main`. A `Main`-pass ancestor has not acted yet and is caught on `Final`
                                    // below; catching the pan *here* is what stops the item recognizing its own
                                    // swipe on this very event and consuming first. This is the other half of
                                    // `SurfaceGestureLock`, which only ever ran the other way: an item that owns the
                                    // finger locks the pan out, while a pan that had already claimed could not tell
                                    // the item anything. The thresholds make that gap routine rather than rare — the
                                    // pan claims at the platform slop (~8dp) and an item needs 20 — so a finger
                                    // between the two when the timer fires opened a menu on an icon the user was
                                    // swiping past, and the next move turned it into a drag under a closing surface.
                                    //
                                    // **Reading consumption here does not contradict ignoring it above.** Where the
                                    // finger is was never in dispute (that is what `positionChangedIgnoreConsumed`
                                    // protects); *who owns the gesture* is a different question, and consumption is
                                    // the honest answer to it. No new state, and nothing to keep in step.
                                    //
                                    // **Only an `Initial`-pass ancestor is visible here, which is exactly right.**
                                    // The surface pan runs on `Initial`, ahead of every descendant, so its claim
                                    // reaches this node on `Main`. An ordinary scroller or pager runs on `Main`,
                                    // where children go first — so list scrolling and home's own pager never trip
                                    // this, and neither should they: they settle with the item the ordinary way.
                                    if (change.isConsumed && !machine.phase.ownsFinger) {
                                        perform(machine.onEvent(ItemGestureEvent.TakenByParent), local)
                                    }
                                    perform(machine.onEvent(ItemGestureEvent.Move(local - down.position)), local)
                                    // Once the item owns the finger, stop anything else reacting to the same
                                    // movement — a parent pager or scroller, and an embedded View below.
                                    if (machine.phase.ownsFinger) change.consume()
                                }

                                // **`Final` is where a `Main`-pass ancestor becomes visible**, and without it the
                                // long-press timer ran underneath a list that was already scrolling. Compose
                                // dispatches `Initial` parent-to-child, `Main` child-to-parent, and `Final`
                                // parent-to-child again — so a scroller that consumed on `Main`, *after* this node,
                                // has still consumed by the time the same event comes back around.
                                //
                                // Consumption was thought to settle these on its own, and for a *swipe* it does:
                                // the item reaches its own slop, finds the movement taken and stands down. It does
                                // not settle the **timer**. A slow drag travels under 20dp for the whole 400ms, so
                                // the machine is still `Pressed` when the long press fires and raises a menu on a
                                // row the user is scrolling past. The timer was what needed telling.
                                val settled = awaitPointerEvent(PointerEventPass.Final)
                                    .changes.firstOrNull { it.id == pointerId }
                                if (settled != null && settled.isConsumed && !machine.phase.ownsFinger) {
                                    perform(machine.onEvent(ItemGestureEvent.TakenByParent), local)
                                }
                            }
                        }
                        longPress.cancel()
                        // Only ever true where a double tap is assigned, so no other item pays for this.
                        if (machine.phase == ItemGesturePhase.AwaitingSecondTap) {
                            window = launch {
                                delay(config.doubleTapWindowMillis.milliseconds)
                                perform(machine.onEvent(ItemGestureEvent.DoubleTapTimeout), local)
                            }
                        }
                    } finally {
                        if (edgeActions.isNotEmpty()) swipeClaim?.release()
                        releaseSurface()
                    }
                }
            }
        }
}
