package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.color.MorphicColorPicker
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetSource
import kotlinx.coroutines.flow.drop

/**
 * The control for one global, chosen by its type — the Style tab is a list of these and nothing else.
 *
 * @param initial the value when the screen opened, which a slider's reset returns to: a design's own default is not
 *   stored, and "how it was before I started" is the undo a person reaches for here.
 * @param expanded whether a color's picker is open; one at a time, so the list stays a list.
 */
@Composable
internal fun StyleControl(
    global: WidgetGlobal,
    initial: WidgetGlobal?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onChange: (WidgetGlobal) -> Unit,
) {
    when (global) {
        is WidgetGlobal.Color -> ColorControl(global, expanded, onExpand, onChange)
        is WidgetGlobal.Number -> MorphicSliderRow(
            value = global.value,
            valueRange = global.min..global.max,
            default = (initial as? WidgetGlobal.Number)?.value ?: global.value,
            what = global.label,
            label = global.label,
            valueLabel = { "%.0f".format(it) },
            onPreview = { onChange(global.copy(value = it)) },
            onCommit = { onChange(global.copy(value = it)) },
        )

        is WidgetGlobal.Switch -> MorphicSwitchRow(
            label = global.label,
            checked = global.value,
            onCheckedChange = { onChange(global.copy(value = it)) },
        )

        is WidgetGlobal.Choice -> Labeled(global.label) {
            MorphicSegmentedButtons(
                options = global.options,
                selectedIndex = global.selected,
                onSelect = { onChange(global.copy(selected = it)) },
            )
        }

        is WidgetGlobal.Font -> Labeled(global.label) {
            MorphicSegmentedButtons(
                options = listOf("Sans", "Serif", "Mono"),
                selectedIndex = global.value.ordinal,
                onSelect = { onChange(global.copy(value = WidgetSource.Text.Font.entries[it])) },
            )
        }

        is WidgetGlobal.Text -> TextControl(global, onChange)
    }
}

/** A swatch that opens the full picker beneath it. */
@Composable
private fun ColorControl(
    global: WidgetGlobal.Color,
    expanded: Boolean,
    onExpand: () -> Unit,
    onChange: (WidgetGlobal) -> Unit,
) {
    val colors = LocalMorphicColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = global.label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            Spacer(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(global.value))
                    .border(1.dp, colors.contentMuted, CircleShape),
            )
        }
        AnimatedVisibility(visible = expanded) {
            MorphicColorPicker(argb = global.value, onArgbChange = { onChange(global.copy(value = it)) })
        }
    }
}

/** A text setting. The field owns its state, for the design system's reason, and reports every edit. */
@Composable
private fun TextControl(global: WidgetGlobal.Text, onChange: (WidgetGlobal) -> Unit) {
    val field = rememberTextFieldState(global.value)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { onChange(global.copy(value = it)) }
    }
    Labeled(global.label) { MorphicTextField(state = field, modifier = Modifier.fillMaxWidth()) }
}

@Composable
internal fun Labeled(label: String, content: @Composable () -> Unit) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
        content()
    }
}
