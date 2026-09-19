package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridItem

/**
 * Where [item] was **visibly** drawn at the instant one drawing of it handed over to another — the resting cell giving
 * way to the floating proxy at a lift, or the proxy giving way to a grid cell at a drop.
 *
 * The two drawings are separate composables and swap in one frame, so without this the item teleports: from its cell
 * to the finger's grab on lift (the lift fires a slop's travel after the press), and from the finger to its cell on
 * drop. The one taking over reads this and starts from [centerInRoot] instead of from where it is laid out, then
 * springs home.
 *
 * **Honoured only while [isFresh]**, and that is what keeps it from being replayed. A drawing that composes *later* —
 * a folder opened a minute after an app was dropped into it, the home proxy taking over when the APPS drawer ejects a
 * drag — would otherwise find the handoff still lying there and fly in from a spot the user left long ago. The window
 * is wide enough for the drop's new placement to arrive through the view model's state flow, which is a few frames.
 *
 * @property centerInRoot the item's visual centre in root px.
 * @property leftSource for a landing: the drop took the item away from where it was lifted — merged into a folder,
 *   removed, or carried into another zone. The cell it was lifted out of is about to be disposed, so it must stay
 *   hidden rather than glide back toward a slot the item no longer has; only a cell that newly holds it glides in.
 * @property fromZone for a landing: the zone the item was lifted in. What tells a holder that draws its own members
 *   (an icon container) "this left *me*" from "this arrived *here* from somewhere else" — both have [leftSource].
 */
data class DragHandoff(
    val item: GridItem,
    val centerInRoot: Offset,
    val leftSource: Boolean = false,
    val fromZone: ZoneId? = null,
    val atNanos: Long = System.nanoTime(),
) {
    val isFresh: Boolean get() = System.nanoTime() - atNanos < HandoffFreshMs * 1_000_000L
}

/** How long after the swap a newly composed drawing may still take the handoff. */
internal const val HandoffFreshMs = 300L
