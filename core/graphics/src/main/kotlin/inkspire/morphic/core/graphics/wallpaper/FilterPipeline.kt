package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import inkspire.morphic.core.graphics.BitmapBlur
import inkspire.morphic.core.model.wallpaper.WallpaperFilter
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The post-process stage: a generator's bitmap and the recipe's filter strengths in, a filtered bitmap out — the
 * studio's *Filters*.
 *
 * **A fixed set of whole-image passes, drawn here rather than borrowed from the icon studio.** The plan's thesis is
 * that this reuses the icon effect helpers, but `core:graphics` cannot reach `core:icon` (the dependency runs the
 * other way, for `BitmapBlur`). So the passes are written fresh: the blur *is* `BitmapBlur`, the rest are small
 * `IntArray` loops. Unifying the two studios' per-pixel math is a real refactor left for later; the pixel arithmetic
 * that is silently wrong lives in the tested helpers below meanwhile.
 *
 * **Applied in a fixed order, not the map's.** Blur first, so a softened image is what the later passes texture;
 * then vignette and scanlines, then grain **last** so its speckle stays sharp rather than being blurred away. The
 * whole stage is skipped when nothing is turned on.
 *
 * The per-pixel passes mutate the working bitmap in place; only the blur allocates, since it needs a second buffer.
 */
object FilterPipeline {

    /**
     * [bitmap] with [filters] applied — a new bitmap when a blur allocates one, otherwise [bitmap] itself, mutated.
     *
     * The caller hands over a freshly generated bitmap it does not keep, so mutating it in place is safe and saves a
     * copy on the common no-blur path.
     */
    fun apply(bitmap: Bitmap, filters: Map<WallpaperFilter, Float>): Bitmap {
        if (filters.values.none { it > 0f }) return bitmap

        val blur = filters[WallpaperFilter.BLUR] ?: 0f
        val result = if (blur > 0f) {
            BitmapBlur.blurred(bitmap, (blur * MaxBlurRadiusPx).roundToInt().coerceAtLeast(1))
        } else {
            bitmap
        }

        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        (filters[WallpaperFilter.VIGNETTE] ?: 0f).takeIf { it > 0f }?.let { vignette(pixels, width, height, it) }
        (filters[WallpaperFilter.SCANLINES] ?: 0f).takeIf { it > 0f }?.let { scanlines(pixels, width, it) }
        (filters[WallpaperFilter.GRAIN] ?: 0f).takeIf { it > 0f }?.let { grain(pixels, it) }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * The corners weighted down — each pixel scaled toward black by how far it is from the center, squared, so the
     * darkening gathers at the corners and leaves the middle clear.
     */
    internal fun vignette(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val maxDistance = hypot(centerX, centerY)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val distance = hypot(x - centerX, y - centerY) / maxDistance
                val factor = (1f - strength * distance * distance).coerceIn(0f, 1f)
                val at = y * width + x
                pixels[at] = scale(pixels[at], factor)
            }
        }
    }

    /** Every other row darkened — the faint horizontal banding of a CRT, at [strength] of full. */
    internal fun scanlines(pixels: IntArray, width: Int, strength: Float) {
        val factor = 1f - strength * ScanlineDepth
        var row = 0
        while (row * width < pixels.size) {
            if (row % 2 == 0) {
                val start = row * width
                for (at in start until start + width) pixels[at] = scale(pixels[at], factor)
            }
            row++
        }
    }

    /** Fine per-pixel noise — a stable hash of each pixel's index, so the grain does not shimmer between renders. */
    internal fun grain(pixels: IntArray, strength: Float) {
        val amplitude = strength * GrainAmplitude
        for (at in pixels.indices) {
            val noise = (hash(at) - 0.5f) * 2f * amplitude
            pixels[at] = shift(pixels[at], noise.roundToInt())
        }
    }

    /** [argb] with its color channels scaled by [factor], alpha kept. */
    private fun scale(argb: Int, factor: Float): Int {
        val a = argb and 0xFF000000.toInt()
        val r = ((argb shr RedShift and 0xFF) * factor).toInt().coerceIn(0, ChannelMax)
        val g = ((argb shr GreenShift and 0xFF) * factor).toInt().coerceIn(0, ChannelMax)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, ChannelMax)
        return a or (r shl RedShift) or (g shl GreenShift) or b
    }

    /** [argb] with [delta] added to each color channel, clamped, alpha kept. */
    private fun shift(argb: Int, delta: Int): Int {
        val a = argb and 0xFF000000.toInt()
        val r = ((argb shr RedShift and 0xFF) + delta).coerceIn(0, ChannelMax)
        val g = ((argb shr GreenShift and 0xFF) + delta).coerceIn(0, ChannelMax)
        val b = ((argb and 0xFF) + delta).coerceIn(0, ChannelMax)
        return a or (r shl RedShift) or (g shl GreenShift) or b
    }

    /** A stable `0..1` hash of [i] — a cheap integer scramble, enough for grain. */
    private fun hash(i: Int): Float {
        var h = i * HashMultiplier
        h = h xor (h ushr HashShift)
        return (h and HashMask) / HashMask.toFloat()
    }

    private const val ChannelMax = 255
    private const val RedShift = 16
    private const val GreenShift = 8
    private const val MaxBlurRadiusPx = 60f
    private const val ScanlineDepth = 0.35f
    private const val GrainAmplitude = 40f
    private const val HashMultiplier = -1640531527 // 0x9E3779B9, the golden-ratio scramble
    private const val HashShift = 15
    private const val HashMask = 0xFFFF
}
