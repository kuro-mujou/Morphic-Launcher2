package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement

/**
 * Lays one arrangement's items out again on a **differently shaped** grid — what a posture with no saved layout
 * of its own is seeded from.
 *
 * **Deliberately not [GridReflow], and the difference is the whole point.** That settles a grid against a new
 * size: whatever still fits keeps its exact cell and only the strays move. Right for an edit, where the user
 * changed one edge and expects the rest to stay put — and wrong for a rotation, where the source's coordinates
 * describe a lattice of another shape entirely. A 4-column portrait arrangement *settled* into a 6-column
 * landscape grid sits in columns 0–3 with the two gained columns empty, which reads as the layout being broken
 * rather than adapted. Here nothing keeps its coordinate.
 *
 * **Reading order in, dense out.** Items are taken in the order the source presents them — page, then row, then
 * column — and packed into the target from its first cell. Gaps in the source do not survive: filling a grid of
 * another shape is exactly what this is for, and a user who wants their gaps preserved wants an independent
 * layout instead. That is a real loss, and it is the reason this is not the only projection the launcher will
 * offer.
 *
 * **The grid is walked in *visual* cells, not logical ones.** HOME's grids declare `cellMultiplier = 2`, so a
 * scan advancing one logical cell at a time would happily seat an app at logical (1, 1) — half a cell from every
 * neighbour, on a lattice whose whole premise is that a visible cell is two. [GridOccupancy]'s own scan does step
 * by one (its callers always hand it a coordinate that is already aligned, so it never had to care), which is why
 * this walks the grid itself and uses that class only as the free-cell index rather than as the search.
 */
object ArrangementProjection {

    /**
     * [source]'s items, re-laid into [into].
     *
     * **One zone at a time.** The caller supplies the placements of a single
     * [inkspire.morphic.core.model.HomeZone] together with that zone's own grid, because a dock and a main area
     * are separate coordinate spaces that merely share a screen — projecting them together would pack dock items
     * into home cells.
     *
     * The source grid is not a parameter and is not needed: reading order is recoverable from the placements
     * alone, and where they land is entirely [into]'s business.
     *
     * An item too large for [into] has its spans clamped to what the grid can hold rather than being dropped — a
     * 3-column widget arriving on a 2-column rail is worth keeping at the wrong size, where losing it silently is
     * not. **Nothing is ever omitted**, which is what lets the caller write the result as the whole of the target
     * arrangement instead of diffing it.
     */
    fun <K> project(source: Map<K, GridPlacement>, into: GridConfig): Map<K, GridPlacement> {
        if (source.isEmpty()) return emptyMap()

        val occupancy = GridOccupancy(into, emptyList())
        val placed = LinkedHashMap<K, GridPlacement>(source.size)
        val ordered = source.entries.sortedWith(
            compareBy({ it.value.page }, { it.value.row }, { it.value.col }),
        )

        for ((key, at) in ordered) {
            // Clamping cannot break visual alignment: `GridConfig` requires both dimensions to be divisible by the
            // multiplier, so a span that was a whole number of visible cells still is after the coerce.
            val rowSpan = at.rowSpan.coerceAtMost(into.rows)
            val colSpan = at.colSpan.coerceAtMost(into.cols)

            // Scanned from page 0 every time rather than from wherever the last item landed, so a small item can
            // still use the space a larger one left behind on an earlier page. Terminates because the spans above
            // fit the grid, so an empty page always has room and there can be no more pages in play than items.
            var page = 0
            var rect = occupancy.firstAlignedFree(into, page, rowSpan, colSpan)
            while (rect == null && page < source.size) {
                page++
                rect = occupancy.firstAlignedFree(into, page, rowSpan, colSpan)
            }
            val landed = rect ?: GridPlacement(page, 0, 0, rowSpan, colSpan)

            occupancy.occupy(landed)
            placed[key] = landed
        }
        return placed
    }
}

/**
 * The first free rectangle of [rowSpan] × [colSpan] on [page], scanning row-major on **visual-cell** boundaries.
 *
 * The aligned counterpart of `GridOccupancy`'s own row-major scan; see [ArrangementProjection]'s KDoc for why the
 * step matters here and nowhere else.
 */
private fun GridOccupancy.firstAlignedFree(
    config: GridConfig,
    page: Int,
    rowSpan: Int,
    colSpan: Int,
): GridPlacement? {
    val step = config.cellMultiplier
    var row = 0
    while (row + rowSpan <= config.rows) {
        var col = 0
        while (col + colSpan <= config.cols) {
            val rect = GridPlacement(page, row, col, rowSpan, colSpan)
            if (isFree(rect)) return rect
            col += step
        }
        row += step
    }
    return null
}
