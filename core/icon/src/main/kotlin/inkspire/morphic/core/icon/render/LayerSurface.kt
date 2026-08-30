package inkspire.morphic.core.icon.render

/**
 * The layer's alpha read as a **height field**, and how steeply it slopes at a point.
 *
 * **Extracted onto its second consumer**, which is the glass. A bevel reads this surface to *light* it; a glass
 * reads the same surface to *bend the sampling through* it — and both need the identical slope at each pixel, taken
 * the identical way. That is precisely the arithmetic the shared-derivation rule exists for: a Sobel copied into two
 * files is two chances to flip a sign or transpose an axis, and either mistake is *silently* wrong — a plausible
 * relief lit from the wrong side, a lens that shears where it should swell. One of the two renderers cannot check
 * the other, so the check has to be that there is only one.
 *
 * **Here rather than in `IconRenderer` because it is pure and it is testable.** The height field itself is built
 * from a `Bitmap` by `IconRenderer.blurredAlpha` (a `BlurMaskFilter` needs the platform), but *reading* that field
 * is `FloatArray` arithmetic, and the part that can be quietly wrong is the reading. Pulled out, it can be checked
 * without an emulator — every line of `IconRenderer` needs one.
 */
object LayerSurface {

    /**
     * The slope of [heights] — a `sizePx`×`sizePx` row-major height field, values in `[0, 1]` — at ([x], [y]),
     * written into [out] as `[slopeX, slopeY]` and already scaled by [scale].
     *
     * **A Sobel gradient, divided by its own weight so the result is a rise per pixel** rather than the kernel's
     * eight-fold sum. [scale] is the caller's — [LayerBevel.slopeScale] cancels the surface's blur width out of it,
     * so a wider, gentler swell reads as strongly as a narrow one instead of fading away.
     *
     * **The border reads as a continuation of itself, not as a cliff** — every sample is clamped into the box. An
     * edge treated as a drop would put a violent slope around the whole rim of any full-bleed layer, which a bevel
     * would light as an outline and a glass would refract as a fringe. Clamping makes the box's edge slope like
     * whatever the surface is doing as it reaches it.
     *
     * @param out a length-2 scratch, written not returned: this runs once per pixel — six hundred thousand times at
     *   preview size — so a returned pair would be six hundred thousand allocations. Called from several threads at
     *   once by [IconRenderer]'s row split, so it must be a scratch each caller owns.
     */
    fun slope(heights: FloatArray, sizePx: Int, x: Int, y: Int, scale: Float, out: FloatArray) {
        out[0] = scale * (
            at(heights, sizePx, x + 1, y - 1) + 2f * at(heights, sizePx, x + 1, y) +
                at(heights, sizePx, x + 1, y + 1) - at(heights, sizePx, x - 1, y - 1) -
                2f * at(heights, sizePx, x - 1, y) - at(heights, sizePx, x - 1, y + 1)
            ) / 8f
        out[1] = scale * (
            at(heights, sizePx, x - 1, y + 1) + 2f * at(heights, sizePx, x, y + 1) +
                at(heights, sizePx, x + 1, y + 1) - at(heights, sizePx, x - 1, y - 1) -
                2f * at(heights, sizePx, x, y - 1) - at(heights, sizePx, x + 1, y - 1)
            ) / 8f
    }

    /** The height at ([x], [y]), with the box's own border reading as a continuation rather than as a cliff. */
    private fun at(heights: FloatArray, sizePx: Int, x: Int, y: Int): Float =
        heights[y.coerceIn(0, sizePx - 1) * sizePx + x.coerceIn(0, sizePx - 1)]
}
