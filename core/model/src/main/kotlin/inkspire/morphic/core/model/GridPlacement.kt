package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * Where an item sits on a paged grid: its [page], top-left [row]/[col], and how many cells it spans
 * ([rowSpan] × [colSpan]). This is the single placement type for every grid item (apps, folders, widgets,
 * containers) and the value persisted in the layout maps.
 *
 * It is a rectangle *with extent* (it carries spans), so it is a box — not a vector. Geometry is evaluated
 * per page: placements on different pages never overlap or contain one another.
 *
 * @property page Zero-based page index the item lives on.
 * @property row Zero-based top edge, inclusive.
 * @property col Zero-based left edge, inclusive.
 * @property rowSpan Number of rows occupied vertically; must be > 0.
 * @property colSpan Number of columns occupied horizontally; must be > 0.
 */
@Serializable
data class GridPlacement(
    val page: Int,
    val row: Int,
    val col: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
) {
    init {
        require(page >= 0) { "page must be >= 0, got $page" }
        require(row >= 0) { "row must be >= 0, got $row" }
        require(col >= 0) { "col must be >= 0, got $col" }
        require(rowSpan > 0) { "rowSpan must be > 0, got $rowSpan" }
        require(colSpan > 0) { "colSpan must be > 0, got $colSpan" }
    }

    /** Row just past the bottom edge, exclusive: [row] + [rowSpan]. */
    val rowEndExclusive: Int get() = row + rowSpan

    /** Column just past the right edge, exclusive: [col] + [colSpan]. */
    val colEndExclusive: Int get() = col + colSpan

    /** True when [other] shares at least one cell with this placement on the same page. */
    fun overlaps(other: GridPlacement): Boolean {
        if (page != other.page) return false
        if (rowEndExclusive <= other.row || other.rowEndExclusive <= row) return false
        if (colEndExclusive <= other.col || other.colEndExclusive <= col) return false
        return true
    }

    /** True when [other] lies entirely within this placement's bounds on the same page. */
    fun contains(other: GridPlacement): Boolean {
        if (page != other.page) return false
        return other.row >= row &&
            other.col >= col &&
            other.rowEndExclusive <= rowEndExclusive &&
            other.colEndExclusive <= colEndExclusive
    }

    /** True when this placement fits inside [grid]: top-left in range and no overflow past its bounds. */
    fun fitsIn(grid: GridConfig): Boolean =
        row >= 0 && col >= 0 &&
            rowEndExclusive <= grid.rows &&
            colEndExclusive <= grid.cols

    /**
     * Rotates this placement from portrait to landscape. [portraitCols] is the portrait grid's column
     * count; the column axis is mirrored (and row/col + spans swapped) so the layout reads correctly after
     * a 90° turn.
     */
    fun rotateForLandscape(portraitCols: Int): GridPlacement = copy(
        row = portraitCols - col - colSpan,
        col = row,
        rowSpan = colSpan,
        colSpan = rowSpan,
    )

    /**
     * Inverse of [rotateForLandscape]: rotates a landscape placement back to portrait. [landscapeRows] is
     * the landscape grid's row count.
     */
    fun rotateForPortrait(landscapeRows: Int): GridPlacement = copy(
        row = col,
        col = landscapeRows - row - rowSpan,
        rowSpan = colSpan,
        colSpan = rowSpan,
    )
}
