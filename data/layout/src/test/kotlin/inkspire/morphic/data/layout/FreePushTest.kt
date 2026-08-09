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

    // ── relocate: the resize path (see FreePush.push) ─────────────────────────────────────────────────────────

    @Test
    fun `relocate rehomes an occupant no direction can clear`() {
        // The widget's right edge is dragged over the far column: the occupant there cannot go right (off the
        // grid), left or up (packed), or down (packed) — but the middle of the grid is empty. This is the case
        // that used to turn a resize red with half the screen free.
        val footprint = GridPlacement(page = 0, row = 0, col = 2, rowSpan = 4, colSpan = 2)
        val occupants = mapOf(
            "far" to GridPlacement(page = 0, row = 0, col = 3),
            "packed" to GridPlacement(page = 0, row = 0, col = 2),
        )
        val moves = FreePush.push(footprint, occupants, config, relocate = true).moves()
        assertEquals(setOf("far", "packed"), moves.keys)
        moves.values.forEach { assertTrue("$it still overlaps", !it.overlaps(footprint)) }
    }

    @Test
    fun `relocate picks the nearest free space, topmost on a tie`() {
        // Only column 0 is free. Rows 1 and 3 are equidistant from the occupant's row 2; the row-major scan
        // takes the upper one, so the item reads as having moved out of the way rather than jumped.
        val footprint = GridPlacement(page = 0, row = 0, col = 1, rowSpan = 4, colSpan = 3)
        val occupants = mapOf(
            "a" to GridPlacement(page = 0, row = 2, col = 1),
            "top" to GridPlacement(page = 0, row = 0, col = 0),
            "bottom" to GridPlacement(page = 0, row = 2, col = 0),
        )
        val moves = FreePush.push(footprint, occupants, config, relocate = true).moves()
        assertEquals(GridPlacement(page = 0, row = 1, col = 0), moves["a"])
    }

    @Test
    fun `relocate still blocks when the grid has nowhere left`() {
        val small = GridConfig(rows = 2, cols = 2)
        val footprint = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 2)
        val occupants = mapOf("a" to GridPlacement(page = 0, row = 0, col = 0))
        assertEquals(PushResult.Blocked, FreePush.push(footprint, occupants, small, relocate = true))
    }

    @Test
    fun `relocate is off for a drag, which keeps the cascade rule`() {
        val footprint = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 1, colSpan = 4)
        val occupants = mapOf(
            "a" to GridPlacement(page = 0, row = 0, col = 0),
            "b" to GridPlacement(page = 0, row = 1, col = 0),
            "c" to GridPlacement(page = 0, row = 2, col = 0),
            "d" to GridPlacement(page = 0, row = 3, col = 0),
        )
        // Columns 1..3 below the footprint are free, so `relocate` would find "a" a home — a drag must not.
        assertEquals(PushResult.Blocked, FreePush.push(footprint, occupants, config))
        assertTrue(FreePush.push(footprint, occupants, config, relocate = true) is PushResult.Moved)
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
