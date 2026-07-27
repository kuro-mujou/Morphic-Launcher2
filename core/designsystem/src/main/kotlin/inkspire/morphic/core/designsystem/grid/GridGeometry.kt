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
     * [step] coarsens the snap lattice: the top-left is rounded to the nearest multiple of [step] cells on each
     * axis. The default `1` snaps to every logical cell; passing the grid's `cellMultiplier` snaps to whole
     * *visual* cells, so full-cell items (e.g. app icons on a sub-cell home grid) stay aligned instead of
     * landing on a half-cell offset. Grid dimensions are a multiple of the multiplier, so the clamp still lands
     * on the lattice.
     */
    fun snapTopLeftCell(fingerInRoot: Offset, colSpan: Int, rowSpan: Int, step: Int = 1): Cell {
        val topLeftX = fingerInRoot.x - originInRoot.x - colSpan * cellW / 2f
        val topLeftY = fingerInRoot.y - originInRoot.y - rowSpan * cellH / 2f
        val col = snapToLattice(topLeftX / cellW, step, cols - colSpan)
        val row = snapToLattice(topLeftY / cellH, step, rows - rowSpan)
        return Cell(row, col)
    }

    /** Rounds [cell] to the nearest multiple of [step], then clamps the start to `0..maxStart`. */
    private fun snapToLattice(cell: Float, step: Int, maxStart: Int): Int {
        val snapped = (cell / step).roundToInt() * step
        return snapped.coerceIn(0, maxStart.coerceAtLeast(0))
    }

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
