package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How wide a box a progressive blur softens its copy with.
 *
 * The last of the arithmetic-only derivations, here for [LayerShadowTest]'s reason: only the bake draws this, so
 * nothing is competing with it — the number is separated because a wrong one produces a plausible-looking blur
 * rather than an error, and because `IconRenderer` needs an emulator for every line.
 *
 * **This used to describe a *downscale factor*, because the blur used to be a `Bitmap.scale` down and back
 * up.** That is not a blur — bilinear downscaling samples a 2×2 neighbourhood, so a 30× reduction threw away
 * almost everything it was meant to average, and edges came out terraced. `BitmapBlur` replaced it.
 *
 * The ramp's own stops moved to [LayerGradientTest] with the function, when a vignette turned out to ask the
 * same question of the same two numbers.
 */
class LayerProgressiveBlurTest {

    @Test
    fun `a bigger radius asks for a bigger box, which is what makes it blurrier`() {
        val gentle = LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.02f, sizePx = 400)!!
        val heavy = LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.08f, sizePx = 400)!!

        assertTrue("gentle $gentle should stay smaller than heavy $heavy", gentle < heavy)
    }

    @Test
    fun `the radius is a fraction of the box, so one recipe blurs the same at every bake size`() {
        // A twentieth of the box is 20px at 400 and 40px at 800 — twice the reach on twice the picture, which is
        // the same blur relative to the icon.
        val small = LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.05f, sizePx = 400)!!
        val large = LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.05f, sizePx = 800)!!

        assertEquals(2f, large.toFloat() / small.toFloat(), 0.2f)
    }

    @Test
    fun `no radius comes back null rather than a box of nothing`() {
        // Null is what lets the renderer skip the work entirely: a blur by zero is a copy, and the pipeline must
        // not be handed one.
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0f, sizePx = 192))
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = -1f, sizePx = 192))
    }

    @Test
    fun `a radius too small to soften anything is null too`() {
        // Under a pixel of blur there is nothing to average, and the bound is in pixels because which *fraction*
        // reaches it depends entirely on the bake size.
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.001f, sizePx = 192))
        assertTrue(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.001f, sizePx = 4096) != null)
    }

    @Test
    fun `a tiny bitmap comes back null rather than being blurred into nothing`() {
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.5f, sizePx = 2))
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.5f, sizePx = 1))
    }

    @Test
    fun `a radius reachable only mid-drag comes back null, not a copy`() {
        // The crash this file exists to have caught, in its current form. The studio's sliders are continuous —
        // their step governs only the stepper buttons — so a finger passes through values like this on the way up,
        // and on a small bake they resolve to less than a pixel of blur.
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.002f, sizePx = 48))
        assertNull(LayerProgressiveBlur.boxRadiusPxOrNull(radius = 0.0005f, sizePx = 800))
    }

    @Test
    fun `the box never exceeds the bitmap it is applied to`() {
        val box = LayerProgressiveBlur.boxRadiusPxOrNull(radius = 4f, sizePx = 64)!!

        assertTrue("$box must stay within the bitmap", box <= 64)
    }
}
