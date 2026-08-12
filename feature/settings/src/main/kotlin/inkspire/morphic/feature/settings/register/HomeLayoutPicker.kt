package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.label
import inkspire.morphic.feature.settings.subtitle

private val RowGap = 10.dp
private val RadioGap = 12.dp

/**
 * **What HOME is: pages over a dock, or a list under a widget area.**
 *
 * [SideBindingPicker]'s twin, and deliberately the same shape — a modal of radio rows, one tap, no confirm. It is the
 * same *kind* of choice one card over (what goes in this slot?), so it should not look like a different kind.
 *
 * **Each row carries a second line**, which the side picker's do not, and the reason is that a pairing is two zones
 * while a side binding is one arrangement: "List with a widget area" names half of what changes, so the subtitle names
 * the rest. L1's equivalent was a scroll row of two mockup cards labeled "Classic" and "Minimalist" — names for eras
 * of that launcher rather than descriptions of what you get, and the mockups are the thing the register cross already
 * decided not to draw at card size.
 *
 * Switching is **non-destructive**: each pairing's zones have their own stored sizes and their own stored contents, so
 * the one that goes off screen keeps everything it had. That is what makes one tap with no confirm honest here.
 *
 * @param selected the pairing currently in force.
 * @param onSelect commits and dismisses. `feature:shell` and `HomeScreen` are watching the same flow, so there is
 *   nothing to apply.
 */
@Composable
internal fun HomeLayoutPicker(
    selected: HomeLayout,
    onSelect: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home") },
        text = {
            Column {
                HomeLayout.entries.forEach { layout ->
                    LayoutRow(
                        label = layout.label,
                        subtitle = layout.subtitle,
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

/** One pairing: what it is called, what it puts where, and whether it is the one in force. */
@Composable
private fun LayoutRow(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = RowGap),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(RadioGap))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
    }
}
