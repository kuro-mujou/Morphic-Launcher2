package inkspire.morphic.core.graphics

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A real blur over a bitmap's pixels — three separable box passes, which is a close approximation of a gaussian.
 *
 * **Its own module because both halves of the launcher blur, and they were doing it differently.** `data:wallpaper`
 * had this arithmetic and `core:icon` could not reach it — a `core` module cannot depend on a `data` one — so the icon
 * renderer approximated a blur by **scaling a bitmap down and back up** with bilinear filtering instead. That is not
 * the same operation and it does not look like one: bilinear *downscaling* samples a 2×2 neighbourhood per output
 * pixel, so reducing an icon by 30× discards almost every pixel it is supposed to be averaging. What came out was
 * aliased stair-stepping along every edge and the tent-shaped blocks of the upscale — visibly terraced, not soft.
 * Its own KDoc claimed the two scales were "the platform doing the same averaging in two calls"; they are not.
 *
 * So the kernel lives here, where both can have it and neither owns it.
 *
 * ### Why three passes
 *
 * One box pass is a flat average and looks like a smear. Three convolved boxes are within a couple of percent of a
 * true gaussian, which is the standard result and the reason this number is not a taste value. Each pass is a
 * **sliding window** — one add and one subtract per pixel — so the whole thing is O(pixels) and *independent of the
 * radius*. A wide blur costs exactly what a narrow one does, which is what makes blurring at full resolution
 * affordable and the downscale-first trick unnecessary.
 *
 * ### Why premultiplied
 *
 * **The one thing the wallpaper's version did not have to care about and an icon cannot do without.** `getPixels`
 * hands back *un*premultiplied ARGB, where a fully transparent pixel is almost always transparent **black**.
 * Averaging those channels directly pulls black into every pixel near an edge, so a blurred icon gains a dark fringe
 * that reads as a rendering fault — the same trap `LayerPixelate.averageArgb` documents for cell averaging, one
 * operation over. Premultiplying first weights each colour by its own coverage, which is what makes the average
 * mean anything; the alpha channel is blurred alongside and the result is divided back out.
 *
 * A wallpaper is opaque, so this costs it nothing and it is not two code paths.
 */
object BitmapBlur {

    /**
     * [source] blurred by [radiusPx], as a new bitmap. A radius below one is nothing to do, and is returned as a
     * plain copy so the caller can treat the result as owned either way.
     */
    fun blurred(source: Bitmap, radiusPx: Int, passes: Int = DEFAULT_PASSES): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        blur(pixels, width, height, radiusPx, passes)

        return createBitmap(width, height).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Blurs [pixels] in place — un-premultiplied ARGB in, un-premultiplied ARGB out, which is the form
     * `Bitmap.getPixels` and `setPixels` both speak.
     *
     * Public alongside [blurred] because a caller that already holds an array should not have to round-trip through a
     * bitmap to use this, and because it is the form the tests can reach.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radiusPx: Int, passes: Int = DEFAULT_PASSES) {
        if (radiusPx < 1 || width < 1 || height < 1 || passes < 1) return

        premultiply(pixels)
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, scratch, width, height, radiusPx, horizontal = true)
            pass(scratch, pixels, width, height, radiusPx, horizontal = false)
        }
        unpremultiply(pixels)
    }

    /**
     * The box radius whose [DEFAULT_PASSES] passes approximate a gaussian of [sigmaPx].
     *
     * Convolving `n` boxes of radius `r` gives a variance of `n·((2r+1)² − 1)/12`; solving that for `r` is the whole
     * of this. It exists so a caller can describe a blur in the one unit that means something to a person looking at
     * the screen — how far a pixel's influence spreads — rather than in the kernel's own units.
     */
    fun boxRadiusFor(sigmaPx: Float, passes: Int = DEFAULT_PASSES): Int {
        if (sigmaPx <= 0f || passes < 1) return 0
        val variance = sigmaPx * sigmaPx / passes
        return ((sqrt(12f * variance + 1f) - 1f) / 2f).roundToInt().coerceAtLeast(1)
    }

    /**
     * How far a bitmap may be reduced before a blur of [radiusPx] is applied to it, 1 meaning not at all.
     *
     * **A blur destroys detail, so it is free to be computed on less of it — but only in proportion.** Reducing by
     * more than the radius can absorb is what turns a soft picture into a blocky one, and reducing by a *constant*
     * is what made the wallpaper backdrop look like a low-resolution copy of itself at every strength, including the
     * strengths where no blur was asked for at all.
     *
     * [MIN_RADIUS_AFTER_DOWNSCALE] is what is kept: enough of a radius on the reduced bitmap that the box passes are
     * still doing the smoothing rather than the upscale.
     */
    fun downscaleFor(radiusPx: Float, max: Int = MAX_DOWNSCALE): Int =
        (radiusPx / MIN_RADIUS_AFTER_DOWNSCALE).toInt().coerceIn(1, max.coerceAtLeast(1))

    /** Three boxes land within a couple of percent of a gaussian; a fourth is not visible and costs a third more. */
    const val DEFAULT_PASSES = 3

    /**
     * The smallest blur radius worth leaving on a reduced bitmap — below this the reduction is doing the work and its
     * artifacts are what show.
     */
    private const val MIN_RADIUS_AFTER_DOWNSCALE = 3f

    /** Past an eighth there is nothing recognisable left to blur, however wide the radius. */
    private const val MAX_DOWNSCALE = 8

    /** Colours weighted by their own coverage, so an average over a transparent edge means something. */
    private fun premultiply(pixels: IntArray) {
        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = argb ushr 24
            if (a == 0xFF) continue
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val r = (argb shr 16 and 0xFF) * a / 0xFF
            val g = (argb shr 8 and 0xFF) * a / 0xFF
            val b = (argb and 0xFF) * a / 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** The inverse, applied once at the end. A pixel the blur left fully transparent has no colour to recover. */
    private fun unpremultiply(pixels: IntArray) {
        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = argb ushr 24
            if (a == 0xFF) continue
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val r = ((argb shr 16 and 0xFF) * 0xFF / a).coerceAtMost(0xFF)
            val g = ((argb shr 8 and 0xFF) * 0xFF / a).coerceAtMost(0xFF)
            val b = ((argb and 0xFF) * 0xFF / a).coerceAtMost(0xFF)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /**
     * One separable box pass over [src] into [dst], along rows when [horizontal] and down columns otherwise.
     *
     * A **sliding window**: each output adds the pixel entering and subtracts the one leaving, so the cost does not
     * follow the radius. `count` is tracked rather than assumed because the window is clipped at both ends — the
     * edges average only real pixels instead of repeating the border, which is what stops a bright edge smearing
     * inward along the frame.
     */
    private fun pass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val step = if (horizontal) 1 else width

        for (line in 0 until lines) {
            val base = if (horizontal) line * width else line
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            var count = 0

            for (j in 0..minOf(radius, length - 1)) {
                val p = src[base + j * step]
                sumA += p ushr 24
                sumR += p shr 16 and 0xFF
                sumG += p shr 8 and 0xFF
                sumB += p and 0xFF
                count++
            }

            for (i in 0 until length) {
                dst[base + i * step] =
                    ((sumA / count) shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)

                val leaving = i - radius
                if (leaving >= 0) {
                    val p = src[base + leaving * step]
                    sumA -= p ushr 24
                    sumR -= p shr 16 and 0xFF
                    sumG -= p shr 8 and 0xFF
                    sumB -= p and 0xFF
                    count--
                }
                val entering = i + radius + 1
                if (entering < length) {
                    val p = src[base + entering * step]
                    sumA += p ushr 24
                    sumR += p shr 16 and 0xFF
                    sumG += p shr 8 and 0xFF
                    sumB += p and 0xFF
                    count++
                }
            }
        }
    }
}
