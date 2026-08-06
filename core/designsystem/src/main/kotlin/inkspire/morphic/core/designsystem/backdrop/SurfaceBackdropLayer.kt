package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * **The full-screen frost a surface is read against** — a sheet of blurred wallpaper that sits *above* HOME and
 * *below* whatever is covering it, and fades in on its own.
 *
 * **A sibling in the stack, not a modifier on the content, and that is the whole reason it exists.** A frosted
 * surface could always sample its own crop ([wallpaperBackdrop]), and for a small panel that is right — it slides
 * over the wallpaper like glass. But a surface that *arrives* wants the opposite: the APPS content should slide up
 * while the frost stays still and simply appears, because a blur that travelled with the content would read as a
 * sheet of frosted plastic being carried on screen rather than as the screen itself frosting over. Two motions, so
 * two nodes — [alpha] here, translation on the pane.
 *
 * **Every effect blurs; what they differ in is the wash** — and at this size **only** the wash, because the frost is
 * not tunable. The stored effect is read for its *variant* and its parameters are replaced by fixed ones
 * (`BackdropEffect.fullScreenFilm`): a strength or tint slider that can make a screenful of text unreadable is not a
 * preference worth offering, so choosing the variant chooses the whole look and the sliders govern smaller panels.
 *
 * | Effect | This layer |
 * |---|---|
 * | `Plain` | blur, no wash |
 * | `Blur(LIGHT)` / `Blur(DARK)` | blur + a white- or black-leaning wash |
 * | `MaterialYou` | blur + the wallpaper's own hue |
 * | `LiquidGlass` | blur + a saturation boost, no wash and no rim |
 *
 * The glass row is the one worth knowing: a lens needs an edge to bend light at, and at this size the rim would fall
 * under the system bars. What survives is the saturation ([BackdropEffect.saturation]) — which is what makes a
 * frosted sheet read as glass rather than as fog, is iOS's own recipe for its materials, and works on every API. The
 * rim stays what a *panel* gets. Hence `refracts = false` below.
 *
 * Since every film blurs by the same amount, switching variants never re-blurs the wallpaper — it is a redraw with a
 * different wash over an identical picture.
 *
 * **[scrimColor] is still required, and still for one reason**: a launcher that has never been given a wallpaper has
 * nothing to sample, and the content above still has to be readable. It is the caller's because only the caller knows
 * what colour its surface is.
 *
 * @param alpha how present the frost is, `0f..1f`, read at draw time — a lambda so a pan can drive it without
 *   recomposing this. At `0f` it is invisible but still composed, which is what lets it fade rather than appear.
 */
@Composable
fun SurfaceBackdropLayer(
    alpha: () -> Float,
    scrimColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
            // The stored effect's *variant*, at fixed parameters — the frost is not the sliders' to move. Read from
            // the local here rather than taken as a parameter, so no caller can hand this layer a tuned one.
            // No shape: the layer *is* the screen, so there is nothing to clip and nothing to round.
            .wallpaperBackdrop(
                effect = LocalBackdropEffect.current.fullScreenFilm,
                scrimColor = scrimColor,
                refracts = false,
            ),
    )
}
