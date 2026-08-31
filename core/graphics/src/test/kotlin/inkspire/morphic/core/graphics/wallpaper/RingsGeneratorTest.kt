package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring count and the distance-to-loop mapping — the fraction must stay in `0..1` (it indexes the looped palette)
 * and must repeat once per unit of distance, or the rings never close.
 */
class RingsGeneratorTest {

    @Test
    fun `density maps to the ring count range`() {
        assertEquals(6, RingsGenerator.ringCount(0f))
        assertEquals(26, RingsGenerator.ringCount(1f))
        assertEquals(6, RingsGenerator.ringCount(-1f)) // clamped
    }

    @Test
    fun `the center reads as the start of the cycle`() {
        assertEquals(0f, RingsGenerator.ringFraction(0.5f, 0.5f, cx = 0.5f, cy = 0.5f, rings = 10), 1e-6f)
    }

    @Test
    fun `the fraction stays in the unit range`() {
        var nx = 0f
        while (nx <= 1f) {
            var ny = 0f
            while (ny <= 1f) {
                val f = RingsGenerator.ringFraction(nx, ny, cx = 0.3f, cy = 0.7f, rings = 14)
                assertTrue("fraction left the unit range: $f", f in 0f..1f)
                ny += 0.05f
            }
            nx += 0.05f
        }
    }

    @Test
    fun `distances a whole ring apart map to the same phase`() {
        // Two points on the x-axis from the center, one and two rings out, land at the same fraction (the ring repeats).
        val cx = 0f
        val cy = 0f
        val rings = 10
        val oneRing = RingsGenerator.ringFraction(1f / rings, 0f, cx, cy, rings)
        val twoRings = RingsGenerator.ringFraction(2f / rings, 0f, cx, cy, rings)
        assertEquals(oneRing, twoRings, 1e-5f)
    }
}
