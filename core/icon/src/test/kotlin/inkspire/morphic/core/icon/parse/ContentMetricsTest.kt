package inkspire.morphic.core.icon.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ContentMetrics.of] — how much of its canvas a layer's artwork covers.
 *
 * **The far edges are the case to get right**, because they decide how far artwork is grown to fill its box: a
 * bound reported one pixel short scales every icon slightly too large, which is the kind of error that looks like
 * nothing on one icon and like a mess across a home screen.
 */
class ContentMetricsTest {

    private val size = 8
    private val opaque = 0xFF336699.toInt()

    /** A canvas with an opaque rectangle from ([left], [top]) to ([right], [bottom]) exclusive. */
    private fun canvas(left: Int, top: Int, right: Int, bottom: Int): IntArray {
        val pixels = IntArray(size * size)
        for (y in top until bottom) for (x in left until right) pixels[y * size + x] = opaque
        return pixels
    }

    @Test
    fun `a solid block reports its coverage and its edges`() {
        val metrics = ContentMetrics.of(canvas(2, 2, 6, 6), size)!!

        assertEquals(0.25f, metrics.left)
        // 5 is the last lit column and it *spans* to 6 — the off-by-one that would otherwise measure a one-pixel
        // glyph as having no width and divide to infinity.
        assertEquals(0.75f, metrics.right)
        assertEquals(0.5f, metrics.longestSide)
    }

    /** Artwork already touching every edge is already filling its canvas, so a fit leaves it alone. */
    @Test
    fun `artwork that reaches the edges measures a full side`() {
        assertEquals(1f, ContentMetrics.of(canvas(0, 0, size, size), size)!!.longestSide)
    }

    /** A wide, short glyph is fitted by its longer side, so growing it cannot push it out sideways. */
    @Test
    fun `a non-square glyph reports its longer side`() {
        val metrics = ContentMetrics.of(canvas(0, 3, size, 5), size)!!

        assertEquals(1f, metrics.width)
        assertEquals(0.25f, metrics.height)
        assertEquals(1f, metrics.longestSide)
    }

    /** Off-center ink reports an off-center middle, which is what the resolver translates back into place. */
    @Test
    fun `ink pushed into a corner reports an off-center middle`() {
        val metrics = ContentMetrics.of(canvas(0, 0, 4, 4), size)!!

        assertEquals(0.25f, metrics.centerX)
        assertEquals(0.25f, metrics.centerY)
    }

    /** Nothing drawn means nothing to scale — null, so the caller leaves the layer exactly as it is. */
    @Test
    fun `an empty canvas measures nothing rather than everything`() {
        assertNull(ContentMetrics.of(IntArray(size * size), size))
    }

    /**
     * The alpha floor earns its keep here. A near-invisible wash over the whole canvas is common in real icons, and
     * at a zero floor it measures as full coverage — which silently defeats the measurement rather than failing.
     */
    @Test
    fun `a nearly invisible wash is not ink`() {
        val washed = IntArray(size * size) { 0x08000000 }
        for (y in 3 until 5) for (x in 3 until 5) washed[y * size + x] = opaque

        assertEquals(0.25f, ContentMetrics.of(washed, size)!!.longestSide)
    }
}
