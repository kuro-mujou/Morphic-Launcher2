package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wedge count and the bearing-to-wedge mapping — the index must tile the full turn, never run off the palette, and
 * be taken from a bearing that exists **on the screen**.
 */
class RaysGeneratorTest {

    /** A tall phone: 1080×2400. Every aspect-sensitive case is measured on one, since a square frame hides the bug. */
    private val phone = 2400f / 1080f

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
                val w = RaysGenerator.wedge(nx, ny, 0.4f, 0.6f, rays, phone)
                assertTrue("wedge $w out of range", w in 0 until rays)
                ny += 0.05f
            }
            nx += 0.05f
        }
    }

    /**
     * The bug this design carried until the quality pass: an `atan2` of two shares-of-their-own-side reads an angle
     * that exists nowhere on the display.
     *
     * **Measured off-axis, because the stretch leaves the four axes exactly where they are** and only moves what lies
     * between them — a test comparing due-right with directly-below passes either way and guards nothing. With a wedge
     * per degree, a point at a true 45° on the screen has to land 45 wedges from the one due right of the centre; in
     * the old metric a phone read that same point at about 24°.
     */
    @Test
    fun `a bearing off the axes is the one on the screen`() {
        val rays = 360
        val right = RaysGenerator.wedge(0.5f + 0.2f, 0.5f, 0.5f, 0.5f, rays, phone)
        // Equal pixel offsets across and down — a true 45° on the display, whatever the frame's proportions.
        val diagonal = RaysGenerator.wedge(0.5f + 0.2f, 0.5f + 0.2f / phone, 0.5f, 0.5f, rays, phone)
        assertEquals(45f, Math.floorMod(diagonal - right, rays).toFloat(), 1f)
    }

    @Test
    fun `opposite bearings fall in opposite wedges`() {
        val rays = 8
        // Diagonally opposite points, one pixel-diagonal either side of the centre — half a turn, four wedges of eight.
        // Chosen off the ±π seam, where a wedge boundary legitimately sits, so this tests the mapping and not the edge.
        val northEast = RaysGenerator.wedge(0.9f, 0.5f - 0.4f / phone, 0.5f, 0.5f, rays, phone)
        val southWest = RaysGenerator.wedge(0.1f, 0.5f + 0.4f / phone, 0.5f, 0.5f, rays, phone)
        assertEquals(rays / 2, Math.floorMod(northEast - southWest, rays))
    }
}
