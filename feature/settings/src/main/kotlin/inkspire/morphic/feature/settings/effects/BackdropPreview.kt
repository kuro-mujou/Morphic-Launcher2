package inkspire.morphic.feature.settings.effects

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.LocalBackdrop
import inkspire.morphic.core.designsystem.backdrop.rememberBackdropState
import inkspire.morphic.core.designsystem.backdrop.wallpaperBackdrop
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.BackdropEffect

/**
 * **What the chosen effect looks like** — a real frosted panel, over the real wallpaper.
 *
 * This section had no preview, and its own KDoc gave the reason: every other section previews a *cell*, which a pane
 * can draw on its own, where an effect previews a frosted surface over the wallpaper and the settings pane has no
 * backdrop to sample. Both halves of that are answerable now, and neither needed anything faked:
 *
 * - **The wallpaper comes from the window.** `PunchThroughPane` composites the detail offscreen, so a layer drawn with
 *   `BlendMode.Src` *replaces* the pane's pixels rather than blending with them — clearing everything this box does not
 *   draw to transparent, and the window shows the wallpaper (`Theme.Wallpaper`). L1's trick, and the same one every
 *   icon preview here already uses.
 * - **The panel is drawn by the same modifier the launcher uses.** [wallpaperBackdrop] samples by *screen position*, so
 *   the card shows the crop of the blurred wallpaper that sits behind it — continuous with the sharp wallpaper punched
 *   through around it. Nothing is approximated: this is a frosted panel, in situ, at the size a menu is.
 *
 * **[LocalBackdrop] is provided here rather than at the settings zone's root**, which is the narrower half of the rule
 * the launcher shell follows. The shell provides it at a zone boundary because *every* surface in that zone samples the
 * wallpaper; in settings exactly one thing does, and it is a preview rather than a surface. Providing it any higher
 * would invite a second pane to frost itself against a picture nobody asked for. (What L1 got wrong was different again
 * — it provided the launcher's backdrop *inside* `HomeScreen`, so its settings feature needed a duplicate to get any at
 * all.)
 *
 * **Everything tracks the drag now, blur included — but blur changes the *picture*, so it arrives smaller.** The wash
 * and all six liquid-glass parameters are draw-time reads of [effect], so passing the dragged value previews them for
 * free. Blur lives in [image], which has to be re-blurred, so a drag is fed a quarter-size copy that can be blurred
 * inside a frame (`WallpaperRepository.backdropPreview`) and the full-size one returns the moment the finger lifts. The
 * caller decides which is in [image]; this composable only draws what it is handed. The visible bill is at the *sharp*
 * end — below about an eighth of the slider the settled picture keeps more of the wallpaper than the dragging one — and
 * it resolves on release, which is why it is the drag that gets the smaller picture rather than the resting state.
 *
 * @param effect the effect to draw — the *dragged* value where a slider is being moved, so this previews what the user
 *   is doing rather than what is stored.
 * @param image the wallpaper blurred at the stored strength, or null when the launcher has none it may claim is on
 *   screen. Null renders the card's scrim, which is what a real surface does in the same state.
 * @param accent the wallpaper's representative color, which the washes are blended toward.
 */
@Composable
internal fun BackdropPreview(
    effect: BackdropEffect,
    image: Bitmap?,
    accent: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val windowSize = LocalWindowInfo.current.containerSize
    val backdrop = rememberBackdropState(panelImage = image, accentColor = accent, windowSize = windowSize)

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        // The punch. Only this box is inside it, so the heading above and the sliders below are untouched — a punch
        // around the whole pane would clear those to the wallpaper too.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .graphicsLayer { blendMode = BlendMode.Src }
                .padding(vertical = PreviewPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(CardWidthFraction)
                    .fillMaxSize()
                    // `refracts = true`, unlike the full-screen frost: a lens needs an edge to bend light at, and this
                    // card has four. It is the whole reason liquid glass has a preview worth looking at.
                    .wallpaperBackdrop(
                        shape = RoundedCornerShape(CardCorner),
                        effect = effect,
                        scrimColor = colors.surfaceElevated,
                    )
                    .padding(CardPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                // Text, because legibility over a photograph is what the effect is *for* — a bare rectangle of glass
                // shows the blur and says nothing about whether a menu on it could be read.
                Text(
                    text = "Frosted surface",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    text = "A menu or a sheet, over your wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = SubtitleAlpha),
                )
            }
        }
    }
}

/** Tall enough to read a two-line card against the wallpaper around it, short enough to pin without eating the pane. */
private val PreviewHeight = 168.dp

/** Breathing room so the punched wallpaper reads as *around* the card rather than as the card's own edge. */
private val PreviewPadding = 12.dp

/** The card, at roughly the width a context menu takes. */
private const val CardWidthFraction = 0.8f
private val CardCorner = 20.dp
private val CardPadding = 16.dp

/** The subtitle sits under the title in the same white, softened — the launcher's own chrome over glass. */
private const val SubtitleAlpha = 0.75f
