package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour spec for [FreePush], ported from L1's `SpreadPushTest` and re-expressed against [GridPlacement] /
 * [PushResult] / [PushDirection]. Each case documents one rule of the free-grid push, so this doubles as the
 * readable reference for how home/dock make room during a drag.
 */
class FreePushTest {

    private val config = GridConfig(rows = 4, cols = 4)

    /** Unwraps a successful push to its move map, failing loudly if the push was blocked. */
    private fun PushResult<String>.moves(): Map<String, GridPlacement> {
        assertTrue("expected Moved but was $this", this is PushResult.Moved)
        return (this as PushResult.Moved).moves
    }

    @Test
    fun `no occupants is a clear placement with no moves`() {
        val footprint = GridPlacement(page = 0, row = 1, col = 1, rowSpan = 2, colSpan = 2)
        assertEquals(
            emptyMap<String, GridPlacement>(),
            FreePush.push(footprint, emptyMap<String, GridPlacement>(), config).moves(),
        )
    }

    @Test
    fun `a non-overlapping occupant is left untouched`() {
        val footprint = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 2)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 3, col = 3))
        assertEquals(emptyMap<String, GridPlacement>(), FreePush.push(footprint, occupants, config).moves())
    }

    @Test
    fun `occupant is pushed up when its top edge is the nearest exit`() {
        val footprint = GridPlacement(page = 0, row = 1, col = 0, rowSpan = 2, colSpan = 4)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 1, col = 1))
        assertEquals(GridPlacement(page = 0, row = 0, col = 1), FreePush.push(footprint, occupants, config).moves()["a"])
    }

    @Test
    fun `occupant is pushed down when its bottom edge is the nearest exit`() {
        val footprint = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 4)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 1, col = 1))
        assertEquals(GridPlacement(page = 0, row = 2, col = 1), FreePush.push(footprint, occupants, config).moves()["a"])
    }

    @Test
    fun `occupant is pushed left when its left edge is the nearest exit`() {
        val footprint = GridPlacement(page = 0, row = 0, col = 1, rowSpan = 4, colSpan = 2)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 1, col = 1))
        assertEquals(GridPlacement(page = 0, row = 1, col = 0), FreePush.push(footprint, occupants, config).moves()["a"])
    }

    @Test
    fun `occupant is pushed right when its right edge is the nearest exit`() {
        val footprint = GridPlacement(page = 0, row = 0, col = 1, rowSpan = 4, colSpan = 2)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 1, col = 2))
        assertEquals(GridPlacement(page = 0, row = 1, col = 3), FreePush.push(footprint, occupants, config).moves()["a"])
    }

    @Test
    fun `an eviction cascades into the next occupant in the same direction`() {
        val footprint = GridPlacement(page = 0, row = 2, col = 0, rowSpan = 2, colSpan = 4)
        val occupants = mapOf(
            "a" to GridPlacement(page = 0, row = 2, col = 1),
            "b" to GridPlacement(page = 0, row = 1, col = 1),
        )
        val moves = FreePush.push(footprint, occupants, config).moves()
        assertEquals(GridPlacement(page = 0, row = 1, col = 1), moves["a"])
        assertEquals(GridPlacement(page = 0, row = 0, col = 1), moves["b"])
    }

    @Test
    fun `preferred direction wins over the nearest edge`() {
        // Left is the nearest exit, but the finger entered from the left sub-zone, so we push right instead.
        val footprint = GridPlacement(page = 0, row = 0, col = 1, rowSpan = 4, colSpan = 2)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 1, col = 1))
        val moves = FreePush.push(footprint, occupants, config, preferred = PushDirection.RIGHT).moves()
        assertEquals(GridPlacement(page = 0, row = 1, col = 3), moves["a"])
    }

    @Test
    fun `push is blocked when an occupant has no way off the footprint`() {
        val small = GridConfig(rows = 2, cols = 2)
        val footprint = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 2)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 0, col = 0))
        assertEquals(PushResult.Blocked, FreePush.push(footprint, occupants, small))
    }

    @Test
    fun `push is blocked when the footprint does not fit the grid`() {
        val footprint = GridPlacement(page = 0, row = 3, col = 3, rowSpan = 2, colSpan = 2)
        assertEquals(PushResult.Blocked, FreePush.push(footprint, emptyMap<String, GridPlacement>(), config))
    }

    @Test
    fun `push is blocked when a cascade would run off the grid edge`() {
        // Footprint fills the top row; the only escape is down, but the column below is packed to the bottom
        // edge, so the chain has nowhere to land and the drop is rejected rather than spilling to a new page.
        val footprint = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 1, colSpan = 4)
        val occupants = mapOf(
            "a" to GridPlacement(page = 0, row = 0, col = 0),
            "b" to GridPlacement(page = 0, row = 1, col = 0),
            "c" to GridPlacement(page = 0, row = 2, col = 0),
            "d" to GridPlacement(page = 0, row = 3, col = 0),
        )
        assertEquals(PushResult.Blocked, FreePush.push(footprint, occupants, config))
    }
}
