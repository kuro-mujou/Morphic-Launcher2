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

    // ── Rotate in place ──

    /**
     * **The property the whole mode exists for.** Turning a board out and back must give the arrangement the user
     * made, coordinates and gaps and spans included — which is what a reflow cannot do and why there are two modes.
     */
    @Test
    fun `a board turned out and back is the arrangement it started as`() {
        val portrait = GridConfig(rows = 12, cols = 8, cellMultiplier = 2)
        val landscape = portrait.swap()
        val source = mapOf(
            "a" to GridPlacement(0, 0, 0, 2, 2),
            "gap-after" to GridPlacement(0, 0, 4, 2, 2),
            "wide" to GridPlacement(0, 4, 2, 2, 4),
            "tall" to GridPlacement(0, 8, 6, 4, 2),
            "page two" to GridPlacement(1, 2, 2, 2, 2),
        )

        val turned = ArrangementRotation.rotate(source, landscape, toLandscape = true)
        val back = ArrangementRotation.rotate(turned, portrait, toLandscape = false)

        assertEquals(source, back)
    }

    /** Every item must land inside the target, or the round-trip above would be passing on items nothing draws. */
    @Test
    fun `a turned board fits the transposed grid, item for item`() {
        val portrait = GridConfig(rows = 12, cols = 8, cellMultiplier = 2)
        val source = mapOf(
            "a" to GridPlacement(0, 0, 0, 2, 2),
            "wide" to GridPlacement(0, 4, 2, 2, 4),
            "corner" to GridPlacement(0, 10, 6, 2, 2),
        )

        val turned = ArrangementRotation.rotate(source, portrait.swap(), toLandscape = true)

        assertEquals(source.size, turned.size)
        turned.forEach { (key, at) -> assertTrue("$key fell outside the grid", at.fitsIn(portrait.swap())) }
    }

    /**
     * The direction, pinned as a coordinate rather than described: counter-clockwise carries the **bottom** edge to
     * the **trailing** edge, which is where the rail is. Turning the other way would look equally plausible in a
     * screenshot and would put the dock on the wrong side.
     */
    @Test
    fun `turning is counter-clockwise, so the bottom row becomes the trailing column`() {
        val portrait = GridConfig(rows = 8, cols = 4, cellMultiplier = 2)
        val bottomLeft = mapOf("x" to GridPlacement(0, 6, 0, 2, 2))

        val turned = ArrangementRotation.rotate(bottomLeft, portrait.swap(), toLandscape = true)

        // Bottom-left in portrait becomes top-left in landscape under CCW: the left column becomes the top row.
        assertEquals(GridPlacement(0, 2, 6, 2, 2), turned.getValue("x"))
    }

    /** Gaps are the difference from a reflow, so they are asserted rather than assumed. */
    @Test
    fun `a turned board keeps the gaps a reflow would close`() {
        val portrait = GridConfig(rows = 8, cols = 4, cellMultiplier = 2)
        val gapped = mapOf("far" to GridPlacement(0, 6, 2, 2, 2))

        val turned = ArrangementRotation.rotate(gapped, portrait.swap(), toLandscape = true)
        val reflowed = ArrangementProjection.project(gapped, portrait.swap())

        assertEquals(GridPlacement(0, 0, 0, 2, 2), reflowed.getValue("far"))
        assertTrue("a rotation that packs to the origin is a reflow", turned.getValue("far") != reflowed.getValue("far"))
    }
}
