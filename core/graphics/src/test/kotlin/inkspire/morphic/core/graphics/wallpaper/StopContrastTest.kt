package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The stop-legibility rule both the tile grid and the line bundle run on. Every case here is one that draws something
 * nobody can see rather than throwing — a shape in its own ground's tone, a line that fades into the frame.
 */
class StopContrastTest {

    @Test
    fun `a long palette offers only stops at least the gap away`() {
        assertArrayEquals(intArrayOf(2, 3, 4, 5), StopContrast.readableAgainst(ground = 0, stops = 6))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), StopContrast.readableAgainst(ground = 5, stops = 6))
        assertArrayEquals(intArrayOf(0, 4, 5), StopContrast.readableAgainst(ground = 2, stops = 6))
    }

    @Test
    fun `the answer never includes the ground itself where there is any alternative`() {
        for (stops in 2..8) {
            for (ground in 0 until stops) {
                val readable = StopContrast.readableAgainst(ground, stops)
                assertTrue("ground $ground of $stops", readable.none { it == ground })
            }
        }
    }

    @Test
    fun `a two-stop palette falls back to the other one, which is as far as it goes`() {
        assertArrayEquals(intArrayOf(1), StopContrast.readableAgainst(ground = 0, stops = 2))
        assertArrayEquals(intArrayOf(0), StopContrast.readableAgainst(ground = 1, stops = 2))
    }

    @Test
    fun `a three-stop palette's middle ground falls back, since nothing is far enough from it`() {
        assertArrayEquals(intArrayOf(0, 2), StopContrast.readableAgainst(ground = 1, stops = 3))
    }

    @Test
    fun `a single-color palette answers with itself rather than nothing`() {
        assertArrayEquals(intArrayOf(0), StopContrast.readableAgainst(ground = 0, stops = 1))
    }

    @Test
    fun `every answer is within range and ascending`() {
        for (stops in 1..8) {
            for (ground in 0 until stops) {
                val readable = StopContrast.readableAgainst(ground, stops)
                assertTrue("empty for ground $ground of $stops", readable.isNotEmpty())
                assertTrue("out of range", readable.all { it in 0 until stops })
                assertTrue("not ascending", readable.toList().zipWithNext().all { (a, b) -> a < b })
            }
        }
    }

    @Test
    fun `where the palette allows it, the gap is honored`() {
        for (stops in 4..8) {
            for (ground in 0 until stops) {
                val readable = StopContrast.readableAgainst(ground, stops)
                assertTrue(
                    "ground $ground of $stops returned a neighbour",
                    readable.all { abs(it - ground) >= StopContrast.MinGap },
                )
            }
        }
    }
}
