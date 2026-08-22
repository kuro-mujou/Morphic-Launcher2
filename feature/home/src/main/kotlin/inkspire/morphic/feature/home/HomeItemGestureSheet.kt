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
import inkspire.morphic.core.model.ItemGesture

/**
 * The **Gestures** sheet for one home item: a row per swipe direction, and what each one is assigned to.
 *
 * **Every gesture is listed, assigned or not**, which is what makes the sheet answer "what could I do here"
 * rather than only "what have I done". An unassigned row is the ordinary way in, so it cannot read as disabled.
 *
 * **A tap toggles, for now.** The intended flow opens a full-screen picker on the chosen direction; until that
 * exists a tap takes the direction or hands it back, and the gesture fires a placeholder. What is being exercised
 * meanwhile is the part that is hard and invisible — the surface pan handing a claimed swipe to the item instead of
 * sliding a surface in — which needs a real assignment to be reachable at all.
 *
 * @param label the item's own name, so the sheet says whose gestures these are.
 * @param assigned the directions this item has taken.
 */
@Composable
internal fun HomeItemGestureSheet(
    label: String,
    assigned: Set<ItemGesture>,
    onToggle: (ItemGesture) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    // Sized to its rows rather than to a fraction of the screen: five short rows in a fixed half-screen box
    // left the last one clipped against the bottom edge.
    LauncherBottomSheet(onDismiss = onDismiss, heightFraction = null) {
        Column(Modifier.padding(horizontal = SheetPadding)) {
            Text(
                text = "Gestures",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.content,
                modifier = Modifier.padding(bottom = TitleGap),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
                modifier = Modifier.padding(bottom = SheetPadding),
            )
            ItemGesture.entries.forEach { gesture ->
                GestureRow(
                    gesture = gesture,
                    assigned = gesture in assigned,
                    onClick = { onToggle(gesture) },
                )
            }
        }
    }
}

/** One direction and its state. The whole row is the target, as every settings row in this launcher is. */
@Composable
private fun GestureRow(gesture: ItemGesture, assigned: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = RowPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            Text(gesture.label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(
                text = if (assigned) AssignedLabel else UnassignedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (assigned) colors.accent else colors.contentMuted,
            )
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

/**
 * What an assigned direction says it does.
 *
 * A placeholder in the literal sense: the action picker is unbuilt, so a taken direction currently has no action
 * behind it and the row says exactly that rather than naming something that does not exist.
 */
private const val AssignedLabel = "Taken by this icon"
private const val UnassignedLabel = "Not assigned"

private val SheetPadding = 20.dp
private val TitleGap = 4.dp
private val RowPaddingV = 14.dp
private val RowGap = 2.dp
