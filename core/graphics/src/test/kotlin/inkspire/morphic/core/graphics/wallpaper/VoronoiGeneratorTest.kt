package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The site scatter and the cells cut around it — which seed owns which part of the frame is geometry that is silently
 * wrong (cells that overlap, or leave the ground showing between them) long before a bitmap could show it. The seam
 * drawing and the fill need a canvas; this checks the part that does not.
 */
class VoronoiGeneratorTest {

    private val palette = Palette(listOf(0xFF241B4E.toInt(), 0xFFB65A78.toInt(), 0xFFFFD9A0.toInt()))

    @Test
    fun `density maps to the site count range`() {
        assertEquals(8, VoronoiGenerator.siteCount(0f))
        assertEquals(40, VoronoiGenerator.siteCount(1f))
        // Out of range clamps rather than running off the ends.
        assertEquals(8, VoronoiGenerator.siteCount(-1f))
        assertEquals(40, VoronoiGenerator.siteCount(2f))
    }

    @Test
    fun `the same seed yields the same sites, so a recipe reproduces`() {
        assertEquals(
            VoronoiGenerator.sites(count = 12, irregularity = 0.5f, stops = palette.size, seed = 99L),
            VoronoiGenerator.sites(count = 12, irregularity = 0.5f, stops = palette.size, seed = 99L),
        )
    }

    @Test
    fun `a different seed yields different sites`() {
        assertTrue(
            VoronoiGenerator.sites(12, 0.5f, palette.size, seed = 1L) !=
                VoronoiGenerator.sites(12, 0.5f, palette.size, seed = 2L),
        )
    }

    @Test
    fun `irregularity scatters the cells off their lattice`() {
        val even = VoronoiGenerator.sites(count = 16, irregularity = 0f, stops = palette.size, seed = 3L)
        val loose = VoronoiGenerator.sites(count = 16, irregularity = 1f, stops = palette.size, seed = 3L)

        // At irregularity 0 the seeds sit on a clean lattice; at 1 they scatter — so the two are not the same cells.
        assertTrue("irregularity did not move the cells", even.map { it.x to it.y } != loose.map { it.x to it.y })
    }

    @Test
    fun `two seeds split the frame along their bisector`() {
        val sites = listOf(VoronoiGenerator.Site(0.25f, 0.5f, 0f), VoronoiGenerator.Site(0.75f, 0.5f, 0f))
        val cells = VoronoiGenerator.cells(sites, aspect = 1f)

        assertEquals(2, cells.size)
        assertEquals(0.5f, GlassCut.area(cells[0].outline), 1e-5f)
        assertEquals(0.5f, GlassCut.bounds(cells[0].outline)[2], 1e-5f) // the left cell ends at the bisector
        assertEquals(0.5f, GlassCut.bounds(cells[1].outline)[0], 1e-5f) // and the right one starts there
    }

    /**
     * **A cell is everything nearer its seed than any other, measured on the screen** — the definition of the diagram,
     * and the one property a wrong bisector breaks without breaking anything else: the cells would still tile the
     * frame, just not around the seeds you can see. On a phone-shaped frame, so a bisector taken in the unit square
     * rather than the aspect-true one would fail it.
     */
    @Test
    fun `every corner of a cell is nearer its own seed than any other, on the screen`() {
        val aspect = 2400f / 1080f
        val sites = VoronoiGenerator.sites(24, 0.8f, palette.size, seed = 5L, heightOverWidth = aspect)
        val cells = VoronoiGenerator.cells(sites, aspect)
        assertEquals(sites.size, cells.size)

        cells.forEachIndexed { i, cell ->
            for (k in cell.outline.indices step 2) {
                val x = cell.outline[k]
                val y = cell.outline[k + 1]
                fun distance(site: VoronoiGenerator.Site) = hypot(x - site.x, y - site.y * aspect)
                val own = distance(sites[i])
                for (other in sites) assertTrue("cell $i reaches nearer another seed", own <= distance(other) + 1e-4f)
            }
        }
    }

    /** The cells are a partition: together they are exactly the frame, which a missed or doubled clip would not be. */
    @Test
    fun `the cells tile the frame with nothing over and nothing missing`() {
        val aspect = 2400f / 1080f
        for (seed in 1L..6L) {
            val sites = VoronoiGenerator.sites(40, 1f, palette.size, seed, heightOverWidth = aspect)
            val cells = VoronoiGenerator.cells(sites, aspect)
            assertEquals("seed $seed", aspect, cells.sumOf { GlassCut.area(it.outline).toDouble() }.toFloat(), 1e-3f)
        }
    }

    @Test
    fun `a cell's color is the gradient near its seed's height`() {
        // With no color jitter a cell would be exactly the gradient at its height, read over the span the seam
        // leaves it; the jitter is bounded, so the cell's red stays within a stop's reach of that un-jittered ramp
        // rather than jumping the palette.
        val sites = VoronoiGenerator.sites(count = 20, irregularity = 0.5f, stops = palette.size, seed = 7L)
        val ceiling = VoronoiGenerator.fillCeiling(palette.size)

        for (site in sites) {
            val here = LinearGradientGenerator.colorAt(site.y * ceiling, palette) shr 16 and 0xFF
            val neighborhood = intArrayOf(
                LinearGradientGenerator.colorAt((site.y - 0.12f).coerceIn(0f, 1f) * ceiling, palette) shr 16 and 0xFF,
                LinearGradientGenerator.colorAt((site.y + 0.12f).coerceIn(0f, 1f) * ceiling, palette) shr 16 and 0xFF,
            )
            val red = LinearGradientGenerator.colorAt(site.tone, palette) shr 16 and 0xFF
            val lo = minOf(here, neighborhood[0], neighborhood[1])
            val hi = maxOf(here, neighborhood[0], neighborhood[1])
            assertTrue("cell color left the gradient's neighborhood", red in lo..hi)
        }
    }

    /**
     * The seams are the design, so no cell may be painted the color they are drawn in — which is what the cells at
     * the bottom of the frame were, before the quality pass, on every palette.
     *
     * Swept across palette sizes because the color mode reduces the palette before the generator sees it, and the
     * two-stop reduction is where a margin taken as a fixed fraction of the ramp would still land on the seam.
     */
    @Test
    fun `no cell takes the seam's own color, at any palette size`() {
        for (stops in 2..6) {
            val reduced = Palette(palette.colors.take(2) + List(stops - 2) { 0xFF808080.toInt() + it })
            val seam = reduced.colorAt(reduced.size - 1)
            for (seed in 1L..8L) {
                for (site in VoronoiGenerator.sites(24, 0.5f, reduced.size, seed)) {
                    val fill = LinearGradientGenerator.colorAt(site.tone, reduced)
                    assertTrue("a cell was painted the seam at $stops stops, seed $seed", fill != seam)
                }
            }
        }
    }

    /**
     * The ramp a cell is read over stops exactly one of [RampTones]' own tones short of the seam — the shared step,
     * rather than a margin of this design's invention.
     */
    @Test
    fun `the fill ramp stops one tone short of the seam`() {
        // Five tones below the ground of a six-stop palette, so the last cell lands on stop four and the seam is five.
        assertEquals(4f / 5f, VoronoiGenerator.fillCeiling(6), 1e-6f)
        // A two-stop palette has no stop to spare, so the floor gives it three tones and a cell stops two thirds down.
        assertEquals(2f / 3f, VoronoiGenerator.fillCeiling(2), 1e-6f)
        // Nothing but ground: no scale separates a cell from the seam, so the ramp is left alone.
        assertEquals(1f, VoronoiGenerator.fillCeiling(1), 1e-6f)
    }

    /**
     * The layouts have to be three pictures, not one — the failure a chooser can carry silently, since a layout that
     * quietly draws its neighbor's picture still looks like a mosaic.
     */
    @Test
    fun `each color layout paints a different set of cells`() {
        val drawn = (0..2).map { layout ->
            VoronoiGenerator.sites(24, 0.5f, palette.size, seed = 11L, layout = layout).map { it.tone }
        }

        assertTrue("radial drew what vertical drew", drawn[0] != drawn[1])
        assertTrue("scattered drew what vertical drew", drawn[0] != drawn[2])
        assertTrue("scattered drew what radial drew", drawn[1] != drawn[2])
    }

    /**
     * Picking a layout must move the colors and nothing else. The cells themselves come off a stream of their own, so
     * a layout that drew from it — even once — would rearrange the whole mosaic underneath a knob labelled *Colors*.
     */
    @Test
    fun `a layout moves the colors and leaves the cells where they are`() {
        val places = (0..2).map { layout ->
            VoronoiGenerator.sites(24, 0.5f, palette.size, seed = 11L, layout = layout).map { it.x to it.y }
        }

        assertEquals(places[0], places[1])
        assertEquals(places[0], places[2])
    }

    /**
     * The radial layout measures on the screen, not in the unit square — so on a tall frame a cell directly above the
     * middle and one the same number of *pixels* to its side read the same place on the ramp. Measured off-axis for
     * [RaysGeneratorTest]'s reason would not help here; what shows the bug is the two axes disagreeing at equal pixel
     * distances, which is exactly what the unit square gets wrong.
     */
    @Test
    fun `the radial layout measures its distance on the screen`() {
        val phone = 2400f / 1080f
        val across = VoronoiGenerator.rampPosition(1, 0.5f + 0.3f, 0.5f, 0f, phone)
        val down = VoronoiGenerator.rampPosition(1, 0.5f, 0.5f + 0.3f / phone, 0f, phone)

        assertEquals(across, down, 1e-5f)
    }

    /** The middle of the frame opens the ramp and a corner closes it, which is what makes the radial layout a bloom. */
    @Test
    fun `the radial layout runs from the middle of the frame to its corner`() {
        val phone = 2400f / 1080f
        assertEquals(0f, VoronoiGenerator.rampPosition(1, 0.5f, 0.5f, 0f, phone), 1e-6f)
        assertEquals(1f, VoronoiGenerator.rampPosition(1, 0f, 0f, 0f, phone), 1e-5f)
    }

    private fun plan(seed: Long) =
        VoronoiGenerator.plan(1080, 2400, DesignParams(density = 1f, irregularity = 0.3f), palette.size, seed)

    /**
     * **A seed's partner is the seed at its own index**, since they sit on `PointScatter`'s lattice — and a
     * misaligned pairing would not fail, it would draw every cell sweeping across the frame to a stranger's place.
     * At a modest scatter each seed's partner is the nearest seed of the next shuffle, which holds only if they share
     * a lattice cell.
     */
    @Test
    fun `a shuffle pairs every seed with the one in its own lattice cell`() {
        val from = plan(1L)
        val to = plan(2L)
        assertNotNull(VoronoiGenerator.morph(from, to))
        from.sites.forEachIndexed { i, a ->
            val nearest = to.sites.indices.minBy { hypot(to.sites[it].x - a.x, (to.sites[it].y - a.y) * from.aspect) }
            assertEquals("seed $i is nearer another seed than its partner", i, nearest)
        }
    }

    /**
     * **The frame stays whole at every moment of a scrub** — the invariant a subdivision's morph owes, and the reason
     * the cells are re-cut per moment rather than paired off: re-cut, a moment is a Voronoi diagram like any other.
     */
    @Test
    fun `the frame stays whole at every moment of a scrub`() {
        val from = plan(3L)
        val to = plan(4L)
        val morph = requireNotNull(VoronoiGenerator.morph(from, to))
        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))

        for (step in 0..20) {
            val moment = morph.at(step / 20f)
            assertEquals("at ${step * 5}%", from.sites.size, moment.cells.size)
            val area = moment.cells.sumOf { GlassCut.area(it.outline).toDouble() }.toFloat()
            assertEquals("at ${step * 5}%", from.aspect, area, 1e-3f)
        }
    }

    @Test
    fun `two mosaics of different counts are refused rather than paired`() {
        val few = VoronoiGenerator.plan(1080, 2400, DesignParams(density = 0f), palette.size, 1L)
        assertNull(VoronoiGenerator.morph(few, plan(1L)))
    }
}
