package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The potential field, the spread that bands it, and the shadow inside each band — the parts a bitmap cannot check.
 *
 * All of them fail *quietly*. A field that saturates draws one flat wash rather than an error; a spread that runs the
 * wrong way still produces a picture, just the opposite one; and a shadow on the low edge of each band reads as a
 * lighting choice rather than as the stack being upside down.
 */
class MetaballsGeneratorTest {

    @Test
    fun `the charge count is fixed, whatever the complexity knob says`() {
        // The finding this design was rebuilt on: driving the reference's Complexity leaves the same systems in the
        // same corners, so it distorts the field rather than adding to it. A count here would be the old mistake.
        assertTrue(MetaballsGenerator.style.amount is AmountKnob.Fraction)
        assertEquals(3, MetaballsGenerator.charges(seed = 1L).size)
        assertEquals(3, MetaballsGenerator.charges(seed = 99L).size)
    }

    @Test
    fun `the same seed yields the same charges, so a recipe reproduces`() {
        assertEquals(MetaballsGenerator.charges(seed = 3L), MetaballsGenerator.charges(seed = 3L))
    }

    @Test
    fun `the field is stronger near a charge than far from it`() {
        val charge = MetaballsGenerator.Charge(x = 0.5f, y = 0.5f, radius = 0.2f)
        val near = MetaballsGenerator.field(0.5f, 0.5f, listOf(charge))
        val far = MetaballsGenerator.field(0.0f, 0.0f, listOf(charge))
        assertTrue("the field did not fall off with distance", near > far)
    }

    @Test
    fun `the level stays in bounds across every field strength`() {
        for (gamma in listOf(0.6f, 1.1f, 2.6f)) {
            assertEquals(0f, MetaballsGenerator.level(0f, gamma), 1e-6f)
            assertTrue(MetaballsGenerator.level(1f, gamma) in 0f..1f)
            assertTrue(MetaballsGenerator.level(1e6f, gamma) in 0f..1f)
        }
    }

    @Test
    fun `a tighter spread pushes the same field lower, so the bands crowd the peaks`() {
        val mid = 1f // a field of 1 rolls to exactly half height, where the spread has the most to say
        val broad = MetaballsGenerator.level(mid, MetaballsGenerator.Spread.BROAD.gamma)
        val tight = MetaballsGenerator.level(mid, MetaballsGenerator.Spread.TIGHT.gamma)
        assertTrue("tight must leave more of the frame on the ground", tight < broad)
    }

    @Test
    fun `thickness runs the other way from the band count`() {
        assertEquals(12, MetaballsGenerator.bandCount(0f))
        assertEquals(2, MetaballsGenerator.bandCount(1f))
        assertTrue("a two-stop palette must still get a ramp", MetaballsGenerator.bandCount(0.5f) >= 3)
    }

    @Test
    fun `the shadow darkens the top of a band and leaves its bottom alone`() {
        assertEquals("the low edge is the band's own color", 1f, MetaballsGenerator.shadowAt(0f, 0.43f), 1e-6f)
        assertEquals("and the high edge is where the layer above sits", 0.57f, MetaballsGenerator.shadowAt(1f, 0.43f), 1e-6f)
        assertTrue("it must recover late, not linearly", MetaballsGenerator.shadowAt(0.5f, 0.43f) > 0.9f)
    }

    @Test
    fun `no shadow leaves every band flat`() {
        for (into in listOf(0f, 0.5f, 1f)) {
            assertEquals(1f, MetaballsGenerator.shadowAt(into, 0f), 1e-6f)
        }
    }

    @Test
    fun `shading scales the channels and leaves the alpha alone`() {
        val dark = MetaballsGenerator.shade(0x80806040U.toInt(), factor = 0.5f)
        assertEquals(0x80, dark ushr 24 and 0xFF)
        assertEquals(0x40, dark shr 16 and 0xFF)
        assertEquals(0x30, dark shr 8 and 0xFF)
        assertEquals(0x20, dark and 0xFF)
    }
}
