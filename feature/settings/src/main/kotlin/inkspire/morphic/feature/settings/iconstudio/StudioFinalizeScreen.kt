package inkspire.morphic.feature.settings.iconstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.backdrop.LocalBackdrop
import inkspire.morphic.core.designsystem.backdrop.LocalBackdropEffect
import inkspire.morphic.core.designsystem.backdrop.rememberBackdropState
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconShape

/**
 * The last page of an editing session: **every icon it is about to change, over the real wallpaper**, and the three
 * settings that belong to the whole icon rather than to a layer.
 *
 * **Why this is a step and not a destination** — see [StudioStep]. The short of it: the recipe being edited lives in
 * this screen's `ViewModel`, and a second `NavEntry` would get its own.
 *
 * **It paints no background, and that is the whole trick.** The launcher's window carries `Theme.Wallpaper` — a
 * transparent `windowBackground` over `windowShowWallpaper` — so a screen that paints nothing *is* the wallpaper.
 * Nothing has to be punched through here: a punch exists to cut a hole in something opaque, and the editor's canvas
 * (the black / white / checkerboard the studio is normally drawn on) is simply not drawn on this step. Which is also
 * the reason this step exists: the studio deliberately is **not** the wallpaper, so it is the one place a plate — a
 * silhouette of blurred wallpaper — structurally cannot be judged.
 *
 * **The previews are the real thing.** `AppIcon` takes an explicit appearance, so each tile is the same composable
 * every surface draws, on the same bake cache, with the plate sampling the same wallpaper by position. There is no
 * second render path here to disagree with the home screen.
 *
 * **Every panel sits on a solid ground.** Over a photograph, a frosted control is a control you cannot read — and
 * these three are being used to judge what the *icons* look like against that photograph, so the panel must not
 * compete for the same trick. The one place the studio's glass is deliberately dropped.
 */
@Composable
internal fun StudioFinalizeScreen(
    state: IconStudioState,
    hazeState: HazeState,
    onPlateEnabled: (Boolean) -> Unit,
    onPlateShape: (IconShape?) -> Unit,
    onZoom: (Float) -> Unit,
    onSavePreset: ((String) -> Unit)?,
    onApply: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back is a step back to the editor, never out of the studio. Declared here so it wins over the screen's own
    // handler, the same layering the effect entries and the pack browser use.
    BackHandler { onBack() }

    // **The previews and the panel are laid out together, never one padded around the other.** The first cut floated
    // the panel over the grid and left the grid a constant `bottom` padding to clear it — a number that has to be
    // right at every screen size and was wrong at the first one it met: rotated, the panel was taller than the whole
    // viewport, so it covered the previews it exists to explain and its own buttons had nowhere to go. Laid out in a
    // `Column`, the previews take what is left and nothing has to be told how tall anything else is.
    //
    // Landscape is deliberately *not* arranged for yet — it comes out cramped rather than broken, which is the honest
    // state of a posture nobody has designed.
    Column(modifier.fillMaxSize()) {
        // **The plate's own wallpaper is provided here**, because the studio is a destination beyond the shell and
        // `LocalBackdrop` is the shell's. Without it every plate on this step drew its scrim — a flat gray square on
        // the one screen that exists to judge blurred wallpaper behind an icon.
        //
        // Measured rather than asked for: the mapping from a node's position to a crop of the wallpaper needs the
        // window's pixel size, and this is where the window is.
        BoxWithConstraints(Modifier.weight(1f)) {
            val windowSize = with(LocalDensity.current) {
                IntSize(maxWidth.roundToPx(), maxHeight.roundToPx())
            }
            CompositionLocalProvider(
                LocalBackdrop provides rememberBackdropState(
                    panelImage = state.backdropImage,
                    accentColor = state.backdropAccent,
                    windowSize = windowSize,
                ),
                LocalBackdropEffect provides state.backdropEffect,
            ) {
                PreviewWall(state = state, onBack = onBack, hazeState = hazeState)
            }
        }

        FinalizePanel(
            state = state,
            onPlateEnabled = onPlateEnabled,
            onPlateShape = onPlateShape,
            onZoom = onZoom,
            onSavePreset = onSavePreset,
            onApply = onApply,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Every icon this session is about to change, over the wallpaper, with the way back over it.
 *
 * **Lazy**, because a device with three hundred apps is the case this step is *for* — "what does this do to
 * everything?" — so composing them all would be the one place this screen janks.
 */
@Composable
private fun PreviewWall(
    state: IconStudioState,
    onBack: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PreviewCell),
            modifier = Modifier.fillMaxSize(),
            // The top is the back pill's room — it floats over the grid rather than taking a row of its own, so the
            // wallpaper runs behind it. Everything else is the layout's, not a constant's.
            contentPadding = PaddingValues(
                start = ScreenMargin,
                end = ScreenMargin,
                top = TopRoom,
                bottom = ScreenMargin,
            ),
            horizontalArrangement = Arrangement.spacedBy(PreviewGap),
            verticalArrangement = Arrangement.spacedBy(PreviewGap),
        ) {
            items(state.affected, key = { it.componentKey.flatten() }) { app ->
                Box(Modifier.size(PreviewCell), contentAlignment = Alignment.Center) {
                    AppIcon(
                        component = app.componentKey,
                        contentDescription = app.label,
                        sizePx = PreviewBakePx,
                        appearance = state.appearance,
                        modifier = Modifier.size(PreviewIcon),
                    )
                }
            }
        }

        StudioPillButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to the editor",
            hazeState = hazeState,
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).uiInsetsPadding().padding(ChromeMargin),
        )
    }
}

/**
 * The settings that apply to the **whole icon**, and the two things you can do with them.
 *
 * The order is the order of the questions: is there a plate, what shape is it, how much room does the artwork take
 * inside it — then keep this look, or use it.
 */
@Composable
private fun FinalizePanel(
    state: IconStudioState,
    onPlateEnabled: (Boolean) -> Unit,
    onPlateShape: (IconShape?) -> Unit,
    onZoom: (Float) -> Unit,
    onSavePreset: ((String) -> Unit)?,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    var naming by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner))
            .background(colors.surface)
            // Content padding, never layout padding: the panel fills to the window edge and insets what is in it,
            // which is this launcher's rule everywhere a surface has a background.
            .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            // **Scrollable, and that is about landscape rather than about long content.** The controls are a fixed
            // handful; what varies is the room they are given, and on a short viewport the shape grid is what would
            // otherwise push the buttons off the bottom edge.
            .verticalScroll(rememberScrollState())
            .padding(PanelPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MorphicSwitchRow(
            label = "Backdrop plate",
            supportingText = "A silhouette of blurred wallpaper behind every icon.",
            checked = state.plate.enabled,
            onCheckedChange = onPlateEnabled,
        )

        // Absent rather than disabled while there is no plate — the settings sections' own rule, and the shape grid
        // is four rows of nothing to look at when it cannot act.
        if (state.plate.enabled) {
            Text(
                text = "Plate shape",
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentMuted,
            )
            // The studio's own shape grid, so the plate's silhouettes and a layer's are drawn from one list — the
            // same reason the plate is masked with the renderer's own `shapeMask` rather than a Compose `Shape`.
            // **Bounded, for the effect grid's reason.** `ShapePage` hands each tile an equal share of the width and
            // makes it square, so on a wide panel four shares are four huge squares — which is exactly what a
            // rotated phone produced before this cap existed.
            Box(Modifier.widthIn(max = ShapeGridMax)) {
                ShapePage(
                    shapes = PlateShapes,
                    selected = state.plate.shape,
                    onSelect = onPlateShape,
                )
            }
        }

        SliderControl(
            label = "Icon size",
            value = state.zoom,
            valueRange = ZoomRange,
            default = 1f,
            onValueChange = onZoom,
            // No commit hook: there is nothing punctuated to record, because the whole-icon controls are not in
            // history (see `IconStudioViewModel.setPlateEnabled`) and nothing is written until Apply.
            onValueChangeFinished = {},
        )

        if (naming && onSavePreset != null) {
            PresetNamePrompt(
                onSave = {
                    onSavePreset(it)
                    naming = false
                },
                onCancel = { naming = false },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // **Only in the global session**, for the reason the studio's own preset panel states: a recipe tuned
            // against one app tends to name that app's own artwork, which a preset would then carry into every icon
            // it was applied to.
            if (onSavePreset != null) {
                MorphicButton(
                    onClick = { naming = !naming },
                    style = MorphicButtonStyle.Outlined,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save as preset")
                }
            }
            MorphicButton(
                onClick = onApply,
                // **Lit only when there is something to write**, which is what the editor's tick used to say by
                // being lit. That signal had nowhere else to go once the tick became "Next step", and here is where
                // it belongs: this is the button that commits, so this is the button that knows whether it would.
                enabled = state.dirty,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.dirty) "Apply" else "Applied")
            }
        }
    }
}

/** Names the current look and keeps it in the library. The field only exists while it is being used. */
@Composable
private fun PresetNamePrompt(onSave: (String) -> Unit, onCancel: () -> Unit) {
    val nameState = rememberTextFieldState()
    val name = nameState.text.toString().trim()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MorphicTextField(
            state = nameState,
            placeholder = "Name this look",
            modifier = Modifier.weight(1f),
        )
        MorphicButton(
            onClick = {
                onSave(name)
                nameState.setTextAndPlaceCursorAtEnd("")
            },
            enabled = name.isNotEmpty(),
        ) {
            Text("Save")
        }
        MorphicButton(onClick = onCancel, style = MorphicButtonStyle.Text) {
            Text("Cancel")
        }
    }
}

/**
 * What the plate may be cut to: no shape — the icon's own square — and then the built-in silhouettes.
 *
 * The same list the studio's shape section offers, with `null` first for its reason: "no shape" is a choice among the
 * same set rather than a toggle beside it. A square plate is a real look, so it is a tile and not an absence.
 */
private val PlateShapes: List<IconShape?> = listOf(null) + IconShapes.All

/**
 * How far the artwork may be scaled inside its own box.
 *
 * **Below 1 is what a plate is for** — an icon at 1f fills its box and so touches the plate's edge everywhere. Above
 * it is allowed as far as a little, because an icon with no plate may want to fill more of its cell than its own
 * padding leaves it; past that it would simply spill into the label.
 */
private val ZoomRange = 0.4f..1.15f

/** One preview: the cell, and the icon inside it. A home cell's proportions, so the picture is the honest one. */
private val PreviewCell = 76.dp
private val PreviewIcon = 52.dp
private val PreviewGap = 4.dp

/** What a preview bakes at. One size for every tile, so a screenful of icons is one bake each and no more. */
private const val PreviewBakePx = 192

private val ScreenMargin = 16.dp
private val PanelCorner = 24.dp
private val PanelPadding = 16.dp

/** Room above the previews for the back pill, which floats over them so the wallpaper runs behind it. */
private val TopRoom = 88.dp

/** How wide the shape grid may get before the extra goes to the gaps rather than to the tiles. */
private val ShapeGridMax = 400.dp
