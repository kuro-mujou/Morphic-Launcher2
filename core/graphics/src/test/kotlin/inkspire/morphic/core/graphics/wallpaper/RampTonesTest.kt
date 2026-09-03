package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tone count and the off-by-one in where each tone sits — both invisible when wrong.
 *
 * A palette long enough to supply its own tones must land on its **own stops**, unblended: a count or an offset one out
 * draws a set of colors slightly beside the ones the user picked, which is a wallpaper nobody can point at as wrong.
 * And the floor has to hold, because without it the studio's *default* color mode leaves a single tone and the designs
 * that read this go flat.
 */
class RampTonesTest {

    private val five = Palette(
        listOf(
            0xFFFFFFFF.toInt(),
            0xFFCCCCCC.toInt(),
            0xFF999999.toInt(),
            0xFF666666.toInt(),
            0xFF000000.toInt(),
        ),
    )

    @Test
    fun `a long palette supplies its own tones, unblended`() {
        // Four tones of a five-stop palette are its four stops above the ground — the claim the whole helper rests on.
        val tones = RampTones.aboveGround(five)
        assertEquals(4, tones.size)
        assertEquals(listOf(1, 2, 3, 4).map { five.colorAt(it) }, tones.toList())
    }

    @Test
    fun `a two-stop palette still gets a ramp of its own, not one flat tone`() {
        val tones = RampTones.aboveGround(Palette(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())))
        assertEquals("the floor is what keeps a design alive in the default color mode", RampTones.Floor, tones.size)
        assertEquals("three distinct tones, not three copies", 3, tones.toSet().size)
    }

    @Test
    fun `the last tone is the palette's final stop and none is the ground`() {
        for (stops in 2..6) {
            val palette = Palette((0 until stops).map { 0xFF000000.toInt() or (it * 40 shl 16) })
            val tones = RampTones.aboveGround(palette)
            assertEquals(palette.colorAt(stops - 1), tones.last())
            assertTrue("the ground is spent elsewhere", tones.none { it == palette.colorAt(0) })
        }
    }

    @Test
    fun `an all-ground palette offers nothing to lay on it`() {
        assertEquals(0, RampTones.countFor(1))
        assertEquals(0, RampTones.aboveGround(Palette(listOf(0xFF123456.toInt()))).size)
    }

    @Test
    fun `a design that needs more rungs than the palette has gets them`() {
        val two = Palette(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        // The palette's own floor is three; a relief with seven sheets asks for seven and gets them.
        assertEquals(3, RampTones.countFor(two.size))
        assertEquals(7, RampTones.countFor(two.size, wanted = 7))
        assertEquals(7, RampTones.aboveGround(two, RampTones.countFor(two.size, wanted = 7)).size)
    }

    @Test
    fun `asking for fewer rungs than the palette offers changes nothing`() {
        val six = Palette(List(6) { 0xFF000000.toInt() or (it * 40) })
        assertEquals(RampTones.countFor(six.size), RampTones.countFor(six.size, wanted = 2))
    }

    @Test
    fun `a single-stop palette still has no ramp, however many rungs a design wants`() {
        val one = Palette(listOf(0xFF808080.toInt()))
        assertEquals(0, RampTones.countFor(one.size, wanted = 9))
    }
}
