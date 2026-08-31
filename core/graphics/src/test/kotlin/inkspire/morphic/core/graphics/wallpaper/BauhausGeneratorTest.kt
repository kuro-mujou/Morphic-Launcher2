package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The subdivision — the blocks must *partition* the frame (cover it, no overlap), because the fill trusts that and a
 * gap or overlap is a silently-wrong tiling no green build would catch.
 */
class BauhausGeneratorTest {

    @Test
    fun `density maps to the pass count range`() {
        assertEquals(3, BauhausGenerator.passes(0f))
        assertEquals(7, BauhausGenerator.passes(1f))
        assertEquals(3, BauhausGenerator.passes(-1f)) // clamped
    }

    @Test
    fun `the blocks cover the whole frame — their areas sum to one`() {
        val blocks = BauhausGenerator.subdivide(passes = 6, random = Random(11L))
        val area = blocks.sumOf { (it.width * it.height).toDouble() }
        assertEquals(1.0, area, 1e-4)
    }

    @Test
    fun `no two blocks overlap`() {
        val blocks = BauhausGenerator.subdivide(passes = 5, random = Random(3L))
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
            BauhausGenerator.subdivide(5, Random(42L)),
            BauhausGenerator.subdivide(5, Random(42L)),
        )
    }
}
