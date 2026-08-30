package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streamline stepper — the part that is silently wrong when a sign is flipped or the bounds check runs a step
 * late. Tested against a known field with no noise in the way, `BitmapBlur`'s reason for the module.
 */
class FlowFieldGeneratorTest {

    @Test
    fun `density maps to the particle count range`() {
        assertEquals(300, FlowFieldGenerator.particleCount(0f))
        assertEquals(1200, FlowFieldGenerator.particleCount(1f))
    }

    @Test
    fun `a flat field to the right steps straight across at the step length`() {
        // angle 0 is due east: x climbs by the step length, y never moves.
        val line = FlowFieldGenerator.trace(startX = 0.1f, startY = 0.5f, steps = 5, stepLength = 0.2f) { _, _ -> 0f }

        val xs = line.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = line.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f), xs)
        assertTrue("y should never move, was $ys", ys.all { kotlin.math.abs(it - 0.5f) < 0.0001f })
    }

    @Test
    fun `a streamline ends the step after it leaves the frame`() {
        // Starts near the right edge and is pushed east: it records the point that lands outside, then stops — so the
        // last mark sits just past the edge rather than the line smearing along it.
        val line = FlowFieldGenerator.trace(startX = 0.9f, startY = 0.5f, steps = 10, stepLength = 0.2f) { _, _ -> 0f }

        // (0.9), then (1.1) which is out of bounds, then break — two points, four values.
        assertEquals(4, line.size)
        assertTrue("last x should be just past the edge, was ${line[2]}", line[2] > 1f)
    }

    @Test
    fun `the same start and field trace the same line`() {
        val field = { x: Float, _: Float -> x * 3f }
        assertTrue(
            FlowFieldGenerator.trace(0.2f, 0.3f, 8, 0.05f, field)
                .contentEquals(FlowFieldGenerator.trace(0.2f, 0.3f, 8, 0.05f, field)),
        )
    }
}
