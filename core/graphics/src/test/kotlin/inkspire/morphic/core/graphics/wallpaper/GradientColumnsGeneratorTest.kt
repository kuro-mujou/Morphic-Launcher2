package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The column count range and the two shades the *Relief* knob drives. The variable-width banding is tested in
 * [BandsTest] and the ramp in [LinearGradientGeneratorTest]; how the two shades read *together* is a picture, and is
 * judged in the render harness.
 */
class GradientColumnsGeneratorTest {

    /** What the design's own default `depth` of `0.5` resolves to — see `ShippedRelief`. */
    private val shipped = 1f

    @Test
    fun `density maps to the column count range`() {
        assertEquals(4, GradientColumnsGenerator.columnCount(0f))
        assertEquals(16, GradientColumnsGenerator.columnCount(1f))
        assertEquals(4, GradientColumnsGenerator.columnCount(-1f)) // clamped
        assertEquals(16, GradientColumnsGenerator.columnCount(2f)) // clamped
    }

    /**
     * `depth` `0` has to be *flat*, which is the field's own contract and the end this design could not draw before
     * the quality pass — the seam shadow used to be unconditional.
     */
    @Test
    fun `no relief leaves both shades untouched`() {
        for (t in listOf(0f, 0.5f, 0.9f, 1f)) {
            assertEquals(1f, GradientColumnsGenerator.edgeShade(t, relief = 0f), 0f)
            assertEquals(1f, GradientColumnsGenerator.rakeShade(t, relief = 0f), 0f)
        }
    }

    /**
     * The design's own default reproduces the seam shadow it always drew — a stored recipe carries `depth` `0.5`
     * whether or not it ever saw the knob, so that value is the one that must not move.
     */
    @Test
    fun `the default relief draws the seam shadow this design has always drawn`() {
        // Flat across the column until the shadow's own fraction, then down to 1 - ShadowDepth at the seam.
        assertEquals(1f, GradientColumnsGenerator.edgeShade(0f, shipped), 1e-6f)
        assertEquals(1f, GradientColumnsGenerator.edgeShade(0.6f, shipped), 1e-6f)
        assertEquals(0.65f, GradientColumnsGenerator.edgeShade(1f, shipped), 1e-6f)
    }

    /** The rake falls from one end of a column to the other, and holds both ends flat so it does not read as a ramp. */
    @Test
    fun `the rake falls along the column and is flat at both ends`() {
        assertEquals(1f, GradientColumnsGenerator.rakeShade(0f, shipped), 1e-6f)

        val steps = (0..10).map { GradientColumnsGenerator.rakeShade(it / 10f, shipped) }
        for (i in 1 until steps.size) {
            assertTrue("the rake must never brighten", steps[i] <= steps[i - 1] + 1e-6f)
        }
        assertTrue("the far end has to be visibly darker", steps.last() < steps.first() - 0.1f)

        // Smoothstep, not a straight line: the middle falls faster than either end.
        val atEnds = (steps[1] - steps[0]) + (steps[10] - steps[9])
        val atMiddle = steps[6] - steps[5]
        assertTrue("the fall should be spent in the middle", atMiddle < atEnds)
    }

    /**
     * The two shades multiply, so the darkest pixel in the frame is the far seam of the far column. Both bounds are
     * chosen to keep that off black at full relief — nothing checks it at runtime.
     */
    @Test
    fun `the darkest pixel stays well off black at full relief`() {
        val full = 1f / 0.5f // depth 1 over the shipped relief
        val darkest = GradientColumnsGenerator.edgeShade(1f, full) * GradientColumnsGenerator.rakeShade(1f, full)

        assertTrue("the frame went to near-black at $darkest", darkest > 0.12f)
        assertTrue("full relief should be plainly deeper than the default at $darkest", darkest < 0.4f)
    }

    /**
     * **The rake's gradient stays within a level of the smoothstep it samples**, at full relief — the claim its stop
     * count rests on. A shader ramps linearly between stops, so too few of them turn the eased fall into a visible
     * polyline of flats and kinks, and nothing reports it: the columns just look a little banded along their length.
     */
    @Test
    fun `the rake's stops follow its smoothstep to within a level`() {
        val relief = 2f
        val stops = GradientColumnsGenerator.rakeStops()
        for (i in 0 until stops.size - 1) {
            val a = GradientColumnsGenerator.rakeShade(stops[i], relief)
            val b = GradientColumnsGenerator.rakeShade(stops[i + 1], relief)
            for (k in 1 until 10) {
                val f = k / 10f
                val s = stops[i] + (stops[i + 1] - stops[i]) * f
                val drawn = a + (b - a) * f
                val exact = GradientColumnsGenerator.rakeShade(s, relief)
                assertTrue("off by ${abs(drawn - exact) * 255} levels at $s", abs(drawn - exact) * 255f < 1f)
            }
        }
    }

    /** The seams slide and never cross, at every moment of a scrub — so no column turns inside out mid-swipe. */
    @Test
    fun `the seams stay in order at every moment of a scrub`() {
        val params = DesignParams(density = 1f, irregularity = 1f)
        val from = GradientColumnsGenerator.plan(params, seed = 1L)
        val to = GradientColumnsGenerator.plan(params, seed = 2L)
        val morph = requireNotNull(GradientColumnsGenerator.morph(from, to))
        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        for (step in 0..20) {
            val seams = morph.at(step / 20f).boundaries.toList()
            assertEquals("at ${step * 5}%", seams.sorted(), seams)
        }
    }

    @Test
    fun `two sets of different column counts are refused rather than paired`() {
        val few = GradientColumnsGenerator.plan(DesignParams(density = 0f), seed = 1L)
        val many = GradientColumnsGenerator.plan(DesignParams(density = 1f), seed = 1L)
        assertNull(GradientColumnsGenerator.morph(few, many))
    }
}
