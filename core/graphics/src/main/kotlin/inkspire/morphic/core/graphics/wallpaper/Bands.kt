package inkspire.morphic.core.graphics.wallpaper

import kotlin.random.Random

/**
 * Splits `0..1` into variable-width bands — the shared derivation behind the banded staples ([DiagonalBandsGenerator]'s
 * stripes, [GradientColumnsGenerator]'s columns).
 *
 * **One place for the width arithmetic, because it is silently wrong when it is wrong.** Both designs project a pixel to
 * one axis and quantize it into bands whose widths the irregularity knob jitters; a set of widths that sums to anything
 * but 1, or a boundary search off by one, drops or doubles a band without a crash. Extracted here on the second consumer
 * so the two cannot drift, and tested without a bitmap.
 */
object Bands {

    /**
     * The internal edges of [count] bands spanning `0..1`, jittered by [irregularity] — a sorted `FloatArray` of the
     * `count - 1` boundaries between bands. At `irregularity = 0` the bands are equal width (`i / count`); climbing it
     * lets each band's width drift by up to [WidthVar], the widths renormalized to still fill exactly `0..1` so no band
     * spills off the axis.
     */
    fun boundaries(count: Int, irregularity: Float, seed: Long): FloatArray {
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
    fun bandAt(t: Float, boundaries: FloatArray): Int {
        var band = 0
        while (band < boundaries.size && t >= boundaries[band]) band++
        return band
    }

    /** How far a band's width may drift from even at full irregularity, as a fraction of the even width. */
    private const val WidthVar = 0.8f
}
