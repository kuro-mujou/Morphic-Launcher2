package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banding — the count range, the projection per direction, and variable-width band assignment. A width that sums
 * wrong or a boundary search off by one drops or doubles a stripe, silently.
 */
class DiagonalBandsGeneratorTest {

    @Test
    fun `density maps to the band count range`() {
        assertEquals(4, DiagonalBandsGenerator.bandCount(0f))
        assertEquals(22, DiagonalBandsGenerator.bandCount(1f))
        assertEquals(4, DiagonalBandsGenerator.bandCount(-1f)) // clamped
        assertEquals(22, DiagonalBandsGenerator.bandCount(2f)) // clamped
    }

    @Test
    fun `each variant projects onto its own axis, spanning zero to one`() {
        // Diagonal down: corner (0,0)=0, opposite corner (1,1)=1.
        assertEquals(0f, DiagonalBandsGenerator.project(0f, 0f, 0), 1e-6f)
        assertEquals(1f, DiagonalBandsGenerator.project(1f, 1f, 0), 1e-6f)
        // Diagonal up: (1,0)=1, (0,1)=0.
        assertEquals(1f, DiagonalBandsGenerator.project(1f, 0f, 1), 1e-6f)
        assertEquals(0f, DiagonalBandsGenerator.project(0f, 1f, 1), 1e-6f)
        // Vertical follows nx, horizontal follows ny.
        assertEquals(0.3f, DiagonalBandsGenerator.project(0.3f, 0.9f, 2), 1e-6f)
        assertEquals(0.9f, DiagonalBandsGenerator.project(0.3f, 0.9f, 3), 1e-6f)
    }

    @Test
    fun `with no irregularity the bands are equal width`() {
        val edges = DiagonalBandsGenerator.boundaries(count = 4, irregularity = 0f, seed = 1L)
        assertEquals(3, edges.size)
        assertEquals(0.25f, edges[0], 1e-5f)
        assertEquals(0.5f, edges[1], 1e-5f)
        assertEquals(0.75f, edges[2], 1e-5f)
    }

    @Test
    fun `irregularity keeps the boundaries sorted and inside the frame`() {
        val edges = DiagonalBandsGenerator.boundaries(count = 8, irregularity = 1f, seed = 3L)
        assertEquals(7, edges.size)
        var prev = 0f
        for (e in edges) {
            assertTrue("boundary left 0..1", e in 0f..1f)
            assertTrue("boundaries not sorted", e >= prev)
            prev = e
        }
    }

    @Test
    fun `a position picks the band its boundaries bracket`() {
        val edges = floatArrayOf(0.25f, 0.5f, 0.75f)
        assertEquals(0, DiagonalBandsGenerator.bandAt(0.1f, edges))
        assertEquals(1, DiagonalBandsGenerator.bandAt(0.3f, edges))
        assertEquals(3, DiagonalBandsGenerator.bandAt(0.99f, edges))
        // Exactly on a boundary falls to the upper band (>=), so bands do not overlap.
        assertEquals(1, DiagonalBandsGenerator.bandAt(0.25f, edges))
    }
}
