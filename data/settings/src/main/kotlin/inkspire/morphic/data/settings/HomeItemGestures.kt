package inkspire.morphic.data.settings

import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.serialization.Serializable

/**
 * **Which swipe directions each home item has taken for itself.**
 *
 * A user assigns an action to a direction on one icon — swipe up on the camera, say — and from then on that swipe
 * belongs to the icon rather than opening a side surface. This is the record of *which* directions are spoken for;
 * what each one does is a separate question, and one this type deliberately does not answer yet (see below).
 *
 * **Home only, and that is a property of the surface rather than a restriction imposed here.** An APPS layout passes
 * no claimed directions at all, so a swipe there always reaches the pan.
 *
 * **A list, not a `Map<GridItem, …>`.** The settings blob is JSON and `GridItem` is a polymorphic sealed type, so as
 * a key it would need structured map keys turned on for every slice to serve this one. A launcher's home holds tens
 * of items, so a scan costs nothing.
 *
 * **Sparse: an item with nothing assigned has no row.** The same rule the icon-sizing overrides follow — nothing is
 * stored for an item nobody has touched, so a later change to what "unassigned" means still reaches them.
 *
 * ## The field name is the seam, and this is the second time it has moved
 *
 * It held a set of bare directions, then a set of gestures, and now a gesture-to-action map. Each step renamed
 * the field rather than re-reading the old one, because a stored set read as a map would have given every
 * assignment whatever the first action happened to be — silently, and only on devices that had one. The cost is
 * that assignments are cleared once per move, which is the trade this codebase takes every time: see
 * `searchByLayout`.
 */
@Serializable
data class HomeItemGestures(val items: List<ItemGestures> = emptyList()) {

    /** What each of [item]'s gestures does, empty for an item nobody has assigned anything on. */
    fun actionsOn(item: GridItem): Map<ItemGesture, GestureAction> =
        items.firstOrNull { it.item == item }?.actions.orEmpty()

    /** The gestures [item] has taken, whatever they do. */
    fun gesturesOn(item: GridItem): Set<ItemGesture> = actionsOn(item).keys

    /**
     * Just the swipes, which is what the gesture contract and the surface pan deal in.
     *
     * The double tap is filtered out here rather than stored apart, so the sheet can draw one list while each
     * recognizer still gets only what it can act on — see [ItemGesture.swipe].
     */
    fun swipesOn(item: GridItem): Set<SwipeDirection> =
        gesturesOn(item).mapNotNullTo(mutableSetOf()) { it.swipe }

    /** Whether [item] has a double tap, which is the only thing that makes its launch wait. */
    fun hasDoubleTapOn(item: GridItem): Boolean = ItemGesture.DOUBLE_TAP in gesturesOn(item)

    /**
     * This record with [gesture] on [item] set to [action], or cleared when it is null.
     *
     * Clearing the **last** gesture on an item removes its row rather than storing an empty one, which is what
     * keeps the record sparse.
     */
    fun withAction(item: GridItem, gesture: ItemGesture, action: GestureAction?): HomeItemGestures {
        val actions = actionsOn(item).let { if (action == null) it - gesture else it + (gesture to action) }
        val rest = items.filterNot { it.item == item }
        return HomeItemGestures(if (actions.isEmpty()) rest else rest + ItemGestures(item, actions))
    }

    companion object {
        /** Nothing assigned, which is every launcher until a user assigns something. */
        val Default = HomeItemGestures()
    }
}

/**
 * One home item's claimed directions.
 *
 * @property item what the gesture belongs to — **the item on home, not the app**. The same app placed twice, or
 *   sitting in a folder as well as on the grid, is two items and can carry two different sets.
 * @property actions what each taken gesture does. A gesture absent from the map is unassigned; there is no
 *   `None` action, since that would make one state expressible two ways.
 */
@Serializable
data class ItemGestures(val item: GridItem, val actions: Map<ItemGesture, GestureAction>)
