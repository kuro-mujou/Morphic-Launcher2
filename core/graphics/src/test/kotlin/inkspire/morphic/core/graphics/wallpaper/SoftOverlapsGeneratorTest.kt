package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The disc count range — this design's only pure mapping. Placement is [PointScatter]'s and the translucent blending is
 * canvas work judged in the render harness.
 */
class SoftOverlapsGeneratorTest {

    @Test
    fun `density maps to the disc count range`() {
        assertEquals(8, SoftOverlapsGenerator.discCount(0f))
        assertEquals(26, SoftOverlapsGenerator.discCount(1f))
        assertEquals(8, SoftOverlapsGenerator.discCount(-1f)) // clamped
        assertEquals(26, SoftOverlapsGenerator.discCount(2f)) // clamped
    }
}
