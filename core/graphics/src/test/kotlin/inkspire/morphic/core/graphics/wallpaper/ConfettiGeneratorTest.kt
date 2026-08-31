package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The lattice, the depth ranking and the color weighting.
 *
 * All three fail *silently*. A lattice generated to the frame's rectangle rather than its bounding circle leaves two
 * bare corners once it is turned, which reads as a bug in the noise rather than as a missing loop bound. A depth read
 * off the radii directly instead of ranked leaves the focus variants with a fraction of their range, so they render as
 * very nearly *Flat*. And a weighting that comes out even just makes the frame busier than it was meant to be, with
 * nothing to point at.
 */
class ConfettiGeneratorTest {

    private fun dots(
        width: Int = 1000,
        height: Int = 2000,
        resolution: Int = 15,
        scatter: Float = 0.5f,
        size: Float = 0.5f,
        inks: Int = 5,
        seed: Long = 42L,
    ) = ConfettiGenerator.dots(width, height, resolution, scatter, size, inks, seed)

    @Test
    fun `the turned lattice still covers every corner`() {
        val dots = dots(scatter = 0f)
        val corners = listOf(0f to 0f, 1000f to 0f, 0f to 2000f, 1000f to 2000f)
        // The pitch is 2000/15 ≈ 133, so a covered corner has a disc centre within a cell of it.
        for ((cx, cy) in corners) {
            val nearest = dots.minOf { hypot(it.x - cx, it.y - cy) }
            assertTrue("corner ($cx, $cy) is bare: nearest disc $nearest away", nearest < 140f)
        }
    }

    @Test
    fun `discs stay inside their cell, so neighbours never merge`() {
        val dots = dots(scatter = 0f, size = 1f)
        val pitch = 2000f / 15f
        assertTrue(dots.isNotEmpty())
        // No jitter and the largest size: every radius is the same and must clear half a pitch.
        assertTrue("a disc is wider than its cell", dots.all { it.radius < pitch / 2f })
    }

    @Test
    fun `zero scatter is a rigid lattice of identical discs`() {
        val dots = dots(scatter = 0f)
        val radii = dots.map { it.radius }.distinct()
        assertEquals("scatter 0 must leave every disc the same size", 1, radii.size)

        // And the spacing is uniform: every disc's nearest neighbour sits one pitch away.
        val pitch = 2000f / 15f
        val inner = dots.filter { it.x in 200f..800f && it.y in 400f..1600f }
        assertTrue(inner.isNotEmpty())
        for (dot in inner) {
            val nearest = dots.filter { it !== dot }.minOf { hypot(it.x - dot.x, it.y - dot.y) }
            assertEquals("the lattice is not even", pitch, nearest, 0.5f)
        }
    }

    @Test
    fun `scatter both moves the discs and spreads their radii`() {
        val rigid = dots(scatter = 0f)
        val loose = dots(scatter = 1f)
        assertEquals("scatter must not change how many discs there are", rigid.size, loose.size)
        assertTrue("scatter moved nothing", rigid.map { it.x } != loose.map { it.x })
        assertTrue("scatter left every radius alone", loose.map { it.radius }.distinct().size > 1)
        assertTrue("a radius went to nothing", loose.all { it.radius > 0f })
    }

    @Test
    fun `the big discs are the near ones, and the depth spans its whole range at any scatter`() {
        for (scatter in listOf(0.2f, 0.5f, 1f)) {
            val dots = dots(scatter = scatter)
            assertTrue(dots.all { it.depth in 0f..1f })
            assertTrue("the biggest disc must be the nearest", dots.maxBy { it.radius }.depth > dots.minBy { it.radius }.depth)
            // Ranked rather than measured: a mild scatter must still reach both ends, or the focus knob gets a
            // fraction of its range and renders as very nearly nothing.
            // The bounds are loose because these are the extremes of ~150 draws, not exact ends; what they rule out
            // is the measured depth this replaced, whose near end stopped at 1 - 0.85 * scatter.
            assertTrue("depth did not reach the near end at scatter $scatter", dots.maxOf { it.depth } > 0.95f)
            assertTrue("depth did not reach the far end at scatter $scatter", dots.minOf { it.depth } < 0.05f)
        }
    }

    @Test
    fun `no scatter leaves every disc at one depth, so there is no field to focus`() {
        val dots = dots(scatter = 0f)
        assertEquals("identical discs cannot be at different distances", 1, dots.map { it.depth }.distinct().size)
        assertEquals(1f, dots.first().depth, 0f)
    }

    @Test
    fun `the same seed yields the same discs, so a recipe reproduces`() {
        assertEquals(dots(seed = 9L), dots(seed = 9L))
    }

    @Test
    fun `the color weighting favours the early stops, geometrically`() {
        val weights = ConfettiGenerator.inkWeights(4)
        assertEquals("the weights must normalize to one", 1f, weights.last(), 1e-5f)
        // Bands are 1, 1/2, 1/4, 1/8 of the total — each stop half as likely as the one before it.
        val bands = weights.mapIndexed { i, w -> if (i == 0) w else w - weights[i - 1] }
        for (i in 1 until bands.size) {
            assertEquals("stop ${i + 1} is not half as likely as stop $i", bands[i - 1] / 2f, bands[i], 1e-5f)
        }
    }

    @Test
    fun `every disc lands on a stop above the ground`() {
        val inks = 3
        val dots = dots(inks = inks)
        assertTrue(dots.all { it.ink in 1..inks })
        assertTrue("the weighting never reached the last stop", dots.any { it.ink == inks })
        val first = dots.count { it.ink == 1 }
        val last = dots.count { it.ink == inks }
        assertTrue("the first stop should dominate: $first vs $last", first > last * 2)
    }

    @Test
    fun `a single ink palette puts every disc on it`() {
        assertEquals(floatArrayOf(1f).toList(), ConfettiGenerator.inkWeights(1).toList())
        assertTrue(dots(inks = 1).all { it.ink == 1 })
    }
}
