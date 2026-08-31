package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The band quantization — which elevation a height falls in is the off-by-one that either doubles a contour line or
 * drops the top one, and it needs no bitmap to check.
 */
class ContourGeneratorTest {

    @Test
    fun `density maps to the band count range`() {
        assertEquals(5, ContourGenerator.bandCount(0f))
        assertEquals(18, ContourGenerator.bandCount(1f))
        assertEquals(5, ContourGenerator.bandCount(-1f)) // clamped
    }

    @Test
    fun `heights split evenly across the bands`() {
        // With 4 bands the quarters map 0,1,2,3.
        assertEquals(0, ContourGenerator.band(0.1f, 4))
        assertEquals(1, ContourGenerator.band(0.3f, 4))
        assertEquals(2, ContourGenerator.band(0.6f, 4))
        assertEquals(3, ContourGenerator.band(0.9f, 4))
    }

    @Test
    fun `the top and bottom of the range stay in the first and last band, not one past`() {
        assertEquals(0, ContourGenerator.band(0f, 6))
        assertEquals(5, ContourGenerator.band(1f, 6)) // exactly 1 must not overflow into a seventh band
        assertEquals(5, ContourGenerator.band(1.5f, 6)) // out of range clamps too
    }
}
