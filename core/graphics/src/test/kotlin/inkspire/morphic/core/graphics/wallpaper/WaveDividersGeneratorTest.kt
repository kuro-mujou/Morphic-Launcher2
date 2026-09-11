package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

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

    /**
     * **The two axes' ends combined put a point exactly where both axes read it**, at every direction — the one piece
     * of geometry the drawn bands add to the design, and the part that would be silently wrong: a band traced a
     * quarter turn or a corner's offset off still draws as a stack of waves, just not the one the knobs describe.
     */
    @Test
    fun `a traced point lands where both axes read it, at every direction`() {
        for (direction in WaveDividersGenerator.Direction.entries) {
            val across = frameAxis(direction.degrees + 90f, 1080, 2400)
            val along = frameAxis(direction.degrees, 1080, 2400)
            for (l in listOf(0.1f, 0.5f, 0.9f)) {
                for (a in listOf(0.2f, 0.6f, 0.95f)) {
                    val x = WaveDividersGenerator.pixelX(l, a, along, across)
                    val y = WaveDividersGenerator.pixelY(l, a, along, across)
                    assertEquals("${direction.label} along", l, along.at(x, y), 1e-4f)
                    assertEquals("${direction.label} across", a, across.at(x, y), 1e-4f)
                }
            }
        }
    }

    /** A shuffle of this design is a phase, so a scrub slides the waves the short way round and nothing else. */
    @Test
    fun `a scrub slides the phase the short way and leaves every knob alone`() {
        val from = WaveDividersGenerator.plan(DesignParams(), seed = 1L)
        val to = WaveDividersGenerator.plan(DesignParams(), seed = 2L)
        val morph = requireNotNull(WaveDividersGenerator.morph(from, to))
        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        val middle = morph.at(0.5f)
        assertEquals(from.depth, middle.depth, 0f)
        assertEquals(from.cycles, middle.cycles, 0f)

        val near = WaveDividersGenerator.Plan(from.direction, from.count, from.depth, from.cycles, 0.1f)
        val far = WaveDividersGenerator.Plan(from.direction, from.count, from.depth, from.cycles, (2 * PI).toFloat() - 0.1f)
        val phase = requireNotNull(WaveDividersGenerator.morph(near, far)).at(0.5f).phase
        assertTrue("the phase went the long way: $phase", abs(phase) < 0.01f)
    }

    @Test
    fun `two stacks of different band counts are refused rather than paired`() {
        val few = WaveDividersGenerator.plan(DesignParams(density = 0f), seed = 1L)
        val many = WaveDividersGenerator.plan(DesignParams(density = 1f), seed = 1L)
        assertNull(WaveDividersGenerator.morph(few, many))
    }
}
