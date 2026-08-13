package inkspire.morphic.core.icon.parse

/**
 * How much of its own canvas a layer's artwork actually covers, and where — measured, never assumed.
 *
 * **Bounds, because the target is to fill the box.** Artwork that stands alone is the whole icon, so what has to
 * reach the box edges is its outermost ink — a bounds question, with no coverage or weighting in it. An earlier cut
 * measured *area* as well, to fit every foreground to a small shared target; that target is gone, because filling
 * the box is geometry rather than a number somebody picked. If a future version ever needs to compare visual weight
 * between icons — a solid disc against a thin glyph — coverage is the metric to add back, and AOSP's
 * `IconNormalizer` is the reference for it.
 *
 * Pure arithmetic over an `IntArray`, split from the rasterizing that feeds it, so it is unit-testable without an
 * emulator. Same split as [LegacyBackground], and for the same reason: the *decision* is the part worth pinning.
 *
 * @property left the ink's edges as fractions of the canvas, 0..1.
 */
data class ContentMetrics(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** The ink's center, which is what a scale has to bring back to the middle of the box. */
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** The larger side of the ink's box — what a fit divides into, so artwork fills the box on both axes. */
    val longestSide: Float get() = maxOf(width, height)

    companion object {

        /**
         * Measures a [size]×[size] grid of ARGB [pixels], or `null` when nothing is drawn.
         *
         * Null means "leave this layer exactly as it is", which is the right answer for artwork that cannot be
         * seen — a fully transparent layer, or one this could not rasterize.
         *
         * @param alphaFloor alpha above which a pixel counts as ink. Deliberately not zero: icons are antialiased,
         *   and some carry a nearly-invisible wash across the whole canvas which at a zero threshold measures as
         *   full coverage and quietly defeats the entire measurement.
         */
        fun of(pixels: IntArray, size: Int, alphaFloor: Int = AlphaFloor): ContentMetrics? {
            if (size <= 0 || pixels.size < size * size) return null
            var left = size
            var top = size
            var right = -1
            var bottom = -1
            var i = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if ((pixels[i] ushr 24) > alphaFloor) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                    i++
                }
            }
            if (right < left || bottom < top) return null
            val span = size.toFloat()
            return ContentMetrics(
                left = left / span,
                top = top / span,
                // +1 on the far edges because a pixel's index is its near edge — a single lit pixel spans one unit,
                // not zero, and without this a one-pixel glyph measures as having no width and divides to infinity.
                right = (right + 1) / span,
                bottom = (bottom + 1) / span,
            )
        }

        /** Low enough to keep genuine antialiasing while rejecting an invisible wash. */
        const val AlphaFloor = 16
    }
}
