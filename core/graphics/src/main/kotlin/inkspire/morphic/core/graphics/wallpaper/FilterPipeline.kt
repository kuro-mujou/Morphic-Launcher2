package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import inkspire.morphic.core.graphics.BitmapBlur
import inkspire.morphic.core.model.wallpaper.WallpaperFilter
import kotlin.math.hypot
import kotlin.math.min
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
 * **The blur is measured in fractions of the frame; the grain and the scanlines are measured in pixels, and stay
 * that way.** A texture whose whole nature is one-pixel speckle or a one-pixel rule has no smaller form to scale to,
 * so on the studio's downscaled draft both read *coarser* than they will on the wallpaper — grain as blotches, the
 * scanline pitch as wider banding. That is a draft telling the truth about which filters are on and lying about how
 * fine they are, which is the trade the draft exists to make; the settled pass is what shows them at their real size.
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
            BitmapBlur.blurred(bitmap, blurRadiusPx(blur, bitmap.width, bitmap.height))
        } else {
            bitmap
        }

        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        (filters[WallpaperFilter.VIGNETTE] ?: 0f).takeIf { it > 0f }?.let { vignette(pixels, width, height, it) }
        (filters[WallpaperFilter.SCANLINES] ?: 0f).takeIf { it > 0f }?.let { scanlines(pixels, width, it) }
        // Before the grain, which is meant to sit on top of the finished colors rather than be graded with them.
        (filters[WallpaperFilter.VIBRANCE] ?: 0f).takeIf { it > 0f }?.let { vibrance(pixels, it) }
        (filters[WallpaperFilter.GRAIN] ?: 0f).takeIf { it > 0f }?.let { grain(pixels, it) }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * How far to blur at [strength], for an image `[width] × [height]` — **a fraction of its short side, not a count
     * of its pixels.**
     *
     * A recipe is a description of a picture rather than of a bitmap, so every quantity in it that has a size has to
     * be relative to the frame or the same recipe means two different pictures at two resolutions. That is not
     * hypothetical: the studio previews a draft at a fraction of the screen's pixels and applies the full-size render,
     * and a radius fixed in pixels made the draft several times blurrier than the wallpaper it was previewing — the
     * whole picture a mush that resolved to something else the moment the finger stopped. A shared recipe landing on
     * a screen of another size is the other half of the same bug.
     *
     * [MaxBlurRadiusFraction] is the 60 pixels this was, at the 1080-wide phone that number was picked on, so the
     * wallpaper a recipe produces there is unchanged. A radius is at least one pixel, since below that there is
     * nothing to average and [BitmapBlur] would copy the image for no reason.
     */
    internal fun blurRadiusPx(strength: Float, width: Int, height: Int): Int =
        (strength * MaxBlurRadiusFraction * min(width, height)).roundToInt().coerceAtLeast(1)

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

    /**
     * Every pixel pushed away from its own grey and lifted, at [strength] of full — the reference's *Vibrancy*.
     *
     * **Two moves, because measuring theirs found two.** Across its slider the mean saturation went `0.61 → 0.84` and
     * the mean value `0.54 → 0.75`, so it is not the pure saturation boost the name suggests: a picture graded only in
     * saturation gets more colorful and no brighter, and theirs plainly does both. [SaturationGain] and [ValueGain]
     * are those two measurements.
     *
     * **Pushed from the pixel's own luminance rather than from mid-grey**, so a dark color deepens and a light one
     * brightens instead of every pixel being dragged toward the middle of the range.
     */
    internal fun vibrance(pixels: IntArray, strength: Float) {
        val saturation = 1f + strength.coerceIn(0f, 1f) * SaturationGain
        val value = 1f + strength.coerceIn(0f, 1f) * ValueGain
        for (i in pixels.indices) {
            val argb = pixels[i]
            val r = argb shr RedShift and 0xFF
            val g = argb shr GreenShift and 0xFF
            val b = argb and 0xFF
            val grey = (r * RedLuma + g * GreenLuma + b * BlueLuma)
            pixels[i] = (argb and AlphaMask) or
                (graded(r, grey, saturation, value) shl RedShift) or
                (graded(g, grey, saturation, value) shl GreenShift) or
                graded(b, grey, saturation, value)
        }
    }

    /** One channel pushed [saturation] away from its pixel's [grey] and scaled by [value], clamped to a byte. */
    private fun graded(channel: Int, grey: Float, saturation: Float, value: Float): Int =
        ((grey + (channel - grey) * saturation) * value).roundToInt().coerceIn(0, ChannelMax)

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

    /** How much further from its own grey a color is pushed at full vibrance — measured off the reference. */
    private const val SaturationGain = 0.8f

    /** ... and how much brighter it is made, which the same measurement showed it also does. */
    private const val ValueGain = 0.4f

    /** Rec. 601 luma weights — the grey a pixel is pushed away from. */
    private const val RedLuma = 0.299f
    private const val GreenLuma = 0.587f
    private const val BlueLuma = 0.114f

    /** The alpha byte, carried through every grade unchanged. */
    private const val AlphaMask = 0xFF000000.toInt()

    /** A stable `0..1` hash of [i] — a cheap integer scramble, enough for grain. */
    private fun hash(i: Int): Float {
        var h = i * HashMultiplier
        h = h xor (h ushr HashShift)
        return (h and HashMask) / HashMask.toFloat()
    }

    private const val ChannelMax = 255
    private const val RedShift = 16
    private const val GreenShift = 8
    /**
     * The widest blur, as a fraction of the image's short side — 60px on the 1080-wide phone the number was picked
     * on. See [blurRadiusPx] for why it is a fraction at all.
     */
    private const val MaxBlurRadiusFraction = 60f / 1080f
    private const val ScanlineDepth = 0.35f
    private const val GrainAmplitude = 40f
    private const val HashMultiplier = -1640531527 // 0x9E3779B9, the golden-ratio scramble
    private const val HashShift = 15
    private const val HashMask = 0xFFFF
}
