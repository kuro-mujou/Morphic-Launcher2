package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The three fields and the pigment laid over the disc. The fields fail the way every gart port here has
 * failed — read at the wrong rate they draw a coherent picture that is simply not the reference — so what is pinned
 * is the *cycle count against the frame*, which is the thing the source does not state.
 */
class PlanetGeneratorTest {

    /**
     * Twelve vortices as the generator builds them — `x, y, spin` triples, the spin `±1`.
     *
     * Written out because the obvious `FloatArray(36) { Random(4).nextFloat() }` makes a *new* generator per element,
     * so every vortex lands on the same point with the same fractional spin — a fixture that quietly tests one
     * vortex, which is exactly the field this class is here to check.
     */
    private val vortices = Random(4).let { r ->
        FloatArray(36) { i -> if (i % 3 == 2) if (r.nextBoolean()) 1f else -1f else r.nextFloat() }
    }

    @Test
    fun `the knobs map to their ranges`() {
        assertEquals(0.26f * 1080f, PlanetGenerator.discRadius(0f, 1080f), 1e-3f)
        assertEquals(0.46f * 1080f, PlanetGenerator.discRadius(1f, 1080f), 1e-3f)
        assertEquals(4, PlanetGenerator.slabCount(0f))
        assertEquals(28, PlanetGenerator.slabCount(1f))
        // Out of range clamps rather than running off the ends.
        assertEquals(4, PlanetGenerator.slabCount(-1f))
    }


    /**
     * **The stir is a distance in radii, not a step count** — the unit that collapsed this design once. The walk
     * stops at the rim, so a drag longer than the disc traces nearly every pixel out through the upstream edge and
     * the whole face takes its color from the slab function along that one arc: two or three enormous flat lobes,
     * whatever the field or the palette. So what is pinned is the *distance*, at both ends and against two disc
     * sizes, and that even the top of the knob stays inside a radius.
     */
    @Test
    fun `the stir is a fraction of the disc, not a fixed number of steps`() {
        for (radius in listOf(200f, 800f)) {
            val stride = radius * 0.011f // the generator's own step, as a share of a disc this size
            val shortest = PlanetGenerator.stirSteps(0f, radius, stride) * stride / radius
            val longest = PlanetGenerator.stirSteps(1f, radius, stride) * stride / radius
            assertTrue("radius $radius barely stirs at the top: $longest", longest > 0.6f)
            assertTrue("radius $radius drags past the rim at the top: $longest", longest < 1f)
            assertTrue("radius $radius already stirs at the bottom: $shortest", shortest < 0.15f)
        }
        // And the same fraction whatever the disc, which a step count cannot be.
        val small = PlanetGenerator.stirSteps(0.5f, 200f, 2f) * 2f / 200f
        val large = PlanetGenerator.stirSteps(0.5f, 800f, 8f) * 8f / 800f
        assertEquals("the stir depends on the disc's size", small, large, 0.02f)
    }

    /** `0` is a flat field on every look — the rigid end the field's contract asks for, and a picture of its own. */
    @Test
    fun `no turbulence leaves one heading, whichever field`() {
        for (look in 0..2) {
            val here = PlanetGenerator.fieldAngle(0.2f, 0.3f, look, 0f, vortices)
            val there = PlanetGenerator.fieldAngle(0.8f, 0.7f, look, 0f, vortices)
            assertEquals("look $look bends at zero turbulence", here, there, 1e-4f)
        }
    }

    /**
     * **The reading every gart port here has got wrong once.** These fields are written against *pixel* coordinates
     * on a 1024 frame, so `sin(x * 0.01)` is about 1.6 cycles across it. Read as a unit square the same constant is a
     * sixth of a cycle — a field that barely turns — and read at [SprayGenerator]'s rate it is noise. Only one of the
     * three draws gart's picture, and the cycle count is what tells them apart.
     */
    @Test
    fun `the fields turn a few times across the frame, not a fraction and not hundreds`() {
        for (look in 0..2) {
            // Turning points along a scanline: two per cycle, so this counts the field's own frequency directly.
            var turns = 0
            var previous = PlanetGenerator.fieldAngle(0f, 0.5f, look, 1f, vortices)
            var rising = 0
            var nx = 0.002f
            while (nx <= 1f) {
                val angle = PlanetGenerator.fieldAngle(nx, 0.5f, look, 1f, vortices)
                val way = if (angle > previous) 1 else if (angle < previous) -1 else rising
                if (rising != 0 && way != rising) turns++
                rising = way
                previous = angle
                nx += 0.002f
            }
            assertTrue("look $look barely turns across the frame: $turns", turns >= 2)
            assertTrue("look $look is noise rather than a field: $turns", turns < 120)
        }
    }

    /**
     * **Every field has to turn at the knob's *default*, not only at its top.** The vortex field shipped a build
     * where it did not: its circulation is added to a uniform drift, and a constant converted as though it were a
     * length made it fifty times the smaller of the two — so at `0.5` the drift won, the disc drew straight streaks,
     * and nothing but the render said so. A knob whose middle is its own rigid end is the failure `DesignStyle`
     * exists to prevent, and it is invisible to a guard that only asks whether the two *ends* differ.
     */
    @Test
    fun `every field turns across the disc at the default turbulence`() {
        for (look in 0..2) {
            var lowest = Float.MAX_VALUE
            var highest = -Float.MAX_VALUE
            var nx = 0.3f
            while (nx <= 0.7f) {
                var ny = 0.3f
                while (ny <= 0.7f) {
                    val angle = PlanetGenerator.fieldAngle(nx, ny, look, 0.5f, vortices)
                    lowest = minOf(lowest, angle)
                    highest = maxOf(highest, angle)
                    ny += 0.01f
                }
                nx += 0.01f
            }
            assertTrue("look $look is flat at half turbulence: ${highest - lowest}", highest - lowest > 0.5f)
        }
    }

    /** Neighbouring steps have to agree, or the particle walks instead of tracing a current. */
    @Test
    fun `one step barely moves the direction, so a particle traces a line`() {
        val step = 0.004f
        for (look in 0..2) {
            var worst = 0f
            var nx = 0.1f
            while (nx <= 0.9f) {
                val here = PlanetGenerator.fieldAngle(nx, 0.45f, look, 1f, vortices)
                val next = PlanetGenerator.fieldAngle(nx + step, 0.45f, look, 1f, vortices)
                worst = maxOf(worst, abs(next - here))
                nx += 0.001f
            }
            assertTrue("look $look decorrelates in one step: $worst", worst < 1f)
        }
    }

    /** And a particle carried by it must actually get somewhere, rather than sitting still or circling on the spot. */
    @Test
    fun `a particle travels across the disc`() {
        val step = 0.004f
        for (look in 0..2) {
            var x = 0.5f
            var y = 0.5f
            repeat(120) {
                val angle = PlanetGenerator.fieldAngle(x, y, look, 1f, vortices)
                val speed = PlanetGenerator.fieldSpeed(x, y, look)
                x += cos(angle) * step * speed
                y += sin(angle) * step * speed
            }
            val moved = abs(x - 0.5f) + abs(y - 0.5f)
            assertTrue("look $look went nowhere: $moved", moved > step * 8f)
        }
    }

    /** Only the marbled field varies its speed — a generator reads only the inputs its look depends on. */
    @Test
    fun `the speed is the marbled field's alone`() {
        assertEquals(1f, PlanetGenerator.fieldSpeed(0.2f, 0.7f, 0), 0f)
        assertEquals(1f, PlanetGenerator.fieldSpeed(0.2f, 0.7f, 2), 0f)
        assertNotEquals(1f, PlanetGenerator.fieldSpeed(0.2f, 0.7f, 1))
        assertTrue(PlanetGenerator.fieldSpeed(0.2f, 0.7f, 1) in 0.6f..1.4f)
    }

    /**
     * A mark has to avoid the **disc's** color, which is the inversion this design makes of the usual rule: every
     * other design here paints on the ground and must not vanish into it, and this one paints on the disc. The
     * ground's own color is deliberately *not* excluded — gart's `Orb3` swirls its clear color through the disc, and
     * on the default two-stop palette holding it back leaves nothing to stir with.
     */
    @Test
    fun `no mark takes the disc's color`() {
        for (stops in 2..6) {
            val reduced = Palette(List(stops) { 0xFF000000.toInt() or (it * 0x2A2A2A) })
            val tones = RampTones.aboveGround(reduced)
            assertTrue("$stops stops leave the disc nothing to be stirred with", tones.size >= 3)
            for (tone in tones) {
                assertNotEquals("a mark took the disc at $stops stops", reduced.colorAt(0), tone)
            }
        }
    }

    /**
     * **Neighbouring slabs have to be neighbouring *stops*, never neighbouring points on a ramp.** That is the whole
     * of the design's color, and it is the thing whose absence is a coherent picture rather than a broken one: the
     * first build sampled a continuous gradient at each particle's birth, so two strands running past each other
     * differed by a percent of a ramp and the disc drew as one hairy wash. What this pins is that walking down the
     * disc changes tone a handful of times rather than continuously.
     */
    @Test
    fun `the pigment is laid in slabs of one stop`() {
        for (look in 0..2) {
            val seen = mutableSetOf<Int>()
            var changes = 0
            var previous = -1
            var v = -1f
            while (v <= 1f) {
                val index = PlanetGenerator.toneIndex(0f, v, look, slabs = 6, tones = 5)
                assertTrue("look $look indexed outside the tones: $index", index in 0..4)
                if (previous != -1 && index != previous) changes++
                seen += index
                previous = index
                v += 0.002f
            }
            assertTrue("look $look paints the disc in one tone", seen.size >= 3)
            // One change per slab boundary, and nothing near the thousand samples a continuous index would give.
            assertTrue("look $look reads as a ramp rather than slabs: $changes", changes in 2..15)
        }
    }

    /** The run turns back on itself at the palette's end rather than wrapping onto its opposite stop. */
    @Test
    fun `the slab run mirrors instead of jumping the palette's ends`() {
        // Four tones over the vortex field's four slabs: 0,1,2,3 walking down, with no 3-to-0 seam anywhere.
        val walk = (0..3).map { slab ->
            PlanetGenerator.toneIndex(0f, -1f + (slab + 0.5f) / 4f * 2f, look = 2, slabs = 4, tones = 4)
        }
        assertEquals(listOf(0, 1, 2, 3), walk)
        // And with fewer tones than slabs the run comes back down rather than snapping to the other end.
        val short = (0..5).map { slab ->
            PlanetGenerator.toneIndex(0f, -1f + (slab + 0.5f) / 6f * 2f, look = 0, slabs = 6, tones = 3)
        }
        assertEquals(listOf(0, 1, 2, 2, 1, 0), short)
    }


    /**
     * **The sphere is the refraction, not the shading.** A lit flat disc is a coin; what makes a poured field read as
     * round is gart's Snell displacement pulling the pattern inward, hard at the limb and barely at all in the middle.
     * So what is pinned is the *shape* of that curve — monotone inward, and an order of magnitude more squeeze at the
     * edge than at the center — plus the flat disc `Orb2` and `Orb3` are, which is what depth `0` has to give back.
     */
    @Test
    fun `the sphere squeezes the limb and leaves the middle alone`() {
        for (nd in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals("depth 0 warps at $nd", 1f, PlanetGenerator.refraction(nd, 0f), 1e-5f)
        }

        val middle = PlanetGenerator.refraction(0f, 1f)
        val limb = PlanetGenerator.refraction(1f, 1f)
        assertTrue("the middle is not magnified: $middle", middle in 0.6f..0.95f)
        assertTrue("the limb is not squeezed: $limb", limb < 0.35f)

        // Monotone inward from the middle to the edge, or the pattern folds over itself somewhere in between.
        var previous = middle
        var nd = 0.02f
        while (nd <= 1f) {
            val here = PlanetGenerator.refraction(nd, 1f)
            assertTrue("the warp turns back out at $nd", here <= previous + 1e-4f)
            previous = here
            nd += 0.02f
        }

        // And it scales with the knob rather than switching on, so the middle of the slider is half a sphere.
        val half = PlanetGenerator.refraction(1f, 0.5f)
        assertTrue("depth does not scale the warp: $half", half > limb && half < 1f)
    }

    /** The three fields have to be three pictures, which a variant chooser cannot check for itself. */
    @Test
    fun `each field carries a particle somewhere different`() {
        fun path(look: Int): Pair<Float, Float> {
            var x = 0.4f
            var y = 0.6f
            repeat(80) {
                val angle = PlanetGenerator.fieldAngle(x, y, look, 1f, vortices)
                x += cos(angle) * 0.004f * PlanetGenerator.fieldSpeed(x, y, look)
                y += sin(angle) * 0.004f * PlanetGenerator.fieldSpeed(x, y, look)
            }
            return x to y
        }

        val ends = (0..2).map { path(it) }
        for (a in 0..2) {
            for (b in a + 1..2) {
                val apart = abs(ends[a].first - ends[b].first) + abs(ends[a].second - ends[b].second)
                assertTrue("fields $a and $b carry a particle to the same place", apart > 0.02f)
            }
        }
    }
}
