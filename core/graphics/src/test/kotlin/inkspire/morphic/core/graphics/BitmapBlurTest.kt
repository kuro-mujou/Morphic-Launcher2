package inkspire.morphic.core.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a blur is supposed to do to an array of pixels.
 *
 * **These exist because the thing they replace was not a blur.** `core:icon` approximated one by scaling a bitmap
 * down and back up, which on a wide radius reduces the image by 30× or more — and bilinear downscaling samples a 2×2
 * neighbourhood, so almost every pixel it was meant to average was simply discarded. The result was terraced rather
 * than soft, and nothing about it threw. The assertions below are the properties that separate the two.
 */
class BitmapBlurTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun alphaOf(argb: Int) = argb ushr 24
    private fun redOf(argb: Int) = argb shr 16 and 0xFF
    private fun greenOf(argb: Int) = argb shr 8 and 0xFF
    private fun blueOf(argb: Int) = argb and 0xFF

    /** A step edge: the left half opaque white, the right half opaque black. */
    private fun stepEdge(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
        if (i % width < width / 2) argb(0xFF, 0xFF, 0xFF, 0xFF) else argb(0xFF, 0, 0, 0)
    }

    @Test
    fun `a flat field is unchanged, whatever the radius`() {
        // The window is clipped at the edges and divided by what it actually holds, so a constant field stays
        // constant right up to the border rather than fading toward it.
        val pixels = IntArray(16 * 16) { argb(0xFF, 0x40, 0x80, 0xC0) }

        BitmapBlur.blur(pixels, 16, 16, radiusPx = 5)

        for (p in pixels) assertEquals(argb(0xFF, 0x40, 0x80, 0xC0), p)
    }

    @Test
    fun `an edge comes out monotonic, which is what separates a blur from a resample`() {
        // The failure being guarded: a scale-down-and-up produces a *staircase* across an edge — flat runs with
        // jumps between them. A real blur is strictly graded, so no two neighbours are equal across the ramp and
        // none of them ever goes back up.
        val width = 64
        val pixels = stepEdge(width, 1)

        BitmapBlur.blur(pixels, width, 1, radiusPx = 6)

        var previous = 0x100
        var graded = 0
        for (x in 0 until width) {
            val value = redOf(pixels[x])
            assertTrue("row must never brighten going right, did at $x", value <= previous)
            if (value != previous && previous != 0x100) graded++
            previous = value
        }
        // A 3-pass box of radius 6 spreads over roughly 36 pixels; a staircase would show a handful of steps.
        assertTrue("expected a wide graded ramp, got $graded steps", graded > 20)
    }

    @Test
    fun `the blur spreads about as far as the radius says`() {
        val width = 64
        val pixels = stepEdge(width, 1)

        BitmapBlur.blur(pixels, width, 1, radiusPx = 4)

        // Well away from the seam the picture is untouched at both ends.
        assertEquals(0xFF, redOf(pixels[2]))
        assertEquals(0x00, redOf(pixels[width - 3]))
        // And at the seam it is somewhere in between rather than still binary.
        assertTrue(redOf(pixels[width / 2]) in 0x20..0xE0)
    }

    /**
     * The property the wallpaper's own version never needed and an icon cannot do without.
     *
     * A transparent pixel is almost always transparent **black**, so averaging the colour channels directly drags
     * black into everything near an edge. Blurring premultiplied is what stops a blurred icon growing a dark fringe.
     */
    @Test
    fun `blurring a coloured shape on transparency does not drag black into it`() {
        val width = 32
        // A red square on transparent black — the ordinary shape of an icon layer.
        val pixels = IntArray(width * width) { i ->
            val x = i % width
            val y = i / width
            if (x in 8..23 && y in 8..23) argb(0xFF, 0xFF, 0, 0) else 0
        }

        BitmapBlur.blur(pixels, width, width, radiusPx = 4)

        // Every pixel that kept any coverage is still red — never a darkened red, which is what an
        // un-premultiplied average produces along the whole boundary.
        for (p in pixels) {
            if (alphaOf(p) < 8) continue
            assertTrue("expected red to survive, got ${p.toUInt().toString(16)}", redOf(p) > 0xE0)
            assertEquals(0, greenOf(p))
            assertEquals(0, blueOf(p))
        }
    }

    @Test
    fun `alpha itself is blurred, so the shape softens rather than only its colour`() {
        val width = 32
        val pixels = IntArray(width * width) { i ->
            if (i % width in 8..23 && i / width in 8..23) argb(0xFF, 0xFF, 0, 0) else 0
        }

        BitmapBlur.blur(pixels, width, width, radiusPx = 4)

        // A pixel just outside the original square has picked up partial coverage.
        val justOutside = alphaOf(pixels[12 * width + 6])
        assertTrue("expected a soft edge, alpha was $justOutside", justOutside in 1..0xFE)
    }

    @Test
    fun `a radius below one leaves the pixels exactly alone`() {
        val pixels = stepEdge(8, 8)
        val before = pixels.copyOf()

        BitmapBlur.blur(pixels, 8, 8, radiusPx = 0)

        assertTrue(before.contentEquals(pixels))
    }

    @Test
    fun `a wider blur asks for a bigger box, and sigma zero asks for none`() {
        assertEquals(0, BitmapBlur.boxRadiusFor(0f))
        assertTrue(BitmapBlur.boxRadiusFor(12f) > BitmapBlur.boxRadiusFor(3f))
        // Three boxes of radius r have variance 3·((2r+1)²−1)/12; at sigma 10 that is a radius near 10.
        assertEquals(10f, BitmapBlur.boxRadiusFor(10f).toFloat(), 2f)
    }

    /**
     * The rule that replaced a constant downscale — and the constant is what made the wallpaper backdrop look like a
     * low-resolution copy of itself even at the strengths where no blur was applied at all.
     */
    @Test
    fun `the downscale follows the radius, and no blur means no reduction`() {
        assertEquals(1, BitmapBlur.downscaleFor(0f))
        assertEquals(1, BitmapBlur.downscaleFor(2f))
        assertTrue(BitmapBlur.downscaleFor(40f) > BitmapBlur.downscaleFor(12f))
        assertEquals(8, BitmapBlur.downscaleFor(1000f))
    }
}
