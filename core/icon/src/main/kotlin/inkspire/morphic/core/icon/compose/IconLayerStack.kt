package inkspire.morphic.core.icon.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import inkspire.morphic.core.icon.render.IconLayerResolver
import inkspire.morphic.core.icon.render.LayerTransform
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconShape

/**
 * The **live** half of the hybrid render: an [IconLayerSet] drawn as Compose nodes, one per layer, rather than
 * composited into a bitmap.
 *
 * This is the editor's render path, and nothing else draws through it. Surfaces use [LauncherIcon], which shows a
 * baked bitmap — right for hundreds of icons that rarely change. The editor is the opposite case: one icon,
 * changing every frame a slider moves, where baking per frame would allocate a bitmap per frame. Here a transform
 * is a `graphicsLayer` on a node that already exists, so a slider drag costs a redraw and no re-composite at all.
 *
 * ## Staying honest with the baked path
 *
 * Two renderers is a real hazard — an icon that looks right while being edited and wrong on every surface is a bug
 * the editor structurally cannot show you. Three things make the paths agree, and each is a shared *thing* rather
 * than a shared intention:
 * - [IconLayerResolver] decides which layers draw and what content each one means, for both.
 * - [LayerTransform] does the offset/zoom/rotation arithmetic, for both.
 * - The shape mask is built from the **same** vector drawable via [IconShapes], and applied the same way — as a
 *   destination-in mask over the finished layer.
 *
 * What is *not* shared is the drawing API (Android's `Canvas` there, [DrawScope] here). That is unavoidable, and
 * it is exactly why the three above are.
 *
 * Effects are not applied yet, in either path: `LayerEffect` has no variants until the effects slice.
 *
 * @param icon the app's parsed layers, from `ParsedIconLoader` — the same input the bake takes.
 * @param customImage resolves a custom-image layer's stored path to a drawable. Defaults to drawing nothing, and
 *   is a parameter rather than something this composable does for itself because decoding a file is I/O: the host
 *   decodes off the main thread and hands the results in, where doing it here would put a disk read in a
 *   composition that reruns on every slider frame.
 * @param modifier must resolve to a **square**. The layer geometry is defined in a square box, so a non-square
 *   node would stretch every transform along one axis — and, being only a distortion, would look plausible.
 */
@Composable
fun IconLayerStack(
    icon: ParsedIcon,
    layerSet: IconLayerSet,
    modifier: Modifier = Modifier,
    customImage: (path: String) -> Drawable? = { null },
) {
    val resolver = remember { IconLayerResolver() }
    val layers = remember(layerSet, icon, customImage) { resolver.resolve(layerSet, icon, customImage) }

    Box(modifier) {
        layers.forEach { layer ->
            // Two nested nodes, and the nesting is the whole trick: the mask sits **outside** the transform, so a
            // shape stays put in the box while the content moves under it. The baked path gets the same ordering
            // by masking after it restores the canvas matrix; here it falls out of which node carries what.
            Box(Modifier.fillMaxSize().shapeMask(layer.spec.shape)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Resolved here, inside the block, because this is where the node's real size is
                            // known — which is what lets the *shared* arithmetic run against real pixels rather
                            // than being re-derived in Compose units.
                            val transform = LayerTransform.of(layer.spec, sizePx = size.width.toInt())
                            scaleX = transform.zoom
                            scaleY = transform.zoom
                            rotationZ = transform.rotationDegrees
                            translationX = transform.translateXPx
                            translationY = transform.translateYPx
                            transformOrigin = TransformOrigin.Center
                        },
                ) {
                    drawLayerContent(layer.content)
                }
            }
        }
    }
}

/** Draws one layer's resolved content over the whole box — the live twin of `IconRenderer.drawContent`. */
private fun DrawScope.drawLayerContent(content: ParsedLayer) {
    when (content) {
        is ParsedLayer.Color -> drawRect(Color(content.argb))
        is ParsedLayer.Image -> drawIntoCanvas { canvas ->
            content.drawable.apply {
                setBounds(0, 0, size.width.toInt(), size.height.toInt())
                draw(canvas.nativeCanvas)
            }
        }
    }
}

/**
 * Keeps the node's pixels only where [shape]'s silhouette is opaque. A null shape — or an unknown id, which stale
 * stored data can still produce — is a no-op, matching the baked path's early return.
 *
 * **The mask goes through `saveLayer` because a `Drawable` cannot be drawn with a blend mode.** It paints with its
 * own paints, so the silhouette has to be captured into its own buffer first and *that* composited with
 * [BlendMode.DstIn]. [CompositingStrategy.Offscreen] is the matching requirement one level out: the blend's
 * destination must be this node's own pixels, and without it the blend would apply against whatever happened to be
 * on the canvas beneath — which for the bottom layer of a stack is the surface behind the icon.
 */
@Composable
private fun Modifier.shapeMask(shape: IconShape?): Modifier {
    val res = shape?.let { IconShapes.drawableResOrNull(it) } ?: return this
    val resource = LocalResources.current
    val maskDrawable = remember(res, resource) { resource.getDrawable(res, null) } ?: return this

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawIntoCanvas { canvas ->
                canvas.saveLayer(
                    bounds = Rect(0f, 0f, size.width, size.height),
                    paint = Paint().apply { blendMode = BlendMode.DstIn },
                )
                maskDrawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                maskDrawable.draw(canvas.nativeCanvas)
                canvas.restore()
            }
        }
}
