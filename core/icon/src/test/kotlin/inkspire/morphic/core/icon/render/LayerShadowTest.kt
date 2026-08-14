package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers a glow or a drop shadow is built from.
 *
 * **Not here because two renderers must agree** — these are the first effects only one path can draw, so nothing is
 * competing with this arithmetic. It is here for the other reason the `render` package keeps its decisions separate
 * from its drawing: pulled out of `IconRenderer` the numbers are checkable on the JVM, where every line of that
 * class needs an emulator.
 */
class LayerShadowTest {

    @Test
    fun `a radius is a fraction of the box, so one recipe is as soft at every bake size`() {
        assertEquals(9.6f, LayerShadow.radiusPxOrNull(radius = 0.1f, sizePx = 96)!!, 0.001f)
        assertEquals(28.8f, LayerShadow.radiusPxOrNull(radius = 0.1f, sizePx = 288)!!, 0.001f)
    }

    @Test
    fun `no radius comes back null rather than zero`() {
        // The point of the nullable: `BlurMaskFilter` **rejects** a non-positive radius, so a slider at its floor
        // would throw rather than draw. Null is read as "skip the blur", which is the hard-edged shadow the user
        // asked for — a legitimate look, and the one a long shadow is built from.
        assertNull(LayerShadow.radiusPxOrNull(radius = 0f, sizePx = 192))
        assertNull(LayerShadow.radiusPxOrNull(radius = -0.5f, sizePx = 192))
    }

    @Test
    fun `a radius too small to soften anything is also null`() {
        // Half a pixel of blur is not a blur, and asking the platform for one costs a mask extraction that produces
        // the silhouette back. The bound is in pixels rather than in the fraction, since the fraction that reaches
        // it depends entirely on the bake size.
        assertNull(LayerShadow.radiusPxOrNull(radius = 0.001f, sizePx = 96))
        assertTrue(LayerShadow.radiusPxOrNull(radius = 0.001f, sizePx = 4096) != null)
    }

    @Test
    fun `a spread is a fraction of the box too, and never negative`() {
        assertEquals(19.2f, LayerShadow.spreadPx(spread = 0.1f, sizePx = 192), 0.001f)
        // Clamped rather than nullable, because zero spread is simply "do not grow it" — there is no platform call
        // waiting to reject it, so it needs no second state.
        assertEquals(0f, LayerShadow.spreadPx(spread = -1f, sizePx = 192), 0.001f)
    }

    @Test
    fun `a wider spread is drawn as more copies, so the ring stays a circle`() {
        // The scallop between adjacent copies is `1 - cos(pi/N)` of the spread — under two percent at twelve, which
        // is why the floor is there. Growing N with the spread keeps it there as the ring gets bigger.
        val narrow = LayerShadow.spreadSteps(spreadPx = 2f)
        val wide = LayerShadow.spreadSteps(spreadPx = 40f)

        assertTrue(narrow >= 12)
        assertTrue(wide > narrow)
    }

    @Test
    fun `the copy count is bounded, since past a point the ring is already round`() {
        assertTrue(LayerShadow.spreadSteps(spreadPx = 10_000f) <= 48)
    }

    @Test
    fun `a throw is a signed fraction of the box`() {
        assertEquals(7.68f, LayerShadow.offsetPx(offset = 0.04f, sizePx = 192), 0.001f)
        assertEquals(-7.68f, LayerShadow.offsetPx(offset = -0.04f, sizePx = 192), 0.001f)
    }
}
