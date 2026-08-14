package inkspire.morphic.core.icon.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.icon.LayerEffect

/**
 * How big a pattern's tile is, which way the tiling runs, and what one tile looks like.
 *
 * The seventh thing the two render paths share, for the six others' reason — an icon whose texture is a different
 * size in the editor than on the home screen is the bug the editor structurally cannot show you. Here the risk is
 * sharper than usual, because a tiled shader has *three* things to agree about and each is invisible on its own: the
 * tile's pixel size, the matrix that turns it, and how the stencil becomes coloured marks.
 *
 * **[tile] returns a bitmap rather than a shader**, because that is the last point the two paths can share: one
 * wraps it in a `BitmapShader`, the other in Compose's `ImageShader`, and those are different types with the same
 * contents. Everything up to that point — rasterising the stencil, tinting it, inverting it — happens once, here.
 */
object LayerPattern {

    /**
     * One tile's side in pixels, for [scale] over a box of [sizePx].
     *
     * Floored at [MinTilePx] because a shader tiling a bitmap one pixel across is a flat wash that costs a texture:
     * the arithmetic allows it, a slider dragged to its end reaches it, and the result looks like the effect is
     * broken rather than small.
     */
    fun tileSizePx(scale: Float, sizePx: Int): Int =
        (scale * sizePx).toInt().coerceAtLeast(MinTilePx)

    /**
     * The shader's local matrix for [angleDegrees] over a box of [sizePx], or `null` when the tiling is square-on —
     * which is the common case and lets a caller skip the work.
     *
     * **About the box's centre**, like every other rotation here, so turning a pattern spins it in place instead of
     * sweeping it across the icon.
     */
    fun localMatrix(angleDegrees: Float, sizePx: Int): Matrix? {
        if (angleDegrees % FullTurn == 0f) return null
        val center = sizePx / 2f
        return Matrix().apply { setRotate(angleDegrees, center, center) }
    }

    /**
     * One tile of [pattern], drawn from [drawable] at [sizePx] square.
     *
     * Two ways round, and the stencil is what makes both cheap. Normally the drawable's alpha *is* the marks, so a
     * `SRC_IN` colour filter paints them without touching the ground. Inverted, the tile is filled and the marks are
     * punched back out with `DST_OUT` — the negative, reached without a second asset or any path arithmetic.
     *
     * The scratch bitmap is unavoidable: a `Drawable` paints with its own paints, so it cannot be handed an xfermode
     * and has to be rasterised before it can be composited. Same reason `ShapeMask` builds its mask into one.
     */
    fun tile(drawable: Drawable, pattern: LayerEffect.Pattern, sizePx: Int): Bitmap {
        val marks = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(marks))

        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (pattern.invert) {
            canvas.drawColor(pattern.argb)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        } else {
            paint.colorFilter = PorterDuffColorFilter(pattern.argb, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(marks, 0f, 0f, paint)
        marks.recycle()
        return out
    }

    /** Small enough to be a texture, large enough that the marks in it survive being rasterised. */
    private const val MinTilePx = 4

    private const val FullTurn = 360f
}
