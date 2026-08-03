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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Provisional spacing — placeholders, like every other surface metric, until the settings layer owns its own. */
private val RowGapV = 12.dp
private val HeaderGapTop = 24.dp
private val ChipPaddingH = 12.dp
private val ChipPaddingV = 8.dp

/**
 * The settings surface's own row primitives — a group header, a switch row, a commit slider, and a chip.
 *
 * **Local to `feature:settings`, not in `core:designsystem`.** L1 filed its equivalents under
 * `core:designsystem/settings`, but there is exactly one consumer here and the design-system rule is to port a group
 * only when the screen needing it exists. They move out if a second surface ever wants them; until then this is the
 * screen that owns its rows.
 *
 * The switch is a stock M3 [Switch] rather than a `MorphicSwitch`, and that is the design-system rule rather than a
 * shortcut: components are built *on* M3 and restyled, and since `LauncherTheme` feeds MaterialTheme a monochrome
 * `ColorScheme` bridged from `MorphicColors`, a stock M3 control already renders greyscale and keeps its Expressive
 * motion. A fully custom one is reserved for controls M3 lacks.
 */

/** A group heading inside a settings screen. */
@Composable
internal fun SettingsSectionHeader(title: String) {
    val colors = LocalMorphicColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.contentMuted,
        modifier = Modifier.padding(top = HeaderGapTop, bottom = RowGapV / 2),
    )
}

/** A labelled on/off row. */
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
 * A slider that **previews while dragging and only writes on release**.
 *
 * The port of L1's `SettingsCommitSlider`, and here it is load-bearing rather than a nicety: every write is a
 * read-modify-write over a DataStore slice, so committing per drag frame would issue hundreds of transactions for one
 * gesture. Instead the dragged value is local state and [onCommit] fires once, when the finger lifts.
 *
 * The local value is `remember`ed **keyed on [value]**, which is what avoids a snap-back: on release the commit is
 * asynchronous, so rather than clearing the local value immediately (and briefly showing the old stored one), it is
 * held until the new value arrives and re-keys this `remember`.
 *
 * @param valueLabel renders the current value for display — a percentage, a multiplier, a dp count.
 */
@Composable
internal fun SettingsCommitSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onCommit: (Float) -> Unit,
    subtitle: String? = null,
    steps: Int = 0,
) {
    val colors = LocalMorphicColors.current
    var dragged by remember(value) { mutableStateOf<Float?>(null) }
    val shown = dragged ?: value

    Column(Modifier.fillMaxWidth().padding(vertical = RowGapV / 2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxWidth(FILL_BESIDE_CONTROL)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.content)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
            }
            Text(valueLabel(shown), style = MaterialTheme.typography.labelLarge, color = colors.accent)
        }
        MorphicSlider(
            value = shown,
            onValueChange = { dragged = it },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { dragged?.let(onCommit) },
        )
    }
}

/** One mutually-exclusive choice. Selection reads by contrast, not hue — the palette is greyscale by design. */
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
