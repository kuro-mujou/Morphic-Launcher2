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
 * The three fields and the ramp between the disc and the ground. The fields fail the way every gart port here has
 * failed — read at the wrong rate they draw a coherent picture that is simply not the reference — so what is pinned
 * is the *cycle count against the frame*, which is the thing the source does not state.
 */
class PlanetGeneratorTest {

    private val palette = Palette(
        listOf(0xFFF2E2C4.toInt(), 0xFFE6A15C.toInt(), 0xFF2C6E6B.toInt(), 0xFF121E2B.toInt()),
    )

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
        assertEquals(300, PlanetGenerator.particleCount(0f))
        assertEquals(2500, PlanetGenerator.particleCount(1f))
        assertEquals(20, PlanetGenerator.trailSteps(0f))
        assertEquals(160, PlanetGenerator.trailSteps(1f))
        // Out of range clamps rather than running off the ends.
        assertEquals(300, PlanetGenerator.particleCount(-1f))
        assertEquals(160, PlanetGenerator.trailSteps(2f))
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
     * A mark has to avoid **both** ends of the palette here, where every other design avoids one: the ground it is
     * drawn over is the last stop and the disc it is drawn on is the first, and a mark in either is a mark that
     * failed to draw.
     */
    @Test
    fun `no mark takes the disc's color or the ground's`() {
        for (stops in 2..6) {
            val reduced = Palette(List(stops) { 0xFF000000.toInt() or (it * 0x2A2A2A) })
            var at = 0f
            while (at <= 1f) {
                val tone = PlanetGenerator.toneAt(at, reduced)
                assertNotEquals("a mark took the disc at $stops stops, $at", reduced.colorAt(0), tone)
                assertNotEquals("a mark took the ground at $stops stops, $at", reduced.colorAt(stops - 1), tone)
                at += 0.01f
            }
        }
    }

    /** The ramp still travels, or every current is the same tone and the disc reads flat. */
    @Test
    fun `the ramp spans what is left between them`() {
        val start = PlanetGenerator.toneAt(0f, palette)
        val end = PlanetGenerator.toneAt(1f, palette)
        val apart = (0..2).sumOf { c -> abs((start shr (c * 8) and 0xFF) - (end shr (c * 8) and 0xFF)) }

        assertTrue("the ramp barely changes color: $apart", apart > 60)
    }

    /** A particle's tone comes off where it was born, and stays on the ramp whatever it is handed. */
    @Test
    fun `the birth tone runs the diagonal and stays in range`() {
        assertTrue(PlanetGenerator.bandAt(0.1f, 0.1f) < PlanetGenerator.bandAt(0.8f, 0.8f))
        for (nx in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
            for (ny in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
                assertTrue(PlanetGenerator.bandAt(nx, ny) in 0f..1f)
            }
        }
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
