package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement

/**
 * A fast "which cells are taken" index for one free-placement grid, used when the engine needs to find a free
 * slot or check that a placement is clear (append-to-grid, post-shrink densify in [GridReflow], later the
 * drop planner's validity checks).
 *
 * Cells are stored as packed `Long` keys in a [HashSet], so membership and marking are O(1) per cell rather
 * than scanning a list of rectangles. The grid is paged; occupancy spans every page it has been told about.
 *
 * Ported from L1's `GridOccupancy` with `GridRect`/`AppPosition` collapsed to [GridPlacement]. L1's
 * `firstFreeCellForAppend()` is intentionally left out until an append-to-grid consumer needs it — this port
 * carries only what [GridReflow] actually uses (the mandate's "no model in a vacuum").
 *
 * Not thread-safe: build one per operation on a single thread.
 *
 * @param config the grid these placements live on.
 * @param placements the items already on the grid; each is marked occupied up front.
 */
class GridOccupancy(
    private val config: GridConfig,
    placements: Iterable<GridPlacement>,
) {
    private val occupied = HashSet<Long>()
    private var maxPage = -1

    init {
        placements.forEach(::occupy)
    }

    /** True when every cell [rect] covers is empty and [rect] lies within the grid's row/column bounds. */
    fun isFree(rect: GridPlacement): Boolean {
        if (rect.colEndExclusive > config.cols || rect.rowEndExclusive > config.rows) return false
        for (r in rect.row until rect.rowEndExclusive) {
            for (c in rect.col until rect.colEndExclusive) {
                if (key(rect.page, r, c) in occupied) return false
            }
        }
        return true
    }

    /** Marks every cell [rect] covers as taken and remembers its page as a candidate append target. */
    fun occupy(rect: GridPlacement) {
        for (r in rect.row until rect.rowEndExclusive) {
            for (c in rect.col until rect.colEndExclusive) {
                occupied += key(rect.page, r, c)
            }
        }
        if (rect.page > maxPage) maxPage = rect.page
    }

    /**
     * Finds the nearest free rectangle of size [rowSpan] × [colSpan] for an item that wants to sit at
     * ([page], [row], [col]). Prefers the requested spot (clamped into bounds); failing that, scans that page
     * and every later one row-major, appending a fresh page past the last occupied one if needed.
     *
     * @return the free placement (carrying the requested spans), or `null` if the item is larger than the
     *   grid itself.
     */
    fun findFreeRect(page: Int, row: Int, col: Int, rowSpan: Int, colSpan: Int): GridPlacement? {
        if (rowSpan > config.rows || colSpan > config.cols) return null

        findFreeRectOnPage(page, row, col, rowSpan, colSpan)?.let { return it }

        // Later pages get no positional hint: the item's own row/col described where it sat on *its* page, which
        // says nothing about a page it has never been on, so those are scanned from the top-left.
        var p = page + 1
        while (p <= maxPage + 1) {
            scanPage(p, rowSpan, colSpan)?.let { return it }
            p++
        }
        return null
    }

    /**
     * [findFreeRect] confined to one page — for a grid that **has** only one.
     *
     * The dock is the case: it is a single non-paged strip, so an item it cannot fit has not overflowed onto a
     * page 1 that would simply never be drawn; it has nowhere to go on this grid at all, and saying so is the
     * caller's cue to re-home it elsewhere. Returning null rather than inventing a page is what makes that
     * difference visible instead of silently hiding items.
     *
     * @return the free placement, or `null` when this page has no room (or the item is larger than the grid).
     */
    fun findFreeRectOnPage(page: Int, row: Int, col: Int, rowSpan: Int, colSpan: Int): GridPlacement? {
        if (rowSpan > config.rows || colSpan > config.cols) return null

        val preferred = GridPlacement(
            page = page,
            row = row.coerceIn(0, config.rows - rowSpan),
            col = col.coerceIn(0, config.cols - colSpan),
            rowSpan = rowSpan,
            colSpan = colSpan,
        )
        if (isFree(preferred)) return preferred
        return scanPage(page, rowSpan, colSpan)
    }

    /** The first free rectangle of this size on [page], scanning row-major from the top-left. */
    private fun scanPage(page: Int, rowSpan: Int, colSpan: Int): GridPlacement? {
        for (r in 0..config.rows - rowSpan) {
            for (c in 0..config.cols - colSpan) {
                val rect = GridPlacement(page, r, c, rowSpan, colSpan)
                if (isFree(rect)) return rect
            }
        }
        return null
    }

    /** Packs (page, row, col) into one `Long` key. Row/col are masked to 16 bits — ample for any real grid. */
    private fun key(page: Int, row: Int, col: Int): Long =
        (page.toLong() shl 32) or ((row.toLong() and 0xFFFF) shl 16) or (col.toLong() and 0xFFFF)
}
