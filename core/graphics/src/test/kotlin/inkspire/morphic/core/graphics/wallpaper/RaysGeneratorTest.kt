package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wedge count and the bearing-to-wedge mapping — the index must tile the full turn and never run off the palette,
 * or the rays tear at the seam or the color lookup crashes.
 */
class RaysGeneratorTest {

    @Test
    fun `density maps to the ray count range`() {
        assertEquals(4, RaysGenerator.rayCount(0f))
        assertEquals(16, RaysGenerator.rayCount(1f))
        assertEquals(4, RaysGenerator.rayCount(-1f)) // clamped
    }

    @Test
    fun `every bearing maps to a wedge in range`() {
        val rays = 12
        var nx = 0f
        while (nx <= 1f) {
            var ny = 0f
            while (ny <= 1f) {
                val w = RaysGenerator.wedge(nx, ny, cx = 0.4f, cy = 0.6f, rays = rays)
                assertTrue("wedge $w out of range", w in 0 until rays)
                ny += 0.05f
            }
            nx += 0.05f
        }
    }

    @Test
    fun `opposite bearings fall in opposite wedges`() {
        val rays = 8
        // Diagonally opposite points (NE and SW of the center) are half a turn apart — four wedges of eight. Chosen off
        // the ±π seam, where a wedge boundary legitimately sits, so this tests the mapping and not the closing edge.
        val northEast = RaysGenerator.wedge(0.9f, 0.1f, cx = 0.5f, cy = 0.5f, rays = rays)
        val southWest = RaysGenerator.wedge(0.1f, 0.9f, cx = 0.5f, cy = 0.5f, rays = rays)
        assertEquals(rays / 2, Math.floorMod(northEast - southWest, rays))
    }
}
