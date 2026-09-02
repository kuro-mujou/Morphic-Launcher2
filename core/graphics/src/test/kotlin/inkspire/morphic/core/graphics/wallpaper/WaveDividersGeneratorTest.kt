package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three knob mappings, and the two measurements taken off the reference that they have to reproduce.
 *
 * All of it is arithmetic that needs no bitmap, and the amplitude in particular is the kind of number a later tidy-up
 * rounds off: it is squared so that the uniform `0.5` default lands on the reference's own restrained wave.
 */
class WaveDividersGeneratorTest {

    @Test
    fun `density maps to the band count range`() {
        assertEquals(2, WaveDividersGenerator.bandCount(0f))
        assertEquals(20, WaveDividersGenerator.bandCount(1f))
        assertEquals(20, WaveDividersGenerator.bandCount(2f)) // clamped
    }

    @Test
    fun `scale runs from a tight ripple to one broad sweep`() {
        assertEquals(20f, WaveDividersGenerator.waveCycles(0f), 1e-6f)
        assertEquals(1f, WaveDividersGenerator.waveCycles(1f), 1e-6f)
        assertEquals(1f, WaveDividersGenerator.waveCycles(2f), 1e-6f) // clamped
    }

    @Test
    fun `the wavelength descends over the whole knob, so no stretch of it is dead`() {
        var previous = WaveDividersGenerator.waveCycles(0f)
        var scale = 0.05f
        while (scale <= 1f) {
            val cycles = WaveDividersGenerator.waveCycles(scale)
            assertTrue("the cycle count stopped falling at $scale", cycles < previous)
            previous = cycles
            scale += 0.05f
        }
    }

    @Test
    fun `zero wave depth leaves the dividers straight`() {
        assertEquals(0f, WaveDividersGenerator.waveDepth(0f), 1e-6f)
    }

    @Test
    fun `the default wave depth reproduces the reference's own amplitude`() {
        // Measured off theirs at its default Wideness: a 235px swing on a 2400px frame, so an amplitude of ~0.049.
        assertEquals(0.049f, WaveDividersGenerator.waveDepth(0.5f), 5e-3f)
        assertEquals(0.19f, WaveDividersGenerator.waveDepth(1f), 1e-6f)
        assertEquals(0.19f, WaveDividersGenerator.waveDepth(2f), 1e-6f) // clamped
    }
}
