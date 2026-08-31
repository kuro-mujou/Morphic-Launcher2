package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Parallel bands of flat palette color marching across the frame — *Diagonal Bands*, the calmest staple. gart's
 * `arts/stripes`.
 *
 * **The trivial-but-restrained design the loud generators are measured against.** Every pixel is projected onto one axis
 * and the position on that axis picks a band; the band picks a flat palette color, cycling. There is no noise, no
 * geometry, no overdraw — a stripe pattern — which is exactly why it reads as composed rather than busy, and why in
 * bichromatic it is a clean two-tone that the wallpaper's own content can sit on.
 *
 * **[DesignParams.variant] sets the direction** — `0` a `↘` diagonal (the default and the design's name), `1` a `↗`
 * diagonal, `2` vertical, `3` horizontal. [DesignParams.density] sets how many bands, and [DesignParams.irregularity]
 * how uneven their widths are — perfectly even at `0`, a jittered, hand-torn set at `1` (Smart Launcher's *Variation*).
 * Deterministic in [seed].
 *
 * [boundaries] and [bandAt] are pure and tested — variable-width banding is where a width that sums wrong or a boundary
 * search off by one drops or doubles a stripe, and it needs no bitmap.
 */
object DiagonalBandsGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val count = bandCount(params.density)
        val boundaries = boundaries(count, params.irregularity, seed)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                val band = bandAt(project(nx, ny, params.variant), boundaries)
                pixels[y * width + x] = palette.colorAt(band % palette.size)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many bands [density] asks for — [MinBands] a few bold stripes up to [MaxBands] a fine set. */
    internal fun bandCount(density: Float): Int =
        MinBands + (density.coerceIn(0f, 1f) * (MaxBands - MinBands)).roundToInt()

    /**
     * The pixel at ([nx], [ny]) projected onto the band axis for [variant], normalized to `0..1` — the position that
     * picks its band. `0` a `↘` diagonal, `1` a `↗` diagonal, `2` vertical (by `nx`), `3` horizontal (by `ny`); any
     * other index falls to the diagonal.
     */
    internal fun project(nx: Float, ny: Float, variant: Int): Float = when (variant) {
        VariantDiagonalUp -> (nx - ny + 1f) / 2f // ↗ : nx - ny ranges -1..1, shifted to 0..1
        VariantVertical -> nx
        VariantHorizontal -> ny
        else -> (nx + ny) / 2f // ↘ (variant 0, the default) : nx + ny ranges 0..2
    }

    /**
     * The internal edges of [count] bands spanning `0..1`, jittered by [irregularity] — a sorted `FloatArray` of the
     * `count - 1` boundaries between bands. At `irregularity = 0` the bands are equal width (`i / count`); climbing it
     * lets each band's width drift by up to [WidthVar], the widths renormalized to still fill exactly `0..1` so no band
     * spills off the frame.
     */
    internal fun boundaries(count: Int, irregularity: Float, seed: Long): FloatArray {
        if (count <= 1) return FloatArray(0)
        val amount = irregularity.coerceIn(0f, 1f)
        val random = Random(seed)
        val widths = FloatArray(count) { 1f + (random.nextFloat() * 2f - 1f) * amount * WidthVar }
        val total = widths.sum()
        val edges = FloatArray(count - 1)
        var cumulative = 0f
        for (i in 0 until count - 1) {
            cumulative += widths[i]
            edges[i] = cumulative / total
        }
        return edges
    }

    /** Which band the position [t] (`0..1`) falls in — the number of [boundaries] at or below it, so `0 until count`. */
    internal fun bandAt(t: Float, boundaries: FloatArray): Int {
        var band = 0
        while (band < boundaries.size && t >= boundaries[band]) band++
        return band
    }

    /** [DesignParams.variant] direction indices — `0` (the default) is the `↘` diagonal, handled by the `else` branch. */
    private const val VariantDiagonalUp = 1
    private const val VariantVertical = 2
    private const val VariantHorizontal = 3

    private const val MinBands = 4
    private const val MaxBands = 22

    /** How far a band's width may drift from even at full irregularity, as a fraction of the even width. */
    private const val WidthVar = 0.8f
}
