package inkspire.morphic.data.wallpaper.internal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reduction a wallpaper goes through before any text asks how bright it is.
 *
 * **Guarded because it fails as a color, not as a crash**: a cell averaged in the wrong space still yields a number in
 * range, and text over it just picks the wrong ink.
 */
class LuminanceMapsTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `cells keep the picture's own sides apart`() {
        // Left half black, right half white, two rows of four.
        val image = IntArray(8) { if (it % 4 < 2) black else white }

        val map = luminanceMapOf(image, width = 4, height = 2, columns = 2)

        assertEquals(1, map.rows)
        assertEquals(0f, map[0, 0], 1e-4f)
        assertEquals(1f, map[1, 0], 1e-4f)
        assertEquals(0.5f, map.mean, 1e-4f)
    }

    @Test
    fun `a cell is the mean of its pixels' luminances, not the luminance of their mean color`() {
        // Half black and half white in one cell is a 50% bright patch. Averaging the *colors* first gives mid-gray
        // 0x80, whose luminance is about 0.22 — below the point where light ink wins, which is the wrong answer.
        val image = intArrayOf(black, white)

        val map = luminanceMapOf(image, width = 2, height = 1, columns = 1)

        assertEquals(0.5f, map[0, 0], 1e-4f)
    }

    @Test
    fun `rows follow the proportions and never outnumber the pixels`() {
        val tall = luminanceMapOf(IntArray(10 * 40) { white }, width = 10, height = 40, columns = 5)
        assertEquals(20, tall.rows)

        val tiny = luminanceMapOf(intArrayOf(white), width = 1, height = 1, columns = 48)
        assertEquals(1, tiny.columns)
        assertEquals(1, tiny.rows)
    }
}
