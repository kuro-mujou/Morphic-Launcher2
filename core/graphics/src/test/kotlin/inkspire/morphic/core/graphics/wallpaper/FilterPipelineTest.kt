package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter passes' per-pixel arithmetic — the part that is silently wrong when it is wrong, tested off a bitmap.
 *
 * The same reason as the generators: a transposed channel tints the whole wallpaper and a dropped alpha turns it
 * transparent, neither of which throws. Only the pure `IntArray` passes are exercised here; the blur is `BitmapBlur`'s
 * own tests and `apply`'s bitmap plumbing is device work.
 */
class FilterPipelineTest {

    private val gray = 0xFF808080.toInt()

    @Test
    fun `vignette leaves the centre pixel untouched and darkens a corner`() {
        // 3x3 so the centre pixel sits exactly at distance 0.
        val pixels = IntArray(9) { gray }
        FilterPipeline.vignette(pixels, width = 3, height = 3, strength = 1f)

        assertEquals("centre unchanged", gray, pixels[4])
        assertTrue("corner darker than centre", channel(pixels[0], 0) < channel(gray, 0))
    }

    @Test
    fun `vignette keeps alpha`() {
        val pixels = IntArray(9) { gray }
        FilterPipeline.vignette(pixels, width = 3, height = 3, strength = 1f)

        for (pixel in pixels) assertEquals(0xFF, pixel ushr 24)
    }

    @Test
    fun `scanlines darkens even rows and leaves odd rows alone`() {
        // 2 wide, 2 tall: indices 0..1 are row 0, 2..3 are row 1.
        val pixels = IntArray(4) { gray }
        FilterPipeline.scanlines(pixels, width = 2, strength = 1f)

        assertTrue("row 0 darkened", channel(pixels[0], 0) < channel(gray, 0))
        assertTrue("row 0 darkened", channel(pixels[1], 0) < channel(gray, 0))
        assertEquals("row 1 untouched", gray, pixels[2])
        assertEquals("row 1 untouched", gray, pixels[3])
    }

    @Test
    fun `grain is deterministic for the same pixels`() {
        val first = IntArray(64) { gray }
        val second = IntArray(64) { gray }
        FilterPipeline.grain(first, strength = 0.5f)
        FilterPipeline.grain(second, strength = 0.5f)

        assertTrue("grain reproduces exactly", first.contentEquals(second))
    }

    @Test
    fun `grain actually perturbs the image`() {
        val pixels = IntArray(64) { gray }
        FilterPipeline.grain(pixels, strength = 0.5f)

        assertTrue("some pixel changed", pixels.any { it != gray })
    }

    @Test
    fun `grain clamps rather than wrapping past the channel bounds`() {
        // Black can only go up, white can only go down — a wrap would flash the opposite extreme.
        val black = IntArray(64) { 0xFF000000.toInt() }
        val white = IntArray(64) { 0xFFFFFFFF.toInt() }
        FilterPipeline.grain(black, strength = 1f)
        FilterPipeline.grain(white, strength = 1f)

        for (pixel in black) for (shift in intArrayOf(16, 8, 0)) assertTrue(channel(pixel, shift) in 0..255)
        for (pixel in white) for (shift in intArrayOf(16, 8, 0)) assertTrue(channel(pixel, shift) in 0..255)
        for (pixel in black) assertEquals(0xFF, pixel ushr 24)
    }

    @Test
    fun `grain moves black up and white down`() {
        val black = IntArray(64) { 0xFF000000.toInt() }
        FilterPipeline.grain(black, strength = 1f)

        assertNotEquals("black got lighter somewhere", 0, black.sumOf { channel(it, 0) })
    }

    private fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF
}
