package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared bilinear field. A transposed row and column turns a wash that runs down the frame into one that runs
 * across it, and both are pictures somebody might have asked for — so it is checked here rather than by eye.
 */
class ColorLatticeTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** Three across, two down — deliberately not square, since that is what would hide a transposition. */
    private val nodes = intArrayOf(black, black, white, white, white, black)

    private fun at(u: Float, v: Float) = ColorLattice.sample(nodes, cols = 3, rows = 2, u = u, v = v)

    @Test
    fun `a node's own color is read exactly at its own position`() {
        assertEquals(nodes[0], at(0f, 0f))
        assertEquals(nodes[2], at(1f, 0f))
        assertEquals(nodes[3], at(0f, 1f))
        assertEquals(nodes[5], at(1f, 1f))
    }

    @Test
    fun `the column and the row are not interchangeable`() {
        // Top-right is white and bottom-right black; reading (u, v) the other way round would swap them.
        assertEquals(white, at(1f, 0f))
        assertEquals(black, at(1f, 1f))
    }

    @Test
    fun `between two nodes it is the plain blend of them`() {
        // Halfway along the top row's second cell: black to white.
        assertEquals(0xFF808080.toInt(), at(0.75f, 0f))
    }

    @Test
    fun `a sample off the edge reads the edge rather than wrapping`() {
        assertEquals(at(0f, 0f), ColorLattice.sample(nodes, 3, 2, -2f, -0.5f))
        assertEquals(at(1f, 1f), ColorLattice.sample(nodes, 3, 2, 4f, 1.5f))
    }
}
