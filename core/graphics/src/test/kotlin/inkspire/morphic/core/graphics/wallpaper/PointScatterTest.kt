package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared grid-jitter placement behind Voronoi's cells and Mesh's points — the cell arithmetic is silently wrong
 * (points bunched in a corner, a column short, a nudge that crosses into the next cell) long before a bitmap could
 * show it.
 */
class PointScatterTest {

    @Test
    fun `it returns exactly count points, all inside the unit square`() {
        val points = PointScatter.gridJitter(count = 17, irregularity = 1f, seed = 4L)
        assertEquals(17 * 2, points.size)
        var i = 0
        while (i < points.size) {
            assertTrue("point left the unit square", points[i] in 0f..1f && points[i + 1] in 0f..1f)
            i += 2
        }
    }

    @Test
    fun `at zero irregularity the lattice is fixed, so the seed does not move it`() {
        // With no jitter the random draws are multiplied by zero, so any two seeds produce the identical lattice.
        assertTrue(
            PointScatter.gridJitter(12, 0f, seed = 1L).contentEquals(PointScatter.gridJitter(12, 0f, seed = 2L)),
        )
    }

    @Test
    fun `at zero irregularity the points are distinct grid cells, not a heap`() {
        // A clean lattice of 9 points is 9 distinct positions; a broken cell calc would collapse them.
        val points = PointScatter.gridJitter(9, 0f, seed = 0L)
        val cells = (0 until 9).map { points[it * 2] to points[it * 2 + 1] }.toSet()
        assertEquals(9, cells.size)
    }

    @Test
    fun `irregularity moves the points off the lattice`() {
        val rigid = PointScatter.gridJitter(12, 0f, seed = 5L)
        val loose = PointScatter.gridJitter(12, 1f, seed = 5L)
        assertTrue("jitter did not disturb the lattice", !rigid.contentEquals(loose))
    }

    @Test
    fun `the same seed reproduces, so a recipe is stable`() {
        assertTrue(
            PointScatter.gridJitter(20, 0.5f, seed = 8L).contentEquals(PointScatter.gridJitter(20, 0.5f, seed = 8L)),
        )
    }
}
