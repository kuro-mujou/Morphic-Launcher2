package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pixel loop behind every field design, and the downscaled scrub it shares with the bake — [MeshGradientGenerator]
 * and [PlasmaGenerator].
 *
 * **One loop, two entry points.** The bake runs it straight into the frame; a scrub runs it into a buffer a fraction
 * the size and lets the canvas blow that up. A field evaluated two ways is the two-renderer hazard and a quiet one,
 * since the two would agree at every setting anyone checked — so both designs call this rather than each writing both.
 *
 * **A buffer pixel is evaluated where it lands, not where its index says.** The blit puts buffer pixel `x`'s center at
 * `(x + ½) · frame / buffer` of the frame's pixels, and the field is read at that point; reading it at `x / (buffer − 1)`
 * — the frame's own formula applied to the buffer — stretches the field by up to half a buffer pixel toward each edge,
 * which at a tenth of the size is five screen pixels. That shows as levels of error wherever the colors are steep and
 * nowhere else, which is how it went unseen on the mesh gradient's gentle ones and was measured on the plasma's. At
 * full size the two formulas are the same one, which is what keeps the bake exactly the render it always was.
 */
internal object FieldRaster {

    /**
     * Fills [bitmap] from [color], which is handed each pixel's position as shares of a `[frameWidth]` × `[frameHeight]`
     * frame — `0` at the first pixel's center and `1` at the last one's, as the field designs have always measured.
     */
    inline fun paint(bitmap: Bitmap, frameWidth: Int, frameHeight: Int, color: (u: Float, v: Float) -> Int) {
        val width = bitmap.width
        val height = bitmap.height
        val us = FloatArray(width) { share(it, width, frameWidth) }
        val row = IntArray(width)
        for (y in 0 until height) {
            val v = share(y, height, frameHeight)
            for (x in 0 until width) row[x] = color(us[x], v)
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
    }

    /**
     * Paints [color] into [canvas] at `[width]` × `[height]`, evaluated on a buffer whose short side is [shortSide]
     * and blown up with [Blit]. At a [shortSide] of the frame's own, the buffer *is* the frame and the blit a copy.
     */
    inline fun draw(canvas: Canvas, width: Int, height: Int, shortSide: Int, color: (u: Float, v: Float) -> Int) {
        val frame = min(width, height)
        val scale = if (frame <= 0) 1f else (shortSide.toFloat() / frame).coerceAtMost(1f)
        val buffer = createBitmap(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
        )
        paint(buffer, width, height, color)
        canvas.drawBitmap(buffer, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Blit)
        buffer.recycle()
    }

    /**
     * Where pixel [index] of a run of [buffer] pixels lands along a run of [frame] pixels, as a share of it.
     *
     * The frame's own formula, `index / (frame − 1)`, when the two are the same run; otherwise the landing point of the
     * pixel's center, measured on the frame. A frame of one pixel reads its middle.
     */
    fun share(index: Int, buffer: Int, frame: Int): Float = when {
        frame <= 1 -> Middle
        buffer == frame -> index.toFloat() / (frame - 1)
        else -> ((index + Middle) * frame / buffer - Middle) / (frame - 1)
    }

    /**
     * What a buffer is blown back up with — bilinear, which for a field is exact rather than merely tidy.
     *
     * Nearest-neighbor would show the buffer's own pixel grid, which is the one artifact that would make a scrub look
     * like a *preview* of the picture rather than the picture. There is no dithering to preserve and no edge to keep
     * crisp: every gradient here is continuous, so the filter is reconstructing the field rather than guessing.
     */
    val Blit = Paint(Paint.FILTER_BITMAP_FLAG)

    private const val Middle = 0.5f
}
