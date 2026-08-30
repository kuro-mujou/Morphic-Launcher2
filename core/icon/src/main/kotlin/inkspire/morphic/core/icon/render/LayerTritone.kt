package inkspire.morphic.core.icon.render

/**
 * A tritone's machinery: the ramp its three colors build, how a pixel's tone is read, and how the mapped color is
 * laid back on — everything but the loop that walks the pixels.
 *
 * [LayerDither]'s and [LayerPixelate]'s reason: only the bake draws a tritone, so nothing competes with this, and it
 * is pulled out of `IconRenderer` because the parts that are *silently* wrong live here. A ramp built the wrong way
 * round maps shadows to the highlight color — a plausible, upside-down grade; a luminance weighted wrongly tilts
 * which parts of the artwork read as light. Neither throws.
 *
 * **The perceptual cost is paid once, not per pixel.** [ramp] interpolates in OKLab through [Oklab], which is where
 * the muddy midpoint is fixed — but it runs 256 times per bake to build a lookup table, and the per-pixel [apply]
 * then only reads a cheap luminance and indexes it. So the whole icon is graded for the price of one small ramp.
 */
object LayerTritone {

    /**
     * A 256-entry sRGB lookup table: index by a pixel's `0..255` luminance, read the color that tone maps to.
     *
     * Two segments meeting at the mid color halfway up — shadows through [midArgb] to highlights — each interpolated
     * in OKLab so the joins are real colors rather than sRGB's gray dip. The entries are opaque; the pixel's own
     * alpha is applied in [apply].
     */
    fun ramp(shadowArgb: Int, midArgb: Int, highlightArgb: Int): IntArray =
        IntArray(RampSize) { i ->
            val tone = i / MaxIndex
            if (tone <= MidTone) {
                Oklab.mix(shadowArgb, midArgb, tone / MidTone)
            } else {
                Oklab.mix(midArgb, highlightArgb, (tone - MidTone) / MidTone)
            }
        }

    /**
     * A pixel's perceived lightness, `0..255` — the tone that indexes [ramp].
     *
     * Rec. 709 weights on the gamma sRGB channels, which is what image tools call "luminosity": green carries most
     * of the sense of brightness, blue almost none. Read on the stored bytes rather than in linear light on purpose
     * — the grade is placed against how light each part *looks*, which is the gamma value, and it keeps the per-pixel
     * cost to a weighted sum.
     */
    fun luminance(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (RedLuma * r + GreenLuma * g + BlueLuma * b).toInt().coerceIn(0, ChannelMax)
    }

    /**
     * [argb] recolored by the [ramp], taken [strength] of the way from the original — and **keeping its own alpha**,
     * so the silhouette is untouched and only the color is graded.
     *
     * A fully transparent pixel is returned unchanged: it has no tone worth reading and no color worth showing.
     */
    fun apply(argb: Int, ramp: IntArray, strength: Float): Int {
        val alpha = argb ushr 24 and 0xFF
        if (alpha == 0) return argb

        val mapped = ramp[luminance(argb)]
        val mix = strength.coerceIn(0f, 1f)

        val r = blend(argb shr 16 and 0xFF, mapped shr 16 and 0xFF, mix)
        val g = blend(argb shr 8 and 0xFF, mapped shr 8 and 0xFF, mix)
        val b = blend(argb and 0xFF, mapped and 0xFF, mix)
        val packed = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }

    /** [from] moved [t] of the way to [to], as a channel byte. */
    private fun blend(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, ChannelMax)

    private const val RedLuma = 0.2126f
    private const val GreenLuma = 0.7152f
    private const val BlueLuma = 0.0722f

    /** An 8-bit tone ramp: 256 entries indexed by luminance, whose top index is 255. */
    private const val RampSize = 256
    private const val MaxIndex = 255f
    private const val ChannelMax = 255

    /** The tone the mid color sits at — halfway, where the two ramp segments meet. */
    private const val MidTone = 0.5f
}
