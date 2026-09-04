package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The subdivision — the blocks must *partition* the frame (cover it, no overlap), because the fill trusts that and a
 * gap or overlap is a silently-wrong tiling no green build would catch.
 */
class MondrianGeneratorTest {

    /**
     * The failure that killed this design at its own default: the color mode reduces every palette to **two** stops,
     * and the old rule handed a two-stop palette the ground for every block — 96% bare paper under a ruling.
     */
    @Test
    fun `a two-stop palette still has accents, and they are neither the ground nor the ink`() {
        val ground = 0xFFF2E2C4.toInt()
        val ink = 0xFF121E2B.toInt()
        val accents = MondrianGenerator.accents(Palette(listOf(ground, ink)))
        assertTrue("a two-stop palette must still accent", accents.isNotEmpty())
        accents.forEach {
            assertNotEquals("an accent must not be the ground", ground, it)
            assertNotEquals("an accent must not be the ink", ink, it)
        }
    }

    @Test
    fun `a full palette accents with exactly its middle stops`() {
        val stops = listOf(0xFFF2E2C4, 0xFFE6A15C, 0xFFC9603E, 0xFF2C6E6B, 0xFF1F3A4D, 0xFF121E2B).map { it.toInt() }
        assertEquals(stops.subList(1, stops.size - 1), MondrianGenerator.accents(Palette(stops)))
    }

    @Test
    fun `a single-stop palette has nothing to accent with, and every block is ground`() {
        val only = 0xFF808080.toInt()
        val accents = MondrianGenerator.accents(Palette(listOf(only)))
        assertTrue(accents.isEmpty())
        assertEquals(only, MondrianGenerator.blockColor(Random(1), Palette(listOf(only)), accents))
    }

    @Test
    fun `density maps to the pass count range`() {
        assertEquals(3, MondrianGenerator.passes(0f))
        assertEquals(7, MondrianGenerator.passes(1f))
        assertEquals(3, MondrianGenerator.passes(-1f)) // clamped
    }

    @Test
    fun `the blocks cover the whole frame — their areas sum to one`() {
        val blocks = MondrianGenerator.subdivide(passes = 6, random = Random(11L))
        val area = blocks.sumOf { (it.width * it.height).toDouble() }
        assertEquals(1.0, area, 1e-4)
    }

    @Test
    fun `no two blocks overlap`() {
        val blocks = MondrianGenerator.subdivide(passes = 5, random = Random(3L))
        for (i in blocks.indices) {
            for (j in i + 1 until blocks.size) {
                val a = blocks[i]
                val b = blocks[j]
                val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue("blocks $i and $j overlap", overlapX <= 1e-4f || overlapY <= 1e-4f)
            }
        }
    }

    @Test
    fun `the same seed yields the same blocks, so a recipe reproduces`() {
        assertEquals(
            MondrianGenerator.subdivide(5, Random(42L)),
            MondrianGenerator.subdivide(5, Random(42L)),
        )
    }
}
