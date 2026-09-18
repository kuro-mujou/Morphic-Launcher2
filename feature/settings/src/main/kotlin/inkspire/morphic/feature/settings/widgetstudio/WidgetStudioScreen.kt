package inkspire.morphic.feature.settings.widgetstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.ShrinkToFit
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widget.WidgetRender
import inkspire.morphic.core.widgetscript.ScriptData
import kotlinx.coroutines.launch
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
    val snackbar = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)
    // A removal is undoable for as long as its prompt shows, and not after — the prompt is the only way back.
    LaunchedEffect(state.removed) {
        val name = state.removed ?: return@LaunchedEffect
        // Long rather than short: this is the only way back from a removal, and four seconds is gone before a
        // reader has decided whether they meant it.
        val result = snackbar.showSnackbar("Removed $name", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove() else viewModel.forgetRemoval()
    }
    // A widget removed from HOME while this was open has nothing left to style.
    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // The scaffold reserves no insets (see above), so the prompt keeps itself off the navigation bar.
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        },
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
                // Tier 3 is behind this and nothing else: a plain word, not an icon, since "the layers underneath" is
                // not a thing anyone recognizes a glyph for.
                actions = {
                    TextButton(onClick = viewModel::toggleAdvanced) {
                        Text(if (state.focus.advanced) "Simple" else "Advanced")
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
                onMove = viewModel::move,
                onZoom = viewModel::zoom,
                onSettle = viewModel::settle,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            )
            if (state.recipe != null) {
                StyleSheet(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    val library = state.library
    val recipe = state.recipe
    if (library != null && recipe != null) {
        BlockPicker(
            blocks = library,
            recipe = recipe,
            data = state.data,
            size = DpSize(route.widthDp.dp, route.heightDp.dp),
            onPick = viewModel::add,
            onDismiss = { viewModel.showLibrary(false) },
        )
    }
}

/**
 * The panel under the preview: which part is being styled, that part's settings, and what can be done to it — or, in
 * Advanced, the layer tree.
 */
@Composable
private fun StyleSheet(state: WidgetStudioState, viewModel: WidgetStudioViewModel, modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    val sheet = modifier.background(colors.background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    if (state.focus.advanced) {
        AdvancedPanel(state, viewModel, sheet)
        return
    }
    Column(sheet) {
        if (state.parts.isNotEmpty()) {
            PartPicker(
                parts = state.parts,
                selected = state.selected,
                onSelect = viewModel::select,
                modifier = Modifier
                    .uiInsetsPadding(WindowInsetsSides.Horizontal)
                    .padding(top = 20.dp),
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
            ) {
                if (state.selected == null) {
                    FooterButton("Add block") { viewModel.showLibrary(true) }
                } else {
                    BlockActions(state, viewModel)
                }
            }
        }
    }
}

/** What can be done to the selected block besides restyling it: move it up or down the stack, or remove it. */
@Composable
private fun BlockActions(state: WidgetStudioState, viewModel: WidgetStudioViewModel) {
    // Each direction is offered only when there is a block that way to move past.
    if (state.canRaise || state.canLower) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
            if (state.canLower) {
                MorphicButton(
                    onClick = { viewModel.restack(-1) },
                    style = MorphicButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                ) { Text("Send back") }
            }
            if (state.canRaise) {
                MorphicButton(
                    onClick = { viewModel.restack(+1) },
                    style = MorphicButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                ) { Text("Bring forward") }
            }
        }
    }
    FooterButton("Remove block", viewModel::removeSelected)
}

/** One full-width action under a part's settings. */
@Composable
private fun FooterButton(label: String, onClick: () -> Unit) {
    MorphicButton(
        onClick = onClick,
        style = MorphicButtonStyle.Tonal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) { Text(label) }
}

/**
 * The widget at the size HOME draws it, shrunk — never grown — to fit the space above the controls. A tap on a block
 * selects it and a tap anywhere else selects the widget; a drag moves a block and a pinch scales it. The selected block
 * is outlined.
 *
 * @param onMove a drag's step, in dp.
 * @param onSettle a drag or pinch is over: the block, where it now draws and the widget's size, both in pixels.
 */
@Suppress("LongParameterList") // What is drawn, the selection drawn over it, and what a finger does to it.
@Composable
private fun WidgetPreview(
    recipe: WidgetRecipe?,
    data: ScriptData?,
    size: DpSize,
    parts: List<StylePart>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onZoom: (Int, Float) -> Unit,
    onSettle: (Int, IntRect, IntSize, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where each layer was drawn, as the renderer reports it — in the widget's own pixels, which is also the space the
    // fingers arrive in, since both sit inside the preview's scaling.
    var bounds by remember { mutableStateOf<Map<Int, IntRect>>(emptyMap()) }
    var widget by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val currentParts by rememberUpdatedState(parts)
    val currentSelected by rememberUpdatedState(selected)
    ShrinkToFit(size = size, modifier = modifier) {
        if (recipe != null && data != null) {
            WidgetRender(
                recipe = recipe,
                data = data,
                modifier = Modifier
                    .onSizeChanged { widget = it }
                    .previewGestures(
                        target = { partAt(it, currentParts, bounds) ?: currentSelected },
                        onTap = { onSelect(partAt(it, currentParts, bounds)) },
                        onStart = { onSelect(it) },
                        onChange = { part, pan, zoom ->
                            onMove(part, pan.x / density, pan.y / density)
                            if (zoom != 1f) onZoom(part, zoom)
                        },
                        onEnd = { part ->
                            // The last step has not been laid out yet when the finger lifts, and the box it lands in
                            // is what the block is re-pinned by — so wait for that layout before reading it.
                            scope.launch {
                                withFrameNanos {}
                                withFrameNanos {}
                                bounds[part]?.let { onSettle(part, it, widget, density) }
                            }
                        },
                    )
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

/** The widget and each of its blocks, one chip each — the same choice a tap on the preview makes. */
@Composable
private fun PartPicker(parts: List<StylePart>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    ChipRow(
        labels = listOf("Widget") + parts.map { it.name },
        selected = selected?.let { index -> parts.indexOfFirst { it.index == index } + 1 } ?: 0,
        onSelect = { i -> onSelect(parts.getOrNull(i - 1)?.index) },
        modifier = modifier,
        // Inside the row rather than around it, so chips scroll to the screen's edge instead of being cut off short.
        contentPadding = PaddingValues(horizontal = 20.dp),
    )
}

/**
 * Every setting of the selected part, one control each — or a plain line when it has none — with [footer], the part's
 * actions, under them.
 */
@Composable
private fun StylePanel(
    globals: List<WidgetGlobal>,
    initial: Map<String, WidgetGlobal>,
    onChange: (WidgetGlobal) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit,
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
        footer()
    }
}
