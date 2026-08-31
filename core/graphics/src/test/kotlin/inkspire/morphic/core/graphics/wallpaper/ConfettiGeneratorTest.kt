package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Poisson-disk sampling — points must stay in the unit square and reproduce from a seed, and the even spread must
 * actually hold (no two points closer than the smallest radius any point was placed at). A wrong wrap or comparison
 * collapses the spread back into clumps, invisibly.
 */
class ConfettiGeneratorTest {

    @Test
    fun `density maps to the sample count range`() {
        assertEquals(40, ConfettiGenerator.sampleCount(0f))
        assertEquals(220, ConfettiGenerator.sampleCount(1f))
        assertEquals(220, ConfettiGenerator.sampleCount(2f)) // clamped
    }

    @Test
    fun `every sample lands inside the unit square`() {
        val samples = ConfettiGenerator.samples(count = 120, seed = 5L)
        assertTrue(samples.isNotEmpty())
        assertTrue(samples.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `the same seed yields the same samples, so a recipe reproduces`() {
        assertEquals(
            ConfettiGenerator.samples(count = 80, seed = 9L),
            ConfettiGenerator.samples(count = 80, seed = 9L),
        )
    }

    @Test
    fun `no two samples are closer than the smaller of their placement radii`() {
        val samples = ConfettiGenerator.samples(count = 100, seed = 7L)
        for (i in samples.indices) {
            for (j in i + 1 until samples.size) {
                val a = samples[i]
                val b = samples[j]
                val dist = kotlin.math.hypot(a.x - b.x, a.y - b.y)
                // A later sample was placed at a radius <= an earlier one, so the min radius is the live spacing.
                assertTrue("samples $i and $j clumped", dist + 1e-4f >= minOf(a.radius, b.radius))
            }
        }
    }
}
