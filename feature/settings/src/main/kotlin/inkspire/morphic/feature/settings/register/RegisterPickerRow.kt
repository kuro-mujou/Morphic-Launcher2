package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * One mutually-exclusive choice in a register modal: what it is called, an optional line under it, and whether it is
 * the one in force.
 *
 * Extracted on its second consumer — [SideBindingPicker] and [SurfaceTransitionPicker] — which is this module's
 * standing rule. Both are radio rows in an `AlertDialog` over the surface register, and what would have drifted
 * between two hand-rolled copies is the small stuff a picker is made of: the corner radius, the gap to the radio, the
 * muted color of the subtitle.
 */
@Composable
internal fun RegisterPickerRow(label: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
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
