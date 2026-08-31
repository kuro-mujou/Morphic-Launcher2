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
            VoronoiGenerator.sites(count = 12, palette = palette, seed = 99L),
            VoronoiGenerator.sites(count = 12, palette = palette, seed = 99L),
        )
    }

    @Test
    fun `a different seed yields different sites`() {
        assertTrue(
            VoronoiGenerator.sites(12, palette, seed = 1L) !=
                VoronoiGenerator.sites(12, palette, seed = 2L),
        )
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
        // With no color jitter a cell would be exactly the gradient at its height; the jitter is bounded, so the
        // cell's red stays within a stop's reach of the un-jittered ramp rather than jumping the palette.
        val sites = VoronoiGenerator.sites(count = 20, palette = palette, seed = 7L)

        for (site in sites) {
            val here = LinearGradientGenerator.colorAt(site.y, palette) shr 16 and 0xFF
            val neighbourhood = intArrayOf(
                LinearGradientGenerator.colorAt((site.y - 0.12f).coerceIn(0f, 1f), palette) shr 16 and 0xFF,
                LinearGradientGenerator.colorAt((site.y + 0.12f).coerceIn(0f, 1f), palette) shr 16 and 0xFF,
            )
            val red = site.argb shr 16 and 0xFF
            val lo = minOf(here, neighbourhood[0], neighbourhood[1])
            val hi = maxOf(here, neighbourhood[0], neighbourhood[1])
            assertTrue("cell color left the gradient's neighbourhood", red in lo..hi)
        }
    }
}
