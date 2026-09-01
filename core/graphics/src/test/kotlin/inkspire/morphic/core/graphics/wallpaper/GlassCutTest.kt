package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cutting a pane in two — the part a bitmap cannot check, and the part that fails as a *hole*.
 *
 * A cut that loses a crossing point leaves two panes that do not quite share an edge, and the ground shows through as
 * a hairline nobody would read as a bug. A cut that welds three pieces into two returns a self-intersecting loop
 * whose area is plausible and wrong, which is worse: the caller keeps recursing on it.
 */
class GlassCutTest {

    /** The unit square, the pane every subdivision starts from. */
    private val frame = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    private fun corners(pane: FloatArray) = (pane.indices step 2).map { pane[it] to pane[it + 1] }.toSet()

    @Test
    fun `a cut through the middle halves the frame`() {
        val halves = GlassCut.split(frame, px = 0.5f, py = 0.5f, dx = 0f, dy = 1f)
        assertEquals(2, halves.size)
        halves.forEach { assertEquals(0.5f, GlassCut.area(it), 1e-5f) }
    }

    @Test
    fun `the two halves share the crossing points exactly, so no hairline opens`() {
        val (near, far) = GlassCut.split(frame, px = 0.37f, py = 0.5f, dx = 0.3f, dy = 1f)
        assertEquals("a cut of a convex pane crosses it exactly twice", 2, (corners(near) intersect corners(far)).size)
    }

    @Test
    fun `a cut that misses the pane leaves it whole`() {
        val missed = GlassCut.split(frame, px = 2f, py = 2f, dx = 0f, dy = 1f)
        assertEquals(1, missed.size)
        assertEquals(1f, GlassCut.area(missed.single()), 1e-5f)
    }

    @Test
    fun `a line crossing a pane four times is refused rather than welded`() {
        // A chevron: the vertical through its waist enters and leaves twice, so a clean cut would be three pieces.
        val chevron = floatArrayOf(0f, 0f, 0.5f, 0.6f, 1f, 0f, 1f, 1f, 0f, 1f)
        val cut = GlassCut.split(chevron, px = 0.5f, py = 0.3f, dx = 1f, dy = 0f)
        assertEquals("a four-crossing cut must be refused, not welded into two lobes", 1, cut.size)
        assertTrue("the refusal hands back the pane itself", cut.single().contentEquals(chevron))
    }

    @Test
    fun `a bowed cut hands both panes the same arc, so no hairline opens`() {
        // The circle is struck from outside the frame, so it bites one side off — the gentle cut the design makes.
        val bowed = GlassCut.bowAbout(frame, cx = -0.6f, cy = 0.5f, r = 1f)
        assertEquals(2, bowed.panes.size)
        val total = bowed.panes.sumOf { GlassCut.area(it).toDouble() }
        assertEquals("the two pieces must still be the whole pane", 1.0, total, 1e-4)

        // The two crossings are shared exactly; the interior arc samples are a rounding apart, which is why this
        // asserts the endpoints rather than the whole chain.
        val shared = corners(bowed.panes[0]) intersect corners(bowed.panes[1])
        assertTrue("both pieces must hold the crossings, not roundings of them", shared.size >= 2)
        assertTrue("a bowed cut is a bone worth drawing", (bowed.arc?.size ?: 0) >= 4)
    }

    @Test
    fun `a bowed cut is actually curved, not a chord`() {
        val bowed = GlassCut.bowAbout(frame, cx = -0.6f, cy = 0.5f, r = 1f)
        assertTrue("an arc has to be sampled into segments", bowed.panes.any { it.size > 8 })
    }

    @Test
    fun `a circle that misses the pane leaves it whole`() {
        val missed = GlassCut.bowAbout(frame, cx = 8f, cy = 8f, r = 1f)
        assertEquals(1, missed.panes.size)
        assertEquals(1f, GlassCut.area(missed.panes.single()), 1e-5f)
    }

    @Test
    fun `a circle that swallows the pane leaves it whole`() {
        val missed = GlassCut.bowAbout(frame, cx = 0.5f, cy = 0.5f, r = 9f)
        assertEquals(1, missed.panes.size)
    }

    @Test
    fun `the sign of a bow's reach is which side it falls`() {
        val left = GlassCut.bow(frame, angle = 0f, px = 0.5f, py = 0.5f, reach = 1f)
        val right = GlassCut.bow(frame, angle = 0f, px = 0.5f, py = 0.5f, reach = -1f)
        assertEquals(2, left.panes.size)
        assertEquals(2, right.panes.size)
        // A horizontal cut bowed one way keeps more of the frame below it than the other way does.
        assertTrue(
            "a bow that always fell the same way would give a whole window one curl",
            GlassCut.area(left.panes[0]) != GlassCut.area(right.panes[0]),
        )
    }

    @Test
    fun `the longest diagonal of a wide pane runs along it`() {
        val wide = floatArrayOf(0f, 0f, 4f, 0f, 4f, 1f, 0f, 1f)
        // atan2 of (4, 1) — the corner-to-corner diagonal, not the horizontal edge.
        assertEquals(kotlin.math.atan2(1f, 4f), GlassCut.longestDiagonal(wide), 1e-5f)
    }

    @Test
    fun `an inset pulls every edge in by the same distance`() {
        val inset = GlassCut.inset(frame, 0.1f)!!
        // A uniform inset of the unit square by 0.1 is the 0.8 square — the check that separates a true edge offset
        // from a scaling about the centroid, which would give 0.8 only by luck of the square's symmetry, so the
        // rectangle below is the case that actually discriminates.
        assertEquals(0.64f, GlassCut.area(inset), 1e-4f)

        val wide = floatArrayOf(0f, 0f, 4f, 0f, 4f, 1f, 0f, 1f)
        val thin = GlassCut.inset(wide, 0.2f)!!
        // Scaling about the centroid by the factor that fixes the short side would leave the long side 3.2, not 3.6.
        assertEquals("a true inset moves every edge 0.2, whatever the side's length", 3.6f * 0.6f, GlassCut.area(thin), 1e-4f)
    }

    @Test
    fun `an inset that would evert the shape is refused, not returned inside out`() {
        // Offset the edges far enough and they cross past each other and re-intersect into a small polygon wound the
        // other way — which draws as a real shape of about the right color in about the right place.
        assertNull(GlassCut.inset(frame, 0.6f))
        assertNull(GlassCut.inset(floatArrayOf(0f, 0f, 4f, 0f, 4f, 1f, 0f, 1f), 0.9f))
    }
}
