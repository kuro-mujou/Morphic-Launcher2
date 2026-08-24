package inkspire.morphic.feature.home

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.data.widgets.WidgetResizeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HomeResize.resolving] — the rule that a resize frame only ever shows something the grid would accept.
 *
 * Worth pinning because the failure it guards against is silent rather than crashing: a frame that moves to a
 * rectangle the planner refused looks like a successful resize, and the *next* drag measures its edges from that
 * rectangle, so one refused frame quietly becomes the base of everything after it.
 */
class HomeResizeTest {

    private val widget = GridItem.Widget(appWidgetId = 7)
    private val neighbor: GridItem = GridItem.App(ComponentKey(packageName = "com.example", className = "Main"))
    private val grid = GridConfig(rows = 4, cols = 4)
    private val at = GridPlacement(page = 0, row = 0, col = 0, rowSpan = 1, colSpan = 1)

    private fun session(
        placement: GridPlacement = at,
        moves: Map<GridItem, GridPlacement> = emptyMap(),
        refused: Boolean = false,
    ) = HomeResize(
        item = widget,
        zone = HomeZone.MAIN,
        rules = HomeResizeRules.Widget(WidgetResizeRules(minWidthPx = 0, minHeightPx = 0)),
        placement = placement,
        moves = moves,
        refused = refused,
    )

    @Test
    fun `an accepted candidate becomes the frame, with the push it costs`() {
        val pushed = mapOf(neighbor to GridPlacement(page = 0, row = 2, col = 0))
        val candidate = at.copy(colSpan = 2)

        val resolved = session().resolving(candidate, grid) { pushed }

        assertEquals(candidate, resolved.placement)
        assertEquals(pushed, resolved.moves)
        assertFalse(resolved.refused)
    }

    @Test
    fun `a candidate outside the grid is taken clamped, and reported as refused`() {
        val resolved = session().resolving(at.copy(colSpan = 6), grid) { emptyMap() }

        assertEquals(at.copy(colSpan = 4), resolved.placement)
        assertTrue(resolved.refused)
    }

    @Test
    fun `a candidate the planner blocks leaves the frame exactly where it was`() {
        val held = at.copy(colSpan = 2)
        val pushed = mapOf(neighbor to GridPlacement(page = 0, row = 2, col = 0))

        val resolved = session(placement = held, moves = pushed).resolving(at.copy(colSpan = 3), grid) { null }

        assertEquals(held, resolved.placement)
        // The preview survives too: it is the push that goes with the rectangle still on screen.
        assertEquals(pushed, resolved.moves)
        assertTrue(resolved.refused)
    }

    @Test
    fun `dragging back into legal cells clears the refusal`() {
        val resolved = session(refused = true).resolving(at.copy(colSpan = 2), grid) { emptyMap() }

        assertFalse(resolved.refused)
    }

    @Test
    fun `the previewed cells are the frame's for the resized item and the plan's for everyone else`() {
        val pushed = GridPlacement(page = 0, row = 3, col = 0)
        val resize = session(placement = at.copy(colSpan = 2), moves = mapOf(neighbor to pushed))

        assertEquals(at.copy(colSpan = 2), resize.previewOf(widget))
        assertEquals(pushed, resize.previewOf(neighbor))
        assertEquals(null, resize.previewOf(GridItem.Folder(folderId = 1)))
    }
}
