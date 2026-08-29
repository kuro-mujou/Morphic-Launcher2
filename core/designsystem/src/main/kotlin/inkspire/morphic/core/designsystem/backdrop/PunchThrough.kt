package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer

/**
 * **Showing the real wallpaper through a hole in an opaque screen** — the two halves of one recipe, kept in one file
 * because either half alone does nothing and neither says so when it is missing.
 *
 * A preview of something that will sit on the wallpaper has to be judged against the wallpaper; a gray panel is not
 * that. There is no way to *draw* the wallpaper into a screen — the launcher may read it for a blur but not composite
 * it — so instead the screen **clears itself** where the preview is, and the window behind it is already showing the
 * real thing (`windowShowWallpaper` in `app`'s theme).
 *
 * Four things must be true at once, and this file owns two of them:
 *
 * 1. [PunchThroughLayer] composites the screen **offscreen** and paints its background **inside** that layer.
 * 2. [punchThroughHole] draws with `BlendMode.Src`, which *replaces* the pixels under it instead of blending — so
 *    wherever its content draws nothing, the layer's background is cleared to transparent.
 * 3. The window shows the wallpaper — `app`'s theme.
 * 4. Nothing opaque is painted *behind* the layer, which is the caller's to get right: a background under the layer
 *    is exactly what the hole would reveal.
 *
 * Extracted here when the second consumer arrived — the settings shell's detail pane was the first, and an icon
 * container's settings preview the second. It is a recipe rather than a component, which is the argument for one
 * file over two names in two modules: the four clauses above are what a reader needs, and they were a comment on a
 * private function nobody else could see.
 */
@Composable
fun PunchThroughLayer(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // **Overscroll is off for everything inside**, and this is the part of the recipe that reads like superstition
    // until it is seen: a stretch re-composites the scrolling content into a layer of its own mid-gesture, and for
    // as long as it does the punch stops reaching the window — the hole fills with the background color and springs
    // back out of it.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.withSaveLayer(
                            bounds = size.toRect(),
                            paint = Paint()
                        ) {
                            drawContent()
                        }
                    }
                }
                // **After `drawWithContent`, which is what makes it part of what the layer captures.** Painted
                // before it, this would sit underneath the punched hole and be the thing the hole revealed.
                .background(background),
            content = content,
        )
    }
}

/**
 * Marks this node as **the hole** — whatever it draws replaces the [PunchThroughLayer] beneath it, and wherever it
 * draws nothing the layer is cleared to transparent and the wallpaper shows.
 *
 * Put it on the smallest node that wants the wallpaper behind it, never on a whole screen: everything inside a hole
 * is punching too, so a title or a caption caught in one clears its own background and reads as text floating on the
 * wallpaper. See [PunchThroughLayer] for the other three clauses this depends on.
 */
fun Modifier.punchThroughHole(): Modifier = graphicsLayer { blendMode = BlendMode.Src }
