package inkspire.morphic.core.icon.render

import inkspire.morphic.core.graphics.BitmapBlur

/**
 * How strong a progressive blur is — the box radius its copy is softened by.
 *
 * Here for [LayerShadow]'s reason rather than the shared one: only the bake draws this, so nothing is competing with
 * it — it is separated because `IconRenderer` needs an emulator for every line and this answer is silently wrong
 * rather than loudly. Which it was: see [boxRadiusPxOrNull].
 *
 * **Where the ramp's stops sit left for [LayerGradient.rampStops]** when a vignette turned out to ask the identical
 * question. It is not a blur's question at all — "how much stays untouched, and how far past that to full" — and
 * leaving it here would have had an effect that is not a blur importing this file to reach it.
 */
object LayerProgressiveBlur {

    /**
     * The box radius for a blur of [radius], or `null` when there is nothing to soften.
     *
     * **This used to be a *downscale factor*, and that was the bug.** The blur was a `Bitmap.scale` down followed by
     * one back up, on the reasoning — stated in this file — that bilinear filtering is "the platform doing the same
     * averaging in two calls, with no arithmetic to get wrong". It is not. Bilinear *downscaling* samples a 2×2
     * neighbourhood per output pixel, so reducing an icon by the 30× a mid-slider radius asked for discarded almost
     * every pixel it was supposed to be averaging. What came out was aliased stair-stepping along each edge and the
     * tent-shaped blocks of the upscale: terraced, not soft, and plainly wrong on a device.
     *
     * [BitmapBlur] is the real thing, and it costs less than the excuse did: three separable box passes with a
     * sliding window are O(pixels) and **independent of the radius**, so there is no longer any reason to reduce the
     * bitmap first.
     *
     * **Null rather than a radius of zero, and the caller must skip the effect on it rather than blurring by
     * nothing.** A radius this small is reachable *by dragging*: the studio's sliders are continuous and their step
     * governs only the stepper buttons, so a finger passes through values like 0.002 on the way up — and a 48dp
     * layer tile at draft scale is a few dozen pixels, where that is a fraction of one. It is the ordinary case, not
     * the edge one.
     */
    fun boxRadiusPxOrNull(radius: Float, sizePx: Int): Int? {
        if (sizePx < MinSidePx) return null
        val radiusPx = radius * sizePx
        if (radiusPx < MinBlurPx) return null
        // Capped against the bitmap, since a window wider than the picture averages the whole of it however far it
        // is allowed to reach — and the arithmetic below has no reason to be handed a number that large.
        return BitmapBlur.boxRadiusFor(radiusPx).coerceAtMost(sizePx)
    }

    /** Below this there is nothing to soften, and a pass over the pixels would return them unchanged. */
    private const val MinBlurPx = 1f

    /** Two pixels cannot be blurred into anything; below this there is no neighbourhood to average. */
    private const val MinSidePx = 3
}
