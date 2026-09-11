package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The grid sizing and the field-to-radius mapping — a dot's radius decides whether the screen fades to paper or floods
 * solid, and the vanish-below-floor rule is what keeps weak areas clean rather than speckled. And the scrub's turn
 * between two seeds' fields, whose failure is a flatter screen passing for the same one.
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

    @Test
    fun `a plan is one screen at every size of one shape`() {
        val params = DesignParams(irregularity = 1f)
        val full = HalftoneGenerator.plan(1080, 2400, params, seed = 3L)
        val small = HalftoneGenerator.plan(135, 300, params, seed = 3L)
        assertEquals(full.cols, small.cols)
        assertEquals(full.rows, small.rows)
        assertArrayEquals(full.x, small.x, 0f)
        assertArrayEquals(full.y, small.y, 0f)
        assertArrayEquals(full.field, small.field, 0f)
    }

    @Test
    fun `two screens on different lattices are refused rather than paired`() {
        val coarse = HalftoneGenerator.plan(1080, 2400, DesignParams(density = 0f), seed = 1L)
        val fine = HalftoneGenerator.plan(1080, 2400, DesignParams(density = 1f), seed = 1L)
        assertNull(HalftoneGenerator.morph(coarse, fine))
        assertNotNull(HalftoneGenerator.morph(coarse, HalftoneGenerator.plan(1080, 2400, DesignParams(density = 0f), 2L)))
    }

    @Test
    fun `a turned field starts on one seed's and ends on the other's`() {
        for ((a, b) in listOf(0.1f to 0.9f, 0.7f to 0.3f, 0f to 1f, 0.5f to 0.5f)) {
            assertEquals(a, HalftoneGenerator.turnField(a, b, 0f), 1e-6f)
            assertEquals(b, HalftoneGenerator.turnField(a, b, 1f), 1e-6f)
        }
    }

    /**
     * **The middle of a scrub is as contrasty a screen as its ends**, which a straight blend of two seeds' fields is
     * not — measured as the field's spread over a whole screen, averaged over several shuffles. The straight blend is
     * measured beside it so the reason for turning stays checked rather than asserted: if it ever stopped flattening
     * the middle, the turn would be a complication with nothing to answer for.
     */
    @Test
    fun `the middle of a scrub keeps the screen's contrast, where a straight blend flattens it`() {
        var ends = 0.0
        var turned = 0.0
        var straight = 0.0
        for (seed in 1L..8L) {
            val a = HalftoneGenerator.plan(1080, 2400, DesignParams(), seed)
            val b = HalftoneGenerator.plan(1080, 2400, DesignParams(), seed + 100)
            val middle = requireNotNull(HalftoneGenerator.morph(a, b)).at(0.5f)
            ends += (spread(a.field) + spread(b.field)) / 2
            turned += spread(middle.field)
            straight += spread(FloatArray(a.field.size) { (a.field[it] + b.field[it]) / 2f })
        }
        assertEquals("turned, the middle keeps its spread", 1.0, turned / ends, 0.1)
        assertTrue("straight, the middle loses a fifth or more: ${straight / ends}", straight / ends < 0.8)
    }

    private fun spread(values: FloatArray): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
