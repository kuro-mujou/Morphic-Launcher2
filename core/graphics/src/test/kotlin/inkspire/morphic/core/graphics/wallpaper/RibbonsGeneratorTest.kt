package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The bundle's nesting. Lines that fall out of order do not fail anything — they draw a scribble instead of a fan, on
 * some seeds and not others, which is exactly the kind of wrong this codebase keeps a pure function for.
 */
class RibbonsGeneratorTest {

    private val spine = RibbonsGenerator.spine(42L, scale = 0.5f, variant = 0)

    @Test
    fun `density maps to the line count range`() {
        assertEquals(6, RibbonsGenerator.lineCount(0f))
        assertEquals(24, RibbonsGenerator.lineCount(1f))
        assertEquals(6, RibbonsGenerator.lineCount(-1f)) // clamped
        assertEquals(24, RibbonsGenerator.lineCount(2f))
    }

    @Test
    fun `the lines stay nested — every control point is ordered across the bundle`() {
        val count = 15
        for (k in 0 until 4) {
            val ys = (0 until count).map { RibbonsGenerator.lineControls(it, count, spine, splay = 0f)[k * 2 + 1] }
            assertTrue("control $k is not ordered: $ys", ys.zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test
    fun `the fan opens — the lines are further apart at the end than at the start`() {
        val count = 10
        val first = RibbonsGenerator.lineControls(0, count, spine, splay = 0f)
        val last = RibbonsGenerator.lineControls(count - 1, count, spine, splay = 0f)
        val atStart = abs(last[1] - first[1])
        val atEnd = abs(last[7] - first[7])
        assertTrue("start $atStart should be well inside end $atEnd", atEnd > atStart * 2f)
    }

    @Test
    fun `the middle line sits on the spine, whatever the splay`() {
        // An odd count has a true middle, and its place in the bundle is zero — so no spread and no shear reach it.
        val middle = RibbonsGenerator.lineControls(4, 9, spine, splay = 1f)
        for (k in 0 until 4) {
            assertEquals(spine.xs[k], middle[k * 2], 1e-6f)
            assertEquals(spine.ys[k], middle[k * 2 + 1], 1e-6f)
        }
    }

    @Test
    fun `splay moves the interior points oppositely and leaves the ends alone`() {
        val ruled = RibbonsGenerator.lineControls(0, 9, spine, splay = 0f)
        val twisted = RibbonsGenerator.lineControls(0, 9, spine, splay = 1f)
        // The ends carry the fan and nothing else, so they must not move.
        assertEquals(ruled[1], twisted[1], 1e-6f)
        assertEquals(ruled[7], twisted[7], 1e-6f)
        assertTrue("interior points should move", abs(twisted[3] - ruled[3]) > 0.01f)
        assertTrue("and move oppositely", (twisted[3] - ruled[3]) * (twisted[5] - ruled[5]) < 0f)
    }

    @Test
    fun `the lines stay nested at full splay too — it twists the bundle rather than crossing it`() {
        val count = 15
        for (k in 0 until 4) {
            val ys = (0 until count).map { RibbonsGenerator.lineControls(it, count, spine, splay = 1f)[k * 2 + 1] }
            assertTrue("control $k is not ordered at full splay: $ys", ys.zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test
    fun `the splay never moves a line along the sweep, only across it`() {
        // A control point pushed along the curve changes how fast the line travels it and barely moves the drawn
        // shape — a knob that looks broken. Every x must therefore be the spine's own.
        val twisted = RibbonsGenerator.lineControls(0, 9, spine, splay = 1f)
        for (k in 0 until 4) assertEquals(spine.xs[k], twisted[k * 2], 1e-6f)
    }

    @Test
    fun `a single line sits on the spine rather than dividing by zero`() {
        val only = RibbonsGenerator.lineControls(0, 1, spine, splay = 1f)
        for (k in 0 until 4) assertEquals(spine.ys[k], only[k * 2 + 1], 1e-6f)
    }

    @Test
    fun `the spread knob widens the bundle at both ends`() {
        val tight = RibbonsGenerator.spine(3L, scale = 0f, variant = 0)
        val wide = RibbonsGenerator.spine(3L, scale = 1f, variant = 0)
        assertTrue("open end", wide.endSpread > tight.endSpread * 2f)
        assertTrue("closed end scales with it", wide.startSpread > tight.startSpread)
    }

    @Test
    fun `the fan closes one end where the weave keeps both open`() {
        val fan = RibbonsGenerator.spine(3L, scale = 0.5f, variant = 0)
        val weave = RibbonsGenerator.spine(3L, scale = 0.5f, variant = 1)
        // Same open end either way — the shape is which end closes, not how wide the bundle is.
        assertEquals(fan.endSpread, weave.endSpread, 1e-6f)
        assertTrue("a fan converges", fan.startSpread < fan.endSpread * 0.2f)
        assertTrue("a weave does not", weave.startSpread > weave.endSpread * 0.5f)
    }

    @Test
    fun `the same seed yields the same spine, so a recipe reproduces`() {
        val a = RibbonsGenerator.spine(7L, scale = 0.5f, variant = 0)
        val b = RibbonsGenerator.spine(7L, scale = 0.5f, variant = 0)
        assertTrue(a.xs.contentEquals(b.xs) && a.ys.contentEquals(b.ys))
    }
}
