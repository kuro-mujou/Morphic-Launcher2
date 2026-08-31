package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layer count and the crest height — a crest is what decides whether a band shows on screen at all, and its
 * displacement from the baseline is bounded arithmetic that needs no canvas.
 */
class WavesGeneratorTest {

    @Test
    fun `density maps to the layer count range`() {
        assertEquals(3, WavesGenerator.layerCount(0f))
        assertEquals(9, WavesGenerator.layerCount(1f))
        assertEquals(9, WavesGenerator.layerCount(2f)) // clamped
    }

    @Test
    fun `irregularity scales the amplitude, with the default landing on the shipped swell`() {
        assertEquals(0.4f, WavesGenerator.amplitudeScale(0f), 1e-6f)
        assertEquals(1f, WavesGenerator.amplitudeScale(0.5f), 1e-6f) // the swell the design shipped with
        assertEquals(1.6f, WavesGenerator.amplitudeScale(1f), 1e-6f)
        assertEquals(1.6f, WavesGenerator.amplitudeScale(2f), 1e-6f) // clamped
    }

    @Test
    fun `a flat wave sits exactly on its baseline`() {
        val flat = WavesGenerator.Wave(
            listOf(WavesGenerator.Term(amplitude = 0f, frequency = 3f, phase = 0f)),
        )
        assertEquals(0.5f, WavesGenerator.crestY(flat, nx = 0.25f, baseline = 0.5f), 1e-6f)
    }

    @Test
    fun `the crest stays within the summed amplitude of its baseline`() {
        val wave = WavesGenerator.Wave(
            listOf(
                WavesGenerator.Term(amplitude = 0.05f, frequency = 2f, phase = 0.4f),
                WavesGenerator.Term(amplitude = 0.02f, frequency = 5f, phase = 1.1f),
            ),
        )
        val span = 0.05f + 0.02f
        var nx = 0f
        while (nx <= 1f) {
            val y = WavesGenerator.crestY(wave, nx, baseline = 0.5f)
            assertTrue("crest strayed past its amplitude at $nx", y in (0.5f - span - 1e-4f)..(0.5f + span + 1e-4f))
            nx += 0.02f
        }
    }
}
