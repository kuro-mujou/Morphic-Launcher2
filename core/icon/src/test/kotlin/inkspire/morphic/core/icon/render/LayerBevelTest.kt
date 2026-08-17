package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a bevel's light is and what each slope catches of it.
 *
 * Here for [LayerShadowTest]'s reason: only the bake draws a bevel, so nothing is competing with this arithmetic —
 * it is separated because every line of `IconRenderer` needs an emulator, and because **every failure available here
 * is silent**. A sign flipped in the light vector produces a perfectly convincing bevel lit from the opposite corner;
 * a missing subtraction produces a convincing brightness control. Neither throws, and neither looks broken.
 */
class LayerBevelTest {

    /** The default light: from the top-left, halfway up. */
    private val topLeft = LayerBevel.light(angleDegrees = 45f, altitudeDegrees = 45f)

    @Test
    fun `a flat surface catches exactly what a flat surface catches, which is nothing extra`() {
        // The subtraction, and the whole reason a bevel appears only at the edges: without it the flat interior of
        // every icon would come out uniformly brightened and the effect would read as a brightness control.
        assertEquals(0f, LayerBevel.relief(slopeX = 0f, slopeY = 0f, light = topLeft), 0.0001f)
    }

    @Test
    fun `a slope facing the light is lit and the opposite one is shaded`() {
        // A bump's left edge rises to the right; its right edge falls. Lit from the top-left, the first catches the
        // light and the second turns away — which is the arrangement everyone reads as raised.
        val leftEdge = LayerBevel.relief(slopeX = 0.6f, slopeY = 0f, light = topLeft)
        val rightEdge = LayerBevel.relief(slopeX = -0.6f, slopeY = 0f, light = topLeft)

        assertTrue("left edge should be lit, was $leftEdge", leftEdge > 0f)
        assertTrue("right edge should be shaded, was $rightEdge", rightEdge < 0f)
    }

    @Test
    fun `the top edge is lit too, so a light from the corner reaches both of its sides`() {
        val topEdge = LayerBevel.relief(slopeX = 0f, slopeY = 0.6f, light = topLeft)
        val bottomEdge = LayerBevel.relief(slopeX = 0f, slopeY = -0.6f, light = topLeft)

        assertTrue("top edge should be lit, was $topEdge", topEdge > 0f)
        assertTrue("bottom edge should be shaded, was $bottomEdge", bottomEdge < 0f)
    }

    @Test
    fun `turning the light by half a turn swaps which side is lit`() {
        val fromBottomRight = LayerBevel.light(angleDegrees = 225f, altitudeDegrees = 45f)

        assertTrue(LayerBevel.relief(slopeX = 0.6f, slopeY = 0f, light = topLeft) > 0f)
        assertTrue(LayerBevel.relief(slopeX = 0.6f, slopeY = 0f, light = fromBottomRight) < 0f)
    }

    @Test
    fun `a lower light rakes across the surface and finds more of the slope`() {
        val low = LayerBevel.light(angleDegrees = 45f, altitudeDegrees = 15f)
        val high = LayerBevel.light(angleDegrees = 45f, altitudeDegrees = 70f)

        assertTrue(
            LayerBevel.relief(slopeX = 0.6f, slopeY = 0f, light = low) >
                LayerBevel.relief(slopeX = 0.6f, slopeY = 0f, light = high),
        )
    }

    /**
     * The behaviour at the top of the altitude control, and it is **not** that the bevel switches off.
     *
     * Directly overhead there is no horizontal component left, so the light no longer favours one side — but a
     * tilted surface still catches less of an overhead light than a flat one does, so *every* slope shades. What
     * comes out is a uniform darkened rim rather than a lit side and an unlit one, which is a real look and the
     * reason the slider runs the whole way up.
     */
    @Test
    fun `an overhead light shades every slope alike rather than none of them`() {
        val overhead = LayerBevel.light(angleDegrees = 45f, altitudeDegrees = 90f)

        val lit = LayerBevel.relief(slopeX = 0.6f, slopeY = 0f, light = overhead)
        val away = LayerBevel.relief(slopeX = -0.6f, slopeY = 0f, light = overhead)

        assertTrue("both sides shade, was $lit", lit < 0f)
        assertEquals("and by the same amount", lit, away, 0.0001f)
    }

    @Test
    fun `the slope scale cancels the width, so a wide bevel is no weaker than a narrow one`() {
        // A blurred edge spreads its whole rise over about twice the blur radius, so its gradient falls as `1 / r`.
        // Scaling by the radius is what stops the size control from doubling as an intensity control — backwards,
        // at that, since widening a bevel would otherwise fade it.
        fun reliefAt(radiusPx: Float): Float {
            val gradientPerPixel = 1f / (2f * radiusPx)
            return LayerBevel.relief(LayerBevel.slopeScale(radiusPx) * gradientPerPixel, 0f, topLeft)
        }

        assertEquals(reliefAt(4f), reliefAt(24f), 0.0001f)
    }

    @Test
    fun `a band carries the colour it was given and the alpha it was asked for`() {
        assertEquals(0x80FF8040.toInt(), LayerBevel.banded(0xFFFF8040.toInt(), amount = 128f / 255f))
        // Clamped at both ends, since a strength and a relief multiplied together are not obliged to be sensible.
        assertEquals(0xFFFFFFFF.toInt(), LayerBevel.banded(0x00FFFFFF, amount = 4f))
        assertEquals(0x00000000, LayerBevel.banded(0xFF000000.toInt(), amount = -1f))
    }
}
