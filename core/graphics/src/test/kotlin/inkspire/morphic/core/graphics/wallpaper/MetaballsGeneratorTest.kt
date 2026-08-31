package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The potential field and its mapping — the field must be strong near a charge and fade with distance (or the blobs
 * never form), and the band mapping must stay in `0..1` for the palette lookup.
 */
class MetaballsGeneratorTest {

    @Test
    fun `density maps to the charge count range`() {
        assertEquals(3, MetaballsGenerator.chargeCount(0f))
        assertEquals(9, MetaballsGenerator.chargeCount(1f))
        assertEquals(9, MetaballsGenerator.chargeCount(2f)) // clamped
    }

    @Test
    fun `the same seed yields the same charges, so a recipe reproduces`() {
        assertEquals(
            MetaballsGenerator.charges(count = 6, seed = 3L),
            MetaballsGenerator.charges(count = 6, seed = 3L),
        )
    }

    @Test
    fun `the field is stronger near a charge than far from it`() {
        val charge = MetaballsGenerator.Charge(x = 0.5f, y = 0.5f, radius = 0.2f)
        val near = MetaballsGenerator.field(0.5f, 0.5f, listOf(charge))
        val far = MetaballsGenerator.field(0.0f, 0.0f, listOf(charge))
        assertTrue("the field did not fall off with distance", near > far)
    }

    @Test
    fun `the band maps to a valid stop index across all field strengths`() {
        val stops = 6
        // Empty space is the first stop; a strong field climbs toward the last; nothing indexes out of the palette.
        assertEquals(0, MetaballsGenerator.band(0f, stops))
        assertTrue(MetaballsGenerator.band(1f, stops) in 0 until stops)
        assertTrue(MetaballsGenerator.band(1000f, stops) in 0 until stops)
        assertEquals("a huge field lands in the last stop, not past it", stops - 1, MetaballsGenerator.band(1e6f, stops))
    }
}
