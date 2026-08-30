package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The noise's determinism, range and lattice behavior — the properties a flow field leans on, checked without a
 * bitmap. A noise that is subtly wrong looks like a different plausible field rather than an error.
 */
class PerlinNoise2dTest {

    @Test
    fun `the same seed gives the same field`() {
        val a = PerlinNoise2d(seed = 7L)
        val b = PerlinNoise2d(seed = 7L)

        assertEquals(a.at(1.3f, 4.8f), b.at(1.3f, 4.8f), 0f)
        assertEquals(a.at(-2.1f, 0.4f), b.at(-2.1f, 0.4f), 0f)
    }

    @Test
    fun `different seeds give different fields`() {
        assertTrue(PerlinNoise2d(1L).at(1.3f, 4.8f) != PerlinNoise2d(2L).at(1.3f, 4.8f))
    }

    @Test
    fun `the field is zero on the integer lattice`() {
        // Gradient noise reads as zero exactly at the lattice points — the property that keeps its structure off the
        // grid, and the thing value noise gets wrong.
        val noise = PerlinNoise2d(seed = 3L)

        for (x in intArrayOf(0, 1, 5, -3)) {
            for (y in intArrayOf(0, 2, -4)) {
                assertEquals(0f, noise.at(x.toFloat(), y.toFloat()), 0.0001f)
            }
        }
    }

    @Test
    fun `the field stays within a sane range and is not constant`() {
        val noise = PerlinNoise2d(seed = 11L)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var step = 0
        while (step < 400) {
            val v = noise.at(step * 0.137f, step * 0.081f)
            if (v < min) min = v
            if (v > max) max = v
            step++
        }

        assertTrue("min $min max $max", min > -1.5f && max < 1.5f)
        assertTrue("field should vary, was flat at $min", max - min > 0.3f)
    }
}
