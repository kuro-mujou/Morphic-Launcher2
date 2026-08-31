package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.BauhausGenerator.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The tile plan. The one that fails *silently* is the color pair: a tile whose arc is drawn in its own ground color is
 * a shape nobody can see, and on a full frame of them the design just looks like flat squares — no crash, no test
 * failure, nothing to notice but a duller wallpaper.
 */
class BauhausGeneratorTest {

    @Test
    fun `density maps to the column count range`() {
        assertEquals(2, BauhausGenerator.columnCount(0f))
        assertEquals(8, BauhausGenerator.columnCount(1f))
        assertEquals(2, BauhausGenerator.columnCount(-1f)) // clamped
        assertEquals(8, BauhausGenerator.columnCount(2f))
    }

    @Test
    fun `a tile never draws its arc in its own ground color`() {
        for (paletteSize in 2..6) {
            val cells = BauhausGenerator.plan(4, 6, variety = 1f, paletteSize, floating = false, seed = 7L)
            for (cell in cells) assertNotEquals("palette of $paletteSize", cell.ground, cell.shape)
        }
    }

    @Test
    fun `a shape sits at least two stops from its ground where the palette is long enough`() {
        // Merely different is not enough: a palette runs light to dark, so one stop apart is one tone apart and the
        // arc is a shape you have to hunt for. Nothing about that fails loudly.
        // From four stops up, every ground has one at least two away; a three-stop palette's middle one does not, and
        // falls back to merely different — which the test above covers.
        for (paletteSize in 4..6) {
            val cells = BauhausGenerator.plan(4, 8, variety = 1f, paletteSize, floating = false, seed = 13L)
            for (cell in cells) {
                assertTrue(
                    "palette of $paletteSize: ${cell.shape} on ${cell.ground}",
                    abs(cell.ground - cell.shape) >= 2,
                )
            }
        }
    }

    @Test
    fun `the floating variant never draws a shape in the frame's own ground stop`() {
        val paletteSize = 6
        val cells = BauhausGenerator.plan(4, 6, variety = 1f, paletteSize, floating = true, seed = 7L)
        // The ground is the last stop, so every shape must come from the ones before it.
        for (cell in cells) assertTrue("stop ${cell.shape}", cell.shape < paletteSize - 1)
    }

    @Test
    fun `a one-color palette degrades to a flat field rather than throwing`() {
        val cells = BauhausGenerator.plan(3, 3, variety = 1f, paletteSize = 1, floating = false, seed = 1L)
        for (cell in cells) assertEquals(0, cell.shape)
    }

    @Test
    fun `no variety is a strict repeat — one shape, one turn`() {
        val cells = BauhausGenerator.plan(5, 8, variety = 0f, paletteSize = 4, floating = false, seed = 3L)
        for (cell in cells) {
            assertEquals(Tile.QUARTER, cell.tile)
            assertEquals(0, cell.turn)
        }
    }

    @Test
    fun `full variety reaches every shape and every turn`() {
        val cells = BauhausGenerator.plan(8, 20, variety = 1f, paletteSize = 4, floating = false, seed = 5L)
        assertEquals(Tile.entries.toSet(), cells.map { it.tile }.toSet())
        assertEquals(setOf(0, 1, 2, 3), cells.map { it.turn }.toSet())
    }

    @Test
    fun `the same seed yields the same plan, so a recipe reproduces`() {
        assertEquals(
            BauhausGenerator.plan(4, 6, 0.5f, 5, floating = false, seed = 42L),
            BauhausGenerator.plan(4, 6, 0.5f, 5, floating = false, seed = 42L),
        )
    }

    @Test
    fun `moving variety re-dresses the same lattice rather than re-rolling it`() {
        // Every roll is drawn at every setting, so the colors a tile lands on do not shift as the knob moves — the
        // knob's own effect is then the only thing that changes on screen.
        val calm = BauhausGenerator.plan(4, 6, 0f, 5, floating = false, seed = 9L)
        val wild = BauhausGenerator.plan(4, 6, 1f, 5, floating = false, seed = 9L)
        assertEquals(calm.map { it.ground }, wild.map { it.ground })
        assertEquals(calm.map { it.shape }, wild.map { it.shape })
    }
}
