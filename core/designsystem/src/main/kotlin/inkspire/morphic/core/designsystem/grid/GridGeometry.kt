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
     */
    fun snapTopLeftCell(fingerInRoot: Offset, colSpan: Int, rowSpan: Int): Cell {
        val topLeftX = fingerInRoot.x - originInRoot.x - colSpan * cellW / 2f
        val topLeftY = fingerInRoot.y - originInRoot.y - rowSpan * cellH / 2f
        val col = (topLeftX / cellW).roundToInt().coerceIn(0, (cols - colSpan).coerceAtLeast(0))
        val row = (topLeftY / cellH).roundToInt().coerceIn(0, (rows - rowSpan).coerceAtLeast(0))
        return Cell(row, col)
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
