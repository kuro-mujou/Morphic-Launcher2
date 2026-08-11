package inkspire.morphic.core.icon.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import inkspire.morphic.core.icon.render.LayerFilter
import inkspire.morphic.core.icon.render.LayerGradient
import inkspire.morphic.core.icon.render.LayerTransform
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
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
 * - [LayerFilter] does the colour-matrix arithmetic, for both — and shares the *same shape*, since Android's and
 *   Compose's `ColorMatrix` are each a row-major `FloatArray(20)`, so neither side converts anything.
 * - [LayerGradient] decides which way an angle runs, for both.
 * - The shape mask is built from the **same** vector drawable via [IconShapes], and applied the same way — as a
 *   destination-in mask over the finished layer.
 *
 * What is *not* shared is the drawing API (Android's `Canvas` there, [DrawScope] here). That is unavoidable, and
 * it is exactly why the five above are.
 *
 * **The per-layer order is content → shape mask → gradient → composite**, and it is the same on both sides for
 * different-looking reasons: the bake gets it from statement order inside one function, and this path gets it from
 * which node carries which modifier. Worth checking against `IconRenderer` if either is touched.
 *
 * A **shadow** effect is the one still missing, and it is not simply additive here — see the plan's S6 note.
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

    // **The stack composites offscreen, and a blend mode is why.** Sibling nodes draw onto whatever canvas they
    // are given, so without its own buffer a `MULTIPLY` on the bottom layer would multiply against the *studio
    // canvas* — the black, white or checkerboard behind the icon — instead of against nothing. The baked path
    // gets this for free by drawing into a fresh bitmap.
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        layers.forEach { layer ->
            // Three nested nodes now, and the nesting is the whole trick. The composite (opacity / blend / colour)
            // is **outermost**, so it applies to the finished layer and blends against the layers already drawn;
            // the mask sits inside it but **outside the transform**, so a shape stays put in the box while the
            // content moves under it. The baked path gets the same ordering from its draw order.
            Box(Modifier.fillMaxSize().layerComposite(layer.spec).shapeMask(layer.spec.shape)) {
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
 * Applies a layer's opacity, blend mode and colour matrix as it joins the stack — the live twin of
 * `IconRenderer.compositePaint`, sharing [LayerFilter]'s arithmetic so a tint cannot come out differently here.
 *
 * Through `saveLayer` rather than `graphicsLayer`, because `GraphicsLayerScope` has an `alpha` but no blend mode
 * and no colour filter; capturing the node into its own buffer and compositing *that* with one paint is the only
 * way to get all three, and it is the same technique [shapeMask] needs one level in.
 *
 * A layer that composites plainly gets no modifier at all, which is the common case — an unedited icon.
 */
@Composable
private fun Modifier.layerComposite(spec: IconLayerSpec): Modifier {
    val matrix = remember(spec.color) { LayerFilter.colorMatrixOf(spec.color) }
    val blend = spec.blend.composeBlendMode()
    val gradient = spec.gradient
    if (spec.opacity == 1f && blend == null && matrix == null && gradient == null) return this

    return this.drawWithContent {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                alpha = spec.opacity.coerceIn(0f, 1f)
                blend?.let { blendMode = it }
                matrix?.let { colorFilter = ColorFilter.colorMatrix(ColorMatrix(it)) }
            }
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            // Inside the layer, so source-atop has the layer's own pixels as its destination — which is what makes
            // the gradient colour the artwork instead of covering the icon with a rectangle. The same isolation the
            // saveLayer already provides for the blend mode, doing a second job.
            gradient?.let { drawGradientOverlay(it) }
            canvas.restore()
        }
    }
}

/** Paints a gradient over whatever has been drawn, clipped to it — the live twin of `IconRenderer.applyGradient`. */
private fun DrawScope.drawGradientOverlay(gradient: LayerEffect.Gradient) {
    val (x0, y0, x1, y1) = LayerGradient.endpoints(gradient.angleDegrees, size.width.toInt()).toList()
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(gradient.startArgb), Color(gradient.endArgb)),
            start = Offset(x0, y0),
            end = Offset(x1, y1),
        ),
        alpha = gradient.strength.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcAtop,
    )
}

/** The Compose blend mode for [LayerBlend], or `null` for `NORMAL` — which is the default source-over. */
private fun LayerBlend.composeBlendMode(): BlendMode? = when (this) {
    LayerBlend.NORMAL -> null
    LayerBlend.MULTIPLY -> BlendMode.Multiply
    LayerBlend.SCREEN -> BlendMode.Screen
    LayerBlend.OVERLAY -> BlendMode.Overlay
    LayerBlend.DARKEN -> BlendMode.Darken
    LayerBlend.LIGHTEN -> BlendMode.Lighten
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
