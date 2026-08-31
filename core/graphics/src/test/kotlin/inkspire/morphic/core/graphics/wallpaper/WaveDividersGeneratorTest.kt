package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The band count and the divider wave — the wave offset must stay bounded by its summed amplitude, or it would fold the
 * band stack over itself, and that is arithmetic no bitmap is needed to check.
 */
class WaveDividersGeneratorTest {

    @Test
    fun `density maps to the band count range`() {
        assertEquals(4, WaveDividersGenerator.bandCount(0f))
        assertEquals(16, WaveDividersGenerator.bandCount(1f))
        assertEquals(4, WaveDividersGenerator.bandCount(-1f)) // clamped
        assertEquals(16, WaveDividersGenerator.bandCount(2f)) // clamped
    }

    @Test
    fun `the wave offset stays within its summed amplitude`() {
        val terms = WaveDividersGenerator.terms(irregularity = 1f, seed = 4L)
        val span = terms.sumOf { it.amplitude.toDouble() }.toFloat()
        var nx = 0f
        while (nx <= 1f) {
            val offset = WaveDividersGenerator.waveOffset(nx, terms)
            assertTrue("offset $offset ran past its amplitude $span at $nx", offset in -span - 1e-4f..span + 1e-4f)
            nx += 0.02f
        }
    }

    @Test
    fun `more irregularity makes a deeper wave`() {
        // The summed amplitude at high irregularity exceeds that at low — the wave cuts deeper.
        val flat = WaveDividersGenerator.terms(irregularity = 0f, seed = 4L).sumOf { it.amplitude.toDouble() }
        val deep = WaveDividersGenerator.terms(irregularity = 1f, seed = 4L).sumOf { it.amplitude.toDouble() }
        assertTrue("deeper irregularity did not raise the amplitude", deep > flat)
    }
}
