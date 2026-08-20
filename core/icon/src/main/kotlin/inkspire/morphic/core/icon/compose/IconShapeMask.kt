package inkspire.morphic.core.icon.compose

import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.withMatrix
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconShape

/**
 * Keeps the node's pixels only where a shape's silhouette is opaque — the one silhouette mask, shared by everything
 * that has to be trimmed to an [IconShape]. A null shape — or an unknown id, which stale stored data can still
 * produce — is a no-op, matching the baked path's early return.
 *
 * **Its own file at its third consumer, and the third is in another module.** A layer's shape and the whole icon's
 * shape were the two that made it take a `matrixOf` lambda; the **plate** behind an icon is the new one, and it
 * arrives from `core:designsystem` — which is why this stopped being private. The alternative was a Compose `Shape`
 * catalog for plates beside the vector one for icons, and two lists meant to look identical are two lists that will
 * not: a plate cut to a squircle while the icon in front of it was cut to a *slightly* different squircle is the
 * kind of wrong nobody can point at.
 *
 * **The mask goes through `saveLayer` because a `Drawable` cannot be drawn with a blend mode.** It paints with its
 * own paints, so the silhouette has to be captured into its own buffer first and *that* composited with
 * [BlendMode.DstIn]. [CompositingStrategy.Offscreen] is the matching requirement one level out: the blend's
 * destination must be this node's own pixels, and without it the blend would apply against whatever happened to be
 * on the canvas beneath — which for the bottom layer of a stack is the surface behind the icon.
 *
 * **This node stays outside the transform whatever the anchor is, and only what it draws inside changes.** The mask
 * has to be applied to the layer's *finished* pixels, so its position cannot come from where the node sits — it
 * comes from [ShapeMask], the same answer the baked path gets. Nesting the mask inside the content's transform
 * would look equivalent and is not: the content is drawn through the transform, so a rotation would turn the mask's
 * own buffer and its edges with it.
 *
 * **The position arrives as a lambda rather than as a `ResolvedLayer`, which is what lets one node serve them
 * all.** A *layer*'s mask
 * is fitted to the ink of the artwork that layer actually resolved to — so it needs the spec and the content
 * together — while the **composite**'s only frame is the box, and it has no content to be handed. Taking
 * `matrixOf` keeps one masking node for both instead of a second one that could drift in how it applies the same
 * silhouette; what each caller decides is only *where*, which stays [ShapeMask]'s answer.
 *
 * @param matrixOf where to draw the silhouette, given the node's pixel size — `null` meaning plainly at box size,
 *   which is the composite always and a box-anchored layer.
 */
@Composable
fun Modifier.shapeMask(shape: IconShape?, matrixOf: (sizePx: Int) -> Matrix? = { null }): Modifier {
    val res = shape?.let { IconShapes.drawableResOrNull(it) } ?: return this
    val resource = LocalResources.current
    // **`mutate`, because `getDrawable` hands back a fresh instance over *shared* constant state** and a
    // `VectorDrawable` caches its rendered bitmap in there — the same hazard `IconLayerResolver.owned()` answers one
    // step up. It matters more here than anywhere: a shape is masked at every scope and every tile, so one shape's
    // constant state can have half a dozen readers at as many sizes.
    val maskDrawable = remember(res, resource) { resource.getDrawable(res, null)?.mutate() } ?: return this

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawIntoCanvas { canvas ->
                canvas.saveLayer(
                    bounds = Rect(0f, 0f, size.width, size.height),
                    paint = Paint().apply { blendMode = BlendMode.DstIn },
                )
                // Square, from the width — the same quantity the transform above is resolved against, so the two
                // read the box the same way.
                val sizePx = size.width.toInt()
                maskDrawable.setBounds(0, 0, sizePx, sizePx)
                val matrix = matrixOf(sizePx)
                val native = canvas.nativeCanvas
                if (matrix == null) maskDrawable.draw(native)
                else native.withMatrix(matrix) { maskDrawable.draw(this) }
                canvas.restore()
            }
        }
}
