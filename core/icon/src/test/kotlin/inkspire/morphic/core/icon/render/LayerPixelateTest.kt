package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How big a pixelate's cells are and what colour each dot comes out.
 *
 * **The averaging is what these exist for.** Straight ARGB averaging passes a casual read and produces a dark fringe
 * around every piece of artwork, because a transparent pixel is almost always transparent *black* and gets counted
 * as black. On screen that reads as a rendering fault rather than as a mistake in an average, which is exactly the
 * kind of wrong this package keeps its arithmetic separate to catch.
 */
class LayerPixelateTest {

    private val opaqueRed = 0xFFFF0000.toInt()
    private val transparentBlack = 0x00000000

    /** A [size]-square image where every pixel is [argb]. */
    private fun solid(size: Int, argb: Int) = IntArray(size * size) { argb }

    @Test
    fun `a uniform block averages to its own colour`() {
        val pixels = solid(4, opaqueRed)

        assertEquals(opaqueRed, LayerPixelate.averageArgb(pixels, sizePx = 4, left = 0, top = 0, cellPx = 4))
    }

    @Test
    fun `transparent pixels dilute the alpha without darkening the colour`() {
        // The assertion this file is for. Half the block is transparent black; the dot must come out *red at half
        // alpha*, not dark red — averaging the channels straight would give a red of 128.
        val pixels = IntArray(4) { if (it < 2) opaqueRed else transparentBlack }

        val average = LayerPixelate.averageArgb(pixels, sizePx = 2, left = 0, top = 0, cellPx = 2)

        assertEquals("alpha", 128, average ushr 24 and 0xFF)
        assertEquals("red", 255, average shr 16 and 0xFF)
        assertEquals("green", 0, average shr 8 and 0xFF)
        assertEquals("blue", 0, average and 0xFF)
    }

    @Test
    fun `a fully transparent block comes back as nothing`() {
        // Which is what lets the renderer skip drawing it, and what keeps the artwork's outline made of dots rather
        // than of a square block of them.
        val pixels = solid(4, transparentBlack)

        assertEquals(0, LayerPixelate.averageArgb(pixels, sizePx = 4, left = 0, top = 0, cellPx = 4))
    }

    @Test
    fun `two opaque colours average between them`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val pixels = intArrayOf(red, blue, red, blue)

        val average = LayerPixelate.averageArgb(pixels, sizePx = 2, left = 0, top = 0, cellPx = 2)

        assertEquals(255, average ushr 24 and 0xFF)
        assertEquals(128, average shr 16 and 0xFF)
        assertEquals(128, average and 0xFF)
    }

    @Test
    fun `a block reaching past the edge averages only what is there`() {
        // The last cell in a row is partial. Clamping or reading past the array would be a crash or a smear; what it
        // must do is average the pixels that exist.
        val pixels = intArrayOf(opaqueRed, opaqueRed, opaqueRed, opaqueRed)

        assertEquals(opaqueRed, LayerPixelate.averageArgb(pixels, sizePx = 2, left = 1, top = 1, cellPx = 8))
    }

    @Test
    fun `a cell is a fraction of the box, so one recipe pixelates the same at every bake size`() {
        val pixelate = LayerEffect.Pixelate(cellSize = 0.1f)

        assertEquals(9.6f, LayerPixelate.cellPx(pixelate, sizePx = 96), 0.001f)
        assertEquals(28.8f, LayerPixelate.cellPx(pixelate, sizePx = 288), 0.001f)
    }

    @Test
    fun `a cell never comes back below a pixel, which is a loop bound as well as a divisor`() {
        assertTrue(LayerPixelate.cellPx(LayerEffect.Pixelate(cellSize = 0f), sizePx = 192) >= 1f)
        assertTrue(LayerPixelate.cellPx(LayerEffect.Pixelate(cellSize = 0.0001f), sizePx = 48) >= 1f)
    }

    @Test
    fun `a full fill leaves no gap, and a half fill leaves a quarter cell either side`() {
        // The inset is *half* the gap, since the dot is centred — getting that wrong halves or doubles every gap.
        assertEquals(0f, LayerPixelate.insetPx(cellPx = 20f, fill = 1f), 0.001f)
        assertEquals(5f, LayerPixelate.insetPx(cellPx = 20f, fill = 0.5f), 0.001f)
    }

    @Test
    fun `full roundness is a circle, whatever the dot's size`() {
        // A fraction of the dot rather than a stored length, which is what keeps it a circle at every fill and every
        // bake size instead of a square with nicked corners on a large one.
        assertEquals(10f, LayerPixelate.cornerRadiusPx(dotPx = 20f, roundness = 1f), 0.001f)
        assertEquals(3f, LayerPixelate.cornerRadiusPx(dotPx = 6f, roundness = 1f), 0.001f)
        assertEquals(0f, LayerPixelate.cornerRadiusPx(dotPx = 20f, roundness = 0f), 0.001f)
    }
}
