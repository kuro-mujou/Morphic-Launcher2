package inkspire.morphic.feature.settings.widgetstudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.WidgetTap
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.WidgetExpression
import kotlinx.coroutines.flow.drop

/**
 * Tier 3: the widget as a tree of layers. A row of chips is the path down to the open layer and a second row is what
 * sits beside it — or inside it, when it is a group — so the tree is walked a level at a time rather than shown whole,
 * the "breadcrumb rail" the plan asked for in place of a tree panel on a half-height sheet.
 */
@Composable
internal fun AdvancedPanel(
    state: WidgetStudioState,
    viewModel: WidgetStudioViewModel,
    onPickTapAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = state.recipe ?: return
    val path = state.focus.path
    val rail = PaddingValues(horizontal = 20.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow(
            labels = listOf("Widget") + path.indices.map { depth ->
                recipe.childrenAt(path.take(depth)).labels().getOrElse(path[depth]) { "" }
            },
            selected = path.size,
            onSelect = { viewModel.open(path.take(it)) },
            modifier = Modifier.padding(top = 20.dp),
            contentPadding = rail,
        )
        // A group's children, or a leaf's siblings with the leaf lit: always the level there is to move along.
        val level = if (recipe.isContainer(path)) path else path.dropLast(1)
        val layers = recipe.childrenAt(level)
        if (layers.isNotEmpty()) {
            ChipRow(
                labels = layers.labels(),
                selected = if (level == path) -1 else path.last(),
                onSelect = { viewModel.open(level + it) },
                contentPadding = rail,
            )
        }
        // Keyed on the path, so a field's text and an open picker belong to the layer they were opened on.
        key(path) {
            LayerEditor(state, viewModel, onPickTapAction, Modifier.weight(1f))
        }
    }
}

/** The open layer's properties, then what can be added beside or inside it, and its removal. */
@Composable
private fun LayerEditor(
    state: WidgetStudioState,
    viewModel: WidgetStudioViewModel,
    onPickTapAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    // The system photo picker: no storage permission, and only the picture chosen is ever readable.
    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addImage(uri)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        state.fields.forEach { field ->
            when (field) {
                is LayerField.Setting -> StyleControl(
                    global = field.control,
                    initial = null,
                    expanded = expanded == field.key,
                    onExpand = { expanded = if (expanded == field.key) null else field.key },
                    onChange = { viewModel.set(field, it) },
                )

                is LayerField.Formula -> FormulaControl(
                    label = field.label,
                    source = field.source,
                    data = state.formulaData,
                    browsing = state.browsing,
                    onBrowse = viewModel::showExamples,
                ) { viewModel.set(field, it) }
                is LayerField.Bound -> BoundRow(field.label, field.setting) { viewModel.unbind(field) }
                is LayerField.Tap -> TapControl(field, onSet = viewModel::setTap, onPickAction = onPickTapAction)
            }
        }
        Labeled("Add layer") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NewLayers.forEach { (label, layer) ->
                    MorphicButton(onClick = { viewModel.addLayer(layer) }, style = MorphicButtonStyle.Tonal) { Text(label) }
                }
                MorphicButton(onClick = { imagePicker.launch(imageRequest) }, style = MorphicButtonStyle.Tonal) {
                    Text("Image")
                }
            }
        }
        if (state.layer != null) {
            MorphicButton(
                onClick = viewModel::removeLayer,
                style = MorphicButtonStyle.Tonal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("Remove layer") }
        }
    }
}

/**
 * A formula typed as text, with what it shows right now under it — or what is wrong with it. The live result is how a
 * formula gets written without a manual: type, and watch what it says. **Examples** opens the worked examples, and
 * picking one puts it where the cursor is.
 *
 * @param data the readings a formula here would read, the open layer's settings included.
 * @param browsing whether the examples are open — the ViewModel's, since they widen what the preview reads.
 */
@Suppress("LongParameterList") // A field, what it is evaluated against, and the examples beside it.
@Composable
private fun FormulaControl(
    label: String,
    source: String,
    data: ScriptData?,
    browsing: Boolean,
    onBrowse: (Boolean) -> Unit,
    onChange: (String) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val field = rememberTextFieldState(source)
    var typed by remember { mutableStateOf(source) }
    // Whether *this* field opened the examples, so a pick lands in the field it was asked from.
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect {
            typed = it
            onChange(it)
        }
    }
    val result = remember(typed, data) { data?.let { WidgetExpression.parse(typed).evaluate(it) } }
    Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                asked = true
                onBrowse(true)
            }) { Text("Examples") }
        }
        MorphicTextField(state = field, modifier = Modifier.fillMaxWidth())
        result?.let {
            val problem = it.problems.firstOrNull()
            Text(
                text = problem?.message ?: "Shows: ${it.text}",
                style = MaterialTheme.typography.bodySmall,
                color = if (problem != null) colors.error else colors.contentMuted,
            )
        }
    }
    if (browsing && asked) {
        ExampleSheet(
            data = data,
            onPick = { example ->
                // Replaces the selection, or goes in at the cursor when nothing is selected.
                field.edit { replace(selection.min, selection.max, example.formula) }
                asked = false
                onBrowse(false)
            },
            onDismiss = {
                asked = false
                onBrowse(false)
            },
        )
    }
}

/**
 * What a tap on the layer does: one chip each for nothing, a launcher action, and every setting it can flip. **Action**
 * opens the launcher's own action picker, which writes the choice to the widget; the studio picks it up from there.
 */
@Composable
private fun TapControl(field: LayerField.Tap, onSet: (WidgetTap?) -> Unit, onPickAction: () -> Unit) {
    val colors = LocalMorphicColors.current
    val current = field.current
    val selected = when (current) {
        null -> 0
        is WidgetTap.Run -> 1
        is WidgetTap.Flip -> field.flippable.indexOfFirst { it.name == current.global }.let { if (it < 0) -1 else it + 2 }
    }
    Labeled("On tap") {
        ChipRow(
            labels = listOf("Nothing", "Action") + field.flippable.map { "Flip ${it.label}" },
            selected = selected,
            onSelect = { i ->
                when (i) {
                    0 -> onSet(null)
                    1 -> onPickAction()
                    else -> onSet(WidgetTap.Flip(field.flippable[i - 2].name))
                }
            },
        )
        when (current) {
            is WidgetTap.Run -> Text(
                text = "Runs a launcher action — tap Action to change it",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
            )
            // A flip whose setting has since gone does nothing on HOME, and says so here rather than looking chosen.
            is WidgetTap.Flip -> if (selected < 0) {
                Text(
                    text = "Flips a setting that no longer exists",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            null -> Unit
        }
    }
}

/** A property a setting decides: which one, and the way to take it back. */
@Composable
private fun BoundRow(label: String, setting: String, onUnbind: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(text = "Set by “$setting”", style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        TextButton(onClick = onUnbind) { Text("Unbind") }
    }
}

/**
 * The layers Advanced can add, each at a size that is visible the moment it lands — a shape or a bar has no size of
 * its own, and one added at its content size would draw nothing and look like a failure.
 */
private val NewLayers: List<Pair<String, WidgetLayerSpec>> = listOf(
    "Text" to WidgetLayerSpec(WidgetSource.Text("Text")),
    "Shape" to WidgetLayerSpec(
        WidgetSource.Shape(color = 0x40FFFFFF, cornerRadius = 12f),
        width = WidgetExtent.Dp(value = 80f),
        height = WidgetExtent.Dp(value = 80f),
    ),
    "Bar" to WidgetLayerSpec(
        WidgetSource.Progress("50"),
        width = WidgetExtent.Dp(value = 120f),
        height = WidgetExtent.Dp(value = 8f),
    ),
    "Group" to WidgetLayerSpec(WidgetSource.Overlap(), name = "Group"),
    "Stack" to WidgetLayerSpec(WidgetSource.Stack(spacing = 4f), name = "Stack"),
)
