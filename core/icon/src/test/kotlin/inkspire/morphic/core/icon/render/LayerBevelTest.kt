package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
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

    /**
     * The regression that took the icon off the screen.
     *
     * The bands were composited as two bitmaps through `PorterDuff.Mode.SCREEN` and `MULTIPLY`, which are **not** the
     * blends of those names: multiply is `[Sa × Da, Sc × Dc]`, so the result alpha is the product as well. A band
     * transparent across most of the artwork therefore multiplied its alpha by zero, and everything the band did not
     * cover was erased — the whole icon, except the shaded slopes. Blending per channel cannot do that, and this is
     * the assertion that says so.
     */
    @Test
    fun `lighting never touches the artwork's own alpha`() {
        val bevel = LayerEffect.Bevel()
        val opaque = 0xFF3366CC.toInt()

        assertEquals(0xFF, LayerBevel.lit(opaque, relief = 0.7f, bevel = bevel) ushr 24)
        assertEquals(0xFF, LayerBevel.lit(opaque, relief = -0.7f, bevel = bevel) ushr 24)
        // Including a half-covered edge pixel, which must stay exactly half covered.
        assertEquals(0x80, LayerBevel.lit(0x803366CC.toInt(), relief = -1f, bevel = bevel) ushr 24)
    }

    @Test
    fun `a flat surface comes back exactly as it went in`() {
        val pixel = 0xFF3366CC.toInt()

        assertEquals(pixel, LayerBevel.lit(pixel, relief = 0f, bevel = LayerEffect.Bevel()))
    }

    @Test
    fun `a lit slope brightens the artwork and a shaded one deepens it`() {
        val bevel = LayerEffect.Bevel(highlightStrength = 1f, shadowStrength = 1f)
        val pixel = 0xFF3366CC.toInt()

        val lit = LayerBevel.lit(pixel, relief = 0.5f, bevel = bevel)
        val shaded = LayerBevel.lit(pixel, relief = -0.5f, bevel = bevel)

        // Channel by channel, because "brighter" and "darker" are the whole claim.
        for (shift in intArrayOf(16, 8, 0)) {
            val base = (pixel shr shift) and 0xFF
            assertTrue(((lit shr shift) and 0xFF) > base)
            assertTrue(((shaded shr shift) and 0xFF) < base)
        }
    }

    @Test
    fun `a band at no strength leaves the artwork alone, whatever the slope`() {
        val off = LayerEffect.Bevel(highlightStrength = 0f, shadowStrength = 0f)
        val pixel = 0xFF3366CC.toInt()

        assertEquals(pixel, LayerBevel.lit(pixel, relief = 1f, bevel = off))
        assertEquals(pixel, LayerBevel.lit(pixel, relief = -1f, bevel = off))
    }

    @Test
    fun `a white highlight at full strength screens to white, and a black shadow multiplies to black`() {
        val full = LayerEffect.Bevel(highlightStrength = 1f, shadowStrength = 1f)
        val pixel = 0xFF3366CC.toInt()

        assertEquals(0xFFFFFFFF.toInt(), LayerBevel.lit(pixel, relief = 1f, bevel = full))
        assertEquals(0xFF000000.toInt(), LayerBevel.lit(pixel, relief = -1f, bevel = full))
    }
}
