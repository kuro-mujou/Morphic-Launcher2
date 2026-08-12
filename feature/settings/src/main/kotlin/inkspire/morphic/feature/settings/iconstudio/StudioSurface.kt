package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/** The dark wash over the blur. Enough to carry white text over a white canvas, light enough to stay glass. */
private val StudioTint = Color.DarkGray.copy(alpha = 0.4f)

/** The colour every studio surface draws its text and icons in — see [studioSurface] for why it is fixed. */
val StudioContentColor = Color.White

/**
 * The one material every floating surface in the icon studio is made of: the live canvas beneath, blurred, under a
 * dark wash.
 *
 * **A shared modifier rather than a convention**, so a new panel cannot arrive looking slightly different from the
 * rest. The studio has several — a tool rail, an extras rail, the settings container, a layer popup — and they
 * overlap the same canvas at different depths; one material is what makes them read as one system rather than as four
 * translucent rectangles.
 *
 * **The material is the library's, not a radius of ours.** `HazeMaterials.ultraThin` carries the blur radius, the tint
 * blend and the noise as one recipe, so the only number left to choose is the tint colour. An earlier cut composed
 * those by hand — a radius we picked, with the tint as a colour effect over an opaque background — and it read as a
 * flat film rather than as glass. There is deliberately **no `blurRadius` parameter**: a per-surface depth is exactly
 * what the paragraph above exists to prevent.
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
 * Requires a node upstream marked `Modifier.hazeSource(state)` with the same [state], which is what
 * `HazeInput.Sources` names — in the studio that is the preview canvas, and it is always composed, so a surface here
 * never has nothing to sample. Anything reusing this modifier outside the studio owes itself that check: there is no
 * longer an opaque background behind the blur to fall back to.
 *
 * @param shape the surface's outline; the blur and the wash are both clipped to it.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.studioSurface(
    state: HazeState,
    shape: Shape = RoundedCornerShape(20.dp),
): Modifier = this
    .clip(shape)
    .hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeMaterials.ultraThin(StudioTint)
    )
