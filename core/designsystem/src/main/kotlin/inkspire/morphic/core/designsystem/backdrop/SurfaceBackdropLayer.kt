package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
 * while the frost stays still and simply appears, because a blur that traveled with the content would read as a
 * sheet of frosted plastic being carried on screen rather than as the screen itself frosting over. Two motions, so
 * two nodes — [alpha] here, translation on the pane.
 *
 * **Every effect blurs; what they differ in is the wash** — and at this size **only** the wash, because the frost is
 * not tunable. The stored effect is read for its *variant* and its parameters are replaced by fixed ones: a strength
 * or tint slider that can make a screenful of text unreadable is not a preference worth offering, so choosing the
 * variant chooses the whole look.
 *
 * **The recipe is [filmBackdrop]'s, not this composable's**, which is what stops it drifting from the two smaller
 * surfaces that wear the same material — the context menu and the launcher's bottom sheets, both clipped to a rounded
 * rect and identical to this otherwise. The fixed effect names a blur strength and the film image is the wallpaper
 * blurred *at* that strength, so the two have to be chosen together; there they are, once.
 *
 * | Effect | This layer |
 * |---|---|
 * | `Blur`, tint `BackdropTint.NONE` | blur, no wash |
 * | `Blur`, tint `BackdropTint.LIGHT` / `BackdropTint.DARK` | blur + a white- or black-leaning wash |
 * | `Blur`, tint `BackdropTint.WALLPAPER` | blur + the wallpaper's own hue |
 * | `LiquidGlass` | blur + a saturation boost, no wash and no rim |
 *
 * The glass row is the one worth knowing: a lens needs an edge to bend light at, and at this size the rim would fall
 * under the system bars. What survives is the saturation ([BackdropEffect.saturation]) — which is what makes a
 * frosted sheet read as glass rather than as fog, is iOS's own recipe for its materials, and works on every API where
 * the rim does not. The rim stays what a surface with **edges of its own** gets — a container tile on the home grid;
 * see [filmBackdrop] for the test that decides which of the two a surface is.
 *
 * Since every film blurs by the same amount, switching variants never re-blurs the wallpaper — it is a redraw with a
 * different wash over an identical picture.
 *
 * **[scrimColor] is still required, and still for one reason**: a launcher that has never been given a wallpaper has
 * nothing to sample, and the content above still has to be readable. It is the caller's because only the caller knows
 * what color its surface is.
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
    // **This layer *is* a film, so it is never over one — even when it composes inside another's subtree.** Which it
    // routinely does: a collection opened on the APPS surface builds its own layer inside the `LocalOverFrost` that
    // `AppsScreen` provides, and a collection on HOME wraps its own content in the same local. Without this, every one
    // of those would find the picture withheld (see [wallpaperBackdrop]) and paint a flat scrim where the frost goes.
    //
    // It is also the one place two films stacking is *wanted*: a folder over the drawer is frosted twice and its wash
    // compounds, which is a depth cue rather than the double-blur the local exists to stop. The rule the local carries
    // is about a **panel** over a film, and this is not a panel.
    CompositionLocalProvider(LocalOverFrost provides false) {
        Box(
            modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
                // The stored effect's *variant* at fixed parameters, the picture blurred to match, and no rim — all
                // three from [filmBackdrop], which is where they are written now that the context menu wears the same
                // material. No shape: the layer *is* the screen, so there is nothing to clip and nothing to round.
                .filmBackdrop(scrimColor = scrimColor),
        )
    }
}
