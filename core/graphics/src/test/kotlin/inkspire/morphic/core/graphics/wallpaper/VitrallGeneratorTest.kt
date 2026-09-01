package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subdivision — the part a bitmap cannot check, and the part that fails as a *hole*.
 *
 * A split that loses a crossing point leaves two panes that do not quite share an edge, and the ground shows through
 * as a hairline nobody would read as a bug. A split that keeps a degenerate half fills the window with needles. And
 * the areas must still sum to the frame, or some of the glass has quietly gone missing.
 */
class VitrallGeneratorTest {

    /** The unit square, the pane every subdivision starts from. */
    private val frame = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    @Test
    fun `a cut through the middle halves the frame`() {
        val halves = VitrallGenerator.split(frame, px = 0.5f, py = 0.5f, dx = 0f, dy = 1f)
        assertEquals(2, halves.size)
        halves.forEach { assertEquals(0.5f, VitrallGenerator.area(it), 1e-5f) }
    }

    @Test
    fun `the two halves share the crossing points exactly, so no hairline opens`() {
        val (near, far) = VitrallGenerator.split(frame, px = 0.37f, py = 0.5f, dx = 0.3f, dy = 1f)
        fun corners(p: FloatArray) = (p.indices step 2).map { p[it] to p[it + 1] }.toSet()
        val shared = corners(near) intersect corners(far)
        assertEquals("a cut of a convex pane crosses it exactly twice", 2, shared.size)
    }

    @Test
    fun `a cut that misses the pane leaves it whole`() {
        val missed = VitrallGenerator.split(frame, px = 2f, py = 2f, dx = 0f, dy = 1f)
        assertEquals(1, missed.size)
        assertEquals(1f, VitrallGenerator.area(missed.single()), 1e-5f)
    }

    @Test
    fun `the panes still tile the frame, however many there are`() {
        for (count in listOf(8, 40, 140)) {
            val panes = VitrallGenerator.panes(count, seed = 4L)
            assertEquals(count, panes.size)
            val total = panes.sumOf { VitrallGenerator.area(it).toDouble() }
            assertEquals("the glass must add up to the window", 1.0, total, 1e-3)
        }
    }

    @Test
    fun `no pane is a needle`() {
        // The subdivision rejects a cut that would leave one, which is what keeps the window from turning to noise.
        val panes = VitrallGenerator.panes(140, seed = 9L)
        assertTrue("a pane came out below the sliver floor", panes.all { VitrallGenerator.area(it) >= 0.00035f })
        assertTrue("every pane needs three corners", panes.all { it.size >= 6 })
    }

    @Test
    fun `the same seed cuts the same window, so a recipe reproduces`() {
        val first = VitrallGenerator.panes(30, seed = 12L)
        val again = VitrallGenerator.panes(30, seed = 12L)
        assertEquals(first.size, again.size)
        assertTrue(first.indices.all { first[it].contentEquals(again[it]) })
    }

    @Test
    fun `a different seed cuts a different window`() {
        val a = VitrallGenerator.panes(30, seed = 1L)
        val b = VitrallGenerator.panes(30, seed = 2L)
        assertTrue(a.indices.any { !a[it].contentEquals(b[it]) })
    }
}
