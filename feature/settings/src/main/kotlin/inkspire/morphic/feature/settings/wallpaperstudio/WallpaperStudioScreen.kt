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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import inkspire.morphic.core.designsystem.component.color.ColorPalettes
import inkspire.morphic.core.designsystem.component.color.PalettePresetBrowser
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.graphics.wallpaper.WallpaperMorph
import inkspire.morphic.core.model.wallpaper.WallpaperFilter
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe
import inkspire.morphic.feature.settings.iconstudio.StudioBottomBar
import inkspire.morphic.feature.settings.iconstudio.StudioContentColor
import inkspire.morphic.feature.settings.iconstudio.StudioIconButton
import inkspire.morphic.feature.settings.iconstudio.StudioPillButton
import inkspire.morphic.feature.settings.iconstudio.studioSurface
import org.koin.androidx.compose.koinViewModel

/**
 * The wallpaper studio's editor: a full-bleed live preview, with every control floating over it as glass.
 *
 * **The preview is the wallpaper, edge to edge; the controls float over it inset from the bars.** A wallpaper is
 * judged full-screen, so the picture takes the whole surface and the back button, the tool bar and each panel sit on
 * top of it rather than beside it — the same placement decision the icon studio makes for the same reason.
 *
 * **Every floating thing here is the icon studio's glass, over the preview as its source.** That is what the Haze
 * source on [WallpaperPreview] is for, and it settles a question the first cut of this screen got wrong in two
 * different ways at once: its buttons were bare white glyphs with nothing behind them (illegible over a pale design)
 * and its panels were a flat 86%-black scrim (a hole punched in the wallpaper being designed). Glass is the one
 * material that is legible over an arbitrary picture *and* still shows it, which is the whole requirement of a
 * chrome-over-the-work surface. `studioSurface` carries the recipe and the argument.
 *
 * **One panel at a time, above the bar, and null is a real state.** The bar's four entries each open a panel and
 * pressing the lit one puts it away, leaving nothing but the wallpaper — the icon studio's tool-rail rule, for its
 * reason: the picture is the work, so there must be a way back to just the picture. The previous arrangement had
 * *designs* as a permanent home state filling the bar with chips, which is what left no room for the grid that
 * replaces them.
 *
 * **A horizontal swipe shuffles**, the gesture the walkthrough found is the app's core toy — and on a design the
 * morph engine has reached it is a *scrub*: the next window is worked out in advance and the finger drags the picture
 * into it, geometry and all, with letting go early putting it back. [ShuffleSwipe] is the gesture and
 * docs/MORPH_ENGINE_PLAN.md is why it is built the way it is. **It lives on the preview rather than on the root**, so
 * the wallpaper still swipes above an open panel while a drag across the panel itself is the panel's — which works
 * because the glass is a hit target and the preview is its sibling, not its parent.
 *
 * **The dissolve is what a design without that seam still does**, and it is a fade between two finished bitmaps —
 * which is why it was only ever a placeholder: no amount of cross-fading reaches a shape that *moves*. Vitrall is the
 * one design past it so far; M6 of the morph plan is the rest of the catalog.
 */
@Composable
fun WallpaperStudioScreen(onBack: () -> Unit) {
    val viewModel: WallpaperStudioViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // **One source, because nothing here floats over anything but the picture.** The icon studio needs two — its rail
    // is both a surface and something the panel sees through — where this screen's panels and its bar are siblings in
    // one column and never overlap. So the only node registered is the preview, and every piece of chrome blurs the
    // wallpaper rather than each other.
    val haze = rememberHazeState()

    // Which panel is open, or null for none. UI position rather than recipe, so it survives rotation and is never
    // stored. The Style panel's tab likewise.
    var panel by rememberSaveable { mutableStateOf<StudioPanel?>(null) }
    var styleTab by rememberSaveable { mutableStateOf(StyleTab.AMOUNT) }

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
        Box(modifier = Modifier.fillMaxSize()) {
            WallpaperPreview(
                shot = state.shot,
                morph = swipe.live,
                progress = { swipe.progress.value },
                onViewport = viewModel::setViewport,
                modifier = Modifier
                    .shuffleSwipe(swipe)
                    .hazeSource(haze),
            )

            StudioPillButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                hazeState = haze,
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .uiInsetsPadding(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .padding(12.dp),
            )

            StudioPillButton(
                icon = Icons.Default.Check,
                contentDescription = "Set as wallpaper",
                hazeState = haze,
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
                    .uiInsetsPadding(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StudioPanelContent(
                    panel = panel,
                    state = state,
                    haze = haze,
                    styleTab = styleTab,
                    onSelectStyleTab = { styleTab = it },
                    viewModel = viewModel,
                )

                StudioToolBar(
                    panel = panel,
                    haze = haze,
                    onToggle = { tapped -> panel = tapped.takeIf { it != panel } },
                    onShuffle = viewModel::shuffle,
                )
            }
        }
    }
}

/**
 * Which panel the studio has open. Each is one entry in the bar, and pressing the lit entry closes it — see
 * [WallpaperStudioScreen] for why "nothing open" is a state rather than a fallback to the designs.
 */
private enum class StudioPanel { DESIGNS, COLORS, STYLE, FILTERS }

/**
 * The open panel, or nothing — the one place that maps a [StudioPanel] to what it draws.
 *
 * **Split out so the bar carries no knowledge of any panel's controls.** The bar owns four toggles and a shuffle; what
 * a toggle reveals is settled here, from the same value the toggle sets. Keeping the two apart is what stops the bar
 * growing a parameter for every control any panel might need — it had reached ten before the previous arrangement was
 * taken apart.
 *
 * **Two of the four take most of the screen and two take as little as they can.** The designs and the colors are
 * *browsing* surfaces — a grid of pictures, a list of named rows — and they are useless at chip height; the Style
 * panel is one control and the filters are five switches, and every row of those is a row of the wallpaper they are
 * being judged against. `0.62` is the fraction the preset browser arrived at and the grid now shares, so the two read
 * as one surface appearing in one place rather than as two panels of different heights.
 */
@Composable
private fun StudioPanelContent(
    panel: StudioPanel?,
    state: WallpaperStudioState,
    haze: HazeState,
    styleTab: StyleTab,
    onSelectStyleTab: (StyleTab) -> Unit,
    viewModel: WallpaperStudioViewModel,
) {
    when (panel) {
        null -> Unit

        StudioPanel.DESIGNS -> WallpaperDesignGrid(
            selected = state.recipe.design,
            thumbnails = state.thumbnails,
            onTileSize = viewModel::previewDesigns,
            onGone = viewModel::stopPreviewingDesigns,
            onPick = viewModel::pickDesign,
            modifier = Modifier
                .fillMaxHeight(0.62f)
                .studioPanelGround(haze),
        )

        StudioPanel.COLORS -> PalettePresetBrowser(
            palettes = ColorPalettes.all,
            selected = state.recipe.palette.colors,
            // Applies and stays open, the reference studio's behavior and the honest one: the wallpaper behind the
            // panel is already the tapped palette, so a confirm step would ask the user to agree with what they can
            // see. It does not close either, because the next thing a user does with a bank of palettes is usually
            // try another one.
            onPick = { viewModel.setPalette(it.colors) },
            modifier = Modifier
                .fillMaxHeight(0.62f)
                .studioPanelGround(haze),
        )

        StudioPanel.STYLE -> WallpaperStylePanel(
            recipe = state.recipe,
            tab = styleTab,
            onSelectTab = onSelectStyleTab,
            onParams = viewModel::setParams,
            modifier = Modifier.studioPanelGround(haze),
        )

        StudioPanel.FILTERS -> WallpaperFilterPanel(
            recipe = state.recipe,
            onToggle = viewModel::toggleFilter,
            modifier = Modifier.studioPanelGround(haze),
        )
    }
}

/**
 * The bottom bar: one entry per panel, then the shuffle.
 *
 * **A pill sized to what it holds rather than a band across the screen**, which is the icon studio's [StudioBottomBar]
 * and its argument — a full-width bar of five buttons reads as a surface the buttons happen to sit on, and develops
 * dead space every time an entry leaves.
 *
 * **The shuffle is in the bar but is not a panel**, so it is the one entry that never lights: it re-seeds whichever
 * design is showing and is done, where the four before it put the studio into a state. Same glass, no wash.
 */
@Composable
private fun StudioToolBar(
    panel: StudioPanel?,
    haze: HazeState,
    onToggle: (StudioPanel) -> Unit,
    onShuffle: () -> Unit,
) {
    StudioBottomBar(hazeState = haze) {
        StudioIconButton(
            icon = Icons.Default.GridView,
            contentDescription = "Designs",
            onClick = { onToggle(StudioPanel.DESIGNS) },
            selected = panel == StudioPanel.DESIGNS,
        )
        StudioIconButton(
            icon = Icons.Default.Palette,
            contentDescription = "Colors",
            onClick = { onToggle(StudioPanel.COLORS) },
            selected = panel == StudioPanel.COLORS,
        )
        StudioIconButton(
            icon = Icons.Default.Straighten,
            contentDescription = "Style",
            onClick = { onToggle(StudioPanel.STYLE) },
            selected = panel == StudioPanel.STYLE,
        )
        StudioIconButton(
            icon = Icons.Default.Tune,
            contentDescription = "Filters",
            onClick = { onToggle(StudioPanel.FILTERS) },
            selected = panel == StudioPanel.FILTERS,
        )
        StudioIconButton(
            icon = Icons.Default.Casino,
            contentDescription = "Shuffle",
            onClick = onShuffle,
        )
    }
}

/**
 * The filters: one chip per pass, lit while it is on.
 *
 * **It wraps rather than scrolling sideways.** The whole set is five and always will be five-ish, and a scrolling row
 * hides however many do not fit behind a gesture with nothing on screen to suggest it — where two short lines show
 * all of them at once and cost one row of wallpaper more.
 */
@Composable
private fun WallpaperFilterPanel(
    recipe: WallpaperRecipe,
    onToggle: (WallpaperFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WallpaperFilter.entries.forEach { filter ->
            ChooserChip(
                label = filter.label,
                selected = filter in recipe.filters,
                onClick = { onToggle(filter) },
            )
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
 * **It paints black under the render, which is what the glass over it needs.** This node is the Haze source for every
 * floating surface on the screen, and a source that draws nothing hands them nothing to blur — so the studio's ground
 * belongs on the sampled node rather than on the root above it.
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
 * The ground every floating panel in the studio sits on — the designs, the palettes, the Style knob and the filters.
 *
 * **Shared because they are the same surface, not because the numbers happen to match.** All four are chrome over an
 * arbitrary wallpaper, and four panels drifting to four grounds would read as four surfaces on one screen.
 *
 * **It is the icon studio's glass now, where it used to be an 86%-black scrim.** The scrim was the honest answer while
 * there was no blur to reach for: a wallpaper's own shapes run *through* a list of small text and small swatches and
 * read as rows that are not there, so the panel had to be most of the way to opaque to be readable, and the cost was a
 * near-black hole punched in the picture being designed. A blur removes exactly the thing that made the wallpaper
 * unreadable — its detail — while leaving its color and its light, which is the whole reason the design system wanted
 * frosted chrome in the first place. `studioSurface` fixes the material for every studio surface, so there is nothing
 * left to choose here but the corner and the padding.
 *
 * @param shape defaulted to the panel corner; a caller wanting a pill passes one.
 */
@Composable
internal fun Modifier.studioPanelGround(
    haze: HazeState,
    shape: Shape = RoundedCornerShape(20.dp),
): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 12.dp)
    .studioSurface(haze, shape = shape)
    .padding(horizontal = 12.dp, vertical = 12.dp)

/** One labelled chip in a panel — a filter or a Style tab, lit when it is the one showing. */
@Composable
internal fun ChooserChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            // A chip sits *on* a panel that is already glass, so it takes a flat wash rather than a blur of its own:
            // Haze samples what is behind a node, and the panel is not a source, so a chip asking for glass here would
            // be blurring the wallpaper the panel has already blurred once.
            .background(StudioContentColor.copy(alpha = if (selected) 0.9f else 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Black else StudioContentColor,
        )
    }
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
