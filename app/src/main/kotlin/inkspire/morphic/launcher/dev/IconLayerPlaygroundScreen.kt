package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.icon.compose.IconLayerStack
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedIconLoader
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.feature.settings.iconstudio.IconStudioRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * **The regression gate for the two render paths**, and the reason it is a playground rather than a test.
 *
 * `core:icon` bakes icons for display and renders them live for the editor, and the failure mode that matters is
 * the two *disagreeing* — an icon that looks right while it is being edited and wrong on every surface. Proving
 * they agree means comparing pixels, which needs instrumentation this project has no setup for (no CI, no
 * Robolectric). So the comparison is made by eye, deliberately and repeatably: the same layer set, the same app,
 * drawn both ways, side by side, with the controls that move it.
 *
 * **Drag any slider and the two must stay identical.** The live one updates continuously; the baked one re-bakes
 * whenever the set changes, so it lags by a frame or two and then lands in exactly the same place. Divergence in
 * the *resting* state is the bug this screen exists to catch — most likely in the shape mask (the one piece each
 * path implements with its own graphics API) or in a transform read the wrong way round.
 */
@Composable
fun IconLayerPlaygroundScreen(modifier: Modifier = Modifier) {
    val repository: AppRepository = koinInject()
    val loader: ParsedIconLoader = koinInject()

    val apps by repository.observeApps().collectAsStateWithLifecycle(emptyList())
    var appIndex by remember { mutableStateOf(0) }
    val component = apps.getOrNull(appIndex % apps.size.coerceAtLeast(1))?.componentKey

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var shapeIndex by remember { mutableStateOf(0) }
    var shapeForeground by remember { mutableStateOf(true) }

    // Index 0 is "no shape", so the unshaped case — which is what every icon renders as today — stays reachable.
    val shape: IconShape? = IconShapes.All.getOrNull(shapeIndex - 1)

    val layerSet = remember(offsetX, offsetY, zoom, rotation, shape, shapeForeground) {
        IconLayerSet(
            IconLayerSet.Base.layers.map { spec ->
                val edited = spec.role == if (shapeForeground) LayerRole.FOREGROUND else LayerRole.BACKGROUND
                if (edited) {
                    spec.copy(offsetX = offsetX, offsetY = offsetY, zoom = zoom, rotation = rotation, shape = shape)
                } else {
                    spec
                }
            },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Baked (surfaces) vs live (editor) — these must match", color = Color.White)

        if (component == null) {
            Text("Loading apps…", color = Color.White)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabelledIcon("Baked", Modifier.weight(1f)) {
                // Explicitly passed, so the playground is unaffected by whatever global default or per-app
                // override happens to be stored — the comparison is between renderers, not between recipes.
                LauncherIcon(
                    component = component,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    sizePx = BakeSizePx,
                    layerSet = layerSet,
                )
            }
            LabelledIcon("Live", Modifier.weight(1f)) {
                LiveIcon(component = component, layerSet = layerSet, loader = loader)
            }
        }

        MorphicSlider(offsetX, { offsetX = it }, valueRange = -0.5f..0.5f)
        Text("offset X %.2f".format(offsetX), color = Color.White)
        MorphicSlider(offsetY, { offsetY = it }, valueRange = -0.5f..0.5f)
        Text("offset Y %.2f".format(offsetY), color = Color.White)
        MorphicSlider(zoom, { zoom = it }, valueRange = 0.2f..2f)
        Text("zoom %.2f".format(zoom), color = Color.White)
        MorphicSlider(rotation, { rotation = it }, valueRange = 0f..360f)
        Text("rotation %.0f°".format(rotation), color = Color.White)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevChip(if (shapeForeground) "editing: foreground" else "editing: background") {
                shapeForeground = !shapeForeground
            }
            DevChip("shape: ${shape?.id ?: "none"}") {
                shapeIndex = (shapeIndex + 1) % (IconShapes.All.size + 1)
            }
            DevChip("next app") { appIndex++ }
        }

        // **Temporary scaffolding, and one of the three is already gone.** Editing *one app* now has its real way
        // in — long-press an icon on the launcher, "Edit icon" — so that chip is deleted rather than kept as a
        // shortcut, since a duplicate route is how two paths to one screen quietly start behaving differently.
        // These two go with the settings dashboard, which is what will offer them for real. Kept in `app` rather
        // than in `feature:settings` so their deletion touches no feature module.
        val navigator = LocalNavigator.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevChip("studio: global") { navigator.goTo(IconStudioRoute.Global) }
            DevChip("studio: pick") { navigator.goTo(IconStudioRoute.App()) }
        }
    }
}

/** The bake resolution used for the comparison — large enough that the baked side is not the blurrier one. */
private const val BakeSizePx = 384

/**
 * The live stack for one app, loading its parsed layers off the main thread.
 *
 * `produceState` keyed on the component: [ParsedIconLoader.load] blocks (a package-manager lookup and drawable
 * inflation), and it is deliberately *not* keyed on the layer set — re-parsing on every slider frame is the exact
 * cost this render path exists to avoid.
 */
@Composable
private fun LiveIcon(
    component: ComponentKey,
    layerSet: IconLayerSet,
    loader: ParsedIconLoader,
    modifier: Modifier = Modifier,
) {
    val parsed by produceState<ParsedIcon?>(initialValue = null, component, loader) {
        value = withContext(Dispatchers.Default) { loader.load(component) }
    }

    parsed?.let { icon ->
        IconLayerStack(icon = icon, layerSet = layerSet, modifier = modifier.fillMaxSize())
    }
}

/** One captioned square cell, so both sides are measured identically and any size difference is visible. */
@Composable
private fun LabelledIcon(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // A mid grey behind both: a shape mask that failed to clip is invisible against black if the
                // artwork is dark, and invisible against white if it is light.
                .background(Color(0xFF505050)),
        ) {
            content()
        }
        Text(label, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** A minimal tap target; the harness has no chrome of its own and this is not a component under test. */
@Composable
private fun DevChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
