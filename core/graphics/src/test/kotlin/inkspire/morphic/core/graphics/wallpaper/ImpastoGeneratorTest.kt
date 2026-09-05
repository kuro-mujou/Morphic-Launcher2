package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Where a mark goes and what shape it is — the two things a render cannot tell you are *wrong*, only that something
 * looks off. A sweep that does not reach both edges paints a stripe down the middle of the frame; a dab whose
 * displacement does not decay grows past the brush that asked for it, and both read as "the design is like that".
 */
class ImpastoGeneratorTest {

    private val width = 1080f

    @Test
    fun `density maps to the stroke count range`() {
        assertEquals(60, ImpastoGenerator.strokeCount(0f))
        assertEquals(420, ImpastoGenerator.strokeCount(1f))
        assertEquals(60, ImpastoGenerator.strokeCount(-1f)) // clamped
        assertEquals(420, ImpastoGenerator.strokeCount(2f)) // clamped
    }

    /** The sweep has to reach both edges on every pass, or the design paints a band and leaves the sides bare. */
    @Test
    fun `the serpentine touches both edges and turns over at each band`() {
        val bands = 20
        for (band in 0 until bands) {
            val start = ImpastoGenerator.serpentineAt(band.toFloat() / bands, width)
            val end = ImpastoGenerator.serpentineAt((band + 0.999f) / bands, width)
            val far = if (band % 2 == 0) width else 0f
            val near = if (band % 2 == 0) 0f else width

            assertEquals("band $band starts at the wrong edge", near, start, 1f)
            assertEquals("band $band ends at the wrong edge", far, end, 2f)
        }
    }

    /** It stays on the frame everywhere between, which nothing downstream clamps. */
    @Test
    fun `the serpentine stays inside the frame`() {
        var along = 0f
        while (along <= 1f) {
            val x = ImpastoGenerator.serpentineAt(along, width)
            assertTrue("ran off the frame at $along: $x", x in 0f..width)
            along += 0.005f
        }
        assertEquals(0f, ImpastoGenerator.serpentineAt(-1f, width), 0f) // clamped
        assertTrue(ImpastoGenerator.serpentineAt(2f, width) in 0f..width) // clamped
    }

    /**
     * `0` is the clean octagon the design starts from — the rigid end `irregularity`'s contract asks for, and an
     * **octagon** rather than a disc.
     *
     * A subdivided point lands on the *chord* between two vertices, not on the circle through them, so the extra
     * rounds add points along the octagon's own edges and change its silhouette not at all. That is the same thing
     * W11x found in gart's `deformPath` — at zero offset it leaves an N-gon where a smooth curve would leave an
     * ellipse — and here it is wanted: a field of flat translucent octagons is a paper-collage reading of this
     * design, where a field of discs would be [SoftOverlapsGenerator].
     */
    @Test
    fun `no roughness leaves a clean octagon, not a disc`() {
        val radius = 50f
        val inradius = radius * kotlin.math.cos(kotlin.math.PI.toFloat() / 8f)
        val points = ImpastoGenerator.dabPoints(0f, 0f, radius, roughness = 0f, random = Random(1))

        var corners = 0
        for (i in points.indices step 2) {
            val out = hypot(points[i], points[i + 1])
            assertTrue("vertex ${i / 2} left the octagon at $out", out in (inradius - 1e-3f)..(radius + 1e-3f))
            if (out > radius - 1e-3f) corners++
        }
        assertEquals("the eight original corners must survive", 8, corners)
    }

    /**
     * **The bound the port exists for.** gart's version random-walks its edge, so a mark ends up wherever the walk
     * went; halving the push each round bounds the total at twice the first one, which is what lets *Brush size* mean
     * a size. Swept over seeds because it is a claim about every draw, not about a lucky one.
     */
    @Test
    fun `a fully rough dab stays within the bound its brush sets`() {
        val radius = 50f
        // The first push is 0.55 of the radius and halves, so the sum is under twice it — plus the radius itself.
        val bound = radius * (1f + 2f * 0.55f)

        for (seed in 1L..60L) {
            val points = ImpastoGenerator.dabPoints(0f, 0f, radius, roughness = 1f, random = Random(seed))
            for (i in points.indices step 2) {
                val out = hypot(points[i], points[i + 1])
                assertTrue("vertex ${i / 2} reached $out at seed $seed, past $bound", out <= bound)
            }
        }
    }

    /** And it is genuinely torn at that end, not merely a rounder octagon — the knob has to do something. */
    @Test
    fun `roughness tears the edge`() {
        val clean = ImpastoGenerator.dabPoints(0f, 0f, 50f, roughness = 0f, random = Random(5))
        val torn = ImpastoGenerator.dabPoints(0f, 0f, 50f, roughness = 1f, random = Random(5))

        assertEquals("both ends must have the same vertex count", clean.size, torn.size)
        val spread = (torn.indices step 2)
            .map { hypot(torn[it], torn[it + 1]) }
            .let { it.max() - it.min() }
        assertTrue("the rough end is barely torn: $spread", spread > 20f)
    }

    /**
     * A dab consumes the same draws whatever the roughness, so *Roughness* changes how far the edge is pushed and
     * never the mark it is pushed from — the reshuffle [SeededHarmonics] documents, one design over.
     */
    @Test
    fun `roughness does not shift the stream under the mark`() {
        val after = listOf(0f, 0.5f, 1f).map { roughness ->
            val random = Random(9)
            ImpastoGenerator.dabPoints(0f, 0f, 50f, roughness, random)
            random.nextFloat()
        }

        assertEquals(after[0], after[1], 0f)
        assertEquals(after[0], after[2], 0f)
    }

    /** A mark is never painted in the ground it sits on, or it simply disappears — at any palette length. */
    @Test
    fun `no mark is painted in the ground`() {
        for (stops in 2..6) {
            val palette = Palette(List(stops) { 0xFF000000.toInt() or (it * 0x2A2A2A) })
            var along = 0f
            while (along <= 1f) {
                assertTrue(
                    "a mark took the ground at $stops stops, $along",
                    ImpastoGenerator.toneAt(along, palette) != palette.colorAt(0),
                )
                along += 0.01f
            }
        }
    }

    /**
     * The tone has to move by a **sliver** from one mark to the next and come back round — that is the whole of why
     * overlapping marks mix, and the thing the first cut got wrong by reading the ramp as a handful of flat bands.
     */
    @Test
    fun `the tone advances by a sliver and cycles`() {
        val palette = Palette(listOf(0xFFF2E2C4.toInt(), 0xFFC9603E.toInt(), 0xFF2C6E6B.toInt(), 0xFF121E2B.toInt()))
        val marks = 240

        // Four cycles along the sweep, so a quarter of the way along is one whole turn of the ramp.
        assertEquals(ImpastoGenerator.toneAt(0f, palette), ImpastoGenerator.toneAt(0.25f, palette))

        // And between neighbours the step is small — a mark and the next are near-neighbours on the ramp, never a jump
        // across it. Measured off the cycle boundaries, where the ramp legitimately rolls over.
        var biggest = 0
        for (i in 0 until marks) {
            val here = ImpastoGenerator.toneAt(i.toFloat() / marks, palette)
            val next = ImpastoGenerator.toneAt((i + 1).toFloat() / marks, palette)
            val step = (0..2).sumOf { c -> abs((here shr (c * 8) and 0xFF) - (next shr (c * 8) and 0xFF)) }
            if (i % (marks / 4) != marks / 4 - 1) biggest = maxOf(biggest, step)
        }
        assertTrue("neighbouring marks jump the ramp: $biggest", biggest < 40)
    }
}
