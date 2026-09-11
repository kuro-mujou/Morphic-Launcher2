package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

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

    /** A phase is an angle, so a scrub turns every wave the short way round rather than rolling it most of a cycle. */
    @Test
    fun `a scrub turns every phase the short way round`() {
        val base = PlasmaGenerator.plan(DesignParams(), seed = 1L)
        fun at(phase: Float) = PlasmaGenerator.Plan(
            PlasmaGenerator.Phases(phase, phase, phase, phase),
            base.frequency,
            base.warp,
        )
        val morph = PlasmaGenerator.Morph(at(0.1f), at((2 * PI).toFloat() - 0.1f))
        val middle = morph.at(0.5f).phases
        for (phase in listOf(middle.x, middle.y, middle.diagonal, middle.radial)) {
            assertTrue("a phase went the long way: $phase", abs(phase) < 0.01f)
        }
    }

    @Test
    fun `a scrub begins and ends on the plans, and blends the two warps between them`() {
        val from = PlasmaGenerator.plan(DesignParams(irregularity = 1f), seed = 1L)
        val to = PlasmaGenerator.plan(DesignParams(irregularity = 1f), seed = 2L)
        val morph = PlasmaGenerator.Morph(from, to)
        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        val middle = morph.at(0.25f)
        assertSame(from.warp, middle.warp)
        assertSame(to.warp, middle.nextWarp)
        assertEquals(0.25f, middle.warpMix, 0f)
    }

    /** The scrub's resolution rises with the frequency, over exactly the range the measurement set. */
    @Test
    fun `the scrub evaluates busier waves at a finer buffer`() {
        assertEquals(120, PlasmaGenerator.scrubShortSide(PlasmaGenerator.frequency(0f)))
        assertEquals(240, PlasmaGenerator.scrubShortSide(PlasmaGenerator.frequency(1f)))
        assertEquals(180, PlasmaGenerator.scrubShortSide(PlasmaGenerator.frequency(0.5f)))
    }
}
