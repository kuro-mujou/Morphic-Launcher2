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

    /**
     * An ancestor consumed this movement — **the surface pan took the gesture.**
     *
     * The one arbitration this contract could not previously express. `SurfaceGestureLock` runs the other way: an
     * item that owns the finger locks the pan out. Nothing ran this way, and the thresholds guarantee the pan gets
     * there first — it claims at the platform slop (~8dp) where an item needs 20 — so between those two distances
     * the pan has claimed and the item does not know.
     *
     * Without this the long-press timer keeps running underneath a pan already under way, and a finger that has
     * travelled 8dp but not 20 when it fires opens a menu on an item the user is swiping *past*, then turns it into
     * a drag. Two owners of one finger, which is what it looks like: the surface slides home while an icon comes up
     * under the thumb.
     *
     * Sent only while the item does **not** own the finger; see `launcherItemGestures`. Once an item is swiping,
     * dragging or holding a menu it has the pan locked out, so this cannot arrive.
     */
    data object TakenByParent : ItemGestureEvent
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
     * Take the context menu back down — a drag has started from it, or the gesture was canceled. Deliberately
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
 * Whether the item has **claimed the finger** — it is swiping, dragging, or holding its menu open.
 *
 * What this decides is *consumption*: from here the gesture is the item's, so nothing else may act on the same
 * pointer. For most items that only stops a parent pager or scroller from reacting. For one it matters far more:
 * a cell hosting an **embedded View** (an `AppWidgetHostView`) is a second, independent consumer of the same
 * touches, and Compose's interop layer sends it an `ACTION_CANCEL` exactly when a Compose ancestor consumes. That
 * cancel is what stops a widget firing its own click as the user releases out of the long-press menu — nothing in
 * this machine could suppress it, because the tap was never ours to suppress.
 *
 * [ItemGesturePhase.Pressed] is deliberately *not* here: an ordinary tap must reach the widget.
 */
internal val ItemGesturePhase.ownsFinger: Boolean
    get() = this is ItemGesturePhase.Swiped ||
        this == ItemGesturePhase.Dragging ||
        this == ItemGesturePhase.MenuOpen

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
 * Keeping the decision logic here — pure, deterministic, unit-tested — is what stops separate recognizers each
 * re-implementing long-press, slop and tap-suppression with their own constants and drifting apart. One machine, one
 * [ItemGestureConfig], identical behavior everywhere.
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
 * **Every call site passes an empty set today, and turning one on needs a change outside this class.** The pan
 * claims at the platform slop and this machine at 20dp, so by the time a claimed direction could be recognized
 * here the pan has already consumed and [ItemGestureEvent.TakenByParent] has stood the item down — the edge
 * action would never fire, and nothing would look wrong. What the planned per-item home gestures need is for the
 * pan to **ask** rather than for this machine to win a race it cannot: the item publishing its claimed directions
 * at the down, and the pan checking them against its own direction at the moment it claims. See
 * docs/DRAG_AND_DROP_DESIGN.md §5.
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
        // The pan claimed at its own slop, below ours: hand the gesture over before the long-press can fire.
        ItemGestureEvent.TakenByParent -> {
            phase = ItemGesturePhase.ReleasedToParent
            noEffect()
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
            // The long-press timer is stale once we've moved; ignore it. `TakenByParent` cannot arrive in the
            // three phases that own the finger — the modifier only sends it while the item does not — and the
            // branch exists so adding a phase has to answer for it.
            ItemGestureEvent.LongPress, ItemGestureEvent.Down, ItemGestureEvent.TakenByParent -> noEffect()
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
        ItemGestureEvent.LongPress, ItemGestureEvent.Down, ItemGestureEvent.TakenByParent -> noEffect()
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
        ItemGestureEvent.LongPress, ItemGestureEvent.Down, ItemGestureEvent.TakenByParent -> noEffect()
    }

    // The swipe belongs to the parent now; do nothing until the pointer lifts, then reset.
    private fun onReleasedToParent(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        ItemGestureEvent.Up, ItemGestureEvent.Cancel -> reset()
        is ItemGestureEvent.Move,
        ItemGestureEvent.LongPress,
        ItemGestureEvent.Down,
        ItemGestureEvent.TakenByParent,
        -> noEffect()
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
 * Shared gesture thresholds and timing. One instance drives every item, so tuning is global rather than scattered
 * across per-recognizer constants that disagree.
 *
 * @property touchSlopPx how far the finger must travel (px) before a move counts as a swipe (in [
 *   ItemGesturePhase.Pressed]) or starts a drag (in [ItemGesturePhase.MenuOpen]); smaller travel is ignored
 *   so the long-press survives a wobble.
 *
 *   **It must stay above the platform touch slop (~8dp), and that ordering is load-bearing rather than a
 *   preference.** The surface pan claims at the platform value, so it always decides first; an item learns that it
 *   lost by seeing the movement arrive consumed ([ItemGestureEvent.TakenByParent]). Bring this down to the
 *   platform's and the two decide at the same distance, which is a race with no arbiter — the pan would sometimes
 *   claim before an item could and sometimes after. All three call sites use 20dp today
 *   (`rememberAppsGestureConfig`, and home's pager and list); the number is theirs to tune, the ordering is not.
 *   docs/DRAG_AND_DROP_DESIGN.md §5 has the whole arbitration.
 * @property longPressTimeoutMillis how long a still press waits before the menu shows. The machine doesn't
 *   time anything itself — the gesture modifier runs this timer and feeds in [ItemGestureEvent.LongPress] — but
 *   it lives here so all gesture tuning has one home.
 */
data class ItemGestureConfig(
    val touchSlopPx: Float,
    val longPressTimeoutMillis: Long,
)
