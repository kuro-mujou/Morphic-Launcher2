package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * A four-way swipe, named for the way the **finger** travels.
 *
 * Deliberately not named for what the swipe reveals: a finger travelling right drags a surface rightward and so
 * opens the one parked on the *left*, and the two vocabularies are exact opposites. Everything a user assigns is
 * named this way, because it is the only one they can see.
 *
 * **In `core:model` rather than beside the gesture machine that reads it**, which is where it began: a user's
 * per-item gesture assignment is stored, so `data:settings` has to name it, and a data module cannot reach into
 * `core:designsystem`. A pure enum with no Compose in it belongs here by the same rule that put [HomeEdge] here.
 */
@Serializable
enum class SwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    ;

    /**
     * Which axis this travels along.
     *
     * Asked by a surface that owns one axis for itself and can only offer the other: home's list scrolls
     * vertically, so a row may take a horizontal swipe and must not take a vertical one.
     */
    val isHorizontal: Boolean get() = this == LEFT || this == RIGHT
}

/**
 * The direction a movement of ([dx], [dy]) is going — its dominant axis, then its sign.
 *
 * **One derivation with three readers**, which is why it is here rather than a private helper in each: the
 * gesture machine names the swipe an item commits to, the surface pan asks whether the pressed item has taken
 * that direction, and home's own pager asks the same before it takes a page swipe. All three must agree about
 * what a diagonal is, and none of them would fail loudly if they did not — a swipe would simply go to the
 * wrong recognizer near 45 degrees.
 *
 * Ties go horizontal, arbitrarily but consistently.
 */
fun swipeDirectionOf(dx: Float, dy: Float): SwipeDirection = when {
    kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> if (dx >= 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
    else -> if (dy >= 0f) SwipeDirection.DOWN else SwipeDirection.UP
}
