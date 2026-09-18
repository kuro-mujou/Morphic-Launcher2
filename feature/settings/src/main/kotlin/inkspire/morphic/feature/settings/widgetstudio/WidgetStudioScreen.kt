package inkspire.morphic.feature.settings.widgetstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.ShrinkToFit
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
 * The widget studio, opened on its **Style** tab — tier 1, the only tier built: the settings a design declared, and
 * the widget itself above them changing as they move.
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            )
            state.recipe?.let { recipe ->
                StylePanel(
                    globals = recipe.globals,
                    initial = state.initial,
                    onChange = viewModel::set,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                )
            }
        }
    }
}

/** The widget at the size HOME draws it, shrunk — never grown — to fit the space above the controls. */
@Composable
private fun WidgetPreview(recipe: WidgetRecipe?, data: ScriptData?, size: DpSize, modifier: Modifier = Modifier) {
    ShrinkToFit(size = size, modifier = modifier) {
        if (recipe != null && data != null) WidgetRender(recipe, data)
    }
}

/** Every global the design declared, one control each — or a plain line when it declared none. */
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
                text = "This widget has nothing to restyle.",
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
