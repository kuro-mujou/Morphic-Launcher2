package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A turning tile's corner. The failure is one no picture shows until the end of a scrub: a corner turned the wrong way
 * still sweeps smoothly, and only jumps when the table takes over.
 */
class TileTurnsTest {

    /**
     * **A turning corner arrives at the corner the table names**, from either side — so the corner worked out between
     * turns and the one read off the table at them are one path, not two.
     */
    @Test
    fun `a turning corner arrives at each corner the table names`() {
        for (turn in 0..3) {
            val corner = tileCornerAt(turn.toFloat())
            for (near in listOf(turn - 0.001f, turn + 0.001f)) {
                val anchor = tileCornerAt(near)
                assertEquals("x near turn $turn", corner[0], anchor[0], 0.01f)
                assertEquals("y near turn $turn", corner[1], anchor[1], 0.01f)
            }
        }
    }

    @Test
    fun `whole turns go clockwise from the top-left, and wrap`() {
        assertArrayEquals(floatArrayOf(0f, 0f), tileCornerAt(0f), 0f)
        assertArrayEquals(floatArrayOf(1f, 0f), tileCornerAt(1f), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f), tileCornerAt(2f), 0f)
        assertArrayEquals(floatArrayOf(0f, 1f), tileCornerAt(3f), 0f)
        assertArrayEquals(tileCornerAt(0f), tileCornerAt(4f), 0f)
        assertArrayEquals(tileCornerAt(3f), tileCornerAt(-1f), 0f)
    }

    @Test
    fun `a corner halfway round stands off the tile, as far from its center as a corner is`() {
        val halfway = tileCornerAt(0.5f)
        assertEquals(0.5f, halfway[0], 1e-6f)
        assertEquals(0.5f - 0.70710677f, halfway[1], 1e-6f)
    }
}
