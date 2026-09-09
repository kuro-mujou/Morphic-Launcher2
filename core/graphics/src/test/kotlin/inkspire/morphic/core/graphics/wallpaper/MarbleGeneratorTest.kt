package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.asin

/**
 * The vein field and the ramp it resolves to — a vein that lands a fraction of a period from where it belongs, or a
 * knob whose two ends resolve to the same ridge, draws a *plausible* slab, which is why none of it can be judged by
 * looking at one.
 */
class MarbleGeneratorTest {

    @Test
    fun `a spine takes the ramp's far end, and the stone between two spines does not`() {
        val onSpine = ramp(phase = 0f)
        val between = ramp(phase = HalfPeriod)
        assertEquals("a spine must reach the palette's last stop", 1f, onSpine, 1e-5f)
        assertTrue("the stone between veins came out in the vein's color: $between", between < 0.5f)
    }

    /** Every spine of the coarse set — the `|sin|` zeros, one per period — not only the one at the origin. */
    @Test
    fun `the veins repeat once per period`() {
        for (vein in 0..5) {
            assertEquals("vein $vein was not on a spine", 1f, ramp(vein * Period), 1e-5f)
        }
    }

    /**
     * The clouded body fills most of the frame, so it has to stay clear of the end of the ramp the veins take — a body
     * that reaches it arrives in the vein's own color and the veins stop reading as veins.
     */
    @Test
    fun `the stone between two veins stays clear of the veins' end of the ramp, however the cloud falls`() {
        val halfWidth = MarbleGenerator.thicknessFor(0f)
        var highest = 0f
        // The middle half of a period: clear of both spines bounding it, and across the hairlines that cross it.
        var phase = Period / 4f
        while (phase < Period * 3f / 4f) {
            for (mottle in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                highest = maxOf(highest, ramp(phase, halfWidth = halfWidth, mottle = mottle))
            }
            phase += 0.01f
        }
        assertTrue("the stone arrived in the veins' own color: $highest", highest < 0.9f)
    }

    @Test
    fun `thickness widens the vein, so the knob's two ends are two slabs`() {
        // A pixel whose ridge falls between the two reaches: inside a thick vein, outside a thin one.
        val phase = 0.15f
        val thin = ramp(phase, halfWidth = MarbleGenerator.thicknessFor(0f))
        val thick = ramp(phase, halfWidth = MarbleGenerator.thicknessFor(1f))
        assertTrue("thickness did not widen the vein: $thin vs $thick", thick > thin)
    }

    /**
     * A vein has to lose most of its depth in the **inner** half of its reach and trail off through the outer one —
     * that asymmetry is what gives the seam an edge. A single smoothstep across the whole reach is the profile this
     * rules out: its steepest point is the middle, so the vein arrives gradually from both sides and reads as an
     * airbrushed wave rather than as a mineral seam.
     */
    @Test
    fun `a vein loses most of its depth inside the core, so the seam has an edge`() {
        val halfWidth = MarbleGenerator.thicknessFor(0.5f)
        val spine = ramp(0f, halfWidth = halfWidth)
        val inner = ramp(phaseForRidge(halfWidth * InnerSample), halfWidth = halfWidth)
        val outer = ramp(phaseForRidge(halfWidth * OuterSample), halfWidth = halfWidth)
        assertTrue("the vein did not fall away from its spine: $spine, $inner, $outer", spine > inner && inner > outer)
        assertTrue("the falloff was not steepest at the seam", spine - inner > inner - outer)
    }

    @Test
    fun `the relief lifts one shoulder of a vein and sinks the other`() {
        // Just either side of the spine at one period, where the ridge's slope flips sign.
        val before = ramp(Period - Shoulder, relief = 1f)
        val after = ramp(Period + Shoulder, relief = 1f)
        val flat = ramp(Period - Shoulder, relief = 0f)
        assertTrue("the two shoulders were lit the same: $before vs $after", before != after)
        assertTrue("relief 0 must be flat", flat != before)
    }

    @Test
    fun `the turbulence knob is measured in periods, so it wanders the same however many veins there are`() {
        for (veins in listOf(1, 10)) {
            val rigid = MarbleGenerator.phaseOf(axisPos = 0.5f, bend = 1f, veins = veins, turbulence = 0f)
            val loose = MarbleGenerator.phaseOf(axisPos = 0.5f, bend = 1f, veins = veins, turbulence = 1f)
            assertEquals("the push was not one period-share at $veins veins", 1.4f, (loose - rigid) / Period, 1e-5f)
        }
    }

    @Test
    fun `turbulence 0 leaves the veins ruled, which is the design's rigid end`() {
        val straight = MarbleGenerator.phaseOf(axisPos = 0.25f, bend = 0.9f, veins = 6, turbulence = 0f)
        assertEquals("a bend moved a rigid vein", 0.25f * 6f * PI.toFloat(), straight, 1e-5f)
    }

    @Test
    fun `variation swells and thins a vein about the thickness it was given`() {
        val thickness = MarbleGenerator.thicknessFor(0.5f)
        assertEquals(
            "variation 0 must leave the reach alone",
            thickness,
            MarbleGenerator.halfWidthAt(thickness, variation = 0f, swell = 1f),
            1e-6f,
        )
        assertTrue(MarbleGenerator.halfWidthAt(thickness, variation = 1f, swell = 1f) > thickness)
        assertTrue(MarbleGenerator.halfWidthAt(thickness, variation = 1f, swell = -1f) < thickness)
    }

    @Test
    fun `a vein never thins to nothing, so a line does not break mid-frame`() {
        // The swell at its most destructive: full variation pushing the thinnest reach the knob offers toward zero.
        val reach = MarbleGenerator.halfWidthAt(MarbleGenerator.thicknessFor(0f), variation = 1f, swell = -1f)
        assertTrue("a vein vanished: $reach", reach > 0f)
    }

    /** The thinned vein has to pale as well as narrow, and the two readings come off one field — see `fadeAt`. */
    @Test
    fun `the swell that thins a vein also fades it, and only where variation asks`() {
        assertEquals("variation 0 must leave every vein at full depth", 1f, MarbleGenerator.fadeAt(0f, -1f), 0f)
        val thinned = MarbleGenerator.fadeAt(variation = 1f, swell = -1f)
        val swollen = MarbleGenerator.fadeAt(variation = 1f, swell = 1f)
        assertTrue("a thinned vein kept its full depth: $thinned", thinned < 1f)
        assertTrue("a swollen vein was faded: $swollen", swollen > thinned)
        assertTrue("the fade left nothing to draw: $thinned", thinned > 0f)
    }

    @Test
    fun `direction leaves the veins upright at 0 and sweeps a half turn`() {
        assertEquals(0f, MarbleGenerator.degreesFor(0f), 0f)
        assertEquals(180f, MarbleGenerator.degreesFor(1f), 0f)
        assertEquals(180f, MarbleGenerator.degreesFor(2f), 0f) // clamped
    }

    @Test
    fun `the dark-stone layout reads the ramp from the other end`() {
        assertEquals(0.2f, MarbleGenerator.laidOut(0.2f, colorLayout = 0), 0f)
        assertEquals(0.8f, MarbleGenerator.laidOut(0.2f, colorLayout = 1), 1e-6f)
    }

    @Test
    fun `the ramp stays within the palette's bounds everywhere, so no reading is clamped into a flat patch`() {
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        // Flattened to one loop over the corners of the parameter box, so the sweep is not four levels deep.
        val corners = listOf(0f, 0.5f, 1f).flatMap { relief ->
            listOf(0f, 1f).flatMap { mottle -> listOf(0.45f, 1f).map { fade -> Triple(relief, mottle, fade) } }
        }
        var phase = 0f
        while (phase < 4f * Period) {
            for ((relief, mottle, fade) in corners) {
                val ramp = ramp(phase, halfWidth = 0.3f, fade = fade, mottle = mottle, relief = relief)
                lowest = minOf(lowest, ramp)
                highest = maxOf(highest, ramp)
            }
            phase += 0.02f
        }
        assertTrue("the ramp went below the first stop: $lowest", lowest >= 0f)
        assertTrue("the ramp went past the last stop: $highest", highest <= 1f)
    }

    /** The default slab, with one reading at a time moved — so a difference can only be the reading that moved. */
    private fun ramp(
        phase: Float,
        halfWidth: Float = MarbleGenerator.thicknessFor(0.5f),
        fade: Float = 1f,
        mottle: Float = 0.5f,
        relief: Float = 0f,
    ): Float = MarbleGenerator.rampAt(phase, halfWidth, fade, mottle, relief)

    /** The phase nearest the origin whose `|sin|` is [ridge] — how a test asks for a distance from a spine. */
    private fun phaseForRidge(ridge: Float): Float = asin(ridge)

    private companion object {

        /** One `|sin|` lobe — the gap from one vein's spine to the next, in [MarbleGenerator.phaseOf]'s phase. */
        const val Period = PI.toFloat()

        /** Half of that, which is the middle of the stone between two veins. */
        const val HalfPeriod = Period / 2f

        /** A small step off a spine, onto the shoulder the relief lights. */
        const val Shoulder = 0.1f

        /** The two halves of a vein's reach, sampled to compare how much depth each of them spends. */
        const val InnerSample = 0.45f
        const val OuterSample = 0.9f
    }
}
