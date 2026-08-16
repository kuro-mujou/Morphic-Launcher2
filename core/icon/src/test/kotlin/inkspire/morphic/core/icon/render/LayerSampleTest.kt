package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a pixel from between pixels.
 *
 * **The alpha weighting is the whole reason this is testable code rather than four lines inside a loop**, and it is
 * `LayerPixelate.averageArgb`'s lesson repeated: an icon is mostly transparent, a transparent pixel is almost always
 * transparent *black*, and blending by colour alone therefore drags every displaced edge toward black. The naive
 * version passes every other assertion here and produces a dark fringe on screen.
 */
class LayerSampleTest {

    /** A 2×2 buffer: opaque red, opaque blue on the top row; transparent, opaque white beneath. */
    private val pixels = intArrayOf(
        0xFFFF0000.toInt(), 0xFF0000FF.toInt(),
        0x00000000, 0xFFFFFFFF.toInt(),
    )

    private fun alpha(argb: Int) = argb ushr 24 and 0xFF
    private fun red(argb: Int) = argb shr 16 and 0xFF
    private fun green(argb: Int) = argb shr 8 and 0xFF
    private fun blue(argb: Int) = argb and 0xFF

    @Test
    fun `a whole position reads that pixel exactly`() {
        assertEquals(0xFFFF0000.toInt(), LayerSample.bilinear(pixels, 2, 0f, 0f))
        assertEquals(0xFF0000FF.toInt(), LayerSample.bilinear(pixels, 2, 1f, 0f))
        assertEquals(0xFFFFFFFF.toInt(), LayerSample.bilinear(pixels, 2, 1f, 1f))
    }

    @Test
    fun `halfway between two opaque pixels is halfway between their colours`() {
        val sampled = LayerSample.bilinear(pixels, 2, 0.5f, 0f)

        assertEquals(255, alpha(sampled))
        assertEquals(127, red(sampled))
        assertEquals(127, blue(sampled))
    }

    @Test
    fun `a transparent neighbour lowers the alpha without darkening the colour`() {
        // **The one that fails on the naive version.** Halfway onto a transparent black pixel, the red must stay
        // red at half alpha — averaging the colours would give a half-alpha *maroon*, and a screenful of those is
        // the dark fringe that reads as a rendering fault.
        val sampled = LayerSample.bilinear(pixels, 2, 0f, 0.5f)

        assertEquals(127, alpha(sampled))
        assertEquals(255, red(sampled))
        assertEquals(0, green(sampled))
        assertEquals(0, blue(sampled))
    }

    @Test
    fun `outside the buffer is transparent rather than clamped`() {
        // `IconRenderer.resample`'s own rule one level down: an icon genuinely is transparent out there, and
        // clamping would smear the outermost row outward wherever a displacement reaches past the box.
        assertEquals(0, LayerSample.bilinear(pixels, 2, -4f, 0f))
        assertEquals(0, LayerSample.bilinear(pixels, 2, 0f, 9f))
    }

    @Test
    fun `a sample straddling the edge fades rather than repeating`() {
        // Half on the last column and half past it: the absent neighbour weighs nothing, so what comes out is the
        // edge pixel at half strength. That falls out of the weighting rather than being a special case.
        val sampled = LayerSample.bilinear(pixels, 2, 1.5f, 0f)

        assertEquals(127, alpha(sampled))
        assertEquals(0xFF, blue(sampled))
    }

    @Test
    fun `every sample stays inside a byte`() {
        // Float weights that do not quite sum to one would otherwise round up past 255 and wrap a channel, which
        // shows as a single wrong-coloured pixel somewhere in the artwork.
        for (step in 0..40) {
            val sampled = LayerSample.bilinear(pixels, 2, step * 0.025f, step * 0.025f)
            assertTrue(alpha(sampled) in 0..255)
            assertTrue(red(sampled) in 0..255)
            assertTrue(green(sampled) in 0..255)
            assertTrue(blue(sampled) in 0..255)
        }
    }
}
