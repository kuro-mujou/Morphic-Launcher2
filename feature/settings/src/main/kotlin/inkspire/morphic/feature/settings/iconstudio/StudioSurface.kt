package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

/** The blur radius every studio surface uses. One value, so surfaces at different depths still read as one material. */
private val StudioBlurRadius = 8.dp

/** The dark wash over the blur. Enough to carry white text over a white canvas, light enough to stay glass. */
private val StudioTint = Color.Black.copy(alpha = 0.4f)

/** The colour every studio surface draws its text and icons in — see [studioSurface] for why it is fixed. */
val StudioContentColor = Color.White

/**
 * The one material every floating surface in the icon studio is made of: the live canvas beneath, blurred, under a
 * dark wash.
 *
 * **A shared modifier rather than a convention**, so a new panel cannot arrive looking slightly different from the
 * rest. The studio has several — a tool rail, an extras rail, the settings container, a layer popup — and they
 * overlap the same canvas at different depths; one blur radius and one tint is what makes them read as one system
 * rather than as four translucent rectangles.
 *
 * **Why this is Haze and not `wallpaperBackdrop`.** The launcher's own blur samples a pre-blurred *wallpaper*
 * bitmap by position — right for a surface sliding over the picture, and only ever able to show the wallpaper. The
 * studio canvas is deliberately not the wallpaper: it is a flat colour or a checkerboard, plus the icon being
 * edited. So this is the one screen whose backdrop is content the launcher itself draws, which `wallpaperBackdrop`
 * structurally cannot serve. Haze blurs whatever is actually beneath the node, live.
 *
 * **The content colour is fixed white, which is the one place the studio departs from the theme.** Everywhere else
 * chrome follows wallpaper brightness or the system. Here the thing behind the glass is a canvas the *user* sets to
 * black or white at will, so a theme-derived colour would be unreadable half the time; a dark wash heavy enough to
 * carry white over either is the only setting that is always legible.
 *
 * Requires a node upstream marked `Modifier.hazeSource(state)` with the same [state] — in the studio that is the
 * preview canvas. With nothing to sample this degrades to the tint alone, which is still a legible panel.
 *
 * @param shape the surface's outline; the blur and the wash are both clipped to it.
 * @param blurRadius overridable only for a surface that genuinely needs a different depth; leave it alone otherwise.
 */
@Composable
fun Modifier.studioSurface(
    state: HazeState,
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = StudioBlurRadius,
): Modifier = this
    .clip(shape)
    // **Before `hazeEffect`, and the order is the whole of its meaning.** Draw modifiers paint in chain order, so
    // this lands *behind* the blur: in the normal case the blurred canvas is opaque and covers it entirely, and it
    // shows only when Haze draws nothing — no source yet, or a device that cannot blur. Put after, it would paint
    // over the blur instead and the wash would be applied twice.
    .background(StudioTint)
    .hazeEffect(state) {
        this.blurEffect {
            this.blurRadius = blurRadius
            colorEffects = listOf(HazeColorEffect.tint(StudioTint))
        }
    }
