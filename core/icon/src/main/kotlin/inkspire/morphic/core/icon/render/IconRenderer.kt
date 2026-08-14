package inkspire.morphic.core.icon.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withMatrix

/**
 * Composites an [IconLayerSet] + the app's [ParsedIcon] into one square [Bitmap] of `sizePx` — the baked icon
 * shown on the home screen and other surfaces.
 *
 * Each layer is drawn into its own bitmap — so its transform and shape mask apply in isolation — and is then
 * composited onto the output through one paint carrying its opacity, blend mode and color matrix. Compositing is
 * **synchronous and CPU/heavy** (bitmap allocation + drawing); callers run it off the main thread and cache the
 * result by `IconId`.
 *
 * **Why the paint is applied at the join and not while the content is drawn**: a blend mode has to mean "against
 * everything beneath this layer", and inside the layer's own bitmap there is nothing beneath. The live path
 * ([inkspire.morphic.core.icon.compose.IconLayerStack]) composes the same three into one paint for the same
 * reason, and shares [LayerFilter]'s matrix arithmetic so the two cannot disagree about a tint.
 *
 * A layer's gradient overlay is applied **after its shape mask**, so it colors the shaped silhouette rather than
 * the square it was cut from — the live path orders it the same way, by which node carries which modifier.
 *
 * Still deferred: a **shadow** effect (not additive — it could not be matched in the live path below API 31; see
 * the plan's S6 note), and adaptive-layer overshoot scaling (`AppDefault` layers draw to the full box; expect to
 * tune on device).
 */
class IconRenderer(
    private val context: Context,
    private val resolver: IconLayerResolver = IconLayerResolver(),
) {
    /** Keeps a layer's pixels only where the shape silhouette is opaque. */
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * Renders the visible layers of [layerSet] for [icon] into one `sizePx` × `sizePx` bitmap.
     *
     * @param packImage this app's artwork from an installed icon pack, pre-bound to the component by the caller —
     *   this class draws pixels and has no business knowing which app they belong to. Defaults to nothing, which
     *   is what a recipe with no pack layer needs and what the harness passes.
     */
    fun render(
        icon: ParsedIcon,
        layerSet: IconLayerSet,
        sizePx: Int,
        packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
    ): Bitmap {
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)

        resolver.resolve(layerSet, icon, ::decodeCustomImage, packImage).forEach { layer ->
            val layerBitmap = renderLayer(layer, sizePx)
            // Opacity, blend and color are applied **as the layer joins the stack**, not while its content is
            // drawn — which is what makes a blend mode mean "against everything beneath" rather than "against the
            // one bitmap I am in". The live path composes the same three into one paint for the same reason.
            canvas.drawBitmap(layerBitmap, 0f, 0f, compositePaint(layer.spec))
            layerBitmap.recycle()
        }
        return output
    }

    /** Alpha, blend mode and color matrix for one layer, or `null` when it composites plainly. */
    private fun compositePaint(spec: IconLayerSpec): Paint? {
        val matrix = LayerFilter.colorMatrixOf(spec.color)
        val mode = spec.blend.porterDuff()
        if (spec.opacity == 1f && mode == null && matrix == null) return null

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (spec.opacity.coerceIn(0f, 1f) * 255).toInt()
            mode?.let { xfermode = PorterDuffXfermode(it) }
            matrix?.let { colorFilter = ColorMatrixColorFilter(ColorMatrix(it)) }
        }
    }

    /** Draws one resolved layer (content + transform, then its shape mask) into its own bitmap. */
    private fun renderLayer(layer: ResolvedLayer, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        canvas.withMatrix(LayerTransform.of(layer.spec, sizePx).toMatrix(sizePx)) {
            drawContent(canvas, layer.content, sizePx)
        }

        // Masked after the matrix is restored, because where the silhouette goes is [ShapeMask]'s answer and not
        // the content's: anchored to the box it stays put while the content moves under it, anchored to the content
        // it is handed that same transform to go through.
        layer.spec.shape?.let {
            applyShapeMask(canvas, it, sizePx, ShapeMask.matrixOf(layer.spec, layer.content, sizePx))
        }
        // After the mask, so a gradient colors the shaped silhouette rather than the square it was cut from.
        layer.spec.gradient?.let { applyGradient(canvas, it, sizePx) }
        return bitmap
    }

    /** Paints [gradient] over the layer, clipped to what the layer has already drawn. */
    private fun applyGradient(canvas: Canvas, gradient: LayerEffect.Gradient, sizePx: Int) {
        val (x0, y0, x1, y1) = LayerGradient.endpoints(gradient.angleDegrees, sizePx).toList()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x0, y0, x1, y1, gradient.startArgb, gradient.endArgb, Shader.TileMode.CLAMP)
            // SRC_ATOP is what makes this an overlay rather than a rectangle: it keeps the layer's own alpha, so
            // the gradient colors the artwork and stops at its edge.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            alpha = (gradient.strength.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    }

    private fun drawContent(canvas: Canvas, content: ParsedLayer, sizePx: Int) {
        when (content) {
            // A flat fill covers the whole box; the transform is a no-op for it, the shape mask trims it.
            is ParsedLayer.Color -> canvas.drawColor(content.argb)
            is ParsedLayer.Image -> content.drawable.apply {
                setBounds(0, 0, sizePx, sizePx)
                draw(canvas)
            }
        }
    }

    /**
     * Cuts the layer down to [shape]'s silhouette, drawn under [matrix] — `null` meaning plainly at box size.
     *
     * The bounds are always the full box: [matrix] is what places the silhouette, so the drawable is asked for its
     * authoring square either way and the anchor is expressed in one place rather than two.
     */
    private fun applyShapeMask(canvas: Canvas, shape: IconShape, sizePx: Int, matrix: Matrix?) {
        val res = IconShapes.drawableResOrNull(shape) ?: return
        val shapeDrawable = context.getDrawable(res) ?: return
        val mask = createBitmap(sizePx, sizePx)
        Canvas(mask).let { maskCanvas ->
            shapeDrawable.setBounds(0, 0, sizePx, sizePx)
            if (matrix == null) shapeDrawable.draw(maskCanvas)
            else maskCanvas.withMatrix(matrix) { shapeDrawable.draw(this) }
        }
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
        mask.recycle()
    }

    private fun decodeCustomImage(path: String): Drawable? =
        BitmapFactory.decodeFile(path)?.toDrawable(context.resources)

    /**
     * The Porter-Duff mode for [LayerBlend], or `null` for [LayerBlend.NORMAL] — which is source-over, i.e. what a
     * paint with no xfermode already does.
     */
    private fun LayerBlend.porterDuff(): PorterDuff.Mode? = when (this) {
        LayerBlend.NORMAL -> null
        LayerBlend.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        LayerBlend.SCREEN -> PorterDuff.Mode.SCREEN
        LayerBlend.OVERLAY -> PorterDuff.Mode.OVERLAY
        LayerBlend.DARKEN -> PorterDuff.Mode.DARKEN
        LayerBlend.LIGHTEN -> PorterDuff.Mode.LIGHTEN
    }
}
