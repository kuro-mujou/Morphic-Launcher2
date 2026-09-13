package inkspire.morphic.data.settings

import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.ShadeStyle
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.serialization.Serializable

/**
 * **What HOME itself does when swiped**, apart from what is bound to its edges: an action per swipe direction, and the
 * [ShadeStyle] a system-panel action needs.
 *
 * **A direction with an action takes the one-finger swipe, and the edge it points at keeps the two-finger one.** So an
 * action never costs the user a side surface, and a direction with none behaves exactly as it does without this.
 *
 * Sparse: an unassigned direction has no entry, and there is no `None` action, for [GestureAction]'s reason.
 *
 * @property shadeStyle read only when [GestureAction.OpenSystemPanel] fires. Kept when that action is cleared, so
 *   assigning it again does not need the question answered twice.
 */
@Serializable
data class HomeGestures(
    val swipes: Map<SwipeDirection, GestureAction> = emptyMap(),
    val shadeStyle: ShadeStyle = ShadeStyle.COMBINED,
) {
    /** This record with [direction] set to [action], or cleared when it is null — removed, which keeps it sparse. */
    fun withSwipe(direction: SwipeDirection, action: GestureAction?): HomeGestures =
        copy(swipes = if (action == null) swipes - direction else swipes + (direction to action))

    /**
     * The edges a surface bound there opens with **two fingers only**, because HOME keeps the swipe revealing them.
     *
     * One derivation for the gesture that obeys it and the settings card that explains it. If they disagreed, the card
     * would promise one finger on an edge that no longer answers to one.
     */
    val twoFingerEdges: Set<HomeEdge>
        get() = swipes.keys.mapTo(mutableSetOf()) { it.revealedEdge }

    companion object {
        /** Nothing assigned: every swipe on HOME does what its edge binding says. */
        val Default = HomeGestures()
    }
}
