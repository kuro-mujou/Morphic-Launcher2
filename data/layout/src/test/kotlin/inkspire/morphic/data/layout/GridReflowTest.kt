package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour spec for [GridReflow], ported from L1's `GridReflowTest` onto the single-map API. String keys
 * stand in for whatever identity the caller keys by (`GridItem` in production).
 */
class GridReflowTest {

    @Test
    fun `everything in bounds is left unchanged`() {
        val placements = mapOf("a" to GridPlacement(0, 0, 0), "b" to GridPlacement(0, 1, 1))
        val result = GridReflow.reflow(placements, GridConfig(rows = 4, cols = 4))
        assertFalse(result.changed)
        assertEquals(placements, result.placements)
    }

    @Test
    fun `an out-of-bounds item reflows into a free cell while in-bounds items stay put`() {
        val fixed = GridPlacement(0, 0, 0)
        val placements = mapOf("fixed" to fixed, "oob" to GridPlacement(0, 0, 9))
        val config = GridConfig(rows = 4, cols = 4)

        val result = GridReflow.reflow(placements, config)

        assertTrue(result.changed)
        assertEquals(fixed, result.placements.getValue("fixed"))
        val moved = result.placements.getValue("oob")
        assertTrue("reflowed item must fit the grid", moved.fitsIn(config))
        assertNotEquals(fixed, moved)
    }

    @Test
    fun `overflow past the last page appends a new page`() {
        // A full 2x2 page plus one out-of-bounds item: the stray must spill onto page 1.
        val placements = mapOf(
            "a" to GridPlacement(0, 0, 0),
            "b" to GridPlacement(0, 0, 1),
            "c" to GridPlacement(0, 1, 0),
            "d" to GridPlacement(0, 1, 1),
            "oob" to GridPlacement(0, 0, 5),
        )
        val result = GridReflow.reflow(placements, GridConfig(rows = 2, cols = 2))
        assertTrue(result.changed)
        assertEquals(1, result.placements.getValue("oob").page)
    }
}
