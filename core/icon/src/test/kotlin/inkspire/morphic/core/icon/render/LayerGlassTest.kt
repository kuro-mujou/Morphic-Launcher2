package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a glass reads its pixel from, and the sheen it strikes.
 *
 * [LayerBevelTest]'s reason: only the bake draws a glass, so nothing competes with this arithmetic — and every
 * failure here is silent. A refraction with its sign flipped is a coherent lens pointing the wrong way, shrinking
 * the artwork into the swell instead of magnifying it under one; a sheen that dropped the alpha would float a
 * highlight in the transparent air beside the glyph. Neither throws.
 */
class LayerGlassTest {

    private val out = FloatArray(2)

    @Test
    fun `the sampling moves along the up-slope, so a convex swell magnifies`() {
        // On the right flank of a central swell the surface falls to the right, so its x-slope is negative; the read
        // then moves to a *smaller* x — toward the peak — which is what pulls the content outward and magnifies it.
        // The sign that, flipped, would minify instead.
        LayerGlass.sourceOf(x = 20, y = 0, slopeX = -0.5f, slopeY = 0f, refractPx = 4f, out = out)
        assertEquals(18f, out[0], 0.0001f)

        // And the left flank, sloping up to the right, reads from further out — the other half of the same lens.
        LayerGlass.sourceOf(x = 20, y = 0, slopeX = 0.5f, slopeY = 0f, refractPx = 4f, out = out)
        assertEquals(22f, out[0], 0.0001f)
    }

    @Test
    fun `a flat surface reads from exactly where it is`() {
        LayerGlass.sourceOf(x = 20, y = 30, slopeX = 0f, slopeY = 0f, refractPx = 8f, out = out)

        assertEquals(20f, out[0], 0.0001f)
        assertEquals(30f, out[1], 0.0001f)
    }

    @Test
    fun `refraction is a fraction of the box`() {
        assertEquals(7.68f, LayerGlass.refractionPx(LayerEffect.Glass(refraction = 0.08f), sizePx = 96), 0.0001f)
    }

    @Test
    fun `the surface blur is floored, never zero, so there is always a height field to slope`() {
        // Unlike a bevel, a glass is only reached when it bends or shines, so it always needs a surface — and
        // `BlurMaskFilter` rejects a radius of zero. Softness at the bottom of its track is a hard-edged shard, not
        // an absence.
        assertTrue(LayerGlass.radiusPx(LayerEffect.Glass(softness = 0f), sizePx = 96) >= 1f)
        assertEquals(12f, LayerGlass.radiusPx(LayerEffect.Glass(softness = 0.125f), sizePx = 96), 0.0001f)
    }

    @Test
    fun `a surface facing away from the light catches no sheen`() {
        val glass = LayerEffect.Glass()
        val pixel = 0xFF3366CC.toInt()

        assertEquals(pixel, LayerGlass.sheened(pixel, relief = 0f, glass = glass))
        assertEquals(pixel, LayerGlass.sheened(pixel, relief = -0.8f, glass = glass))
    }

    @Test
    fun `a lit surface brightens toward the sheen, keeping its own alpha`() {
        val glass = LayerEffect.Glass(highlightArgb = 0xFFFFFFFF.toInt(), highlightStrength = 1f)
        val pixel = 0xFF3366CC.toInt()

        val lit = LayerGlass.sheened(pixel, relief = 1f, glass = glass)

        // Screened toward white where it faces the light — and at full relief and full strength, to white.
        assertEquals(0xFFFFFFFF.toInt(), lit)
    }

    @Test
    fun `the sheen never lands on transparent artwork, so it cannot float off the glass`() {
        // A pixel that bent in from beyond the artwork comes back transparent; screening light onto it must leave it
        // transparent, which is what keeps the highlight on the surface rather than in the air beside it.
        val glass = LayerEffect.Glass(highlightStrength = 1f)

        assertEquals(0, LayerGlass.sheened(0x00000000, relief = 1f, glass = glass) ushr 24)
    }

    @Test
    fun `a sheen at no strength leaves the artwork alone, however lit`() {
        val glass = LayerEffect.Glass(highlightStrength = 0f)
        val pixel = 0xFF3366CC.toInt()

        assertEquals(pixel, LayerGlass.sheened(pixel, relief = 1f, glass = glass))
    }
}
