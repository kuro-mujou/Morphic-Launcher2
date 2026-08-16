package inkspire.morphic.core.icon.render

import kotlin.math.floor

/**
 * Reading a pixel from **between** pixels — what the two resampling effects do to every pixel they produce.
 *
 * **Pulled out of `IconRenderer.resample` because it is alpha arithmetic, which is the kind that is silently
 * wrong.** That is the same argument `LayerPixelate.averageArgb` makes and the same trap: an icon is mostly
 * transparent, and a transparent pixel is almost always transparent *black*, so blending four samples by their
 * colours alone drags every edge toward black. What comes out is a dark fringe around the artwork, which reads as a
 * rendering fault rather than as a blend done the naive way.
 *
 * **Why this exists at all**: both [LayerRipple] and [LayerGrain] displace by fractions of a pixel, and rounding
 * that displacement to a whole pixel throws the fraction away. The cost was worst exactly where the effects are
 * subtlest — a fine grain became hard aliased specks instead of dust, and a shallow ripple stepped rather than
 * flowed — because at small amplitudes *the whole displacement is the fraction*.
 */
internal object LayerSample {

    /**
     * The colour at ([x], [y]) in [pixels], a [size]×[size] row-major buffer, blended from the four pixels around it.
     *
     * **Outside the buffer reads as transparent, never clamped**, which is `IconRenderer.resample`'s own rule one
     * level down: clamping would smear the outermost row outward wherever a displacement reaches past the box, and
     * an icon genuinely *is* transparent out there. A sample straddling the edge therefore fades rather than
     * repeating, which is the honest picture and costs no special case — an absent neighbour simply weighs nothing.
     */
    fun bilinear(pixels: IntArray, size: Int, x: Float, y: Float): Int {
        val left = floor(x).toInt()
        val top = floor(y).toInt()
        val fracX = x - left
        val fracY = y - top

        // Premultiplied, which is the whole point: each sample's contribution to the colour is weighted by its own
        // alpha as well as by its distance, and the sum is divided by the alpha that accumulated. A fully
        // transparent neighbour then contributes nothing to the colour rather than contributing black to it.
        var alpha = 0f
        var red = 0f
        var green = 0f
        var blue = 0f

        for (corner in 0 until 4) {
            val sampleX = left + (corner and 1)
            val sampleY = top + (corner shr 1)
            val weight = (if (corner and 1 == 0) 1f - fracX else fracX) *
                (if (corner shr 1 == 0) 1f - fracY else fracY)
            if (weight <= 0f) continue
            if (sampleX !in 0 until size || sampleY !in 0 until size) continue

            val argb = pixels[sampleY * size + sampleX]
            val sampleAlpha = (argb ushr 24 and 0xFF) * weight
            alpha += sampleAlpha
            red += (argb shr 16 and 0xFF) * sampleAlpha
            green += (argb shr 8 and 0xFF) * sampleAlpha
            blue += (argb and 0xFF) * sampleAlpha
        }

        if (alpha <= 0f) return 0
        return (alpha.toInt().coerceIn(0, 255) shl 24) or
            ((red / alpha).toInt().coerceIn(0, 255) shl 16) or
            ((green / alpha).toInt().coerceIn(0, 255) shl 8) or
            (blue / alpha).toInt().coerceIn(0, 255)
    }
}
