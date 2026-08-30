package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid, the triangulation and the facet shading — the parts a bitmap cannot check. A triangulation that indexes
 * a point it does not have, or a border that pulls off the frame's edge, is silently wrong until it is a hole.
 */
class TriangularFacetsGeneratorTest {

    @Test
    fun `density maps to the column count range`() {
        assertEquals(5, TriangularFacetsGenerator.gridColumns(0f))
        assertEquals(16, TriangularFacetsGenerator.gridColumns(1f))
    }

    @Test
    fun `the grid has a point per lattice node`() {
        val grid = TriangularFacetsGenerator.grid(cols = 4, rows = 3, jitter = 0.5f, seed = 1L)
        assertEquals((4 + 1) * (3 + 1) * 2, grid.size)
    }

    @Test
    fun `the border points are pinned to the frame's edge`() {
        val cols = 3
        val rows = 3
        val grid = TriangularFacetsGenerator.grid(cols, rows, jitter = 0.9f, seed = 5L)
        fun x(c: Int, r: Int) = grid[(r * (cols + 1) + c) * 2]
        fun y(c: Int, r: Int) = grid[(r * (cols + 1) + c) * 2 + 1]

        // Corners exact, and every edge point flush against its edge whatever the jitter.
        assertEquals(0f, x(0, 0), 0f)
        assertEquals(0f, y(0, 0), 0f)
        assertEquals(1f, x(cols, rows), 0f)
        assertEquals(1f, y(cols, rows), 0f)
        assertEquals(0f, y(1, 0), 0f) // top edge: y pinned
        assertEquals(0f, x(0, 2), 0f) // left edge: x pinned
    }

    @Test
    fun `every triangle indexes a point the grid has`() {
        val cols = 4
        val rows = 3
        val triangles = TriangularFacetsGenerator.triangles(cols, rows)
        val pointCount = (cols + 1) * (rows + 1)

        assertEquals(cols * rows * 6, triangles.size) // two triangles a cell, three indices each
        assertTrue("an index ran off the grid", triangles.all { it in 0 until pointCount })
    }

    @Test
    fun `an unshaded facet is exactly the gradient at its height`() {
        val palette = Palette(listOf(0xFF241B4E.toInt(), 0xFFFFD9A0.toInt()))

        assertEquals(
            LinearGradientGenerator.colorAt(0.3f, palette),
            TriangularFacetsGenerator.facetColor(centroidY = 0.3f, shade = 0f, palette = palette),
        )
    }

    @Test
    fun `a positive shade lifts the facet and a negative one darkens it`() {
        val palette = Palette(listOf(0xFF808080.toInt(), 0xFF808080.toInt()))
        val base = 0x80

        val lit = TriangularFacetsGenerator.facetColor(0.5f, shade = 0.2f, palette = palette)
        val dark = TriangularFacetsGenerator.facetColor(0.5f, shade = -0.2f, palette = palette)

        assertTrue((lit shr 16 and 0xFF) > base)
        assertTrue((dark shr 16 and 0xFF) < base)
    }
}
