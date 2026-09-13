package inkspire.morphic.feature.home.gestureaction

import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.SwipeDirection

/**
 * What the action picker is choosing for — and so which store it reads the current choice from and writes to.
 *
 * Two, because a gesture belongs either to one home item or to HOME itself, and the two are stored apart: an item's
 * gesture follows the item wherever it is placed, while HOME's swipes decide how its edges are crossed.
 */
sealed interface GestureTarget {

    /** One gesture on one home item. */
    data class Item(val item: GridItem, val gesture: ItemGesture) : GestureTarget

    /** A swipe on HOME itself. */
    data class HomeSwipe(val direction: SwipeDirection) : GestureTarget

    /** A double tap on HOME's empty space. */
    data object HomeDoubleTap : GestureTarget
}
