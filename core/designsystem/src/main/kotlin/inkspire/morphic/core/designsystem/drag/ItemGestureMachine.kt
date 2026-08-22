package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.core.model.swipeDirectionOf

/** A cardinal swipe direction, used for the press-and-swipe custom action on an item. */
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
     * **Not only the pan.** Any ancestor that consumes stands the item down: the surface pan is seen on `Main`,
     * since it runs on `Initial` ahead of this node, and a scroller or pager that consumes on `Main` is seen one
     * pass later on `Final`. The second was thought unnecessary, because consumption settles a *swipe* by itself —
     * the item reaches its slop, finds the movement taken and gives up. It does not settle the **timer**: a slow
     * scroll drag travels under 20dp for the whole 400ms, so the machine is still `Pressed` when the long press
     * fires and raises a menu on a row the user is scrolling past.
     *
     * Sent only while the item does **not** own the finger; see `launcherItemGestures`. Once an item is swiping,
     * dragging or holding a menu it has the pan locked out, so this cannot arrive.
     */
    data object TakenByParent : ItemGestureEvent

    /** The double-tap window closed with no second press: the tap that was waiting was a single one. */
    data object DoubleTapTimeout : ItemGestureEvent
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

    /**
     * The finger has moved while a claimed swipe is in flight — **the feedback for a gesture that has not fired
     * yet**, so the item can be pulled toward the edge it will fire at.
     *
     * Carries the raw offset from the down and the direction already committed to. **The raw offset, because how
     * far the item actually moves is presentation**: resistance, a ceiling and the axis projection belong to
     * whatever draws the pull, not to the contract that recognizes the swipe. [direction] rides along so the
     * drawer need not re-derive it and risk disagreeing with the direction that will fire.
     */
    data class SwipeProgress(val direction: SwipeDirection, val offsetFromDown: Offset) : ItemGestureEffect

    /**
     * A claimed swipe ended — the item returns to rest.
     *
     * Emitted on release **and** on cancel, because the pull has to come back either way; whether the action fires
     * is [EdgeAction]'s separate business. Without it a canceled swipe would leave the item parked off-center with
     * nothing to bring it home.
     */
    data object SwipeSettled : ItemGestureEffect

    /** A second press landed inside the window — the item's double-tap action. */
    data object DoubleTapAction : ItemGestureEffect

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
     * A tap has been released on an item that has a **double tap assigned**, and the window for a second
     * press is open. No finger is down.
     *
     * The state that makes a double tap cost something, which is why it exists only where one is assigned:
     * the launch has to be held back until the window closes, or every double tap would open the app too.
     * Items without one go straight from [Pressed] to firing, as they always have — the delay is paid by the
     * icons whose owner asked for it and by no others.
     */
    data object AwaitingSecondTap : ItemGesturePhase

    /** The second press of a double tap is down; releasing it fires. */
    data object SecondPressed : ItemGesturePhase

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
 * @param doubleTap this item has a double tap assigned, so a release holds the launch back until the window
 *   closes. **False everywhere it is not assigned, and that is the whole mitigation**: waiting on every tap
 *   would slow the one action a user performs most, to serve a gesture almost no icon has.
 */
class ItemGestureMachine(
    private val config: ItemGestureConfig,
    private val edgeActions: Set<SwipeDirection> = emptySet(),
    private val doubleTap: Boolean = false,
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
        ItemGesturePhase.AwaitingSecondTap -> onAwaitingSecondTap(event)
        ItemGesturePhase.SecondPressed -> onSecondPressed(event)
    }

    /**
     * The gap between the two taps. A press restarts the gesture as the second one; the window closing means
     * the first tap was a single tap, and it opens the item late rather than not at all.
     */
    private fun onAwaitingSecondTap(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        ItemGestureEvent.Down -> {
            phase = ItemGesturePhase.SecondPressed
            noEffect()
        }
        ItemGestureEvent.DoubleTapTimeout -> {
            phase = ItemGesturePhase.Idle
            effect(ItemGestureEffect.OpenItem)
        }
        // No finger is down, so nothing else can reach this state.
        is ItemGestureEvent.Move,
        ItemGestureEvent.LongPress,
        ItemGestureEvent.Up,
        ItemGestureEvent.Cancel,
        ItemGestureEvent.TakenByParent,
        -> noEffect()
    }

    /**
     * The second press.
     *
     * **A move past slop abandons the double tap rather than completing it**, and the swipe is left to
     * whoever wants it: a finger that presses twice and then drags has stopped making a double tap, and
     * firing one on release would act on a gesture the user visibly changed their mind about. A long press is
     * still the menu — the second press is an ordinary press in every way except what its release means.
     */
    private fun onSecondPressed(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        ItemGestureEvent.Up -> {
            phase = ItemGesturePhase.Idle
            effect(ItemGestureEffect.DoubleTapAction)
        }
        is ItemGestureEvent.Move -> {
            if (event.offsetFromDown.pastSlop()) phase = ItemGesturePhase.ReleasedToParent
            noEffect()
        }
        ItemGestureEvent.LongPress -> {
            phase = ItemGesturePhase.MenuOpen
            effect(ItemGestureEffect.ShowMenu)
        }
        ItemGestureEvent.TakenByParent -> {
            phase = ItemGesturePhase.ReleasedToParent
            noEffect()
        }
        ItemGestureEvent.Cancel -> reset()
        ItemGestureEvent.Down, ItemGestureEvent.DoubleTapTimeout -> noEffect()
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
            // A quick, still press is a tap — **unless a second one could still be coming**, which is only
            // true on an item whose owner assigned a double tap. Everywhere else this fires at once, as ever.
            if (doubleTap) {
                phase = ItemGesturePhase.AwaitingSecondTap
                noEffect()
            } else {
                phase = ItemGesturePhase.Idle
                effect(ItemGestureEffect.OpenItem)
            }
        }
        // The pan claimed at its own slop, below ours: hand the gesture over before the long-press can fire.
        ItemGestureEvent.TakenByParent -> {
            phase = ItemGesturePhase.ReleasedToParent
            noEffect()
        }
        ItemGestureEvent.Cancel -> reset()
        ItemGestureEvent.Down, ItemGestureEvent.DoubleTapTimeout -> noEffect()
    }

    private fun onSwiped(current: ItemGesturePhase.Swiped, event: ItemGestureEvent): List<ItemGestureEffect> =
        when (event) {
            // Direction is locked at recognition; further movement doesn't change the committed swipe, but it does
            // move the item — the pull is how a swipe that has not fired yet shows that it is being made.
            is ItemGestureEvent.Move ->
                effect(ItemGestureEffect.SwipeProgress(current.direction, event.offsetFromDown))
            ItemGestureEvent.Up -> {
                phase = ItemGesturePhase.Idle
                listOf(ItemGestureEffect.EdgeAction(current.direction), ItemGestureEffect.SwipeSettled)
            }
            ItemGestureEvent.Cancel -> {
                phase = ItemGesturePhase.Idle
                effect(ItemGestureEffect.SwipeSettled)
            }
            // The long-press timer is stale once we've moved; ignore it. `TakenByParent` cannot arrive in the
            // three phases that own the finger — the modifier only sends it while the item does not — and the
            // branch exists so adding a phase has to answer for it.
            ItemGestureEvent.LongPress,
            ItemGestureEvent.Down,
            ItemGestureEvent.TakenByParent,
            ItemGestureEvent.DoubleTapTimeout,
            -> noEffect()
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
        ItemGestureEvent.LongPress,
        ItemGestureEvent.Down,
        ItemGestureEvent.TakenByParent,
        ItemGestureEvent.DoubleTapTimeout,
        -> noEffect()
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
        ItemGestureEvent.LongPress,
        ItemGestureEvent.Down,
        ItemGestureEvent.TakenByParent,
        ItemGestureEvent.DoubleTapTimeout,
        -> noEffect()
    }

    // The swipe belongs to the parent now; do nothing until the pointer lifts, then reset.
    private fun onReleasedToParent(event: ItemGestureEvent): List<ItemGestureEffect> = when (event) {
        ItemGestureEvent.Up, ItemGestureEvent.Cancel -> reset()
        is ItemGestureEvent.Move,
        ItemGestureEvent.LongPress,
        ItemGestureEvent.Down,
        ItemGestureEvent.TakenByParent,
        ItemGestureEvent.DoubleTapTimeout,
        -> noEffect()
    }

    private fun reset(): List<ItemGestureEffect> {
        phase = ItemGesturePhase.Idle
        return noEffect()
    }

    private fun Offset.pastSlop(): Boolean = getDistance() >= config.touchSlopPx

    private fun Offset.dominantDirection(): SwipeDirection = swipeDirectionOf(x, y)

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
 * @property doubleTapWindowMillis how long a release waits for a second press before it counts as a single
 *   tap — **the delay an item with a double tap assigned adds to its own launch**, and the reason the flag is
 *   per item rather than global. Timed by the gesture modifier, like the long press, and kept here so all
 *   gesture tuning has one home.
 * @property longPressTimeoutMillis how long a still press waits before the menu shows. The machine doesn't
 *   time anything itself — the gesture modifier runs this timer and feeds in [ItemGestureEvent.LongPress] — but
 *   it lives here so all gesture tuning has one home.
 */
data class ItemGestureConfig(
    val touchSlopPx: Float,
    val longPressTimeoutMillis: Long,
    val doubleTapWindowMillis: Long = DEFAULT_DOUBLE_TAP_WINDOW_MS,
)

/**
 * The platform's own double-tap window, which is what a user's thumb is already calibrated to.
 *
 * A default rather than a value every call site passes, unlike the slop and the long press: those two are
 * tuned for this launcher's feel, while this one is a fact about how fast people tap and there is nothing
 * here to have an opinion about.
 */
private const val DEFAULT_DOUBLE_TAP_WINDOW_MS = 300L
