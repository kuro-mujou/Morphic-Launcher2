package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptExample
import inkspire.morphic.core.widgetscript.ScriptExamples
import inkspire.morphic.core.widgetscript.WidgetExpression

/**
 * The language, taught by example: each one in three lines — what it is for, **what it shows on this device right
 * now**, and the formula — one family at a time. Tapping one puts its formula in the field.
 *
 * The live result is the point. A formula read beside what it produces is learned; one read beside a description of
 * what it would produce is not.
 *
 * @param data what the examples are evaluated against — the device's readings with the open layer's settings, so what
 *   each example shows is what it would show if inserted here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExampleSheet(data: ScriptData?, onPick: (ScriptExample) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalMorphicColors.current
    val families = remember { ScriptExamples.families.entries.toList() }
    var family by rememberSaveable { mutableStateOf(families.first().key) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Text(
            text = "Formulas",
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        ChipRow(
            labels = families.map { it.value },
            selected = families.indexOfFirst { it.key == family },
            onSelect = { family = families[it].key },
            modifier = Modifier.padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        )
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            items(ScriptExamples.all.filter { it.family == family }, key = { it.formula }) { example ->
                ExampleRow(example, data) { onPick(example) }
            }
        }
    }
}

/** One example: its title, its result now — or what is wrong, which a working example never shows — and its formula. */
@Composable
private fun ExampleRow(example: ScriptExample, data: ScriptData?, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    val result = remember(example, data) { data?.let { WidgetExpression.parse(example.formula).evaluate(it) } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(text = example.title, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        result?.let {
            Text(
                text = it.problems.firstOrNull()?.message ?: it.text.ifEmpty { "(nothing right now)" },
                style = MaterialTheme.typography.titleMedium,
                color = if (it.problems.isEmpty()) colors.content else colors.error,
            )
        }
        Text(
            text = example.formula,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.contentMuted,
        )
    }
}
