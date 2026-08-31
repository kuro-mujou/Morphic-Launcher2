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
}
