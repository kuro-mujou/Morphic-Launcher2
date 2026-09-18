package inkspire.morphic.feature.home.gestureaction

import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.core.model.widget.LayerPath

/**
 * What the action picker is choosing for — and so which store it reads the current choice from and writes to.
 *
 * An item's gesture follows the item wherever it is placed and HOME's swipes decide how its edges are crossed, so the
 * two are stored apart; a tap on part of a widget is stored in that widget's recipe, and travels with it.
 */
sealed interface GestureTarget {

    /** One gesture on one home item. */
    data class Item(val item: GridItem, val gesture: ItemGesture) : GestureTarget

    /** A swipe on HOME itself. */
    data class HomeSwipe(val direction: SwipeDirection) : GestureTarget

    /** A double tap on HOME's empty space. */
    data object HomeDoubleTap : GestureTarget

    /**
     * A tap on one part of one of the launcher's own widgets, stored in that widget's recipe — so it travels with the
     * widget rather than living in settings beside it.
     */
    data class WidgetLayer(val widgetId: Long, val path: LayerPath) : GestureTarget
}
