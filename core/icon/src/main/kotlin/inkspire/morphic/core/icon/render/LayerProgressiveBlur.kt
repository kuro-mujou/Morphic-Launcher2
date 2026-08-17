package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.roundToInt

/**
 * How strong a progressive blur is — how far its copy is scaled down before being grown back.
 *
 * Here for [LayerShadow]'s reason rather than the shared one: only the bake draws this, so nothing is competing with
 * it — it is separated because `IconRenderer` needs an emulator for every line and this answer is silently wrong
 * rather than loudly.
 *
 * **Where the ramp's stops sit left for [LayerGradient.rampStops]** when a vignette turned out to ask the identical
 * question. It is not a blur's question at all — "how much stays untouched, and how far past that to full" — and
 * leaving it here would have had an effect that is not a blur importing this file to reach it.
 */
object LayerProgressiveBlur {

    /**
     * The side to downscale to for a blur of [radius], or `null` when there is no blur to apply.
     *
     * **The blur is a downscale and an upscale**, which is the one real decision in this file. A box blur over the
     * `IntArray` was the alternative and would have been a second copy of the one in `data:wallpaper`'s `Blur.kt` —
     * unreachable from here, since `core:icon` cannot depend on a `data` module. Scaling down and back up with
     * bilinear filtering is the platform doing the same averaging, in two calls, with no arithmetic to get wrong.
     *
     * What it costs is exactness: the result approximates a Gaussian rather than being one, and at a large radius
     * the high frequencies are gone entirely — which is what a blur does anyway, so the approximation is invisible
     * in the only place it is used.
     *
     * **Null rather than the full size, and the caller must skip the effect on it rather than copying.** A radius
     * this small is reachable *by dragging*: the studio's sliders are continuous and their step only governs the
     * stepper buttons, so a finger passes through values like 0.002 on its way up — and a 48dp layer tile at draft
     * scale is a few dozen pixels, where that is a fraction of one. It is the ordinary case, not the edge one.
     *
     * Null also when the box itself is too small to scale down within, which is the same inverted-range trap
     * [stops] had: `coerceIn(3, sizePx - 1)` **throws** rather than clamping once `sizePx` reaches 3.
     */
    fun downscaledSidePx(radius: Float, sizePx: Int): Int? {
        if (sizePx <= MinSidePx) return null
        val radiusPx = radius * sizePx
        if (radiusPx < MinBlurPx) return null
        return (sizePx / radiusPx).roundToInt().coerceIn(MinSidePx, sizePx - 1)
    }

    /** Below this there is nothing to soften, and the scale below would be a copy. */
    private const val MinBlurPx = 1f

    /** Small enough for a heavy blur, large enough that the upscale still has something to interpolate between. */
    private const val MinSidePx = 3
}
