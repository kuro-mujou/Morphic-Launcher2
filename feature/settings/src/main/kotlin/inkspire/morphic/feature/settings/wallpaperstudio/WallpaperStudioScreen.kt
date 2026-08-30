package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.color.ColorPalettes
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import inkspire.morphic.feature.settings.iconstudio.StudioIconButton
import org.koin.androidx.compose.koinViewModel
import kotlin.math.abs

/**
 * The wallpaper studio's editor: a full-bleed live preview with the designs to pick from and a shuffle.
 *
 * **The preview is the wallpaper, edge to edge; the controls float over it inset from the bars.** A wallpaper is
 * judged full-screen, so the picture takes the whole surface and the back button, the design row and the shuffle sit
 * on top of it rather than beside it — the same placement decision the icon studio's color picker makes for the same
 * reason.
 *
 * **A [Crossfade] on the render is the transition.** When a new design or a shuffled seed produces a fresh bitmap it
 * dissolves over the old one rather than snapping, which is the whole of the studio's premium motion at this stage —
 * the discrete re-seed with an animated fade the plan settled on, not a continuous morph.
 *
 * **A horizontal swipe shuffles**, the gesture the walkthrough found is the app's core toy — mapped here to the
 * discrete re-roll it actually is. Picking a design is the row; applying it as the wallpaper is the next slice.
 */
@Composable
fun WallpaperStudioScreen(onBack: () -> Unit) {
    val viewModel: WallpaperStudioViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    // Which chooser the bottom bar is showing — the designs or the palettes. UI position, not recipe, so it is
    // remembered across rotation but never stored.
    var showColors by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var travelled = 0f
                val threshold = with(density) { 110.dp.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (abs(travelled) > threshold) viewModel.shuffle() },
                ) { _, amount -> travelled += amount }
            },
    ) {
        // The preview measures itself and hands its pixel size to the model, so the render is exactly the resolution
        // it is shown at. `onGloballyPositioned` would do, but the size is all that is wanted.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewModel.setViewport(it.width, it.height) },
        ) {
            Crossfade(targetState = state.bitmap, label = "wallpaperPreview") { bitmap ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Wallpaper preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        StudioIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        )

        StudioIconButton(
            icon = Icons.Default.Check,
            contentDescription = "Set as wallpaper",
            onClick = { viewModel.apply(onApplied = onBack) },
            // Nothing to apply until the first render lands, and one write at a time — the model guards the second,
            // this greys the button while it runs so the guard is visible rather than silent.
            enabled = state.bitmap != null && !state.applying,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
        )

        BottomChooser(
            showColors = showColors,
            onToggleColors = { showColors = !showColors },
            currentDesign = state.recipe.design,
            currentPaletteColors = state.recipe.palette.colors,
            onPickDesign = viewModel::pickDesign,
            onSetPalette = viewModel::setPalette,
            onShuffle = viewModel::shuffle,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The bottom bar: the color toggle, the chooser it flips between (the designs or the palettes), and the shuffle.
 *
 * **One chooser slot, two contents.** The toggle swaps the middle between the design chips and the palette pills
 * rather than stacking both, so the bar stays one row over the wallpaper. The shuffle re-seeds whichever design is
 * showing — a new variation, the same for both choosers.
 */
@Composable
private fun BottomChooser(
    showColors: Boolean,
    onToggleColors: () -> Unit,
    currentDesign: WallpaperDesign,
    currentPaletteColors: List<Int>,
    onPickDesign: (WallpaperDesign) -> Unit,
    onSetPalette: (List<Int>) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The one toggle between the two choosers — lit while the palettes are showing.
        StudioIconButton(
            icon = Icons.Default.Palette,
            contentDescription = "Colors",
            onClick = onToggleColors,
            selected = showColors,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (showColors) {
                // Lazy, because the bank runs to a couple of hundred palettes — the picker ribbon's reason.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ColorPalettes.all, key = { it.name }) { palette ->
                        PalettePill(
                            colors = palette.colors,
                            selected = palette.colors == currentPaletteColors,
                            onClick = { onSetPalette(palette.colors) },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WallpaperDesign.entries.forEach { design ->
                        DesignChip(
                            label = design.label,
                            selected = design == currentDesign,
                            onClick = { onPickDesign(design) },
                        )
                    }
                }
            }
        }
        StudioIconButton(
            icon = Icons.Default.Casino,
            contentDescription = "Shuffle",
            onClick = onShuffle,
        )
    }
}

/** One design in the picker row — its name, lit when it is the one showing. */
@Composable
private fun DesignChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (selected) 0.9f else 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Black else Color.White,
        )
    }
}

/**
 * One palette in the color chooser — its colors packed into a pill, the whole thing a tap that recolors the design.
 *
 * **Tapping applies the *whole* palette, not one swatch** — the difference from the icon picker's ribbon, where each
 * swatch is its own pick. A wallpaper's generator wants a set of colors, so the pill is one unit. The one showing is
 * ringed so the chooser says which it is.
 */
@Composable
private fun PalettePill(colors: List<Int>, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        colors.forEach { swatch ->
            Box(modifier = Modifier.size(width = 16.dp, height = 34.dp).background(Color(swatch)))
        }
    }
}

/** A short, human name for the picker — the enum name is a code identifier, not a label. */
private val WallpaperDesign.label: String
    get() = when (this) {
        WallpaperDesign.LINEAR_GRADIENT -> "Gradient"
        WallpaperDesign.MESH_GRADIENT -> "Mesh"
        WallpaperDesign.FLOW_FIELD -> "Flow"
        WallpaperDesign.TRIANGULAR_FACETS -> "Facets"
    }
