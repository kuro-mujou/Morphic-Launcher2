package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * A gesture a home item can take for itself — **the user's vocabulary, and the unit an assignment is keyed by.**
 *
 * One type covering the four swipes and the double tap, because that is how they are chosen: a single list on one
 * sheet, one action apiece. They are recognized by quite different machinery — a swipe by direction past a
 * threshold, a double tap by a second press inside a window — and [swipe] is where the two part company. Keeping
 * them one type at the point of *assignment* and splitting them at the point of *recognition* is the right way
 * round; splitting the model as well would make the sheet stitch two sets back together to draw one list.
 */
@Serializable
enum class ItemGesture {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    DOUBLE_TAP,
    ;

    /**
     * The swipe this is, or null for [DOUBLE_TAP].
     *
     * Null is the seam between the two recognizers: the swipes become an item's `edgeActions` and are what the
     * surface pan is asked about, while the double tap is a timing decision the pan never sees.
     */
    val swipe: SwipeDirection?
        get() = when (this) {
            SWIPE_UP -> SwipeDirection.UP
            SWIPE_DOWN -> SwipeDirection.DOWN
            SWIPE_LEFT -> SwipeDirection.LEFT
            SWIPE_RIGHT -> SwipeDirection.RIGHT
            DOUBLE_TAP -> null
        }
}

/**
 * The gesture this swipe is, which is [ItemGesture.swipe] read the other way.
 *
 * Needed because the gesture *contract* speaks in [SwipeDirection] — it knows nothing of double taps — while an
 * assignment is keyed by [ItemGesture]. One conversion in one place, rather than a `when` at each surface that
 * fires one.
 */
fun SwipeDirection.asItemGesture(): ItemGesture = when (this) {
    SwipeDirection.UP -> ItemGesture.SWIPE_UP
    SwipeDirection.DOWN -> ItemGesture.SWIPE_DOWN
    SwipeDirection.LEFT -> ItemGesture.SWIPE_LEFT
    SwipeDirection.RIGHT -> ItemGesture.SWIPE_RIGHT
}
