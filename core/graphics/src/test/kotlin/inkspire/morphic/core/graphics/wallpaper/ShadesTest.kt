package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared brightness scale — a transposed channel or dropped alpha tints a whole wallpaper, so the packing is checked
 * here without a bitmap.
 */
class ShadesTest {

    @Test
    fun `a factor of one leaves the color untouched`() {
        val argb = 0xC0804020.toInt()
        assertEquals(argb, Shades.scale(argb, 1f))
    }

    @Test
    fun `a factor of a half darkens the color channels but keeps alpha`() {
        val scaled = Shades.scale(0xFF80402A.toInt(), 0.5f)
        assertEquals(0xFF, scaled ushr 24 and 0xFF) // alpha untouched
        assertEquals(0x40, scaled shr 16 and 0xFF) // 0x80 -> 0x40
        assertEquals(0x20, scaled shr 8 and 0xFF) // 0x40 -> 0x20
        assertEquals(0x15, scaled and 0xFF) // 0x2A -> 0x15
    }

    @Test
    fun `a factor of zero blacks out the color but keeps alpha`() {
        val scaled = Shades.scale(0x80FFFFFF.toInt(), 0f)
        assertEquals(0x80000000.toInt(), scaled)
    }
}
