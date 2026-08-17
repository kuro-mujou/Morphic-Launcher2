package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a progressive blur's copy is scaled down before being grown back.
 *
 * The last of the arithmetic-only derivations, here for [LayerShadowTest]'s reason: only the bake draws this, so
 * nothing is competing with it — the number is separated because a wrong one produces a plausible-looking blur
 * rather than an error, and because `IconRenderer` needs an emulator for every line.
 *
 * The ramp's own stops moved to [LayerGradientTest] with the function, when a vignette turned out to ask the
 * same question of the same two numbers.
 */
class LayerProgressiveBlurTest {

    @Test
    fun `a bigger radius scales further down, which is what makes it blurrier`() {
        // The whole of the blur is how small the image gets before being grown back, so this is the one number that
        // decides how soft the result is — and it runs the *opposite* way to the slider, which is worth pinning.
        val gentle = LayerProgressiveBlur.downscaledSidePx(radius = 0.02f, sizePx = 400)!!
        val heavy = LayerProgressiveBlur.downscaledSidePx(radius = 0.08f, sizePx = 400)!!

        assertTrue("gentle $gentle should stay larger than heavy $heavy", gentle > heavy)
    }

    @Test
    fun `the downscale is a fraction of the box, so one recipe blurs the same at every bake size`() {
        // A twentieth of the box is a radius of 20px at 400 and 40px at 800, so both land on the same side.
        assertEquals(
            LayerProgressiveBlur.downscaledSidePx(radius = 0.05f, sizePx = 400),
            LayerProgressiveBlur.downscaledSidePx(radius = 0.05f, sizePx = 800),
        )
    }

    @Test
    fun `no radius comes back null rather than the full size`() {
        // Null is what lets the renderer skip the work entirely: scaling to the original side and back would
        // allocate two bitmaps to produce a copy.
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = 0f, sizePx = 192))
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = -1f, sizePx = 192))
    }

    @Test
    fun `a radius too small to soften anything is null too`() {
        // Under a pixel of blur there is nothing to average, and the bound is in pixels because which *fraction*
        // reaches it depends entirely on the bake size.
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = 0.001f, sizePx = 192))
        assertTrue(LayerProgressiveBlur.downscaledSidePx(radius = 0.001f, sizePx = 4096) != null)
    }

    @Test
    fun `the downscale never reaches the full size, which would be a copy rather than a blur`() {
        val side = LayerProgressiveBlur.downscaledSidePx(radius = 0.005f, sizePx = 400)!!

        assertTrue("$side must be smaller than 400", side < 400)
        assertTrue("$side must leave something to interpolate", side >= 3)
    }

    @Test
    fun `a tiny box comes back null rather than throwing on an inverted clamp`() {
        // The same trap `stops` had: `coerceIn(3, sizePx - 1)` throws rather than clamping once the box reaches
        // three, and a bitmap that small is reachable — the draft of a layer tile is a few dozen pixels.
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = 0.5f, sizePx = 3))
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = 0.5f, sizePx = 1))
    }

    @Test
    fun `a radius reachable only mid-drag comes back null, not a copy`() {
        // The crash this file exists to have caught. The studio's sliders are continuous — their step governs only
        // the stepper buttons — so a finger passes through values like this on the way up, and on a small bake they
        // resolve to less than a pixel of blur. The caller must skip the effect entirely on a null; it used to
        // return an immutable copy instead, which the renderer then wrapped in a `Canvas` and threw.
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = 0.002f, sizePx = 48))
        assertNull(LayerProgressiveBlur.downscaledSidePx(radius = 0.0005f, sizePx = 800))
    }
}
