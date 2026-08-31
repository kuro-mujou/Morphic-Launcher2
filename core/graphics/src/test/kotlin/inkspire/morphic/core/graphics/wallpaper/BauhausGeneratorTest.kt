package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The tile plan. The one that fails *silently* is the color pair: a tile whose arc is drawn in its own ground color is
 * a shape nobody can see, and on a full frame of them the design just looks like flat squares — no crash, no test
 * failure, nothing to notice but a duller wallpaper.
 */
class BauhausGeneratorTest {

    private fun plan(
        coverage: Float = 1f,
        variety: Float = 1f,
        stops: Int = 5,
        floating: Boolean = false,
        seed: Long = 7L,
    ) = BauhausGenerator.plan(6, 10, coverage, variety, stops, floating, seed)

    @Test
    fun `density maps to the column count range`() {
        assertEquals(2, BauhausGenerator.columnCount(0f))
        assertEquals(9, BauhausGenerator.columnCount(1f))
        assertEquals(2, BauhausGenerator.columnCount(-1f)) // clamped
        assertEquals(9, BauhausGenerator.columnCount(2f))
    }

    @Test
    fun `a tile never draws its arc in its own ground color`() {
        for (stops in 2..6) {
            for (cell in plan(stops = stops)) assertNotEquals("palette of $stops", cell.ground, cell.shape)
        }
    }

    @Test
    fun `a shape sits at least two stops from its ground where the palette is long enough`() {
        // Merely different is not enough: a palette runs light to dark, so one stop apart is one tone apart and the
        // arc is a shape you have to hunt for. Nothing about that fails loudly.
        for (stops in 4..6) {
            for (cell in plan(stops = stops)) {
                assertTrue("palette of $stops: ${cell.shape} on ${cell.ground}", abs(cell.ground - cell.shape) >= 2)
            }
        }
    }

    @Test
    fun `the floating variant never draws a shape in the frame's own ground stop`() {
        val stops = 6
        // The ground is the last stop, so every shape must come from the ones before it.
        for (cell in plan(stops = stops, floating = true)) assertTrue("stop ${cell.shape}", cell.shape < stops - 1)
    }

    @Test
    fun `a one-color palette degrades to a flat field rather than throwing`() {
        for (cell in plan(stops = 1)) assertEquals(0, cell.shape)
    }

    @Test
    fun `full coverage decorates every tile, and the lowest still decorates some`() {
        assertTrue("full coverage", plan(coverage = 1f).all { it.decorated })
        val sparse = plan(coverage = 0f).count { it.decorated }
        assertTrue("the emptiest setting still draws something, got $sparse", sparse in 1 until 60)
    }

    @Test
    fun `no variety is a strict repeat — every quarter faces the same corner`() {
        for (cell in plan(variety = 0f)) assertEquals(0, cell.turn)
    }

    @Test
    fun `full variety reaches every turn`() {
        assertEquals(setOf(0, 1, 2, 3), plan(variety = 1f).map { it.turn }.toSet())
    }

    @Test
    fun `coverage and variety are independent — a sparse field can still be a strict repeat`() {
        val cells = plan(coverage = 0.2f, variety = 0f)
        assertTrue("some tiles are bare", cells.any { !it.decorated })
        assertTrue("and the decorated ones all face one way", cells.filter { it.decorated }.all { it.turn == 0 })
    }

    @Test
    fun `the same seed yields the same plan, so a recipe reproduces`() {
        assertEquals(plan(coverage = 0.5f, variety = 0.5f), plan(coverage = 0.5f, variety = 0.5f))
    }

    @Test
    fun `moving a knob re-dresses the same lattice rather than re-rolling it`() {
        // Every roll is drawn at every setting, so the colors a tile lands on do not shift as a knob moves — the
        // knob's own effect is then the only thing that changes on screen.
        val calm = plan(coverage = 0.3f, variety = 0f, seed = 9L)
        val wild = plan(coverage = 0.9f, variety = 1f, seed = 9L)
        assertEquals(calm.map { it.ground }, wild.map { it.ground })
        assertEquals(calm.map { it.shape }, wild.map { it.shape })
    }
}
