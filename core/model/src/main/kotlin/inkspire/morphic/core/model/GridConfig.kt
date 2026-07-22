package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * The dimensions of a grid, in **logical** units.
 *
 * Free-placement surfaces (home, dock) use a [cellMultiplier] greater than 1 so each visual cell is
 * subdivided into finer logical cells, enabling sub-cell placement; app-list surfaces use a multiplier of 1
 * where logical and visual units coincide. [visualRows]/[visualCols] are what the user actually sees.
 *
 * @property rows Total logical rows; must be > 0 and divisible by [cellMultiplier].
 * @property cols Total logical columns; must be > 0 and divisible by [cellMultiplier].
 * @property cellMultiplier Logical cells per visual cell along each axis (1 = no subdivision).
 */
@Serializable
data class GridConfig(
    val rows: Int,
    val cols: Int,
    val cellMultiplier: Int = 1,
) {
    init {
        require(rows > 0) { "rows must be > 0, got $rows" }
        require(cols > 0) { "cols must be > 0, got $cols" }
        require(cellMultiplier > 0) { "cellMultiplier must be > 0, got $cellMultiplier" }
        require(rows % cellMultiplier == 0) { "rows must be divisible by cellMultiplier" }
        require(cols % cellMultiplier == 0) { "cols must be divisible by cellMultiplier" }
    }

    /** Total logical cell count: [rows] × [cols]. */
    val cellCount: Int get() = rows * cols

    /** Rows as the user sees them: [rows] / [cellMultiplier]. */
    val visualRows: Int get() = rows / cellMultiplier

    /** Columns as the user sees them: [cols] / [cellMultiplier]. */
    val visualCols: Int get() = cols / cellMultiplier

    /** True when the logical coordinate ([row], [col]) lies inside this grid. */
    fun contains(row: Int, col: Int): Boolean = row in 0 until rows && col in 0 until cols

    /** Returns a copy with rows and columns swapped (a 90° reshape), preserving [cellMultiplier]. */
    fun swap(): GridConfig = GridConfig(rows = cols, cols = rows, cellMultiplier = cellMultiplier)
}
