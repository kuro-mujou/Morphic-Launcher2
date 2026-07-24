package inkspire.morphic.core.icon.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.icon.IconShape
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.icon.layer.IconLayerSet
import inkspire.morphic.core.icon.layer.IconLayerSpec
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withMatrix

/**
 * Composites an [IconLayerSet] + the app's [ParsedIcon] into one square [Bitmap] of `sizePx` — the baked icon
 * shown on the home screen and other surfaces.
 *
 * Each layer is drawn into its own bitmap (so a per-layer shape mask, and later per-layer effects, can be
 * applied in isolation), then stacked bottom→top onto the output. Compositing is **synchronous and CPU/heavy**
 * (bitmap allocation + drawing); callers run it off the main thread and cache the result by `IconId`.
 *
 * Not done yet, both deferred with reason: per-layer **effects** (the effect bag is still empty), and any
 * adaptive-layer overshoot scaling (`AppDefault` layers draw to the full box for now — expect to tune the fit
 * on device).
 */
class IconRenderer(
    private val context: Context,
    private val resolver: IconLayerResolver = IconLayerResolver(),
) {
    /** Keeps a layer's pixels only where the shape silhouette is opaque. */
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /** Renders the visible layers of [layerSet] for [icon] into one `sizePx` × `sizePx` bitmap. */
    fun render(icon: ParsedIcon, layerSet: IconLayerSet, sizePx: Int): Bitmap {
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)

        resolver.resolve(layerSet, icon, ::decodeCustomImage).forEach { layer ->
            val layerBitmap = renderLayer(layer, sizePx)
            canvas.drawBitmap(layerBitmap, 0f, 0f, null)
            layerBitmap.recycle()
        }
        return output
    }

    /** Draws one resolved layer (content + transform, then its shape mask) into its own bitmap. */
    private fun renderLayer(layer: ResolvedLayer, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        canvas.withMatrix(layerTransform(layer.spec, sizePx)) {
            drawContent(canvas, layer.content, sizePx)
        }

        // Shape is fixed in the box (it does not move with the content), so mask after restoring the matrix.
        layer.spec.shape?.let { applyShapeMask(canvas, it, sizePx) }
        return bitmap
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

    private fun applyShapeMask(canvas: Canvas, shape: IconShape, sizePx: Int) {
        val res = IconShapes.drawableResOrNull(shape) ?: return
        val shapeDrawable = context.getDrawable(res) ?: return
        val mask = createBitmap(sizePx, sizePx)
        Canvas(mask).let { maskCanvas ->
            shapeDrawable.setBounds(0, 0, sizePx, sizePx)
            shapeDrawable.draw(maskCanvas)
        }
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
        mask.recycle()
    }

    /** offset (fraction of the box) · zoom (about centre) · rotation (degrees, about centre). */
    private fun layerTransform(spec: IconLayerSpec, sizePx: Int): Matrix {
        val center = sizePx / 2f
        return Matrix().apply {
            postScale(spec.zoom, spec.zoom, center, center)
            postRotate(spec.rotation, center, center)
            postTranslate(spec.offsetX * sizePx, spec.offsetY * sizePx)
        }
    }

    private fun decodeCustomImage(path: String): Drawable? =
        BitmapFactory.decodeFile(path)?.toDrawable(context.resources)
}
