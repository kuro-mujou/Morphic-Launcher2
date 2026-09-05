package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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

    /**
     * `0` is the hard-edged sunburst this design drew before the quality pass, and it has to stay exactly that: the
     * knob's low end is the whole reason the old look is still reachable.
     */
    @Test
    fun `no softness leans nowhere, at any bearing`() {
        val edges = even(8)
        var bearing = 0f
        while (bearing < 1f) {
            assertEquals(0f, RaysGenerator.neighborMix(bearing, RaysGenerator.wedgeAt(bearing, edges), edges, 0f), 0f)
            bearing += 0.01f
        }
    }

    /**
     * The blend is continuous across a seam without either wedge knowing the other exists, which rests entirely on
     * both of them reading exactly half-way there. Asked of the *same* bearing from both sides, which is the only way
     * a mismatch would show.
     */
    @Test
    fun `a seam reads half way in from either wedge`() {
        val edges = even(8)
        val seam = edges[2]
        assertEquals(-0.5f, RaysGenerator.neighborMix(seam, 2, edges, 1f), 1e-4f)
        assertEquals(0.5f, RaysGenerator.neighborMix(seam, 1, edges, 1f), 1e-4f)
    }

    /** However soft, the middle of a wedge is its own flat color — there is no softness that washes the palette out. */
    @Test
    fun `the middle of a wedge is its own color at every softness`() {
        val edges = even(8)
        for (softness in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val middle = (edges[3] + edges[4]) / 2f
            assertEquals(0f, RaysGenerator.neighborMix(middle, 3, edges, softness), 1e-4f)
        }
    }

    /**
     * Full softness leaves no flat interior at all — every part of every wedge is in transition, which is what makes
     * the knob's top end a conic gradient rather than a sunburst with soft seams.
     */
    @Test
    fun `full softness leaves no flat interior`() {
        val edges = even(8)
        val low = edges[3]
        val span = edges[4] - low
        for (step in 1..9) {
            if (step == 5) continue // the exact middle, which is the one pure color a full blend keeps
            val bearing = low + span * step / 10f
            val lean = RaysGenerator.neighborMix(bearing, 3, edges, 1f)
            assertTrue("flat at $bearing", abs(lean) > 1e-3f)
        }
    }

    /**
     * The *Rays* slider counts what the frame **shows**, so the fan it is cut into has to scale with how much of the
     * turn the frame can see. A count left against the full turn is the failure this exists to stop: an apex far
     * enough out to see a fifth of the circle would put eight wedges around itself and two of them on screen, with
     * nothing but a look at the render to say so.
     */
    @Test
    fun `the fan is cut so the frame shows the number of rays that was asked for`() {
        for (sector in listOf(1f, 0.5f, 0.3f, 0.12f)) {
            for (visible in listOf(4, 9, 16)) {
                val fan = RaysGenerator.fanCount(visible, sector)
                val shown = fan * sector
                assertEquals("sector $sector, visible $visible", visible.toFloat(), shown, 1f)
            }
        }
    }

    /** A fan never has fewer wedges than the frame is meant to show, and never runs away on a pathological sector. */
    @Test
    fun `the fan count stays between the visible count and the cap`() {
        assertEquals(9, RaysGenerator.fanCount(9, 1f))
        assertEquals(9, RaysGenerator.fanCount(9, 2f)) // more than a whole turn is still at least the visible count
        assertTrue(RaysGenerator.fanCount(16, 0.0001f) <= 512)
        assertEquals(16, RaysGenerator.fanCount(16, 0f)) // no sector at all, rather than a divide by zero
    }

    /**
     * The wedge lookup became a binary search when the fan grew from a handful of wedges to a few hundred. It has to
     * answer exactly what the walk answered — the last edge at or below the bearing — including at an edge itself and
     * below the first one.
     */
    @Test
    fun `the wedge lookup answers the last edge at or below the bearing`() {
        for (rays in listOf(1, 2, 5, 16, 257)) {
            val edges = even(rays)
            for (i in edges.indices) {
                assertEquals("on edge $i of $rays", i, RaysGenerator.wedgeAt(edges[i], edges))
                val inside = edges[i] + 0.5f / rays
                if (inside < 1f) assertEquals("inside wedge $i of $rays", i, RaysGenerator.wedgeAt(inside, edges))
            }
            assertEquals("below the first edge", 0, RaysGenerator.wedgeAt(-0.1f, edges))
            assertEquals("past the last edge", rays - 1, RaysGenerator.wedgeAt(1.5f, edges))
        }
    }
}
