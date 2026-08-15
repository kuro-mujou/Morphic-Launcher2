package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How strong a progressive blur is and where along its ramp the layer stops being sharp.
 *
 * The last of the arithmetic-only derivations, here for [LayerShadowTest]'s reason: only the bake draws this, so
 * nothing is competing with it — the numbers are separated because a wrong one produces a plausible-looking blur
 * rather than an error, and because `IconRenderer` needs an emulator for every line.
 */
class LayerProgressiveBlurTest {

    private fun blur(sharpArea: Float = 0.2f, softness: Float = 0.4f) =
        LayerEffect.ProgressiveBlur(radius = 0.05f, sharpArea = sharpArea, softness = softness)

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
    fun `the stops are the sharp edge and the point of full blur, in that order`() {
        val (sharp, blurred) = LayerProgressiveBlur.stops(blur(sharpArea = 0.2f, softness = 0.4f)).toList()

        assertEquals(0.2f, sharp, 0.001f)
        assertEquals(0.6f, blurred, 0.001f)
    }

    @Test
    fun `no softness is a hard edge rather than an invalid gradient`() {
        // Two coincident stops are undefined, and a softness of zero is a legitimate request — so they are kept a
        // hair apart instead of the request being refused.
        val (sharp, blurred) = LayerProgressiveBlur.stops(blur(sharpArea = 0.5f, softness = 0f)).toList()

        assertTrue("stops must ascend", blurred > sharp)
        assertTrue("and only just", blurred - sharp < 0.01f)
    }

    @Test
    fun `a ramp that would run past the end is clamped, still ascending`() {
        val (sharp, blurred) = LayerProgressiveBlur.stops(blur(sharpArea = 0.9f, softness = 0.9f)).toList()

        assertEquals(1f, blurred, 0.001f)
        assertTrue(blurred > sharp)
    }

    @Test
    fun `a sharp area of everything still leaves an ascending pair`() {
        // The degenerate end: nothing gets blurred, and the gradient must still be constructible rather than
        // throwing on two stops at 1.
        val (sharp, blurred) = LayerProgressiveBlur.stops(blur(sharpArea = 1f, softness = 0f)).toList()

        assertTrue(blurred > sharp)
        assertTrue(blurred <= 1f)
    }
}
