package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import inkspire.morphic.core.designsystem.backdrop.LocalOverFrost
import inkspire.morphic.core.designsystem.backdrop.wallpaperBackdrop
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.icon.compose.localAppearanceOf
import inkspire.morphic.core.icon.compose.shapeMask
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconAppearance

/**
 * One app icon **as a surface draws it**: the baked recipe, the live plate behind it, and the artwork scaled inside
 * its own box.
 *
 * **This is what every cell calls, not `LauncherIcon`.** That one is the primitive — a component and a recipe in,
 * one flat bitmap out — and it deliberately knows nothing about the plate, because it *cannot*: a bake is keyed on
 * `IconId(component, layerSet, sizePx)` with no screen position in it, while the plate samples the wallpaper and so
 * depends on where the cell sits. Two cells showing the same app must show different plate pixels. So the split
 * follows the render boundary, and this composable is the seam where the two halves meet — one place, so a new
 * surface cannot draw an icon and forget its glass.
 *
 * **The plate is masked with the icon renderer's own silhouette** (`Modifier.shapeMask`), not with a Compose `Shape`.
 * A shape here is a vector drawable referenced by id, and the whole point of reusing that node is that a plate cut to
 * a squircle and an icon cut to a squircle are cut to the *same* squircle. A parallel `Shape` catalog would drift
 * silently, in the one direction nobody can see.
 *
 * **No refraction on a plate**, which is the full-screen film's reason reached from the other end: liquid glass's rim
 * is a rounded-rect SDF, so masked to a hexagon or a teardrop it would trace an outline the plate does not have. What
 * it renders instead is the blur plus `BackdropEffect.saturation`, which is what makes frosted glass read as glass at
 * any API.
 *
 * **The cost is paid only when a plate is on.** An enabled plate is one backdrop node and one offscreen mask layer
 * per icon, per frame — real on a dense grid, and nothing at all for an appearance that has no plate, which is every
 * icon until someone turns one on.
 *
 * @param appearance what to draw. Defaults to the resolution every surface wants — this app's own recipe if it has
 *   been detached, otherwise the global default. **An explicit one bypasses both**, which is what a preview of a
 *   *particular* look needs (a preset tile), the same escape hatch `LauncherIcon.layerSet` already offers.
 */
@Composable
fun AppIcon(
    component: ComponentKey,
    contentDescription: String?,
    sizePx: Int,
    modifier: Modifier = Modifier,
    appearance: IconAppearance = localAppearanceOf(component),
) {
    // **Absent over the film, not flattened.** A plate is a piece of the wallpaper cut to the icon's silhouette, and
    // on a surface that is already a blurred sheet of that wallpaper there is nothing for it to be a piece *of* — it
    // would read as a sharper patch floating on the frost. Drawing the scrim instead would be a gray blob behind
    // every icon, which is worse than the plate the user turned on not appearing on this one surface.
    val plated = appearance.plate.enabled && !LocalOverFrost.current

    // **And the zoom goes with the plate it was set against.** [IconAppearance.zoom] is the artwork's size *relative
    // to its plate* — the fraction that keeps an icon clear of the glass's edge — so replaying it where no plate is
    // drawn leaves an icon that is simply smaller than every other icon in the row, for a reason nothing on screen
    // shows. Dropping the plate silently costs the user the size they set everywhere else; dropping the zoom with it
    // is what makes the icon its own size here.
    //
    // **Only when *this* surface suppressed the plate.** A zoom stored against a plate the user has switched *off* is
    // a plain size choice and stands wherever it is drawn — which is why the condition is the suppression, not the
    // absence of a plate.
    val zoom = if (appearance.plate.enabled && !plated) 1f else appearance.zoom

    Box(modifier, contentAlignment = Alignment.Center) {
        if (plated) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .shapeMask(appearance.plate.shape)
                    .wallpaperBackdrop(
                        scrimColor = LocalMorphicColors.current.surface.copy(alpha = PlateScrimAlpha),
                        refracts = false,
                    ),
            )
        }

        LauncherIcon(
            component = component,
            contentDescription = contentDescription,
            sizePx = sizePx,
            layerSet = appearance.layerSet,
            // **The artwork scales, the plate does not** — that is the whole of what this zoom is for, and why it is
            // not `IconSizing`: an icon at 1f fills its box and so touches the plate's edge everywhere. Deliberately
            // unclipped, since an icon's own glow or shadow is meant to escape its box; a zoom above 1 therefore
            // spills, which is visible while it is being set rather than a surprise later. Resolved above, since
            // what it is depends on whether the plate it was set against is being drawn at all.
            modifier = Modifier
                .fillMaxSize()
                .scale(zoom.coerceAtLeast(0f)),
        )
    }
}

/**
 * How opaque a plate is when there is **no wallpaper to sample** — which is what every frosted surface in this
 * launcher falls back to, and the state the launcher is in until the user gives it an image.
 *
 * Visible rather than invisible, because a switch that appears to do nothing is worse than one whose result is
 * plain: with nothing to blur, a plate can still say it is there. The theme's own `surface`, so it reads correctly
 * against either wallpaper brightness.
 */
private const val PlateScrimAlpha = 0.45f
