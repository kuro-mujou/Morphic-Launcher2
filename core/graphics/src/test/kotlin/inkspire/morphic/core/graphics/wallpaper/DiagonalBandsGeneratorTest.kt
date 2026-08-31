package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This design's own logic — the band count range and the per-direction projection. The variable-width banding it shares
 * with the columns is tested in [BandsTest].
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
}
