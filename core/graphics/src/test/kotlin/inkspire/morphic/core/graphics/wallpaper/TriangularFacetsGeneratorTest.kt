package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lattice, the triangulation, the colour field's reach and the relief — the parts a bitmap cannot check.
 *
 * All of them fail *quietly*. A lattice counted on the wrong axis comes out stretched rather than broken; a fixed
 * diagonal makes slivers that read as a style; a field that holds the ground back on a two-stop palette renders a flat
 * block; and a shading term with the wrong sign lights the picture from underneath, which looks like a picture lit from
 * above of something else.
 */
class TriangularFacetsGeneratorTest {

    private val cell = TriangularFacetsGenerator.Cells(cols = 1, rows = 1)

    /** The unit cell's four corners, unjittered — `(0,0) (1,0) (0,1) (1,1)`, interleaved. */
    private val unitCell = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

    @Test
    fun `resolution counts the long axis, and the short one keeps the cells square`() {
        val portrait = TriangularFacetsGenerator.cells(width = 1000, height = 2000, density = 1f)
        assertEquals(20, portrait.rows)
        assertEquals(10, portrait.cols)

        // The same frame turned over must give the same facet size, not the same column count.
        val landscape = TriangularFacetsGenerator.cells(width = 2000, height = 1000, density = 1f)
        assertEquals(20, landscape.cols)
        assertEquals(10, landscape.rows)
    }

    @Test
    fun `an extreme aspect still has a cell on its short axis`() {
        val strip = TriangularFacetsGenerator.cells(width = 100, height = 4000, density = 0f)
        assertEquals(3, strip.rows)
        assertTrue("a frame with no cells across it draws nothing", strip.cols >= 1)
    }

    @Test
    fun `distortion maps to the jitter range`() {
        assertEquals(0f, TriangularFacetsGenerator.jitter(0f), 0f)
        assertEquals(0.55f, TriangularFacetsGenerator.jitter(1f), 1e-6f)
        assertEquals(0.55f, TriangularFacetsGenerator.jitter(2f), 1e-6f) // clamped
    }

    @Test
    fun `the grid has a point per lattice node`() {
        val cells = TriangularFacetsGenerator.Cells(cols = 4, rows = 3)
        assertEquals((4 + 1) * (3 + 1) * 2, TriangularFacetsGenerator.grid(cells, jitter = 0.5f, seed = 1L).size)
    }

    @Test
    fun `a border point slides along its edge but never leaves the frame`() {
        val cells = TriangularFacetsGenerator.Cells(cols = 3, rows = 3)
        val grid = TriangularFacetsGenerator.grid(cells, jitter = 0.55f, seed = 5L)
        fun x(c: Int, r: Int) = grid[(r * (cells.cols + 1) + c) * 2]
        fun y(c: Int, r: Int) = grid[(r * (cells.cols + 1) + c) * 2 + 1]

        assertEquals("a corner is pinned in both axes", 0f, x(0, 0), 0f)
        assertEquals(0f, y(0, 0), 0f)
        assertEquals(1f, x(cells.cols, cells.rows), 0f)
        assertEquals(1f, y(cells.cols, cells.rows), 0f)

        assertEquals("the top edge stays on the top edge", 0f, y(1, 0), 0f)
        assertEquals("the left edge stays on the left edge", 0f, x(0, 2), 0f)
        assertTrue("but it must be free to slide along it", x(1, 0) != 1f / cells.cols)
        assertTrue(y(0, 2) != 2f / cells.rows)
    }

    @Test
    fun `every triangle indexes a point the grid has`() {
        val cells = TriangularFacetsGenerator.Cells(cols = 4, rows = 3)
        val points = TriangularFacetsGenerator.grid(cells, jitter = 0.4f, seed = 3L)
        val triangles = TriangularFacetsGenerator.triangles(points, cells)

        assertEquals(cells.cols * cells.rows * 6, triangles.size) // two triangles a cell, three indices each
        assertTrue("an index ran off the grid", triangles.all { it in 0 until (5 * 4) })
    }

    @Test
    fun `a cell splits along its shorter diagonal, so a stretched quad gives no sliver`() {
        // Rigid: the diagonals are equal and every cell takes the same one, which is what makes the clean quilt.
        assertEquals(
            listOf(0, 1, 3, 0, 3, 2),
            TriangularFacetsGenerator.triangles(unitCell, cell).toList(),
        )

        // Pull the top-right corner in toward the bottom-left one: that diagonal is now much the shorter.
        val pulled = unitCell.copyOf().also { it[2] = 0.2f; it[3] = 0.2f }
        assertEquals(
            listOf(0, 1, 2, 1, 3, 2),
            TriangularFacetsGenerator.triangles(pulled, cell).toList(),
        )
    }

    @Test
    fun `the field holds the ground back, but not from a palette that cannot spare it`() {
        assertEquals(listOf(1, 2, 3, 4), TriangularFacetsGenerator.fieldStops(5).toList())
        assertEquals(listOf(1, 2), TriangularFacetsGenerator.fieldStops(3).toList())
        // Bichromatic is the default color mode, and holding one of its two stops back leaves a flat block.
        assertEquals(listOf(0, 1), TriangularFacetsGenerator.fieldStops(2).toList())
        assertEquals(listOf(0), TriangularFacetsGenerator.fieldStops(1).toList())
    }

    @Test
    fun `the field never paints with the ground, so the leading always reads`() {
        val palette = Palette(listOf(0xFF101010.toInt(), 0xFF884422.toInt(), 0xFF3388CC.toInt()))
        val nodes = TriangularFacetsGenerator.field(TriangularFacetsGenerator.Cells(6, 10), palette, seed = 11L)
        assertTrue("the ground leaked into the glass", nodes.none { it == palette.colorAt(0) })
    }

    @Test
    fun `a flat sheet is unlit whatever its facets look like`() {
        val flat = FloatArray(4)
        assertEquals(1f, TriangularFacetsGenerator.lighting(unitCell, flat, cell, 0, 1, 3), 0f)
        assertEquals(1f, TriangularFacetsGenerator.lighting(unitCell, flat, cell, 0, 3, 2), 0f)
    }

    @Test
    fun `a facet tilted toward the light is brighter, and the opposite tilt darker`() {
        // Height rising to the right and down: the surface normal then leans up and to the left, at the light.
        val toward = floatArrayOf(0f, 1f, 1f, 2f)
        val away = FloatArray(4) { -toward[it] }

        val lit = TriangularFacetsGenerator.lighting(unitCell, toward, cell, 0, 1, 3)
        val shaded = TriangularFacetsGenerator.lighting(unitCell, away, cell, 0, 1, 3)
        assertTrue("a facet facing the light must brighten", lit > 1f)
        assertTrue("and one facing away must darken", shaded < 1f)
        assertEquals("the two tilts are mirror images", 2f, lit + shaded, 1e-5f)
    }

    @Test
    fun `the relief scales with depth and vanishes at zero`() {
        val cells = TriangularFacetsGenerator.Cells(cols = 4, rows = 6)
        assertTrue(TriangularFacetsGenerator.relief(cells, depth = 0f, seed = 2L).all { it == 0f })

        val half = TriangularFacetsGenerator.relief(cells, depth = 0.5f, seed = 2L)
        val full = TriangularFacetsGenerator.relief(cells, depth = 1f, seed = 2L)
        assertTrue("the same field, twice as tall", half.indices.all { kotlin.math.abs(full[it] / 2f - half[it]) < 1e-6f })
    }

    @Test
    fun `shading scales the channels and leaves the alpha alone`() {
        val lifted = TriangularFacetsGenerator.shade(0x80402010U.toInt(), factor = 2f)
        assertEquals(0x80, lifted ushr 24 and 0xFF)
        assertEquals(0x80, lifted shr 16 and 0xFF)
        assertEquals(0x40, lifted shr 8 and 0xFF)
        assertEquals(0x20, lifted and 0xFF)

        // Saturating rather than wrapping: a lit near-white facet is white, not a different hue.
        assertEquals(0xFF, TriangularFacetsGenerator.shade(0xFFF0F0F0.toInt(), factor = 4f) shr 16 and 0xFF)
    }

    @Test
    fun `the leading is a hairline through the lower half of its knob`() {
        assertEquals(0f, TriangularFacetsGenerator.leading(0f), 0f)
        assertEquals(0.12f, TriangularFacetsGenerator.leading(1f), 1e-6f)
        assertTrue("the default must not open on stained glass", TriangularFacetsGenerator.leading(0.5f) < 0.02f)
    }
}
