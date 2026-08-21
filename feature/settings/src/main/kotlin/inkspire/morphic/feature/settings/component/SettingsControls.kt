package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Provisional spacing — placeholders, like every other surface metric, until the settings layer owns its own. */
private val RowGapV = 12.dp
private val HeaderGapTop = 24.dp
private val ChipPaddingH = 12.dp
private val ChipPaddingV = 8.dp

/**
 * The settings surface's own row primitives — a group header, a switch row, and a chip.
 *
 * **Local to `feature:settings`, not in `core:designsystem`.** There is exactly one consumer, and the design-system
 * rule is to port a group only when the screen needing it exists. They move out if a second surface ever wants them;
 * until then this is the screen that owns its rows. The slider row went the other way, and for that exact reason: the
 * icon studio is a second surface wanting one, so `MorphicSliderRow` lives in the design system and this file keeps
 * only how far apart two of them sit ([SettingsRowPadding]).
 *
 * The switch is a stock M3 [Switch] rather than a `MorphicSwitch`, and that is the design-system rule rather than a
 * shortcut: components are built *on* M3 and restyled, and since `LauncherTheme` feeds MaterialTheme a monochrome
 * `ColorScheme` bridged from `MorphicColors`, a stock M3 control already renders grayscale and keeps its Expressive
 * motion. A fully custom one is reserved for controls M3 lacks.
 */

/** A group heading inside a settings screen, or above a run of rows in the section list. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.contentMuted,
        modifier = modifier.padding(top = HeaderGapTop, bottom = RowGapV / 2),
    )
}

/** A labeled on/off row. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = RowGapV / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth(FILL_BESIDE_CONTROL)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The breathing room a slider row takes in a settings pane.
 *
 * **The row itself carries none**, deliberately: `MorphicSliderRow` is two stacked rows and the icon studio stacks six
 * of them in a rail where any extra would be a scroll. So the *surface* says how far apart they sit, and it says it
 * once — a `Modifier` shared by every call site rather than a dp repeated at a dozen of them, since the sections'
 * slots are plain `Column`s with no arrangement of their own.
 */
internal val SettingsRowPadding = Modifier.padding(vertical = RowGapV / 2)

/** One mutually-exclusive choice. Selection reads by contrast, not hue — the palette is grayscale by design. */
@Composable
internal fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onAccent else colors.content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = ChipPaddingH, vertical = ChipPaddingV),
    )
}

/** How much of a row the label takes, leaving the rest for the control beside it. */
private const val FILL_BESIDE_CONTROL = 0.7f
