package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summed-sine field and its wrap — the plasma value must stay in `0..1` (it indexes the looped palette) and must
 * reproduce from a seed. A field that drifts out of range bands wrong or crashes the color lookup.
 */
class PlasmaGeneratorTest {

    @Test
    fun `density maps to the frequency range`() {
        assertEquals(8f, PlasmaGenerator.frequency(0f), 0f)
        assertEquals(34f, PlasmaGenerator.frequency(1f), 0f)
        assertEquals(8f, PlasmaGenerator.frequency(-1f), 0f) // clamped
    }

    @Test
    fun `the same seed yields the same phases, so a still reproduces`() {
        assertEquals(PlasmaGenerator.phases(7L), PlasmaGenerator.phases(7L))
    }

    @Test
    fun `a different seed yields different phases`() {
        assertTrue(PlasmaGenerator.phases(1L) != PlasmaGenerator.phases(2L))
    }

    @Test
    fun `the field stays within the unit range everywhere, so the looped color lookup is always in bounds`() {
        val phases = PlasmaGenerator.phases(3L)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var x = 0f
        while (x <= 1f) {
            var y = 0f
            while (y <= 1f) {
                val v = PlasmaGenerator.sample(x, y, frequency = 20f, phases = phases)
                if (v < min) min = v
                if (v > max) max = v
                y += 0.05f
            }
            x += 0.05f
        }
        assertTrue("field went below 0: $min", min >= 0f)
        assertTrue("field reached or passed 1: $max", max < 1f)
    }
}
