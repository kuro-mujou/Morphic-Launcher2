package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.DotGridGenerator.Look
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fit — where the block of tiles lands, how big each one is, and which colors its bands take.
 *
 * All of it is arithmetic that fails *silently*: a block that overflows its margin still renders, one that stops a tile
 * short of its own box just looks slightly off-center, and bands that land beside the palette's stops instead of on
 * them are colors nobody can point at as wrong. Invisible in a screenshot, obvious in a number.
 */
class DotGridGeneratorTest {

    @Test
    fun `the painted block fills the box its margin leaves, exactly`() {
        val grid = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 8, margin = 0.5f, look = Look.DOTS)
        // margin 0.5 insets a quarter of each side, so the box is the middle half: 500 x 1000.
        val painted = grid.cellWidth * (grid.columns - 1) + grid.tileWidth
        assertEquals("the painted width is the box, not the cell count", 500f, painted, 0.01f)
        assertEquals("the block is centred", (1000f - painted) / 2f, grid.left, 0.01f)
    }

    @Test
    fun `rows fill the box without overflowing it`() {
        val grid = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 8, margin = 0.5f, look = Look.DOTS)
        val painted = grid.cellHeight * (grid.rows - 1) + grid.tileHeight
        assertTrue("the block overflows its box: $painted", painted <= 1000f)
        // One more row would not have fit — that is what "however many reach the bottom" has to mean.
        assertTrue("a row was left on the table", painted + grid.cellHeight > 1000f)
        assertEquals("the block is centred", (2000f - painted) / 2f, grid.top, 0.01f)
    }

    @Test
    fun `zero margin reaches the frame's edges`() {
        val grid = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 6, margin = 0f, look = Look.DOTS)
        assertEquals(0f, grid.left, 0.01f)
        val painted = grid.cellWidth * (grid.columns - 1) + grid.tileWidth
        assertEquals(1000f, painted, 0.01f)
    }

    @Test
    fun `full margin still leaves a block to look at`() {
        val grid = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 6, margin = 1f, look = Look.DOTS)
        assertTrue("the block vanished", grid.tileWidth > 0f)
        assertTrue("the block was not shrunk", grid.left > 400f)
    }

    @Test
    fun `tiles fill their cell completely, and dots do not`() {
        val tiles = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 8, margin = 0.5f, look = Look.TILES)
        assertEquals("neighbouring tiles must touch", tiles.cellWidth, tiles.tileWidth, 0.01f)
        assertEquals(tiles.cellHeight, tiles.tileHeight, 0.01f)

        val dots = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 8, margin = 0.5f, look = Look.DOTS)
        assertTrue("dots must leave air between them", dots.tileWidth < dots.cellWidth)
    }

    @Test
    fun `bars keep their square sibling's row count, so the block comes out short`() {
        val bars = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 8, margin = 0.5f, look = Look.BARS)
        assertEquals("the cell squashes with the tile", bars.cellWidth / 3f, bars.cellHeight, 0.01f)
        assertEquals("the tile keeps the look's proportion", 3f, bars.tileWidth / bars.tileHeight, 0.01f)

        // Refilling the box would triple the rows and close the vertical gaps until only the columns read, so the row
        // count is the square cell's and the block is left short. The box here is 1000 tall.
        val dots = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 8, margin = 0.5f, look = Look.DOTS)
        assertEquals("rows are counted against the square cell", dots.rows, bars.rows)
        val painted = bars.cellHeight * (bars.rows - 1) + bars.tileHeight
        assertTrue("the block should be a short banner, not a full box: $painted", painted < 500f)
    }

    @Test
    fun `a single column still lays out`() {
        val grid = DotGridGenerator.gridOf(width = 1000, height = 2000, columns = 1, margin = 0.5f, look = Look.DOTS)
        assertEquals(1, grid.columns)
        assertEquals("one tile alone is the whole painted width", 500f, grid.tileWidth, 0.01f)
    }

    @Test
    fun `a palette long enough lands the bands on its own stops, unblended`() {
        val palette = Palette(
            listOf(
                0xFFF2E2C4.toInt(), 0xFFE6A15C.toInt(), 0xFFC9603E.toInt(),
                0xFF2C6E6B.toInt(), 0xFF1F3A4D.toInt(), 0xFF121E2B.toInt(),
            ),
        )
        val bands = RampTones.countFor(palette.size)
        assertEquals("one rung per stop above the ground", 5, bands)
        for (band in 0 until bands) {
            assertEquals(
                "band $band must be stop ${band + 1} itself, not a blend beside it",
                palette.colorAt(band + 1),
                LinearGradientGenerator.colorAt((band + 1f) / bands, palette),
            )
        }
    }

    @Test
    fun `a two-stop palette still gets a ramp rather than one flat band`() {
        assertEquals(3, RampTones.countFor(2))
        val duo = Palette(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        val tones = (0 until 3).map { LinearGradientGenerator.colorAt((it + 1f) / 3f, duo) }
        assertEquals("the rungs must be three different tones", 3, tones.toSet().size)
        assertEquals("the last rung is the ink itself", duo.colorAt(1), tones.last())
    }

    @Test
    fun `a one-stop palette has no ramp at all`() {
        assertEquals(0, RampTones.countFor(1))
    }

    /**
     * **A tile switches band at most once each way in a scrub, and never flickers** — at full dither on five bands, the
     * setting where the drift carries a tile furthest. A tile going back and forth between two bands would read as
     * noise in a picture whose whole point is its calm.
     */
    @Test
    fun `a tile's band turns back at most once in a scrub, so no tile flickers`() {
        val params = DesignParams(irregularity = 1f)
        for (seed in 1L..4L) {
            val morph = requireNotNull(
                DotGridGenerator.morph(
                    DotGridGenerator.plan(1080, 2400, params, seed),
                    DotGridGenerator.plan(1080, 2400, params, seed + 100),
                ),
            )
            val moments = (0..200).map { morph.at(it / 200f) }
            val grid = moments.first().grid
            for (tile in 0 until grid.rows * grid.columns) {
                val down = (tile / grid.columns).toFloat() / (grid.rows - 1)
                val bands = moments.map { DotGridGenerator.bandAt(down, it.drift[tile], it.dither, 5) }
                val steps = bands.zipWithNext { a, b -> b.compareTo(a) }.filter { it != 0 }
                val turns = steps.zipWithNext { a, b -> a != b }.count { it }
                assertTrue("tile $tile of seed $seed turned back $turns times: $bands", turns <= 1)
            }
        }
    }

    @Test
    fun `at no dither the seed decides nothing, and the scrub holds still`() {
        val params = DesignParams(irregularity = 0f)
        val from = DotGridGenerator.plan(1080, 2400, params, seed = 1L)
        val morph = requireNotNull(DotGridGenerator.morph(from, DotGridGenerator.plan(1080, 2400, params, seed = 2L)))
        val grid = from.grid
        for (t in listOf(0f, 0.25f, 0.5f, 1f)) {
            val moment = morph.at(t)
            for (tile in 0 until grid.rows * grid.columns) {
                val down = (tile / grid.columns).toFloat() / (grid.rows - 1)
                assertEquals(
                    DotGridGenerator.bandAt(down, from.drift[tile], from.dither, 5),
                    DotGridGenerator.bandAt(down, moment.drift[tile], moment.dither, 5),
                )
            }
        }
    }

    @Test
    fun `two blocks on different lattices are refused rather than paired`() {
        val coarse = DotGridGenerator.plan(1080, 2400, DesignParams(density = 0f), seed = 1L)
        val fine = DotGridGenerator.plan(1080, 2400, DesignParams(density = 1f), seed = 1L)
        assertNull(DotGridGenerator.morph(coarse, fine))
        assertNotNull(DotGridGenerator.morph(coarse, DotGridGenerator.plan(1080, 2400, DesignParams(density = 0f), 2L)))
    }
}
