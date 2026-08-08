package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset

/** A cardinal swipe direction, used for the press-and-swipe custom action on an item. */
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * A pointer event fed to [ItemGestureMachine], already reduced to what the machine cares about. The Compose
 * modifier (built next) translates raw `PointerInputChange`s and the long-press timer into these.
 *
 * @property offsetFromDown for [Move], the finger's travel since [Down] — the machine measures slop and swipe
 *   direction from this, so it never needs raw coordinates.
 */
sealed interface ItemGestureEvent {
    data object Down : ItemGestureEvent
    data class Move(val offsetFromDown: Offset) : ItemGestureEvent
    data object LongPress : ItemGestureEvent
    data object Up : ItemGestureEvent
    data object Cancel : ItemGestureEvent
}

/**
 * A side effect the machine asks the caller to perform. The machine itself is pure: it mutates only its own
 * [ItemGestureMachine.state] and returns these for the modifier to carry out (open the item, show the menu,
 * drive the [DragCoordinator], …).
 */
sealed interface ItemGestureEffect {
    /** A completed tap: launch the item. */
    data object OpenItem : ItemGestureEffect

    /** A press-and-swipe in [direction]: run that direction's custom action (a toast, for now). */
    data class EdgeAction(val direction: SwipeDirection) : ItemGestureEffect

    /** The long-press fired with the finger still: open the item's context menu. */
    data object ShowMenu : ItemGestureEffect

    /**
     * Take the context menu back down — a drag has started from it, or the gesture was cancelled. Deliberately
     * **not** emitted when the finger simply lifts: see [ItemGesturePhase.MenuOpen].
     */
    data object DismissMenu : ItemGestureEffect

    /** Lift the item into a drag ([DragCoordinator.start]). */
    data object BeginDrag : ItemGestureEffect

    /** Move the in-flight drag ([DragCoordinator.moveTo]); [offsetFromDown] is the finger travel. */
    data class DragTo(val offsetFromDown: Offset) : ItemGestureEffect

    /** Release the drag ([DragCoordinator.drop]). */
    data object Drop : ItemGestureEffect

    /** Abandon the drag ([DragCoordinator.cancel]). */
    data object CancelDrag : ItemGestureEffect
}

/** The machine's current phase. Exposed for the modifier (e.g. to suppress the click) and for tests. */
sealed interface ItemGesturePhase {
    /** No pointer down. */
    data object Idle : ItemGesturePhase

    /** Finger down, within slop, long-press still pending: could become a tap, a swipe, or the menu. */
    data object Pressed : ItemGesturePhase

    /** Moved past slop before the long-press: committed to a swipe in [direction], firing on release. */
    data class Swiped(val direction: SwipeDirection) : ItemGesturePhase

    /**
     * The context menu is open (long-press fired). Moving past slop starts a drag; **releasing leaves the menu
     * up** — the finger has to come off the item before any row on it can be tapped — and fires no tap.
     */
    data object MenuOpen : ItemGesturePhase

    /** A drag is in flight; the finger is being tracked at the root. */
    data object Dragging : ItemGesturePhase

    /**
     * Moved past slop before the long-press, in a direction the item does **not** have an edge action for — so
     * the swipe is left to the parent (the pager, or home↔side-surface navigation). The item stops consuming;
     * it does nothing else until the pointer lifts.
     */
    data object ReleasedToParent : ItemGesturePhase
}

/**
 * The one item-gesture state machine, shared by every draggable item on every surface. It turns a stream of
 * [ItemGestureEvent]s into [ItemGestureEffect]s, encoding exactly the launcher's gesture contract
 * (docs/DRAG_AND_DROP_DESIGN.md §5):
 *
 * - **tap** → [ItemGestureEffect.OpenItem]
 * - **press + swipe (4 directions)** → [ItemGestureEffect.EdgeAction] (before the long-press fires)
 * - **long-press** → [ItemGestureEffect.ShowMenu]; then **move** → drag ([ItemGestureEffect.BeginDrag] …
 *   [ItemGestureEffect.Drop]); or **release with no move** → the menu stays up and *no* tap fires
 *
 * Keeping the decision logic here — pure, deterministic, unit-tested — is the deliberate fix for L1, where
 * four separate recognizers each re-implemented long-press/slop/tap-suppression with different constants and
 * drifted out of sync. One machine, one [ItemGestureConfig], identical behaviour everywhere.
 *
 * The long-press *timer* lives in the modifier (a coroutine race); the machine just receives
 * [ItemGestureEvent.LongPress] and ignores it once the gesture has already moved on. Not thread-safe — drive
 * it from the pointer thread.
 *
 * **Edge actions gate swipe ownership.** A pre-long-press swipe is claimed as an [ItemGestureEffect.EdgeAction]
 * only if its direction is in [edgeActions]; a swipe in an unregistered direction goes to
 * [ItemGesturePhase.ReleasedToParent], so the parent (pager / surface navigation) handles it instead. Thus an
 * item with no horizontal edge action lets horizontal swipes flow to the pager, and one with a LEFT/RIGHT
 * action keeps them.
 *
 * @param config the shared slop threshold.
 * @param edgeActions the swipe directions this item handles itself; the rest are released to the parent.
 */
class ItemGestureMachine(
    private val config: ItemGestureConfig,
    private val edgeActions: Set<SwipeDirection> = emptySet(),
) {

    var phase: ItemGesturePhase = ItemGesturePhase.Idle
        private set

    /** Feeds one [event], updates [phase], and returns the effects to perform (in order). */
    fun onEvent(event: ItemGestureEvent): List<ItemGestureEffect> = when (val current = phase) {
        ItemGesturePhase.Idle -> onIdle(event)
        ItemGesturePhase.Pressed -> onPressed(event)
        is ItemGesturePhase.Swiped -> onSwiped(current, event)
        ItemGesturePhase.MenuOpen -> onMenuOpen(event)
        ItemGesturePhase.Dragging -> onDragging(event)
        ItemGesturePhase.ReleasedToParent -> onReleasedToParent(event)
    }

    private fun onIdle(event: ItemGestureEvent): List<ItemGestureEffect> {
        if (event is ItemGestureEvent.Down) phase = ItemGesturePhase.Pressed
        return noEffect()
    }

    private fun onPressed(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        is ItemGestureEvent.Move -> {
            // Below slop is just a wobble — stay pending so the long-press can still fire.
            if (!event.offsetFromDown.pastSlop()) {
                noEffect()
            } else {
                val direction = event.offsetFromDown.dominantDirection()
                phase = if (direction in edgeActions) {
                    // The item handles this direction — claim the swipe (fires on release).
                    ItemGesturePhase.Swiped(direction)
                } else {
                    // Not ours — let the parent (pager / surface nav) take this swipe.
                    ItemGesturePhase.ReleasedToParent
                }
                noEffect()
            }
        }
        ItemGestureEvent.LongPress -> {
            phase = ItemGesturePhase.MenuOpen
            effect(ItemGestureEffect.ShowMenu)
        }
        ItemGestureEvent.Up -> {
            // A quick, still press is a tap.
            phase = ItemGesturePhase.Idle
            effect(ItemGestureEffect.OpenItem)
        }
        ItemGestureEvent.Cancel -> reset()
        ItemGestureEvent.Down -> noEffect()
    }

    private fun onSwiped(current: ItemGesturePhase.Swiped, event: ItemGestureEvent): List<ItemGestureEffect> =
        when (event) {
            // Direction is locked at recognition; further movement doesn't change the committed swipe.
            is ItemGestureEvent.Move -> noEffect()
            ItemGestureEvent.Up -> {
                phase = ItemGesturePhase.Idle
                effect(ItemGestureEffect.EdgeAction(current.direction))
            }
            ItemGestureEvent.Cancel -> reset()
            // The long-press timer is stale once we've moved; ignore it.
            ItemGestureEvent.LongPress, ItemGestureEvent.Down -> noEffect()
        }

    private fun onMenuOpen(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        is ItemGestureEvent.Move -> {
            if (!event.offsetFromDown.pastSlop()) {
                noEffect() // small wobble with the menu up doesn't start a drag
            } else {
                phase = ItemGesturePhase.Dragging
                listOf(
                    ItemGestureEffect.DismissMenu,
                    ItemGestureEffect.BeginDrag,
                    ItemGestureEffect.DragTo(event.offsetFromDown),
                )
            }
        }
        // **Released with the menu open: the menu stays, and crucially no tap fires.** The finger lifting is how a
        // user reaches the menu they just asked for — dismissing here would make it unusable, since the rows can
        // only be tapped once the finger is off the item. The menu is taken down by choosing something on it, by
        // tapping away from it, or by the drag above; none of those is this event.
        //
        // This reverses what the machine did before there was a menu to open, where releasing emitted
        // `DismissMenu` and the docs' state diagram said "lift, no move → dismiss menu". Nothing depended on it:
        // every `onDismissMenu` in the tree was an empty lambda.
        ItemGestureEvent.Up -> reset()
        // A cancel is not a release — the pointer was taken away (the node left the tree, another window claimed
        // the stream), so nothing was chosen and the menu should not be left behind.
        ItemGestureEvent.Cancel -> {
            phase = ItemGesturePhase.Idle
            effect(ItemGestureEffect.DismissMenu)
        }
        ItemGestureEvent.LongPress, ItemGestureEvent.Down -> noEffect()
    }

    private fun onDragging(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        is ItemGestureEvent.Move -> effect(ItemGestureEffect.DragTo(event.offsetFromDown))
        ItemGestureEvent.Up -> {
            phase = ItemGesturePhase.Idle
            effect(ItemGestureEffect.Drop)
        }
        ItemGestureEvent.Cancel -> {
            phase = ItemGesturePhase.Idle
            effect(ItemGestureEffect.CancelDrag)
        }
        ItemGestureEvent.LongPress, ItemGestureEvent.Down -> noEffect()
    }

    // The swipe belongs to the parent now; do nothing until the pointer lifts, then reset.
    private fun onReleasedToParent(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        ItemGestureEvent.Up, ItemGestureEvent.Cancel -> reset()
        is ItemGestureEvent.Move, ItemGestureEvent.LongPress, ItemGestureEvent.Down -> noEffect()
    }

    private fun reset(): List<ItemGestureEffect> {
        phase = ItemGesturePhase.Idle
        return noEffect()
    }

    private fun Offset.pastSlop(): Boolean = getDistance() >= config.touchSlopPx

    private fun Offset.dominantDirection(): SwipeDirection =
        if (kotlin.math.abs(x) >= kotlin.math.abs(y)) {
            if (x >= 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
        } else {
            if (y >= 0f) SwipeDirection.DOWN else SwipeDirection.UP
        }

    private fun noEffect(): List<ItemGestureEffect> = emptyList()
    private fun effect(e: ItemGestureEffect): List<ItemGestureEffect> = listOf(e)
}

/**
 * Shared gesture thresholds and timing. One instance drives every item, so tuning is global — no more L1's
 * scattered, conflicting per-recognizer constants (350 vs 500ms long-press, four different edge dwells).
 *
 * @property touchSlopPx how far the finger must travel (px) before a move counts as a swipe (in [
 *   ItemGesturePhase.Pressed]) or starts a drag (in [ItemGesturePhase.MenuOpen]); smaller travel is ignored
 *   so the long-press survives a wobble.
 * @property longPressTimeoutMillis how long a still press waits before the menu shows. The machine doesn't
 *   time anything itself — the gesture modifier runs this timer and feeds in [ItemGestureEvent.LongPress] — but
 *   it lives here so all gesture tuning has one home.
 */
data class ItemGestureConfig(
    val touchSlopPx: Float,
    val longPressTimeoutMillis: Long,
)
