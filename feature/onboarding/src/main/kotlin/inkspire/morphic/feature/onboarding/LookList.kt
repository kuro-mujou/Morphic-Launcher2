package inkspire.morphic.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The offered looks as rows of a name over one line saying what it is — one selected at a time.
 *
 * **Filled, not ticked**, which is this codebase's one way of saying "selected": the settings index marks its open
 * section the same way. A radio group to accessibility services, since exactly one look is ever the one on screen.
 */
@Composable
internal fun LookList(
    looks: List<LookRow>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        looks.forEach { row ->
            LookRowItem(row = row, selected = row.id == selected, onClick = { onSelect(row.id) })
        }
    }
}

@Composable
private fun LookRowItem(row: LookRow, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    val content = if (selected) colors.onAccent else colors.content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = row.name, style = MaterialTheme.typography.titleMedium, color = content)
        Text(
            text = row.summary,
            style = MaterialTheme.typography.bodySmall,
            // On a filled row the fill is the emphasis, so the summary keeps the row's own ink rather than a second gray.
            color = if (selected) content else colors.contentMuted,
        )
    }
}
