package inkspire.morphic.data.settings

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
 * ## What an assignment holds, and why it is only a direction today
 *
 * The action picker is unbuilt, so an assignment currently records that a direction is *taken* and nothing more; the
 * gesture fires a placeholder. That is deliberate rather than unfinished — the shape an action wants (an app, a
 * shortcut, one of a set of system verbs) is the picker's to determine, and inventing it here would be a model with
 * no consumer to shape it.
 *
 * **When it gains one, the field name is the seam.** [directions] becomes something like an action map under a new
 * name, because re-reading a stored set of bare directions as actions would silently give every assignment whatever
 * the first action in the list happened to be. That is the settings rule this codebase already applies to
 * `searchByLayout`.
 */
@Serializable
data class HomeItemGestures(val items: List<ItemGestures> = emptyList()) {

    /** The gestures [item] has taken, or empty for an item nobody has assigned anything on. */
    fun gesturesOn(item: GridItem): Set<ItemGesture> =
        items.firstOrNull { it.item == item }?.gestures.orEmpty()

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
     * This record with [item]'s gestures replaced.
     *
     * An empty set **removes the row** rather than storing one, which is what keeps the record sparse when a user
     * clears the last gesture off an icon.
     */
    fun withGestures(item: GridItem, gestures: Set<ItemGesture>): HomeItemGestures {
        val rest = items.filterNot { it.item == item }
        return HomeItemGestures(if (gestures.isEmpty()) rest else rest + ItemGestures(item, gestures))
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
 */
@Serializable
data class ItemGestures(val item: GridItem, val gestures: Set<ItemGesture>)
