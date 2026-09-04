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
        assertEquals(6f, PlasmaGenerator.frequency(0f), 0f)
        assertEquals(26f, PlasmaGenerator.frequency(1f), 0f)
        assertEquals(6f, PlasmaGenerator.frequency(-1f), 0f) // clamped
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

    /** `0` is the rigid interference this design drew before it had a turbulence knob, at every frequency. */
    @Test
    fun `no turbulence pushes nothing`() {
        for (density in listOf(0f, 0.5f, 1f)) {
            assertEquals(0f, PlasmaGenerator.warpReach(0f, PlasmaGenerator.frequency(density)), 0f)
        }
    }

    /**
     * The push is a share of a **wavelength**, which is the whole reason it is not a share of the frame: the frequency
     * knob spans a four-fold range, so a fixed distance would be a nudge at one end and noise at the other — one knob
     * quietly changing what the knob beside it means.
     *
     * Stated as the invariant rather than as a number: whatever the frequency, the push is the same fraction of a
     * swell.
     */
    @Test
    fun `the push is the same share of a swell at every frequency`() {
        val broad = PlasmaGenerator.frequency(0f)
        val busy = PlasmaGenerator.frequency(1f)
        val twoPi = 2f * Math.PI.toFloat()

        val shareWhenBroad = PlasmaGenerator.warpReach(1f, broad) / (twoPi / broad)
        val shareWhenBusy = PlasmaGenerator.warpReach(1f, busy) / (twoPi / busy)

        assertEquals(shareWhenBroad, shareWhenBusy, 1e-6f)
        assertTrue("the push should be under a whole swell", shareWhenBroad < 1f)
    }

    /** A stored value outside `0..1` reads as the nearer end rather than running off the range. */
    @Test
    fun `turbulence outside the range clamps`() {
        val frequency = PlasmaGenerator.frequency(0.5f)
        assertEquals(0f, PlasmaGenerator.warpReach(-1f, frequency), 0f)
        assertEquals(PlasmaGenerator.warpReach(1f, frequency), PlasmaGenerator.warpReach(2f, frequency), 0f)
    }
}
