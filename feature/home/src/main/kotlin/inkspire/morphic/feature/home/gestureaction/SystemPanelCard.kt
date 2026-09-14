package inkspire.morphic.feature.home.gestureaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.gesture.describeGestureAction
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadePull

/**
 * The ways a gesture can pull down the system's panels, as an [ExpandableCard] like an app's shortcuts.
 *
 * **Each row is a gesture, not a description of the phone** — see [ShadePull]. The two whose result is not their name
 * say what it is on a line of their own.
 *
 * **Open by default only while it holds the current choice**, for `ShortcutGroupCard`'s reason. The search does not
 * reach it: four fixed rows are not what anyone types a query to find.
 *
 * @param offersBySide whether [ShadePull.BY_SIDE] is listed — on a gesture whose starting side is the user's choice.
 */
@Composable
internal fun SystemPanelCard(assigned: GestureAction?, offersBySide: Boolean, onChoose: (GestureAction) -> Unit) {
    val colors = LocalMorphicColors.current
    val choices = ShadePull.entries
        .filter { offersBySide || it != ShadePull.BY_SIDE }
        .map { GestureAction.OpenSystemPanel(it) }
    ExpandableCard(
        items = choices,
        openByDefault = choices.any { it == assigned },
        header = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Filled.Notifications, contentDescription = null, tint = colors.contentMuted)
            }
            Text(
                text = "System panel",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
        },
        row = { choice ->
            PanelChoiceRow(choice = choice, selected = choice == assigned, onClick = { onChoose(choice) })
        },
    )
}

/** One way to pull, its label starting on the header's label line, with what it does beneath when its name does not say. */
@Composable
private fun PanelChoiceRow(choice: GestureAction.OpenSystemPanel, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 40.dp + 16.dp),
        ) {
            Text(
                text = describeGestureAction(choice, emptyMap()),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.onAccent else colors.content,
            )
            choice.pull.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onAccent else colors.contentMuted,
                )
            }
        }
        if (selected) SelectedMark()
    }
}

/** What a pull does, for the two whose name does not say it. */
private val ShadePull.note: String?
    get() = when (this) {
        ShadePull.PULL_DOWN -> "Opens whatever your phone opens when you pull down from the top"
        ShadePull.BY_SIDE -> "Left half opens notifications, right half opens quick settings"
        ShadePull.NOTIFICATIONS, ShadePull.QUICK_SETTINGS -> null
    }
