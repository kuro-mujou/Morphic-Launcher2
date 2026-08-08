package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/** A grid cell coordinate. */
data class Cell(val row: Int, val col: Int)

/**
 * The measured geometry of a laid-out [LauncherGrid] in root/window space — the **seam** between the grid's
 * pixels and a drag layer's cell maths. A surface publishes this from the grid's measured bounds
 * (`origin = boundsInRoot top-left`, `cellW = width / cols`, `cellH = height / rows`) so finger→cell and
 * cell→root use the exact cells the grid drew, and can't drift when the surface resizes. Cells may be
 * non-square.
 *
 * Only the pure geometry lives here; drag-planning helpers that pull in higher layers (push direction, folder
 * merge rings, the MovingGap thirds) are extensions defined where those types are available.
 */
data class GridGeometry(
    val originInRoot: Offset,
    val cellW: Float,
    val cellH: Float,
    val cols: Int,
    val rows: Int,
) {
    /**
     * The footprint's top-left [Cell] for a [colSpan]×[rowSpan] item whose proxy is centred on the finger —
     * the item's own top-left rounded to the nearest cell (half-cell hysteresis), clamped onto the grid.
     *
     * **It snaps to the logical lattice, always, and that is the whole point of a sub-divided grid.** On a
     * `cellMultiplier = 2` home grid an app is a 2×2 logical footprint, and rounding its top-left to any *logical*
     * cell is what lets it come to rest straddling two of the cells the user can see — the offsets between them
     * are reachable, which is the reason to subdivide at all.
     *
     * There used to be a `step` parameter that coarsened this, and home passed its `cellMultiplier` to it: the
     * footprint was rounded back onto the visual lattice, so a grid declared at 2 behaved in every observable way
     * like one declared at 1 and the subdivision bought nothing but twice the occupancy bookkeeping. It is gone
     * rather than defaulted, because its only ever use was that mistake and a parameter is an invitation to repeat
     * it. A surface that genuinely wants whole-cell alignment can round the result itself and say so.
     */
    fun snapTopLeftCell(fingerInRoot: Offset, colSpan: Int, rowSpan: Int): Cell {
        val topLeftX = fingerInRoot.x - originInRoot.x - colSpan * cellW / 2f
        val topLeftY = fingerInRoot.y - originInRoot.y - rowSpan * cellH / 2f
        return Cell(
            row = snapToLattice(topLeftY / cellH, rows - rowSpan),
            col = snapToLattice(topLeftX / cellW, cols - colSpan),
        )
    }

    /** Rounds [cell] to the nearest whole cell, then clamps the start to `0..maxStart`. */
    private fun snapToLattice(cell: Float, maxStart: Int): Int =
        cell.roundToInt().coerceIn(0, maxStart.coerceAtLeast(0))

    /** The [Cell] directly under [rootPosition], or null when the finger is outside the grid. */
    fun cellAt(rootPosition: Offset): Cell? {
        val lx = rootPosition.x - originInRoot.x
        val ly = rootPosition.y - originInRoot.y
        if (lx < 0f || ly < 0f) return null
        val col = (lx / cellW).toInt()
        val row = (ly / cellH).toInt()
        return if (row in 0 until rows && col in 0 until cols) Cell(row, col) else null
    }

    /** Root-space top-left of the cell at ([row], [col]). */
    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellW, originInRoot.y + row * cellH)
}
