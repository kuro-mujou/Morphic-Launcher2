package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The wedge count and the bearing-to-wedge mapping — the index must tile the full turn, never run off the palette, and
 * be taken from a bearing that exists **on the screen**.
 */
class RaysGeneratorTest {

    /** A tall phone: 1080×2400. Every aspect-sensitive case is measured on one, since a square frame hides the bug. */
    private val phone = 2400f / 1080f

    /** An even fan of [rays] wedges — what the design draws at irregularity `0`. */
    private fun even(rays: Int) = RaysGenerator.edges(rays, 0f, Random(1))

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
                val w = RaysGenerator.wedge(nx, ny, 0.4f, 0.6f, even(rays), phone)
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
        val right = RaysGenerator.wedge(0.5f + 0.2f, 0.5f, 0.5f, 0.5f, even(rays), phone)
        // Equal pixel offsets across and down — a true 45° on the display, whatever the frame's proportions.
        val diagonal = RaysGenerator.wedge(0.5f + 0.2f, 0.5f + 0.2f / phone, 0.5f, 0.5f, even(rays), phone)
        assertEquals(45f, Math.floorMod(diagonal - right, rays).toFloat(), 1f)
    }

    @Test
    fun `opposite bearings fall in opposite wedges`() {
        val rays = 8
        // Diagonally opposite points, one pixel-diagonal either side of the centre — half a turn, four wedges of eight.
        // Chosen off the ±π seam, where a wedge boundary legitimately sits, so this tests the mapping and not the edge.
        val northEast = RaysGenerator.wedge(0.9f, 0.5f - 0.4f / phone, 0.5f, 0.5f, even(rays), phone)
        val southWest = RaysGenerator.wedge(0.1f, 0.5f + 0.4f / phone, 0.5f, 0.5f, even(rays), phone)
        assertEquals(rays / 2, Math.floorMod(northEast - southWest, rays))
    }

    @Test
    fun `unevenness zero is a fan of exactly equal wedges`() {
        val edges = RaysGenerator.edges(8, 0f, Random(7))
        edges.forEachIndexed { i, edge -> assertEquals(i / 8f, edge, 1e-6f) }
    }

    /**
     * The edges have to stay in order and no wedge may collapse, or a ray turns inside out. Held by the travel bound
     * alone rather than by a check, so it is worth pinning across counts and seeds.
     */
    @Test
    fun `however uneven, the edges ascend and every wedge keeps a share`() {
        for (rays in listOf(4, 7, 16)) {
            for (seed in 1L..40L) {
                val edges = RaysGenerator.edges(rays, 1f, Random(seed))
                assertEquals("the fan has to start somewhere", 0f, edges[0], 1e-6f)
                for (i in 1 until edges.size) {
                    assertTrue("edge $i out of order at rays=$rays seed=$seed", edges[i] > edges[i - 1])
                }
                assertTrue("the last edge must leave the closing wedge room", edges.last() < 1f)
            }
        }
    }
}
