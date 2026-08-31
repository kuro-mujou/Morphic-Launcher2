package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared variable-width banding behind the stripe and column staples — a width set that sums to anything but 1, or a
 * boundary search off by one, drops or doubles a band silently.
 */
class BandsTest {

    @Test
    fun `with no irregularity the bands are equal width`() {
        val edges = Bands.boundaries(count = 4, irregularity = 0f, seed = 1L)
        assertEquals(3, edges.size)
        assertEquals(0.25f, edges[0], 1e-5f)
        assertEquals(0.5f, edges[1], 1e-5f)
        assertEquals(0.75f, edges[2], 1e-5f)
    }

    @Test
    fun `irregularity keeps the boundaries sorted and inside the axis`() {
        val edges = Bands.boundaries(count = 8, irregularity = 1f, seed = 3L)
        assertEquals(7, edges.size)
        var prev = 0f
        for (e in edges) {
            assertTrue("boundary left 0..1", e in 0f..1f)
            assertTrue("boundaries not sorted", e >= prev)
            prev = e
        }
    }

    @Test
    fun `a single band has no internal boundaries`() {
        assertEquals(0, Bands.boundaries(count = 1, irregularity = 1f, seed = 5L).size)
    }

    @Test
    fun `a position picks the band its boundaries bracket`() {
        val edges = floatArrayOf(0.25f, 0.5f, 0.75f)
        assertEquals(0, Bands.bandAt(0.1f, edges))
        assertEquals(1, Bands.bandAt(0.3f, edges))
        assertEquals(3, Bands.bandAt(0.99f, edges))
        // Exactly on a boundary falls to the upper band (>=), so bands do not overlap.
        assertEquals(1, Bands.bandAt(0.25f, edges))
    }
}
