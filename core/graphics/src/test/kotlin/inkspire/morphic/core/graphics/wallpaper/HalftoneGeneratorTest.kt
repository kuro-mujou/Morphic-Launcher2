package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid sizing and the field-to-radius mapping — a dot's radius decides whether the screen fades to paper or floods
 * solid, and the vanish-below-floor rule is what keeps weak areas clean rather than speckled.
 */
class HalftoneGeneratorTest {

    @Test
    fun `density maps to the column count range`() {
        assertEquals(8, HalftoneGenerator.gridColumns(0f))
        assertEquals(26, HalftoneGenerator.gridColumns(1f))
        assertEquals(26, HalftoneGenerator.gridColumns(2f)) // clamped
    }

    @Test
    fun `a weak field draws no dot, so paper stays clean`() {
        assertEquals(0f, HalftoneGenerator.radiusAt(0f), 0f)
        assertEquals(0f, HalftoneGenerator.radiusAt(0.1f), 0f) // below the floor
    }

    @Test
    fun `radius climbs with the field above the floor and never exceeds the cell`() {
        val mid = HalftoneGenerator.radiusAt(0.6f)
        val strong = HalftoneGenerator.radiusAt(1f)
        assertTrue("radius did not grow with the field", strong > mid)
        assertEquals("a full field fills the cell exactly", 1f, strong, 1e-6f)
        assertTrue("radius must not exceed the cell", mid in 0f..1f)
    }
}
