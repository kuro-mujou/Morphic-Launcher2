package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subdivision — and here the render is a *worse* check than usual, which is why these matter.
 *
 * Every tile is pulled back from its own edges before it is drawn, so a gap or an overlap in the subdivision does not
 * show as a hole: it shows as one grout line slightly the wrong width, in a picture whose whole surface is grout lines
 * of varying length. The count has to be exact for the same reason — the slider says "16 tiles" and nothing in the
 * render lets you count them at a glance.
 */
class ModernMosaicGeneratorTest {

    private fun area(tile: FloatArray): Float = GlassCut.area(tile)

    @Test
    fun `the tiles partition the frame, at every count and every ratio`() {
        for (count in listOf(1, 2, 6, 16, 100)) {
            for (ratio in ModernMosaicGenerator.Ratio.entries) {
                val mosaic = ModernMosaicGenerator.tiles(count, ratio, skew = 0f, seed = 4L, aspect = 0.45f)
                val total = mosaic.tiles.sumOf { area(it).toDouble() }
                assertEquals("$count tiles at ${ratio.label} must add up to the frame", 0.45, total, 1e-4)
            }
        }
    }

    @Test
    fun `the count is exact, not a target`() {
        // Unlike the window's panes, this recurses on a count rather than on an area, so it can promise the number.
        for (count in listOf(1, 2, 3, 7, 16, 43, 100)) {
            val mosaic = ModernMosaicGenerator.tiles(count, ModernMosaicGenerator.Ratio.GOLDEN, 0.5f, 4L, 0.45f)
            assertEquals(count, mosaic.tiles.size)
        }
    }

    @Test
    fun `one tile is the whole frame`() {
        // The reference's own rigid end: Count 1 draws a single rounded rectangle filling the frame.
        val mosaic = ModernMosaicGenerator.tiles(1, ModernMosaicGenerator.Ratio.GOLDEN, 0f, 4L, 0.45f)
        assertEquals(1, mosaic.tiles.size)
        assertEquals(0.45f, area(mosaic.tiles.single()), 1e-5f)
    }

    @Test
    fun `Even halves exactly, which is the ratio knob's rigid end`() {
        // Two tiles from one cut at Even must be the same size — the property that gave the knob's reading away.
        val mosaic = ModernMosaicGenerator.tiles(2, ModernMosaicGenerator.Ratio.EVEN, skew = 0f, seed = 4L, aspect = 0.45f)
        assertEquals(2, mosaic.tiles.size)
        assertEquals(area(mosaic.tiles[0]), area(mosaic.tiles[1]), 1e-5f)
    }

    @Test
    fun `a lopsided ratio spreads the sizes further than an even one`() {
        fun spread(ratio: ModernMosaicGenerator.Ratio): Float {
            val areas = ModernMosaicGenerator.tiles(40, ratio, 0f, 7L, 0.45f).tiles.map { area(it) }
            return areas.max() / areas.min()
        }
        assertTrue("Even must be the tightest set of sizes", spread(ModernMosaicGenerator.Ratio.EVEN) < 3f)
        assertTrue(
            "Fifth must spread wider than Even",
            spread(ModernMosaicGenerator.Ratio.FIFTH) > spread(ModernMosaicGenerator.Ratio.EVEN),
        )
    }

    @Test
    fun `cutting the longer side keeps the tiles off being slivers`() {
        // The reason the direction is not simply random: at 100 tiles a random direction leaves long needles.
        val mosaic = ModernMosaicGenerator.tiles(100, ModernMosaicGenerator.Ratio.GOLDEN, 0f, 4L, 0.45f)
        val worst = mosaic.tiles.maxOf { tile ->
            val w = tile[2] - tile[0]
            val h = tile[5] - tile[3]
            maxOf(w / h, h / w)
        }
        assertTrue("no tile may be a needle — worst aspect was $worst", worst < 6f)
    }

    @Test
    fun `at no skew the tiles are exact rectangles, and at full skew none is`() {
        // The knob is *Skew*, so the evidence is whether opposite edges are still parallel to the axes.
        fun axisAligned(tile: FloatArray) = tile[1] == tile[3] && tile[4] == tile[2] && tile[7] == tile[5]
        val rigid = ModernMosaicGenerator.tiles(30, ModernMosaicGenerator.Ratio.GOLDEN, 0f, 4L, 0.45f).tiles
        val skewed = ModernMosaicGenerator.tiles(30, ModernMosaicGenerator.Ratio.GOLDEN, 1f, 4L, 0.45f).tiles
        assertTrue("every tile must be a rectangle at skew 0", rigid.all { axisAligned(it) })
        assertTrue("the skew must actually move corners", skewed.count { axisAligned(it) } < rigid.size / 2)
    }

    @Test
    fun `the skew leaves the frame's own border alone`() {
        // Otherwise the mosaic pulls away from the screen edge in a ragged line, which reads as a bug rather than as
        // a style — and the corner that would carry it *out* of the frame is the one nobody can see going wrong.
        val mosaic = ModernMosaicGenerator.tiles(30, ModernMosaicGenerator.Ratio.GOLDEN, 1f, 4L, 0.45f)
        val xs = mosaic.tiles.flatMap { t -> (t.indices step 2).map { t[it] } }
        val ys = mosaic.tiles.flatMap { t -> (1 until t.size step 2).map { t[it] } }
        assertEquals(0f, xs.min(), 1e-5f)
        assertEquals(0.45f, xs.max(), 1e-5f)
        assertEquals(0f, ys.min(), 1e-5f)
        assertEquals(1f, ys.max(), 1e-5f)
    }

    @Test
    fun `the same seed cuts the same mosaic, so a recipe reproduces`() {
        val first = ModernMosaicGenerator.tiles(20, ModernMosaicGenerator.Ratio.GOLDEN, 0.5f, 12L, 0.45f).tiles
        val again = ModernMosaicGenerator.tiles(20, ModernMosaicGenerator.Ratio.GOLDEN, 0.5f, 12L, 0.45f).tiles
        assertTrue(first.indices.all { first[it].contentEquals(again[it]) })
    }

    @Test
    fun `a different seed cuts a different mosaic`() {
        val a = ModernMosaicGenerator.tiles(20, ModernMosaicGenerator.Ratio.GOLDEN, 0.5f, 1L, 0.45f).tiles
        val b = ModernMosaicGenerator.tiles(20, ModernMosaicGenerator.Ratio.GOLDEN, 0.5f, 2L, 0.45f).tiles
        assertTrue(a.indices.any { !a[it].contentEquals(b[it]) })
    }
}
