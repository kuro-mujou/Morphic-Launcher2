package inkspire.morphic.data.settings

import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.serialization.Serializable

/**
 * **What HOME itself does when swiped or double-tapped**, apart from what is bound to its edges: an action per swipe
 * direction and one for a double tap on empty space.
 *
 * **A direction with an action takes the one-finger swipe, and the edge it points at keeps the two-finger one.** So an
 * action never costs the user a side surface, and a direction with none behaves exactly as it does without this.
 *
 * Sparse: an unassigned direction has no entry, and there is no `None` action, for [GestureAction]'s reason.
 *
 * @property doubleTap what a double tap on HOME's empty space does, or null for nothing — a double tap then does
 *   nothing at all.
 */
@Serializable
data class HomeGestures(
    val swipes: Map<SwipeDirection, GestureAction> = emptyMap(),
    val doubleTap: GestureAction? = null,
) {
    /** This record with [direction] set to [action], or cleared when it is null — removed, which keeps it sparse. */
    fun withSwipe(direction: SwipeDirection, action: GestureAction?): HomeGestures =
        copy(swipes = if (action == null) swipes - direction else swipes + (direction to action))

    /** Every action assigned on HOME, whichever gesture holds it — what "does anything here need X" is asked of. */
    val actions: List<GestureAction>
        get() = swipes.values + listOfNotNull(doubleTap)

    /**
     * The edges a surface bound there opens with **two fingers only**, because HOME keeps the swipe revealing them.
     *
     * One derivation for the gesture that obeys it and the settings card that explains it. If they disagreed, the card
     * would promise one finger on an edge that no longer answers to one.
     */
    val twoFingerEdges: Set<HomeEdge>
        get() = swipes.keys.mapTo(mutableSetOf()) { it.revealedEdge }

    companion object {
        /** Nothing assigned: every swipe on HOME does what its edge binding says, and a double tap does nothing. */
        val Default = HomeGestures()
    }
}
