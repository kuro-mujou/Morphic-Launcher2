package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.color.ColorPalettes
import inkspire.morphic.core.designsystem.component.color.PalettePresetBrowser
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.graphics.wallpaper.WallpaperMorph
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import inkspire.morphic.core.model.wallpaper.WallpaperFilter
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe
import inkspire.morphic.feature.settings.iconstudio.StudioIconButton
import org.koin.androidx.compose.koinViewModel

/**
 * The wallpaper studio's editor: a full-bleed live preview with the designs to pick from and a shuffle.
 *
 * **The preview is the wallpaper, edge to edge; the controls float over it inset from the bars.** A wallpaper is
 * judged full-screen, so the picture takes the whole surface and the back button, the design row and the shuffle sit
 * on top of it rather than beside it — the same placement decision the icon studio's color picker makes for the same
 * reason.
 *
 * **A dissolve is the transition, and only between *pictures*.** A new design or a shuffled seed fades over the last;
 * a knob being dragged is a picture changing rather than a new one, so it swaps. See [WallpaperPreview].
 *
 * **A horizontal swipe shuffles**, the gesture the walkthrough found is the app's core toy — and on a design the
 * morph engine has reached it is a *scrub*: the next window is worked out in advance and the finger drags the picture
 * into it, geometry and all, with letting go early putting it back. [ShuffleSwipe] is the gesture and
 * docs/MORPH_ENGINE_PLAN.md is why it is built the way it is.
 *
 * **The dissolve is what a design without that seam still does**, and it is a fade between two finished bitmaps —
 * which is why it was only ever a placeholder: no amount of cross-fading reaches a shape that *moves*. Vitrall is the
 * one design past it so far; M6 of the morph plan is the rest of the catalog.
 */
@Composable
fun WallpaperStudioScreen(onBack: () -> Unit) {
    val viewModel: WallpaperStudioViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Which chooser the bottom bar is showing — the designs, the palettes, the filters, or the Style panel. UI
    // position, not recipe, so it is remembered across rotation but never stored. The Style tab likewise.
    var mode by rememberSaveable { mutableStateOf(ChooserMode.DESIGNS) }
    var styleTab by rememberSaveable { mutableStateOf(StyleTab.AMOUNT) }
    // Whether the colors chooser has its browser open above the bar. Not a fourth [ChooserMode]: the browser is a
    // second view of the palettes the bar is already showing, and leaving the ribbon under it is what lets a pick made
    // in the list be nudged along by the ribbon without a trip back through the toggles.
    var browsingPresets by rememberSaveable { mutableStateOf(false) }

    val swipe = rememberShuffleSwipe(
        state = state,
        onCommit = viewModel::commitScrub,
        onShuffle = viewModel::shuffle,
    )

    BackHandler(onBack = onBack)

    // **The studio is its own theme zone, and a fixed dark one** — the icon studio's call, for the same reason with a
    // different canvas: the chrome here floats over the *wallpaper being designed*, which can be anything, so there is
    // nothing to follow that would stay legible. It was missing until the Style panel put the first themed component
    // on this screen (every control before it was hand-colored white), and a bare MaterialTheme drew M3's default
    // purple over a monochrome studio.
    LauncherTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .shuffleSwipe(swipe),
        ) {
            WallpaperPreview(
                shot = state.shot,
                morph = swipe.live,
                progress = { swipe.progress.value },
                onViewport = viewModel::setViewport,
            )

            StudioIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .uiInsetsPadding(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .padding(12.dp),
            )

            StudioIconButton(
                icon = Icons.Default.Check,
                contentDescription = "Set as wallpaper",
                onClick = { viewModel.apply(onApplied = onBack) },
                // Nothing to apply until a *settled* render lands — a draft is a fraction of the screen's pixels — and
                // one write at a time. The model guards both; this greys the button so each guard is visible rather
                // than silent.
                enabled = state.shot?.draft == false && !state.applying,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .uiInsetsPadding(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .padding(12.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .uiInsetsPadding(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Above the bar rather than in it: the Style panel is two rows — its tabs and the control they choose —
                // where every other chooser is one row of chips, and the bar stays one row of chips whatever is open.
                if (mode == ChooserMode.STYLE) {
                    WallpaperStylePanel(
                        recipe = state.recipe,
                        tab = styleTab,
                        onSelectTab = { styleTab = it },
                        onParams = viewModel::setParams,
                    )
                }

                if (mode == ChooserMode.COLORS && browsingPresets) {
                    PalettePresetBrowser(
                        palettes = ColorPalettes.all,
                        selected = state.recipe.palette.colors,
                        // Applies and closes, the reference studio's behavior and the honest one: the wallpaper behind
                        // the panel is already the tapped palette, so a confirm step would ask the user to agree with
                        // what they can see. The ribbon below is left showing that same pick.
                        onPick = {
                            viewModel.setPalette(it.colors)
                            browsingPresets = false
                        },
                        // Most of the screen, because a list of two-line rows is useless at chip height — and not all
                        // of it, because the picture it is filtering has to stay visible to filter against.
                        modifier = Modifier
                            .fillMaxHeight(0.62f)
                            .studioPanelGround(),
                    )
                }

                BottomChooser(
                    mode = mode,
                    onModeToggle = { tapped ->
                        mode = if (mode == tapped) ChooserMode.DESIGNS else tapped
                        // The browser belongs to the colors chooser; leaving it must not leave a panel behind.
                        if (mode != ChooserMode.COLORS) browsingPresets = false
                    },
                    onShuffle = viewModel::shuffle,
                ) {
                    Chooser(
                        mode = mode,
                        recipe = state.recipe,
                        presetsOpen = browsingPresets,
                        onTogglePresets = { browsingPresets = !browsingPresets },
                        onPickDesign = viewModel::pickDesign,
                        onSetPalette = viewModel::setPalette,
                        onToggleFilter = viewModel::toggleFilter,
                    )
                }
            }
        }
    }
}



/**
 * Which of the four choosers the bottom bar is showing. [DESIGNS] is home — the three toggles flip to and from it.
 *
 * [STYLE] is the one that does not replace the bar's chips: it opens a panel *above* them and leaves the designs in
 * the bar, so a knob can be tuned and a design tried without a trip back through the toggles.
 */
private enum class ChooserMode { DESIGNS, COLORS, FILTERS, STYLE }

/**
 * The bottom bar: the three chooser toggles, whatever [chooser] fills the middle with, and the shuffle.
 *
 * **The bar does not know what it is showing.** It owns the toggles, the one middle slot and the shuffle; which
 * chooser goes in the slot is [Chooser]'s business, decided from the same `mode` the toggles here set. Keeping the
 * two apart is what stops this growing a parameter for every control any chooser might need — it had reached ten.
 *
 * **One slot, not a stack.** The palette and filter toggles swap the middle rather than stacking, so the bar stays one
 * row over the wallpaper; either toggle flips back to the designs when it is already on. The style toggle is the
 * exception and opens a panel above instead, leaving the designs here. The shuffle re-seeds whichever design is
 * showing — a new variation, the same whatever the chooser.
 */
@Composable
private fun BottomChooser(
    mode: ChooserMode,
    onModeToggle: (ChooserMode) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    chooser: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.Default.Palette,
            contentDescription = "Colors",
            onClick = { onModeToggle(ChooserMode.COLORS) },
            selected = mode == ChooserMode.COLORS,
        )
        StudioIconButton(
            icon = Icons.Default.Straighten,
            contentDescription = "Style",
            onClick = { onModeToggle(ChooserMode.STYLE) },
            selected = mode == ChooserMode.STYLE,
        )
        StudioIconButton(
            icon = Icons.Default.Tune,
            contentDescription = "Filters",
            onClick = { onModeToggle(ChooserMode.FILTERS) },
            selected = mode == ChooserMode.FILTERS,
        )
        Box(modifier = Modifier.weight(1f)) { chooser() }
        StudioIconButton(
            icon = Icons.Default.Casino,
            contentDescription = "Shuffle",
            onClick = onShuffle,
        )
    }
}

/**
 * What fills the bar's middle slot: the designs, the palettes or the filters, whichever [mode] names.
 *
 * Split from [BottomChooser] so the bar carries no knowledge of any one chooser's controls — see its KDoc.
 */
@Composable
private fun Chooser(
    mode: ChooserMode,
    recipe: WallpaperRecipe,
    presetsOpen: Boolean,
    onTogglePresets: () -> Unit,
    onPickDesign: (WallpaperDesign) -> Unit,
    onSetPalette: (List<Int>) -> Unit,
    onToggleFilter: (WallpaperFilter) -> Unit,
) {
    when (mode) {
        // Two ways into one bank: the chip opens the named, filterable browser for "something green", the ribbon
        // beside it answers "show me the next one" in a tap. Neither replaces the other.
        ChooserMode.COLORS ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChooserChip(label = "Presets", selected = presetsOpen, onClick = onTogglePresets)
                // Lazy, because the bank runs to several hundred palettes — the picker ribbon's reason.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(ColorPalettes.all, key = { it.name }) { palette ->
                        PalettePill(
                            colors = palette.colors,
                            selected = palette.colors == recipe.palette.colors,
                            onClick = { onSetPalette(palette.colors) },
                        )
                    }
                }
            }

        ChooserMode.FILTERS ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperFilter.entries.forEach { filter ->
                    ChooserChip(
                        label = filter.label,
                        selected = filter in recipe.filters,
                        onClick = { onToggleFilter(filter) },
                    )
                }
            }

        // Style keeps the designs here, its own panel being above the bar.
        ChooserMode.DESIGNS, ChooserMode.STYLE ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperDesign.entries.forEach { design ->
                    ChooserChip(
                        label = design.label,
                        selected = design == recipe.design,
                        onClick = { onPickDesign(design) },
                    )
                }
            }
    }
}

/**
 * The render, full-bleed — dissolving into a new picture, swapping outright into the next frame of the same one.
 *
 * **Two arrivals, one surface, and the shot says which.** A shuffle or a new design is a different picture and earns
 * the fade the studio's motion is built on; the drafts that stream out of a Style knob under a finger are one picture
 * changing, and fading each into the last would smear a drag into a trail of half-dissolved renders. It cannot be a
 * [Crossfade], which animates every change alike — hence [AnimatedContent] with the transition chosen per arrival.
 *
 * **It measures itself and reports its pixel size**, so the generator paints exactly the resolution being shown rather
 * than a fixed guess scaled to fit. `onGloballyPositioned` would do, but the size is all that is wanted.
 *
 * **A live [morph] is painted over the bitmap rather than instead of it**, which is what makes the two handoffs
 * invisible. Going in, the scrub's first frame is the window already on screen; coming out, its last frame is the
 * window the bitmap underneath is about to become — the same draw calls from the same plan, differing only by which
 * rasterizer ran them. So both swaps are between two copies of one picture, and neither needs a transition to hide it.
 *
 * **[progress] is a lambda, not a value.** Read inside the draw scope it is a deferred read: the finger moves and the
 * frame is redrawn, with no recomposition of this or anything around it.
 */
@Composable
private fun WallpaperPreview(
    shot: WallpaperShot?,
    morph: WallpaperMorph?,
    progress: () -> Float,
    onViewport: (Int, Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { onViewport(it.width, it.height) },
    ) {
        AnimatedContent(
            targetState = shot,
            transitionSpec = {
                if (targetState?.dissolve == true) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "wallpaperPreview",
        ) { shown ->
            if (shown != null) {
                Image(
                    bitmap = shown.bitmap.asImageBitmap(),
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (morph != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas {
                    morph.draw(it.nativeCanvas, progress(), size.width.toInt(), size.height.toInt())
                }
            }
        }
    }
}

/**
 * The ground the studio's floating panels sit on — the Style panel and the preset browser.
 *
 * **Shared because they are the same surface, not because the numbers happen to match.** Both are a panel of chrome
 * over an arbitrary wallpaper, and two panels drifting to two scrims would read as two surfaces on one screen. The
 * frosted backdrop the design system defers is what would eventually replace this.
 *
 * **The alpha is set by the browser, not by the Style panel** — and it is most of the way to opaque. A thin strip of
 * slider over a picture stays readable at `0.6`; two thirds of the screen filled with rows of small text and small
 * swatches does not, because the wallpaper's own shapes run *through* the list and read as rows that are not there.
 * The number is the one a reading surface needs, and the strip only gets darker for it.
 */
internal fun Modifier.studioPanelGround(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
    .clip(RoundedCornerShape(16.dp))
    .background(Color.Black.copy(alpha = 0.86f))
    .padding(horizontal = 12.dp, vertical = 10.dp)

/** One labelled chip in the picker row — a design, a filter or a Style tab, lit when it is the one showing. */
@Composable
internal fun ChooserChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
        WallpaperDesign.VORONOI -> "Voronoi"
        WallpaperDesign.PLASMA -> "Plasma"
        WallpaperDesign.CONTOUR -> "Contour"
        WallpaperDesign.WAVES -> "Waves"
        WallpaperDesign.BAUHAUS -> "Bauhaus"
        WallpaperDesign.MONDRIAN -> "Mondrian"
        WallpaperDesign.CONFETTI -> "Confetti"
        WallpaperDesign.TRUCHET -> "Truchet"
        WallpaperDesign.METABALLS -> "Blobs"
        WallpaperDesign.RIBBONS -> "Ribbons"
        WallpaperDesign.DOT_GRID -> "Dot Grid"
        WallpaperDesign.HALFTONE -> "Halftone"
        WallpaperDesign.FLOW_LINES -> "Flow Lines"
        WallpaperDesign.RIBBON_FLOW -> "Ribbon Flow"
        WallpaperDesign.POLYGON_CASCADE -> "Cascade"
        WallpaperDesign.DIAGONAL_BANDS -> "Bands"
        WallpaperDesign.GRADIENT_COLUMNS -> "Columns"
        WallpaperDesign.LOUVERS -> "Louvers"
        WallpaperDesign.SOFT_OVERLAPS -> "Overlaps"
        WallpaperDesign.WAVE_DIVIDERS -> "Wave Dividers"
        WallpaperDesign.RIBBED_GLASS -> "Ribbed Glass"
        WallpaperDesign.VITRALL -> "Vitrall"
        WallpaperDesign.MODERN_MOSAIC -> "Mosaic"
        WallpaperDesign.ROUNDED_TILES -> "Bars"
        WallpaperDesign.IMPASTO -> "Impasto"
        WallpaperDesign.SPRAY -> "Spray"
        WallpaperDesign.PLANET -> "Planet"
        WallpaperDesign.MARBLE -> "Marble"
    }

/** A short, human name for the color-mode segment — the enum name is a code identifier, not a label. */
internal val WallpaperColorMode.label: String
    get() = when (this) {
        WallpaperColorMode.MONOCHROMATIC -> "Mono"
        WallpaperColorMode.BICHROMATIC -> "Duo"
        WallpaperColorMode.COLORFUL -> "Full"
    }

/** A short, human name for the filter chip — the enum name is a code identifier, not a label. */
private val WallpaperFilter.label: String
    get() = when (this) {
        WallpaperFilter.BLUR -> "Blur"
        WallpaperFilter.VIGNETTE -> "Vignette"
        WallpaperFilter.GRAIN -> "Grain"
        WallpaperFilter.SCANLINES -> "Scanlines"
        WallpaperFilter.VIBRANCE -> "Vibrance"
    }
