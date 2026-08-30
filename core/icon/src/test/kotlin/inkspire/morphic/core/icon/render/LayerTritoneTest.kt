package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tritone's ramp, its luminance and how the grade is laid on.
 *
 * [LayerDitherTest]'s reason: only the bake draws this, and its failures are silent — a ramp built the wrong way
 * round maps shadows to the highlight color, a plausible upside-down grade that never throws.
 */
class LayerTritoneTest {

    private val shadow = 0xFF241B4E.toInt()
    private val mid = 0xFFB65A78.toInt()
    private val highlight = 0xFFFFD9A0.toInt()

    private fun assertChannels(expected: Int, actual: Int, tolerance: Int = 2) {
        for (shift in intArrayOf(16, 8, 0)) {
            val e = expected shr shift and 0xFF
            val a = actual shr shift and 0xFF
            assertTrue("channel at $shift: expected ~$e, was $a", kotlin.math.abs(e - a) <= tolerance)
        }
    }

    @Test
    fun `the ramp runs shadow to highlight through the mid`() {
        val ramp = LayerTritone.ramp(shadow, mid, highlight)

        assertEquals(256, ramp.size)
        assertChannels(shadow, ramp[0])
        assertChannels(highlight, ramp[255])
        // The two segments meet at the mid halfway up.
        assertChannels(mid, ramp[128])
    }

    @Test
    fun `luminance reads green as bright and blue as dark`() {
        assertEquals(255, LayerTritone.luminance(0xFFFFFFFF.toInt()))
        assertEquals(0, LayerTritone.luminance(0xFF000000.toInt()))
        // Rec. 709: green carries most of the sense of brightness, blue almost none.
        assertEquals(182, LayerTritone.luminance(0xFF00FF00.toInt()))
        assertEquals(18, LayerTritone.luminance(0xFF0000FF.toInt()))
    }

    @Test
    fun `a full grade replaces the color and keeps the alpha`() {
        val ramp = LayerTritone.ramp(shadow, mid, highlight)
        // A mid-gray pixel with a half alpha: its color becomes the ramp's mid entry, its alpha survives.
        val graded = LayerTritone.apply(0x80808080.toInt(), ramp, strength = 1f)

        assertEquals(0x80, graded ushr 24)
        assertChannels(ramp[LayerTritone.luminance(0x80808080.toInt())], graded or 0xFF000000.toInt())
    }

    @Test
    fun `no strength leaves the pixel untouched`() {
        val ramp = LayerTritone.ramp(shadow, mid, highlight)
        val pixel = 0xFF3366CC.toInt()

        assertEquals(pixel, LayerTritone.apply(pixel, ramp, strength = 0f))
    }

    @Test
    fun `a fully transparent pixel is left alone, having no tone to grade`() {
        val ramp = LayerTritone.ramp(shadow, mid, highlight)

        assertEquals(0, LayerTritone.apply(0, ramp, strength = 1f))
    }
}
