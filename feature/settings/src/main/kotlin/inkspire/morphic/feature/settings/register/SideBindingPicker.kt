package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.feature.settings.label

/**
 * **What a register slot may hold: nothing, or the APPS surface in one of its layouts.**
 *
 * L1's dialog, ported as it stands — a modal because the choice belongs to **one edge**, and a title is what says
 * which. Radio rows because the six options are mutually exclusive and an edge holds exactly one thing or nothing.
 *
 * **"None" is a peer of the layouts, not a separate switch**, which was already the chip row's rule and survives the
 * port: one set of mutually exclusive rows says it without a second control that could disagree with the first.
 *
 * @param edge the slot being filled — named in the title, since a modal with no subject is a list of layouts floating
 *   over the screen.
 * @param selected the layout currently bound to [edge], or null when it is unbound.
 * @param onSelect commits and dismisses. One tap, no confirm: the register is a live setting and `feature:shell` is
 *   watching the same flow, so there is nothing to apply.
 */
@Composable
internal fun SideBindingPicker(
    edge: HomeEdge,
    selected: AppsLayout?,
    onSelect: (AppsLayout?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${edge.label} edge") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerRow(
                    label = "None",
                    subtitle = "This edge is not swipeable",
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
                AppsLayout.entries.forEach { layout ->
                    PickerRow(
                        label = layout.label,
                        subtitle = null,
                        selected = selected == layout,
                        onClick = { onSelect(layout) },
                    )
                }
            }
        },
        // A "Cancel" would suggest the taps above were provisional; they are not. This only closes.
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.content) } },
        containerColor = colors.background,
        titleContentColor = colors.content,
        textContentColor = colors.content,
    )
}

/** One choice: what it is called, and whether it is the one in force. */
@Composable
private fun PickerRow(label: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
        }
    }
}

/** The edge's own name, for the dialog title. Sentence case, since it is read as a phrase rather than a heading. */
private val HomeEdge.label: String
    get() = when (this) {
        HomeEdge.LEFT -> "Left"
        HomeEdge.RIGHT -> "Right"
        HomeEdge.TOP -> "Top"
        HomeEdge.BOTTOM -> "Bottom"
    }
