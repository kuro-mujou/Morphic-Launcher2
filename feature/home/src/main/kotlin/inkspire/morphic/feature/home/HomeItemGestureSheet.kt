package inkspire.morphic.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ItemGesture

/**
 * The **Gestures** sheet for one home item: a row per swipe direction, and what each one is assigned to.
 *
 * **Every gesture is listed, assigned or not**, which is what makes the sheet answer "what could I do here"
 * rather than only "what have I done". An unassigned row is the ordinary way in, so it cannot read as disabled.
 *
 * **A tap opens the picker** for that gesture, and the row shows what it currently does. Clearing is a choice
 * inside the picker rather than a second gesture on the row: "tap to choose, long-press to clear" would be two
 * verbs on a row whose whole job is to lead somewhere.
 *
 * @param label the item's own name, so the sheet says whose gestures these are.
 * @param assigned what each taken gesture does; a gesture absent from the map is unassigned.
 * @param describe names an action for a row — resolved by the caller, which is the only layer holding the app
catalog a stored component has to be looked up in.
 * @param unavailable gestures the surface on screen cannot honor, shown with [unavailableNote] under them.
 **Still assignable, deliberately**: an assignment belongs to the item and is keyed the same in both of
home's pairings, so one made here is live on the other. Hiding these rows would leave a stored gesture
that silently does nothing and no way to find out why.
 * @param unavailableNote why, in the words of the surface that cannot honor them.
 */
@Composable
internal fun HomeItemGestureSheet(
    label: String,
    assigned: Map<ItemGesture, GestureAction>,
    describe: (GestureAction) -> String,
    onPick: (ItemGesture) -> Unit,
    onDismiss: () -> Unit,
    unavailable: Set<ItemGesture> = emptySet(),
    unavailableNote: String = "",
) {
    val colors = LocalMorphicColors.current
    // Sized to its rows rather than to a fraction of the screen: five short rows in a fixed half-screen box
    // left the last one clipped against the bottom edge.
    LauncherBottomSheet(onDismiss = onDismiss, heightFraction = null) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Gestures",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.content,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            ItemGesture.entries.forEach { gesture ->
                GestureRow(
                    gesture = gesture,
                    action = assigned[gesture]?.let(describe),
                    note = if (gesture in unavailable) unavailableNote else null,
                    onClick = { onPick(gesture) },
                )
            }
        }
    }
}

/** One direction and its state. The whole row is the target, as every settings row in this launcher is. */
@Composable
private fun GestureRow(gesture: ItemGesture, action: String?, note: String?, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(gesture.label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(
                text = action ?: UnassignedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (action != null) colors.accent else colors.contentMuted,
            )
            // A warning rather than a description, which is the only kind of second line this launcher keeps:
            // it says why a gesture the user set is not firing, and nothing on screen could show that.
            if (note != null) {
                Text(text = note, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
    }
}

/** The swipes are named for the way the finger travels, matching [SwipeDirection]'s own vocabulary. */
private val ItemGesture.label: String
    get() = when (this) {
        ItemGesture.SWIPE_UP -> "Swipe up"
        ItemGesture.SWIPE_DOWN -> "Swipe down"
        ItemGesture.SWIPE_LEFT -> "Swipe left"
        ItemGesture.SWIPE_RIGHT -> "Swipe right"
        ItemGesture.DOUBLE_TAP -> "Double tap"
    }

private const val UnassignedLabel = "Not assigned"
