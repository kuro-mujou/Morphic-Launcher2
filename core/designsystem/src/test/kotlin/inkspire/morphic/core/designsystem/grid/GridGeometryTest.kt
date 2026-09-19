package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** [GridGeometry.snapTopLeftCell]: the footprint is laid around the item's centre and rounded onto the lattice. */
class GridGeometryTest {

    // 8x10 logical cells of 50x60px, starting at (10, 20) in root.
    private val geo = GridGeometry(originInRoot = Offset(10f, 20f), cellW = 50f, cellH = 60f, cols = 8, rows = 10)

    @Test
    fun `an item centred on a cell corner puts its top-left one half-span up and left`() {
        // A 2x2 item centred on the corner shared by cells (2,2)..(3,3): its top-left is exactly cell (2, 2).
        val center = Offset(10f + 3 * 50f, 20f + 3 * 60f)
        assertEquals(Cell(row = 2, col = 2), geo.snapTopLeftCell(center, colSpan = 2, rowSpan = 2))
    }

    @Test
    fun `the top-left moves one cell only once the centre has crossed half of one`() {
        val corner = Offset(10f + 3 * 50f, 20f + 3 * 60f)
        assertEquals(Cell(2, 2), geo.snapTopLeftCell(corner + Offset(24f, 0f), colSpan = 2, rowSpan = 2))
        assertEquals(Cell(2, 3), geo.snapTopLeftCell(corner + Offset(26f, 0f), colSpan = 2, rowSpan = 2))
    }

    @Test
    fun `a footprint is clamped onto the grid`() {
        assertEquals(Cell(0, 0), geo.snapTopLeftCell(Offset(-500f, -500f), colSpan = 2, rowSpan = 2))
        assertEquals(Cell(8, 6), geo.snapTopLeftCell(Offset(5000f, 5000f), colSpan = 2, rowSpan = 2))
    }
}
