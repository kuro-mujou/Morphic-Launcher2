package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior spec for [ArrangementProjection]. String keys stand in for whatever identity the caller keys by
 * (`GridItem` in production), exactly as `GridReflowTest` does.
 */
class ArrangementProjectionTest {

    @Test
    fun `an empty arrangement projects to an empty one`() {
        assertEquals(emptyMap<String, GridPlacement>(), ArrangementProjection.project<String>(emptyMap(), grid(4, 4)))
    }

    @Test
    fun `items land in the source's reading order`() {
        // Deliberately inserted out of reading order, so a result matching it cannot have come from map order.
        val source = mapOf(
            "third" to GridPlacement(page = 0, row = 1, col = 0),
            "first" to GridPlacement(page = 0, row = 0, col = 0),
            "fourth" to GridPlacement(page = 1, row = 0, col = 0),
            "second" to GridPlacement(page = 0, row = 0, col = 1),
        )
        val projected = ArrangementProjection.project(source, grid(rows = 2, cols = 2))

        assertEquals(GridPlacement(0, 0, 0), projected["first"])
        assertEquals(GridPlacement(0, 0, 1), projected["second"])
        assertEquals(GridPlacement(0, 1, 0), projected["third"])
        assertEquals(GridPlacement(0, 1, 1), projected["fourth"])
    }

    @Test
    fun `gaps in the source do not survive`() {
        val source = mapOf(
            "a" to GridPlacement(page = 0, row = 0, col = 0),
            // Three empty cells, then the second item.
            "b" to GridPlacement(page = 0, row = 1, col = 1),
        )
        val projected = ArrangementProjection.project(source, grid(rows = 4, cols = 4))

        assertEquals(GridPlacement(0, 0, 0), projected["a"])
        assertEquals(GridPlacement(0, 0, 1), projected["b"])
    }

    @Test
    fun `a wider target uses the columns it gained`() {
        // The case that separates this from `GridReflow`: settling would leave every item in columns 0-1, because
        // each one still fits where it was. Four items across a 4-column grid must fill its first row.
        val source = (0..3).associate { i -> "app$i" to GridPlacement(page = 0, row = i / 2, col = i % 2) }
        val projected = ArrangementProjection.project(source, grid(rows = 2, cols = 4))

        assertEquals(GridPlacement(0, 0, 0), projected["app0"])
        assertEquals(GridPlacement(0, 0, 1), projected["app1"])
        assertEquals(GridPlacement(0, 0, 2), projected["app2"])
        assertEquals(GridPlacement(0, 0, 3), projected["app3"])
    }

    @Test
    fun `spans are carried across`() {
        val source = mapOf(
            "widget" to GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 2),
            "app" to GridPlacement(page = 0, row = 2, col = 0),
        )
        val projected = ArrangementProjection.project(source, grid(rows = 4, cols = 4))

        assertEquals(GridPlacement(0, 0, 0, rowSpan = 2, colSpan = 2), projected["widget"])
        // Beside the widget rather than under it: the scan is row-major and row 0 still has room at column 2.
        assertEquals(GridPlacement(0, 0, 2), projected["app"])
    }

    @Test
    fun `an item too large for the target is clamped rather than dropped`() {
        val source = mapOf("wide" to GridPlacement(page = 0, row = 0, col = 0, rowSpan = 1, colSpan = 4))
        val projected = ArrangementProjection.project(source, grid(rows = 3, cols = 2))

        assertEquals(GridPlacement(0, 0, 0, rowSpan = 1, colSpan = 2), projected["wide"])
    }

    @Test
    fun `nothing is ever omitted`() {
        val source = (0..19).associate { i -> "app$i" to GridPlacement(page = i / 4, row = (i % 4) / 2, col = i % 2) }
        val projected = ArrangementProjection.project(source, grid(rows = 2, cols = 2))

        assertEquals(source.keys, projected.keys)
        assertEquals(20, projected.values.distinct().size)
    }

    @Test
    fun `overflow cascades onto later pages`() {
        val source = (0..5).associate { i -> "app$i" to GridPlacement(page = 0, row = i / 2, col = i % 2) }
        val projected = ArrangementProjection.project(source, grid(rows = 2, cols = 2))

        assertEquals(GridPlacement(0, 0, 0), projected["app0"])
        assertEquals(GridPlacement(0, 1, 1), projected["app3"])
        assertEquals(GridPlacement(1, 0, 0), projected["app4"])
        assertEquals(GridPlacement(1, 0, 1), projected["app5"])
    }

    @Test
    fun `a small item reuses space a larger one left on an earlier page`() {
        // The widget fills page 0's top half and cannot share a row with anything 2 tall; the apps behind it must
        // still find page 0's bottom half rather than being pushed forward with it.
        val source = mapOf(
            "widget" to GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 2),
            "a" to GridPlacement(page = 0, row = 2, col = 0),
            "b" to GridPlacement(page = 0, row = 2, col = 1),
        )
        val projected = ArrangementProjection.project(source, grid(rows = 3, cols = 2))

        assertEquals(GridPlacement(0, 0, 0, rowSpan = 2, colSpan = 2), projected["widget"])
        assertEquals(GridPlacement(0, 2, 0), projected["a"])
        assertEquals(GridPlacement(0, 2, 1), projected["b"])
    }

    @Test
    fun `home's visual cells are respected, so nothing lands half a cell out`() {
        // HOME's grids are `cellMultiplier = 2`: an app is a 2x2 logical footprint and every coordinate it may
        // occupy is even. A scan stepping one logical cell would seat the second app at (0, 2) — correct — but the
        // third at (0, 4) only by luck, and anything after a 3-wide widget at an odd column for certain.
        val source = mapOf(
            "wide" to GridPlacement(page = 0, row = 0, col = 0, rowSpan = 2, colSpan = 4),
            "a" to GridPlacement(page = 0, row = 0, col = 4),
            "b" to GridPlacement(page = 0, row = 2, col = 0),
        )
        val projected = ArrangementProjection.project(source, GridConfig(rows = 4, cols = 6, cellMultiplier = 2))

        assertEquals(GridPlacement(0, 0, 0, rowSpan = 2, colSpan = 4), projected["wide"])
        assertEquals(GridPlacement(0, 0, 4, rowSpan = 1, colSpan = 1), projected["a"])
        assertTrue(
            "every coordinate must sit on a visual-cell boundary",
            projected.values.all { it.row % 2 == 0 && it.col % 2 == 0 },
        )
        assertEquals(GridPlacement(0, 2, 0), projected["b"])
    }

    @Test
    fun `projecting an arrangement into its own grid is not required to be identity`() {
        // Stated as a test because it is the surprising half of the contract: this is a re-lay, not a settle, so
        // even the source's own grid closes its gaps. `GridReflow.reflow` is the operation that leaves them alone.
        val source = mapOf("a" to GridPlacement(page = 0, row = 2, col = 2))
        val projected = ArrangementProjection.project(source, grid(rows = 4, cols = 4))

        assertEquals(GridPlacement(0, 0, 0), projected["a"])
    }

    private fun grid(rows: Int, cols: Int) = GridConfig(rows = rows, cols = cols)
}
