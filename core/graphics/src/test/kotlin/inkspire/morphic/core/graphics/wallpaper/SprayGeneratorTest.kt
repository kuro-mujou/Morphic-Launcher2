package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The field's metric and the trail's ramp. Both fail the same quiet way: a stretched field draws arcs that are simply
 * the wrong shape, and a ramp that reaches the ground paints dots in the color of the ground behind them — neither
 * looks like an error, only like a design that was composed for something else.
 */
class SprayGeneratorTest {

    /** A tall phone: 1080×2400. Every aspect-sensitive case is measured on one, since a square frame hides the bug. */
    private val phone = 2400f / 1080f

    private val palette = Palette(
        listOf(0xFFF2E2C4.toInt(), 0xFFE6A15C.toInt(), 0xFF2C6E6B.toInt(), 0xFF121E2B.toInt()),
    )

    @Test
    fun `density maps to the trail count range`() {
        assertEquals(100, SprayGenerator.trailCount(0f))
        assertEquals(1500, SprayGenerator.trailCount(1f))
        assertEquals(100, SprayGenerator.trailCount(-1f)) // clamped
        assertEquals(1500, SprayGenerator.trailCount(2f)) // clamped
    }

    @Test
    fun `trail length maps to the step range`() {
        assertEquals(24, SprayGenerator.trailSteps(0f))
        assertEquals(260, SprayGenerator.trailSteps(1f))
        assertEquals(24, SprayGenerator.trailSteps(-1f)) // clamped
        assertEquals(260, SprayGenerator.trailSteps(2f)) // clamped
    }

    /** `0` is a flat field — every particle runs the same way, which is the rigid end and a picture of its own. */
    @Test
    fun `no swirl leaves one direction everywhere`() {
        val here = SprayGenerator.flowAngle(0.1f, 0.3f, swirl = 0f)
        val there = SprayGenerator.flowAngle(0.9f, 1.8f, swirl = 0f)

        assertEquals(here, there, 0f)
    }

    /** And it genuinely turns at the other end — far enough to fold a trail back on itself, not merely bend it. */
    @Test
    fun `full swirl turns the field through more than a whole revolution`() {
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        var nx = 0f
        while (nx <= 1f) {
            var ny = 0f
            while (ny <= phone) {
                val angle = SprayGenerator.flowAngle(nx, ny, swirl = 1f)
                lowest = minOf(lowest, angle)
                highest = maxOf(highest, angle)
                ny += 0.02f
            }
            nx += 0.02f
        }
        assertTrue("the field barely turns: ${highest - lowest}", highest - lowest > 2f * Math.PI.toFloat())
    }

    /**
     * **The metric bug five designs carried into the quality pass.** Both coordinates are shares of the frame's
     * *width*, so the field is the same shape across the frame as down it: a particle a given number of *pixels*
     * along either axis has been carried the same distance through the field.
     *
     * Measured as the two axes agreeing at equal pixel offsets, which is exactly what reading each as a share of its
     * own side gets wrong.
     */
    @Test
    fun `the field is read in one metric, so it is not stretched by the frame`() {
        val step = 0.2f
        // The same pixel offset across and down, expressed in the width-share metric the generator uses.
        val across = SprayGenerator.flowAngle(0.5f + step, 0.5f, swirl = 1f)
        val down = SprayGenerator.flowAngle(0.5f, 0.5f + step, swirl = 1f)

        // sin and cos of the same argument, so the two are a quarter turn of the wave apart and never equal — what
        // matters is that the *field's* period is the same on both axes.
        val acrossAgain = SprayGenerator.flowAngle(0.5f + step + 2f * Math.PI.toFloat() / 10f, 0.5f, swirl = 1f)
        val downAgain = SprayGenerator.flowAngle(0.5f, 0.5f + step + 2f * Math.PI.toFloat() / 10f, swirl = 1f)

        assertEquals("the field's period differs across", across, acrossAgain, 1e-3f)
        assertEquals("the field's period differs down", down, downAgain, 1e-3f)
    }

    /** A dot at the end of its trail must never be painted in the ground it lies on, at any palette length. */
    @Test
    fun `no dot is painted in the ground`() {
        for (stops in 2..6) {
            val reduced = Palette(List(stops) { 0xFF000000.toInt() or (it * 0x2A2A2A) })
            val ground = reduced.colorAt(reduced.size - 1)
            var along = 0f
            while (along <= 1f) {
                assertNotEquals("a dot took the ground at $stops stops, $along", ground, SprayGenerator.toneAt(along, reduced))
                along += 0.01f
            }
        }
    }

    /** The ramp still runs the length of the trail — a start and an end that are plainly different colors. */
    @Test
    fun `the tone travels along the trail`() {
        val start = SprayGenerator.toneAt(0f, palette)
        val end = SprayGenerator.toneAt(1f, palette)
        val apart = (0..2).sumOf { c -> abs((start shr (c * 8) and 0xFF) - (end shr (c * 8) and 0xFF)) }

        assertTrue("the trail barely changes color: $apart", apart > 120)
    }

    /**
     * A particle stepped through the field has to actually go somewhere — the integration is two lines in the render
     * loop and would sit still if the angle were read in degrees, which nothing else would report.
     */
    @Test
    fun `a particle carried by the field travels`() {
        var x = 0.5f
        var y = 0.5f
        val stride = 0.006f
        repeat(60) {
            val angle = SprayGenerator.flowAngle(x, y, swirl = 1f)
            x += cos(angle) * stride
            y += sin(angle) * stride
        }

        val moved = abs(x - 0.5f) + abs(y - 0.5f)
        assertTrue("the particle went nowhere: $moved", moved > stride * 4f)
    }
}
