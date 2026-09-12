package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/** The dark wash over the blur. Enough to carry white text over a white canvas, light enough to stay glass. */
private val StudioTint = Color.DarkGray.copy(alpha = 0.4f)

/** The color every studio surface draws its text and icons in — see [studioSurface] for why it is fixed. */
val StudioContentColor = Color.White

/**
 * The one material every floating surface in **either** studio is made of: the live work beneath, blurred, under a
 * dark wash.
 *
 * **A shared modifier rather than a convention**, so a new panel cannot arrive looking slightly different from the
 * rest. The icon studio has several — a tool rail, an extras rail, the settings container, a layer popup — and they
 * overlap the same canvas at different depths; one material is what makes them read as one system rather than as four
 * translucent rectangles.
 *
 * **The wallpaper studio is the second consumer, and it is what makes this the studios' material rather than the icon
 * studio's.** Its panels used to be a flat 86%-black scrim, which was the honest answer while there was no blur to
 * reach for and is a hole punched in the picture being designed now that there is. What it needs from this is exactly
 * what the icon studio needs: a surface legible over a canvas that could be any color, which still shows the canvas.
 * It lives in this package rather than a shared one because moving it is a repackaging of nine files and a separate
 * idea from either studio's UI.
 *
 * **The material is the library's, not a radius of ours.** `HazeMaterials.ultraThin` carries the blur radius, the tint
 * blend and the noise as one recipe, so the only number left to choose is the tint color. An earlier cut composed
 * those by hand — a radius we picked, with the tint as a color effect over an opaque background — and it read as a
 * flat film rather than as glass. There is deliberately **no `blurRadius` parameter**: a per-surface depth is exactly
 * what the paragraph above exists to prevent.
 *
 * **Why this is Haze and not `wallpaperBackdrop`.** The launcher's own blur samples a pre-blurred *wallpaper*
 * bitmap by position — right for a surface sliding over the picture, and only ever able to show **the wallpaper that
 * is set**. Neither studio's backdrop is that: the icon studio's canvas is a flat color or a checkerboard plus the
 * icon being edited, and the wallpaper studio's is a wallpaper that has not been applied and may never be. Both are
 * content the launcher itself draws a frame at a time, which `wallpaperBackdrop` structurally cannot serve. Haze
 * blurs whatever is actually beneath the node, live.
 *
 * **The content color is fixed white, which is the one place the studio departs from the theme.** Everywhere else
 * chrome follows wallpaper brightness or the system. Here the thing behind the glass is a canvas the *user* sets to
 * black or white at will, so a theme-derived color would be unreadable half the time; a dark wash heavy enough to
 * carry white over either is the only setting that is always legible.
 *
 * **A surface stops what lands on it**, which is the other half of being one — and it was missing. A `Modifier` that
 * only draws makes no node a hit target, so a press on a panel's padding, its header, or the gap between two controls
 * found nothing in the panel's subtree and fell through to the **canvas sibling underneath** — whose job is to put
 * the chrome away. Pressing the header of a panel closed it. Compose stops hit-testing at the first sibling that
 * reports a hit, so one detector here is the whole fix, for taps and for drags alike: with it the canvas is never
 * reached, and without it a drag begun on a panel panned the icon behind it.
 *
 * It is in the shared material rather than at each panel for `launcherItemGestures`' reason — wiring that must not be
 * forgotten belongs in the one place every caller already goes through.
 *
 * **It claims nothing, which is the point.** Being a *hit target* is the whole of what blocks the sibling, so the
 * detector awaits a down and consumes neither it nor anything after. A `detectTapGestures {}` would also work and is
 * what this was first written as, but it consumes the down and the up — and every slider, switch and tile inside a
 * panel is a descendant sharing that hit path. Swallowing a press that a control underneath is in the middle of
 * reading is a far worse failure than the one being fixed, and it would show up as one control in one panel
 * occasionally not responding.
 *
 * Requires a node upstream marked `Modifier.hazeSource(state)` with the same [state], which is what
 * `HazeInput.Sources` names — the icon studio's preview canvas and the wallpaper studio's preview, each of which is
 * always composed *and always paints something*, so a surface here never has nothing to sample. Anything reusing this
 * modifier owes itself both halves of that check: there is no opaque background behind the blur to fall back to, and a
 * source node that draws nothing (a `Box` waiting on its first bitmap, say) is the same failure as no source at all.
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
    // Awaits a down and does nothing with it: the node being a hit target is what stops the press reaching the
    // canvas, and consuming would reach *into* the panel's own controls instead. `requireUnconsumed = false` so a
    // press a child has already taken still counts as landing here, which is what keeps the block above true for
    // every gesture rather than only the ones nothing wanted.
    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
