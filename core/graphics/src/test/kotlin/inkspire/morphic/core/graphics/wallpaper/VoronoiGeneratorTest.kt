package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The site scatter and the nearest-seed assignment — which cell owns a pixel is index arithmetic that is silently
 * wrong (a wallpaper of one flat color, or seams in the wrong place) long before a bitmap could show it. The seam
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
            VoronoiGenerator.sites(count = 12, irregularity = 0.5f, palette = palette, seed = 99L),
            VoronoiGenerator.sites(count = 12, irregularity = 0.5f, palette = palette, seed = 99L),
        )
    }

    @Test
    fun `a different seed yields different sites`() {
        assertTrue(
            VoronoiGenerator.sites(12, 0.5f, palette, seed = 1L) !=
                VoronoiGenerator.sites(12, 0.5f, palette, seed = 2L),
        )
    }

    @Test
    fun `irregularity scatters the cells off their lattice`() {
        val even = VoronoiGenerator.sites(count = 16, irregularity = 0f, palette = palette, seed = 3L)
        val loose = VoronoiGenerator.sites(count = 16, irregularity = 1f, palette = palette, seed = 3L)

        // At irregularity 0 the seeds sit on a clean lattice; at 1 they scatter — so the two are not the same cells.
        assertTrue("irregularity did not move the cells", even.map { it.x to it.y } != loose.map { it.x to it.y })
    }

    @Test
    fun `a pixel takes the nearer of two seeds`() {
        val sites = listOf(
            VoronoiGenerator.Site(x = 0.2f, y = 0.2f, argb = 0),
            VoronoiGenerator.Site(x = 0.8f, y = 0.8f, argb = 0),
        )

        assertEquals(0, VoronoiGenerator.nearestSite(0.25f, 0.25f, sites))
        assertEquals(1, VoronoiGenerator.nearestSite(0.75f, 0.75f, sites))
    }

    @Test
    fun `a pixel equidistant from two seeds falls to the lower index, so a boundary does not flicker`() {
        val sites = listOf(
            VoronoiGenerator.Site(x = 0f, y = 0.5f, argb = 0),
            VoronoiGenerator.Site(x = 1f, y = 0.5f, argb = 0),
        )

        assertEquals(0, VoronoiGenerator.nearestSite(0.5f, 0.5f, sites))
    }

    @Test
    fun `a cell's color is the gradient near its seed's height`() {
        // With no color jitter a cell would be exactly the gradient at its height, read over the span the seam
        // leaves it; the jitter is bounded, so the cell's red stays within a stop's reach of that un-jittered ramp
        // rather than jumping the palette.
        val sites = VoronoiGenerator.sites(count = 20, irregularity = 0.5f, palette = palette, seed = 7L)
        val ceiling = VoronoiGenerator.fillCeiling(palette.size)

        for (site in sites) {
            val here = LinearGradientGenerator.colorAt(site.y * ceiling, palette) shr 16 and 0xFF
            val neighborhood = intArrayOf(
                LinearGradientGenerator.colorAt((site.y - 0.12f).coerceIn(0f, 1f) * ceiling, palette) shr 16 and 0xFF,
                LinearGradientGenerator.colorAt((site.y + 0.12f).coerceIn(0f, 1f) * ceiling, palette) shr 16 and 0xFF,
            )
            val red = site.argb shr 16 and 0xFF
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
                for (site in VoronoiGenerator.sites(24, 0.5f, reduced, seed)) {
                    assertTrue("a cell was painted the seam at $stops stops, seed $seed", site.argb != seam)
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
}
