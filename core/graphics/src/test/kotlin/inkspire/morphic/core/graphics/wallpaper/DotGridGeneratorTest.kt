package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.DotGridGenerator.Look
import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
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
        val bands = DotGridGenerator.bandsFor(palette.size)
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
        assertEquals(3, DotGridGenerator.bandsFor(2))
        val duo = Palette(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        val tones = (0 until 3).map { LinearGradientGenerator.colorAt((it + 1f) / 3f, duo) }
        assertEquals("the rungs must be three different tones", 3, tones.toSet().size)
        assertEquals("the last rung is the ink itself", duo.colorAt(1), tones.last())
    }

    @Test
    fun `a one-stop palette has no ramp at all`() {
        assertEquals(0, DotGridGenerator.bandsFor(1))
    }
}
