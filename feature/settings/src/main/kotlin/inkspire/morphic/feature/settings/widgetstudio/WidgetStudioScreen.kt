package inkspire.morphic.feature.settings.widgetstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.ShrinkToFit
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widget.WidgetRender
import inkspire.morphic.core.widgetscript.ScriptData
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The widget studio, opened on its **Style** tab — the widget above, and below it the settings of **one part at a
 * time**: the widget itself, or a block selected by tapping it or picking it by name. Showing one part's settings
 * rather than every setting at once is what keeps two Clock blocks' "Text color" from being two identical rows.
 *
 * **The preview sits on the real wallpaper.** The window is transparent, so the top half paints nothing and the widget
 * is seen over what it will be seen over on HOME, at the size HOME draws it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetStudioScreen(route: WidgetStudioRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: WidgetStudioViewModel = koinViewModel { parametersOf(route) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current
    BackHandler(onBack = onBack)
    // A widget removed from HOME while this was open has nothing left to style.
    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Style") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = uiInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            WidgetPreview(
                recipe = state.recipe,
                data = state.data,
                size = DpSize(route.widthDp.dp, route.heightDp.dp),
                parts = state.parts,
                selected = state.selected,
                onSelect = viewModel::select,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            )
            if (state.recipe != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                ) {
                    if (state.parts.isNotEmpty()) {
                        PartPicker(
                            parts = state.parts,
                            selected = state.selected,
                            onSelect = viewModel::select,
                            modifier = Modifier
                                .uiInsetsPadding(WindowInsetsSides.Horizontal)
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                        )
                    }
                    // Keyed on the part, so a control left open in one does not open its namesake in the next.
                    key(state.selected) {
                        StylePanel(
                            globals = state.globals,
                            initial = state.initial[state.selected].orEmpty(),
                            onChange = viewModel::set,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The widget at the size HOME draws it, shrunk — never grown — to fit the space above the controls. A tap on a block
 * selects it and a tap anywhere else selects the widget; the selected block is outlined.
 */
@Suppress("LongParameterList") // What is drawn, and the selection drawn over it.
@Composable
private fun WidgetPreview(
    recipe: WidgetRecipe?,
    data: ScriptData?,
    size: DpSize,
    parts: List<StylePart>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where each layer was drawn, as the renderer reports it — in the widget's own pixels, which is also the space the
    // tap arrives in, since both sit inside the preview's scaling.
    var bounds by remember { mutableStateOf<Map<Int, IntRect>>(emptyMap()) }
    val currentParts by rememberUpdatedState(parts)
    val currentOnSelect by rememberUpdatedState(onSelect)
    ShrinkToFit(size = size, modifier = modifier) {
        if (recipe != null && data != null) {
            WidgetRender(
                recipe = recipe,
                data = data,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { currentOnSelect(partAt(it, currentParts, bounds)) }
                    }
                    .drawWithContent {
                        drawContent()
                        selected?.let(bounds::get)?.let { drawSelection(it.toRect()) }
                    },
                onLayout = { if (it != bounds) bounds = it },
            )
        }
    }
}

/** An outline just outside [box]: white over a soft dark edge, so it reads on a light widget and a dark one alike. */
private fun DrawScope.drawSelection(box: Rect) {
    val outline = box.inflate(4.dp.toPx())
    val corner = CornerRadius(8.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = 0.35f), outline.topLeft, outline.size, corner, style = Stroke(4.dp.toPx()))
    drawRoundRect(Color.White, outline.topLeft, outline.size, corner, style = Stroke(2.dp.toPx()))
}

/** The widget and each of its blocks, one segment each — the same choice a tap on the preview makes. */
@Composable
private fun PartPicker(parts: List<StylePart>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    MorphicSegmentedButtons(
        options = listOf("Widget") + parts.map { it.name },
        selectedIndex = selected?.let { index -> parts.indexOfFirst { it.index == index } + 1 } ?: 0,
        onSelect = { i -> onSelect(parts.getOrNull(i - 1)?.index) },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Every setting of the selected part, one control each — or a plain line when it has none. */
@Composable
private fun StylePanel(
    globals: List<WidgetGlobal>,
    initial: Map<String, WidgetGlobal>,
    onChange: (WidgetGlobal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (globals.isEmpty()) {
            Text(
                text = "Nothing here to restyle.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
            )
        }
        globals.forEach { global ->
            StyleControl(
                global = global,
                initial = initial[global.name],
                expanded = expanded == global.name,
                onExpand = { expanded = if (expanded == global.name) null else global.name },
                onChange = onChange,
            )
        }
    }
}
