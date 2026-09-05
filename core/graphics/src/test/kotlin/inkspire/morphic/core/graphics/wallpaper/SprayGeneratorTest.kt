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
 * The field, the walk it produces and the trail's ramp. All three fail the same quiet way — a field read at the wrong
 * rate draws a picture that is simply a *different* design rather than a broken one, which is exactly what happened
 * the first time this generator was built.
 */
class SprayGeneratorTest {

    /** A tall phone: 1080×2400. Every aspect-sensitive case is measured on one, since a square frame hides the bug. */
    private val phone = 2400f / 1080f

    /** The two looks' field rates, as the generator's own constants. */
    private val spray = 3000f
    private val plume = 10f

    /** One step, as a share of the frame's width — what the field's rate has to be judged against. */
    private val step = 0.006f

    private val palette = Palette(
        listOf(0xFFF2E2C4.toInt(), 0xFFE6A15C.toInt(), 0xFF2C6E6B.toInt(), 0xFF121E2B.toInt()),
    )

    @Test
    fun `density maps to the trail count range`() {
        assertEquals(100, SprayGenerator.trailCount(0f))
        assertEquals(1200, SprayGenerator.trailCount(1f))
        assertEquals(100, SprayGenerator.trailCount(-1f)) // clamped
        assertEquals(1200, SprayGenerator.trailCount(2f)) // clamped
    }

    @Test
    fun `trail length maps to the step range`() {
        assertEquals(40, SprayGenerator.trailSteps(0f))
        assertEquals(500, SprayGenerator.trailSteps(1f))
        assertEquals(40, SprayGenerator.trailSteps(-1f)) // clamped
        assertEquals(500, SprayGenerator.trailSteps(2f)) // clamped
    }

    /** `0` is a flat field — every particle runs the same way, which is the rigid end and a picture of its own. */
    @Test
    fun `no turbulence leaves one direction everywhere`() {
        val here = SprayGenerator.flowAngle(0.1f, 0.3f, turbulence = 0f, frequency = spray)
        val there = SprayGenerator.flowAngle(0.9f, 1.8f, turbulence = 0f, frequency = spray)

        assertEquals(here, there, 0f)
    }

    /** And it reaches every direction at the top, so a particle can be sent anywhere rather than merely deflected. */
    @Test
    fun `full turbulence spans a whole revolution`() {
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        var nx = 0f
        while (nx <= 1f) {
            val angle = SprayGenerator.flowAngle(nx, nx * phone, turbulence = 1f, frequency = plume)
            lowest = minOf(lowest, angle)
            highest = maxOf(highest, angle)
            nx += 0.001f
        }
        assertTrue("the field barely turns: ${highest - lowest}", highest - lowest > 2f * Math.PI.toFloat())
    }

    /**
     * **The finding this design was rebuilt on.** gart's field is `sin(x * 10)` over *pixel* coordinates, so its
     * period is a fraction of one step and consecutive positions read it several periods apart — which is what makes
     * a particle random walk and the picture a mist. Read as though those coordinates were normalized, the same two
     * numbers describe a field that turns once across the whole frame and the particles trace arcs instead.
     *
     * Both are drawable and only one is Spring, so the two rates are pinned against the step they are judged by.
     */
    @Test
    fun `a spray step crosses several of the field's periods and a plume step stays inside one`() {
        val sprayPeriods = step / (2f * Math.PI.toFloat() / spray)
        val plumePeriods = step / (2f * Math.PI.toFloat() / plume)

        assertTrue("a spray step must cross several periods, was $sprayPeriods", sprayPeriods > 2f)
        assertTrue("a plume step must stay inside one, was $plumePeriods", plumePeriods < 0.05f)
    }

    /** Which is the property that actually matters: on a spray, one step is enough to forget the last direction. */
    @Test
    fun `a spray step decorrelates the direction and a plume step does not`() {
        var sprayApart = 0f
        var plumeApart = 0f
        var nx = 0.1f
        while (nx <= 0.9f) {
            val sprayHere = SprayGenerator.flowAngle(nx, 0.4f, 1f, spray)
            val sprayNext = SprayGenerator.flowAngle(nx + step, 0.4f, 1f, spray)
            sprayApart = maxOf(sprayApart, abs(sprayNext - sprayHere))

            val plumeHere = SprayGenerator.flowAngle(nx, 0.4f, 1f, plume)
            val plumeNext = SprayGenerator.flowAngle(nx + step, 0.4f, 1f, plume)
            plumeApart = maxOf(plumeApart, abs(plumeNext - plumeHere))
            nx += 0.0013f
        }

        assertTrue("a spray step should swing the angle past a right angle, was $sprayApart", sprayApart > 2.5f)
        assertTrue("a plume step should barely move it, was $plumeApart", plumeApart < 0.5f)
    }

    /**
     * A particle carried by the spray field has to spread as a **cloud**, not travel as a line — the random walk is
     * the whole design, and it is what a smooth field cannot produce.
     */
    @Test
    fun `a spray particle walks and a plume particle travels`() {
        fun reach(frequency: Float): Float {
            var x = 0.5f
            var y = 0.5f
            repeat(400) {
                val angle = SprayGenerator.flowAngle(x, y, 1f, frequency)
                x += cos(angle) * step
                y += sin(angle) * step
            }
            return abs(x - 0.5f) + abs(y - 0.5f)
        }

        val walked = reach(spray)
        val travelled = reach(plume)
        val straight = 400 * step

        assertTrue("a walk should stay near where it started, went $walked of $straight", walked < straight / 4f)
        assertTrue("an arc should get somewhere, went $travelled", travelled > straight / 4f)
    }

    /** A dot must never be painted in the ground it lies on, at any palette length. */
    @Test
    fun `no dot is painted in the ground`() {
        for (stops in 2..6) {
            val reduced = Palette(List(stops) { 0xFF000000.toInt() or (it * 0x2A2A2A) })
            val ground = reduced.colorAt(reduced.size - 1)
            var at = 0f
            while (at <= 1f) {
                val tone = SprayGenerator.toneAt(at, reduced)
                assertNotEquals("a dot took the ground at $stops stops, $at", ground, tone)
                at += 0.01f
            }
        }
    }

    /** The ramp still runs the length of the palette — two ends that are plainly different colors. */
    @Test
    fun `the ramp spans the palette`() {
        val start = SprayGenerator.toneAt(0f, palette)
        val end = SprayGenerator.toneAt(1f, palette)
        val apart = (0..2).sumOf { c -> abs((start shr (c * 8) and 0xFF) - (end shr (c * 8) and 0xFF)) }

        assertTrue("the ramp barely changes color: $apart", apart > 120)
    }

    /**
     * The tone is mostly where a trail started and partly how far along it is, and the split matters both ways: all
     * height and the frame bands into flat stripes with no mixing, all walk and every cloud holds the whole palette
     * so the frame averages to one tone.
     */
    @Test
    fun `the tone drifts with the start height and still mixes along the trail`() {
        val high = SprayGenerator.bandAt(from = 0.1f, along = 0.5f)
        val low = SprayGenerator.bandAt(from = 0.9f, along = 0.5f)
        assertTrue("the start height should carry the drift", low - high > 0.4f)

        val early = SprayGenerator.bandAt(from = 0.5f, along = 0f)
        val late = SprayGenerator.bandAt(from = 0.5f, along = 1f)
        assertTrue("a cloud should still spread over the ramp", late - early > 0.15f)
    }

    /** It stays on the ramp whatever it is handed, since the render indexes a band array with it. */
    @Test
    fun `the tone stays in the unit range`() {
        for (from in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
            for (along in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
                assertTrue(SprayGenerator.bandAt(from, along) in 0f..1f)
            }
        }
    }
}
