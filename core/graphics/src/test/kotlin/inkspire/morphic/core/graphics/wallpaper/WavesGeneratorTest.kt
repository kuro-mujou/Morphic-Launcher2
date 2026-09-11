package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * The crest arithmetic and the two measurements the design is built on — the shadow's default and the palette turn.
 *
 * A crest decides which band a pixel is in and whether a band shows at all, and it is bounded arithmetic that needs no
 * canvas. The two measured constants are checked here because they are the kind of thing a later tidy-up rounds off
 * without noticing: `0.5` reproducing the reference's shading, and the `±20°/±20pp` turn.
 */
class WavesGeneratorTest {

    private val ripple = WavesGenerator.Lobe(
        listOf(
            WavesGenerator.Term(amplitude = 0.65f, frequency = 2f, phase = 0.4f),
            WavesGenerator.Term(amplitude = 0.35f, frequency = 3f, phase = 1.1f),
        ),
    )

    @Test
    fun `density maps to the crest count range`() {
        assertEquals(1, WavesGenerator.layerCount(0f))
        assertEquals(10, WavesGenerator.layerCount(1f))
        assertEquals(10, WavesGenerator.layerCount(2f)) // clamped
    }

    @Test
    fun `an undistorted crest is the smoothstep between its two edge heights`() {
        val left = 0.2f
        val right = 0.6f
        assertEquals(left, WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 0f), 1e-6f)
        assertEquals(right, WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 1f), 1e-6f)
        // Halfway is halfway for any S with this symmetry; the quarter point is what tells 3t²-2t³ from a half-cosine.
        assertEquals(0.4f, WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 0.5f), 1e-6f)
        assertEquals(
            left + (right - left) * (3f * 0.25f * 0.25f - 2f * 0.25f * 0.25f * 0.25f),
            WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 0.25f),
            1e-6f,
        )
    }

    @Test
    fun `equal edge heights leave the crest flat, however far distortion is pushed`() {
        var nx = 0f
        while (nx <= 1f) {
            val flat = WavesGenerator.crestAt(0.5f, 0.5f, ripple, distortion = 0f, nx = nx)
            assertEquals("a crest with equal edges strayed off them at $nx", 0.5f, flat, 1e-6f)
            nx += 0.05f
        }
    }

    @Test
    fun `distortion never moves a crest off its edge heights`() {
        val left = 0.2f
        val right = 0.6f
        assertEquals(left, WavesGenerator.crestAt(left, right, ripple, distortion = 1f, nx = 0f), 1e-6f)
        assertEquals(right, WavesGenerator.crestAt(left, right, ripple, distortion = 1f, nx = 1f), 1e-6f)
    }

    @Test
    fun `distortion moves the crest between its edges, and bounded`() {
        var moved = false
        var nx = 0f
        while (nx <= 1f) {
            val sweep = WavesGenerator.crestAt(0.3f, 0.7f, ripple, distortion = 0f, nx = nx)
            val warped = WavesGenerator.crestAt(0.3f, 0.7f, ripple, distortion = 1f, nx = nx)
            val off = warped - sweep
            assertTrue("distortion pushed the crest past its sweep at $nx", off in -0.3001f..0.3001f)
            if (off > 0.05f || off < -0.05f) moved = true
            nx += 0.02f
        }
        assertTrue("full distortion left the crest on its sweep", moved)
    }

    @Test
    fun `the default depth reproduces the reference's shading`() {
        assertEquals(0f, WavesGenerator.shadowDepth(0f), 1e-6f)
        // Measured off theirs: a band's top multiplies to x0.815 at the crest, which is a shadow of 0.185.
        assertEquals(0.185f, WavesGenerator.shadowDepth(0.5f), 1e-4f)
        assertEquals(0.37f, WavesGenerator.shadowDepth(1f), 1e-6f)
        assertEquals(0.37f, WavesGenerator.shadowDepth(2f), 1e-6f) // clamped
    }

    @Test
    fun `the gradient fill turns a color by twenty degrees and twenty points, both ways`() {
        // The reference's own blue, and one of the four pairs the transform was measured from: H 217.2 S 91.2 L 59.8.
        val blue = 0xFF3B82F6.toInt()

        // ... turning down lands on the measured (9, 141, 194): H 197.2, the same saturation, L 39.8.
        val down = WavesGenerator.turned(blue, up = false)
        assertChannels(expected = 0xFF098DC2.toInt(), actual = down)

        // Up is the same turn the other way, so a round trip through both is the color again.
        val back = WavesGenerator.turned(WavesGenerator.turned(blue, up = true), up = false)
        assertChannels(expected = blue, actual = back)
    }

    /** [actual]'s channels against [expected]'s, to within a byte or two of the rounding a color round trip costs. */
    private fun assertChannels(expected: Int, actual: Int) {
        assertEquals("alpha", expected ushr 24 and 0xFF, actual ushr 24 and 0xFF)
        assertEquals("red", (expected shr 16 and 0xFF).toDouble(), (actual shr 16 and 0xFF).toDouble(), 2.0)
        assertEquals("green", (expected shr 8 and 0xFF).toDouble(), (actual shr 8 and 0xFF).toDouble(), 2.0)
        assertEquals("blue", (expected and 0xFF).toDouble(), (actual and 0xFF).toDouble(), 2.0)
    }

    /**
     * **Painting the traced edges is counting the crests**, which is the shared derivation between the bands the
     * canvas fills and the design's own rule: a pixel's band is how many crests sit at or above it. Band `k` is filled
     * below the `k`-th highest crest of its column, so at any height the last fill is the count — and at full
     * distortion, where crests cross and swallow each other, this is the only place that would show if it were not.
     */
    @Test
    fun `the traced edges give every pixel the band the crests count for it`() {
        val plan = WavesGenerator.plan(DesignParams(density = 1f, scale = 1f, irregularity = 1f), seed = 7L)
        val width = 1080
        val height = 2400
        val xs = FloatArray(55) { it * 20f }
        val edges = WavesGenerator.edges(plan, xs, width, height)

        for (j in xs.indices) {
            val nx = xs[j] / (width - 1)
            for (y in 0 until height step 7) {
                val t = y.toFloat() / height
                val counted = plan.lobes.indices.count {
                    WavesGenerator.crestAt(plan.left[it], plan.right[it], plan.lobes[it], plan.distortion, nx) <= t
                }
                val painted = edges.count { it[j] <= y.toFloat() }
                assertEquals("column ${xs[j]}, row $y", counted, painted)
            }
        }
    }

    @Test
    fun `a scrub begins and ends on the plans, and keeps each edge layout in order`() {
        val params = DesignParams(density = 1f, scale = 1f, irregularity = 1f)
        val from = WavesGenerator.plan(params, seed = 1L)
        val to = WavesGenerator.plan(params, seed = 2L)
        val morph = requireNotNull(WavesGenerator.morph(from, to))
        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        for (step in 0..20) {
            val moment = morph.at(step / 20f)
            assertEquals(moment.left.toList().sorted(), moment.left.toList())
            assertEquals(moment.right.toList().sorted(), moment.right.toList())
        }
    }

    /** A phase is an angle, so a ripple turns the short way to its partner rather than spinning most of a cycle. */
    @Test
    fun `a ripple's phase turns the short way round`() {
        val plan = WavesGenerator.plan(DesignParams(), seed = 1L)
        fun withPhase(phase: Float) = WavesGenerator.Plan(
            plan.left, plan.right,
            plan.lobes.map { lobe -> WavesGenerator.Lobe(lobe.terms.map { it.copy(phase = phase) }) },
            plan.distortion, plan.shadow, plan.fill,
        )
        val morph = requireNotNull(WavesGenerator.morph(withPhase(0.1f), withPhase((2 * PI).toFloat() - 0.1f)))
        val middle = morph.at(0.5f).lobes.first().terms.first().phase
        assertTrue("the phase went the long way: $middle", abs(middle) < 0.01f)
    }

    @Test
    fun `two frames of different crest counts are refused rather than paired`() {
        val few = WavesGenerator.plan(DesignParams(density = 0f), seed = 1L)
        val many = WavesGenerator.plan(DesignParams(density = 1f), seed = 1L)
        assertNull(WavesGenerator.morph(few, many))
    }
}
