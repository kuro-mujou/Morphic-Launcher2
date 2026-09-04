package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid sizing and the per-cell orientation flips — the flips are the whole of what a Truchet seed decides, so they
 * must reproduce, and there must be one per cell (a short array leaves cells undrawn).
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
}
