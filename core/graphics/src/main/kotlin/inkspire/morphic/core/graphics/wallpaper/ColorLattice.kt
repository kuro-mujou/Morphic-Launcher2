package inkspire.morphic.core.graphics.wallpaper

/**
 * A grid of colored nodes read as a continuous field — bilinear between the four nodes around a point.
 *
 * **Two designs now build a field this way and they must not each write the indexing.** The mesh gradient samples it
 * per *pixel*; the facet field samples it per *facet centroid*. The arithmetic is the part that is silently wrong when
 * it is wrong — a transposed row and column turns a wash that runs down the frame into one that runs across it, and
 * nothing about the picture says which one was asked for — so it is written once.
 *
 * **Rectangular, not square, because the two callers disagree.** The mesh's lattice is square by construction (its
 * density knob is one number of patches per axis); a facet field wants a lattice shaped like the frame, so its blobs
 * come out round rather than stretched. One extra parameter buys both.
 *
 * A sample outside the unit square **reads the edge** rather than wrapping — a warp that pushes a coordinate off the
 * lattice should find the border color there, not the far side of the picture.
 */
internal object ColorLattice {

    /**
     * The color of a `[cols] × [rows]` lattice of [nodes] (row-major, ARGB) at ([u], [v]) in `0..1`.
     *
     * Blends through [LinearGradientGenerator.lerpArgb] rather than mixing channels here, so every ramp in the studio
     * packs and unpacks ARGB in exactly one place.
     */
    fun sample(nodes: IntArray, cols: Int, rows: Int, u: Float, v: Float): Int {
        val fx = u.coerceIn(0f, 1f) * (cols - 1)
        val fy = v.coerceIn(0f, 1f) * (rows - 1)
        val x0 = fx.toInt().coerceIn(0, cols - 1)
        val y0 = fy.toInt().coerceIn(0, rows - 1)
        val x1 = (x0 + 1).coerceAtMost(cols - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = LinearGradientGenerator.lerpArgb(nodes[y0 * cols + x0], nodes[y0 * cols + x1], tx)
        val bottom = LinearGradientGenerator.lerpArgb(nodes[y1 * cols + x0], nodes[y1 * cols + x1], tx)
        return LinearGradientGenerator.lerpArgb(top, bottom, ty)
    }
}
