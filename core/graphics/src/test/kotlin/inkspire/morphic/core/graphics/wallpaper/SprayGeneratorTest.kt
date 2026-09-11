package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The field, the walk it produces and the trail's ramp. All three fail the same quiet way — a field read at the wrong
 * rate draws a picture that is simply a *different* design rather than a broken one, which is exactly what happened
 * the first time this generator was built.
 */
class SprayGeneratorTest {

    /** A tall phone: 1080×2400. Every aspect-sensitive case is measured on one, since a square frame hides the bug. */
    private val phone = 2400f / 1080f

    /** The field's rate, as the generator's own constant — what a step has to be judged against. */
    private val rate = 3000f

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
        val here = SprayGenerator.flowAngle(0.1f, 0.3f, turbulence = 0f)
        val there = SprayGenerator.flowAngle(0.9f, 1.8f, turbulence = 0f)

        assertEquals(here, there, 0f)
    }

    /** And it reaches every direction at the top, so a particle can be sent anywhere rather than merely deflected. */
    @Test
    fun `full turbulence spans a whole revolution`() {
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        var nx = 0f
        while (nx <= 1f) {
            val angle = SprayGenerator.flowAngle(nx, nx * phone, turbulence = 1f)
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
     * numbers describe a field that turns once across the whole frame and the particles trace arcs instead: a
     * different design, drawn from the same source, and only one of them is Spring.
     */
    @Test
    fun `a step crosses several of the field's periods`() {
        val periods = step / (2f * Math.PI.toFloat() / rate)

        assertTrue("a step must cross several periods, was $periods", periods > 2f)
    }

    /** Which is the property that actually matters: one step is enough to forget the last direction. */
    @Test
    fun `a step decorrelates the direction`() {
        var apart = 0f
        var nx = 0.1f
        while (nx <= 0.9f) {
            val here = SprayGenerator.flowAngle(nx, 0.4f, 1f)
            val next = SprayGenerator.flowAngle(nx + step, 0.4f, 1f)
            apart = maxOf(apart, abs(next - here))
            nx += 0.0013f
        }

        assertTrue("a step should swing the angle past a right angle, was $apart", apart > 2.5f)
    }

    /**
     * A particle carried by the field has to spread as a **cloud**, not travel as a line — the random walk is the
     * whole design, and it is what a smooth field cannot produce.
     */
    @Test
    fun `a particle walks rather than travels`() {
        var x = 0.5f
        var y = 0.5f
        repeat(400) {
            val angle = SprayGenerator.flowAngle(x, y, 1f)
            x += cos(angle) * step
            y += sin(angle) * step
        }

        val walked = abs(x - 0.5f) + abs(y - 0.5f)
        val straight = 400 * step

        assertTrue("a walk should stay near where it started, went $walked of $straight", walked < straight / 4f)
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

    /**
     * **A moment holds every dot of both mists**: those both have, at full opacity, and those only one has, fading —
     * so nothing goes missing or is doubled on the way across.
     */
    @Test
    fun `a moment holds both mists' dots, the unshared ones fading`() {
        val a = SprayGenerator.plan(1080, 2400, DesignParams(), seed = 1L)
        val b = SprayGenerator.plan(1080, 2400, DesignParams(), seed = 2L)
        val (both, leaving, arriving) = SprayGenerator.Morph(a, b).at(0.3f)
        assertEquals(a.lengths.sum(), both.counts.sum() + leaving.counts.sum())
        assertEquals(b.lengths.sum(), both.counts.sum() + arriving.counts.sum())
        assertEquals(0.7f, leaving.opacity, 1e-6f)
        assertEquals(0.3f, arriving.opacity, 1e-6f)
    }

    /**
     * **A cloud keeps its spread through the middle of a scrub, and the mist its drift.** The spread is every shared
     * dot's distance from its step's mean offset, which a straight blend of two unrelated walks would pull in to about
     * 0.7, and a turn of the whole offset would inflate along the drift. Measured on the scrub's own positions.
     */
    @Test
    fun `a cloud keeps its spread mid-scrub and the mist its drift`() {
        val a = SprayGenerator.plan(1080, 2400, DesignParams(), seed = 3L)
        val b = SprayGenerator.plan(1080, 2400, DesignParams(), seed = 4L)
        val morph = SprayGenerator.Morph(a, b)
        val (spreadA, driftA) = statistics(morph, a, b, 0f)
        val (spreadB, driftB) = statistics(morph, a, b, 1f)
        val (spreadMid, driftMid) = statistics(morph, a, b, 0.5f)
        assertEquals("the spread holds", 1.0, spreadMid / ((spreadA + spreadB) / 2), 0.05)
        // Relative, and loose enough for the sampling: the mean is over the trails that reach that step in both mists,
        // a slightly different set from either end's own. A drift turned whole would read about 1.4 here.
        assertEquals("the drift is the ends' own, halfway", 1.0, driftMid / ((driftA + driftB) / 2), 0.03)
    }

    /**
     * **The mist keeps its reach to the frame's edges mid-scrub.** Trail starts are uniform over the frame, and two
     * unrelated uniform points averaged fall toward the middle — so paired by index, the whole mist pulls in from the
     * sides halfway. Paired by nearness, a start moves only locally and the share near an edge holds.
     */
    @Test
    fun `mid-scrub the trails still start all the way out to the frame's edges`() {
        val a = SprayGenerator.plan(1080, 2400, DesignParams(), seed = 5L)
        val b = SprayGenerator.plan(1080, 2400, DesignParams(), seed = 6L)
        val morph = SprayGenerator.Morph(a, b)
        fun nearEdge(x: Float) = x < 108f || x > 972f
        val ends = a.lengths.indices.count { nearEdge(a.xs[it * a.steps]) } +
            b.lengths.indices.count { nearEdge(b.xs[it * b.steps]) }
        val mid = a.lengths.indices.count { trail ->
            nearEdge((a.xs[trail * a.steps] + b.xs[morph.partner[trail] * b.steps]) / 2f)
        }
        assertTrue("mid-scrub $mid near an edge, against ${ends / 2} at the ends", mid > 0.8 * ends / 2)
    }

    /**
     * The shared dots' spread about their step's mean offset, and that mean's `x` at the last step every trail shares
     * — the drift — at the moment [t], read through [morph] itself.
     */
    private fun statistics(
        morph: SprayGenerator.Morph,
        a: SprayGenerator.Plan,
        b: SprayGenerator.Plan,
        t: Float,
    ): Pair<Double, Double> {
        val turn = NoiseTurn(t)
        val out = FloatArray(2)
        val offsets = List(a.steps) { ArrayList<Pair<Double, Double>>() }
        for (trail in a.lengths.indices) {
            val first = trail * a.steps
            val other = morph.partner[trail] * b.steps
            val x0 = a.xs[first] + (b.xs[other] - a.xs[first]) * t
            val y0 = a.ys[first] + (b.ys[other] - a.ys[first]) * t
            for (i in 0 until minOf(a.lengths[trail], b.lengths[morph.partner[trail]])) {
                morph.shared(trail, i, t, turn, out)
                offsets[i].add((out[0] - x0).toDouble() to (out[1] - y0).toDouble())
            }
        }
        var squares = 0.0
        var count = 0
        for (step in offsets) {
            if (step.isEmpty()) continue
            val mx = step.sumOf { it.first } / step.size
            val my = step.sumOf { it.second } / step.size
            for ((x, y) in step) squares += (x - mx) * (x - mx) + (y - my) * (y - my)
            count += step.size
        }
        val deep = offsets.indexOfLast { it.size > 50 }
        return sqrt(squares / count) to offsets[deep].sumOf { it.first } / offsets[deep].size
    }
}
