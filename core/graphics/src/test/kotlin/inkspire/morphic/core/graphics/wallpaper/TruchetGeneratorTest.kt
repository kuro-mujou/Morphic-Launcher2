package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The grid sizing and the per-cell orientation flips — the flips are the whole of what a Truchet seed decides, so they
 * must reproduce, and there must be one per cell (a short array leaves cells undrawn). And the turn a scrub carries a
 * flipping cell through, which must end on the orientation it was going to.
 */
class TruchetGeneratorTest {

    @Test
    fun `density maps to the column count range`() {
        assertEquals(4, TruchetGenerator.gridSize(0f))
        assertEquals(14, TruchetGenerator.gridSize(1f))
        assertEquals(4, TruchetGenerator.gridSize(-1f)) // clamped
    }

    @Test
    fun `there is one orientation per cell`() {
        assertEquals(6 * 5, TruchetGenerator.orientations(cols = 6, rows = 5, seed = 1L).size)
    }

    @Test
    fun `the same seed yields the same orientations, so a recipe reproduces`() {
        assertArrayEquals(
            TruchetGenerator.orientations(8, 8, seed = 42L),
            TruchetGenerator.orientations(8, 8, seed = 42L),
        )
    }

    @Test
    fun `a different seed yields different orientations`() {
        val a = TruchetGenerator.orientations(10, 10, seed = 1L)
        val b = TruchetGenerator.orientations(10, 10, seed = 2L)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `the default thickness is the weight this design shipped with`() {
        assertEquals(0.34f, TruchetGenerator.arcWidthFraction(0.5f), 0.005f)
    }

    @Test
    fun `the thinnest setting is still a line, and the widest closes the maze`() {
        assertTrue("a Truchet with no ink is a flat frame", TruchetGenerator.arcWidthFraction(0f) > 0f)
        assertTrue("the widest arcs must meet", TruchetGenerator.arcWidthFraction(1f) > 0.8f)
        assertEquals(TruchetGenerator.arcWidthFraction(0f), TruchetGenerator.arcWidthFraction(-1f), 1e-6f)
        assertEquals(TruchetGenerator.arcWidthFraction(1f), TruchetGenerator.arcWidthFraction(2f), 1e-6f)
    }

    /**
     * **A flip lands on the other orientation**, which is the whole scrub: turns are counted from the unflipped tile,
     * and a turn of any odd count draws the flipped one. Landing on an even count would sweep smoothly and then jump
     * back to where it started when the bake takes over.
     */
    @Test
    fun `a flipping cell turns a quarter, either way round, and lands flipped the other way`() {
        for (a in listOf(false, true)) {
            val start = TruchetGenerator.turnAt(a, !a, 0f)
            val end = TruchetGenerator.turnAt(a, !a, 1f)
            assertEquals("$a turns a quarter", 1f, end - start, 1e-6f)
            assertEquals("$a lands on the other orientation", if (a) 0 else 1, end.roundToInt().mod(2))
            assertEquals("$a starts on its own", if (a) 1 else 0, start.roundToInt().mod(2))
        }
    }

    @Test
    fun `a cell that keeps its orientation stays put for the whole scrub`() {
        for (a in listOf(false, true)) {
            for (t in listOf(0f, 0.3f, 0.7f, 1f)) {
                assertEquals(TruchetGenerator.turnAt(a, a, 0f), TruchetGenerator.turnAt(a, a, t), 0f)
            }
        }
    }

    @Test
    fun `a plan is one maze at every size of one shape`() {
        val params = DesignParams()
        val full = TruchetGenerator.plan(1080, 2400, params, seed = 3L)
        val small = TruchetGenerator.plan(135, 300, params, seed = 3L)
        assertEquals(full.cols, small.cols)
        assertEquals(full.rows, small.rows)
        assertArrayEquals(full.flipped, small.flipped)
    }

    @Test
    fun `two mazes on different grids are refused rather than paired`() {
        val params = DesignParams()
        val coarse = TruchetGenerator.plan(1080, 2400, params.copy(density = 0f), seed = 1L)
        val fine = TruchetGenerator.plan(1080, 2400, params.copy(density = 1f), seed = 1L)
        assertNull(TruchetGenerator.morph(coarse, fine))
        assertNotNull(TruchetGenerator.morph(coarse, TruchetGenerator.plan(1080, 2400, params.copy(density = 0f), 2L)))
    }
}
