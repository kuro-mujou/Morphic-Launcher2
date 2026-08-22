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
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }
