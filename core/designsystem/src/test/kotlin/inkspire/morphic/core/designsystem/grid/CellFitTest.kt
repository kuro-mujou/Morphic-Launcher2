package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.model.AppsScrollGrid
import inkspire.morphic.core.model.DockGrid
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.blueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fitting arithmetic, checked without an emulator — which is the whole reason the label height is a parameter
 * rather than a `MaterialTheme` read inside the maths.
 */
class CellFitTest {

    private val metrics = IconMetrics(iconPercent = 0.5f, minIconDp = 24.dp, maxIconDp = 72.dp, showLabel = false)
    private val labelHeight = 16f

    @Test
    fun `a cell is the icon divided by its fraction, plus the cell's own padding`() {
        // The inverse of `IconLabelCell`: 24dp icon at 50% needs 48dp of inner width, plus CellPadH on both sides (4+4).
        assertEquals(56f, minCellWidthDp(metrics), 0.01f)
    }

    @Test
    fun `showing a label makes the cell taller by the label row and its gap`() {
        val withLabel = metrics.copy(showLabel = true)

        val bare = minCellHeightDp(metrics, labelHeight)
        val labelled = minCellHeightDp(withLabel, labelHeight)

        // LabelGap is 4dp, so a 16dp label row adds 20dp — and nothing when labels are off, even though the height is
        // still passed in.
        assertEquals(20f, labelled - bare, 0.01f)
        assertEquals(bare, minCellHeightDp(metrics, labelHeightDp = 999f), 0.01f)
    }

    @Test
    fun `the guardrails are read order-safe, matching resolveIconSize`() {
        // `resolveIconSize` coerces with minOf/maxOf, so a crossed pair must not make the cell requirement explode.
        val crossed = metrics.copy(minIconDp = 72.dp, maxIconDp = 24.dp)

        assertEquals(minCellWidthDp(metrics), minCellWidthDp(crossed), 0.01f)
    }

    @Test
    fun `a cell's height is its width-driven icon plus the padding, and the label when shown`() {
        // 120dp wide → 112dp inner → 56dp icon at 50%, plus CellPadV on both sides (4+4).
        assertEquals(64f, cellHeightDp(cellWidthDp = 120f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
        // With labels on, the gap (4dp) and the 16dp label row.
        assertEquals(
            84f,
            cellHeightDp(cellWidthDp = 120f, metrics = metrics.copy(showLabel = true), labelHeightDp = labelHeight),
            0.01f,
        )
    }

    @Test
    fun `a cell's height follows the icon guardrails at both ends`() {
        // The point of deriving it at all: the height tracks what the icon settings resolve to. A very wide cell is
        // capped by maxIconDp (72 + 8), a very narrow one floored by minIconDp (24 + 8) rather than collapsing.
        assertEquals(80f, cellHeightDp(cellWidthDp = 400f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
        assertEquals(32f, cellHeightDp(cellWidthDp = 8f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
        assertEquals(32f, cellHeightDp(cellWidthDp = 0f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
    }

    @Test
    fun `bigger icons mean taller cells`() {
        val small = cellHeightDp(100f, metrics.copy(iconPercent = 0.4f), labelHeight)
        val large = cellHeightDp(100f, metrics.copy(iconPercent = 0.8f), labelHeight)

        assertTrue("a larger icon fraction must not give a shorter cell ($large vs $small)", large > small)
    }

    @Test
    fun `cells divide the area and never report zero`() {
        assertEquals(4, maxCells(availableDp = 240f, minCellDp = 56f))
        assertEquals(1, maxCells(availableDp = 10f, minCellDp = 56f))
        assertEquals(1, maxCells(availableDp = 0f, minCellDp = 56f))
        assertEquals(1, maxCells(availableDp = 240f, minCellDp = 0f))
    }

    @Test
    fun `a scrolling grid is bounded across but not down`() {
        val bounds = AppsScrollGrid.boundsIn(GridArea(360f, 800f), metrics, labelHeight)

        assertEquals(6, bounds.maxCols)
        assertNull("rows scroll, so the area cannot bound them", bounds.maxRows)
    }

    @Test
    fun `a paged grid is bounded on both axes`() {
        val bounds = HomePagerGrid.boundsIn(GridArea(360f, 800f), metrics, labelHeight)

        assertEquals(6, bounds.maxCols)
        assertEquals(14, bounds.maxRows)
    }

    @Test
    fun `a wider area never reports fewer columns`() {
        // The property L1's "fixed cell size, not one derived from the current grid" note protects: measuring against
        // the current cell would let a wider area report no more room.
        var previous = 0
        listOf(120f, 240f, 360f, 480f, 720f, 1024f).forEach { width ->
            val cols = HomePagerGrid.boundsIn(GridArea(width, 800f), metrics, labelHeight).maxCols
            assertTrue("$width dp reported $cols after $previous", cols >= previous)
            previous = cols
        }
    }

    @Test
    fun `bigger icons mean fewer columns`() {
        val area = GridArea(360f, 800f)

        val small = HomePagerGrid.boundsIn(area, metrics.copy(minIconDp = 24.dp), labelHeight).maxCols
        val large = HomePagerGrid.boundsIn(area, metrics.copy(minIconDp = 48.dp), labelHeight).maxCols

        assertTrue("48dp icons should fit fewer columns than 24dp ($large vs $small)", large < small)
    }

    @Test
    fun `a grid with no editor has no editable range`() {
        assertNull(FolderGrid.editableRangeIn(GridArea(360f, 800f), metrics, labelHeight))
    }

    @Test
    fun `an editable range runs from the blueprint's floor to what fits`() {
        val range = HomePagerGrid.editableRangeIn(GridArea(360f, 800f), metrics, labelHeight)

        assertNotNull(range)
        assertEquals(HomePagerGrid.editRange!!.minCols, range!!.cols.first)
        assertEquals(6, range.cols.last)
        assertEquals(HomePagerGrid.editRange!!.minRows, range.rows!!.first)
    }

    @Test
    fun `on an area too small the floor wins rather than the range going empty`() {
        // A blueprint's minimum is its own promise the grid is usable at that size; an empty range would leave an
        // editor with nothing to offer.
        val range = HomePagerGrid.editableRangeIn(GridArea(20f, 20f), metrics, labelHeight)

        assertNotNull(range)
        assertEquals(HomePagerGrid.editRange!!.minCols, range!!.cols.first)
        assertEquals(HomePagerGrid.editRange!!.minCols, range.cols.last)
        assertTrue("an editable range must never be empty", !range.cols.isEmpty())
    }

    @Test
    fun `a column-only blueprint offers no row range`() {
        val range = AppsScrollGrid.editableRangeIn(GridArea(360f, 800f), metrics, labelHeight)

        assertNotNull(range)
        assertNull("a scrolling grid's rows are not the user's to set", range!!.rows)
    }

    @Test
    fun `a stored size that fits passes through, in logical units`() {
        // A 56dp minimum cell (24dp icon at 50%, plus 4dp padding a side): six columns fit 360dp and one row fits
        // 96dp, so a stored 4 × 1 is untouched.
        val config = DockGrid.fitGridConfig(GridArea(360f, 96f), cols = 4, rows = 1, metrics = metrics, labelHeightDp = labelHeight)

        assertEquals(1, config.visualRows)
        assertEquals(4, config.visualCols)
        // Scaled by the dock's multiplier of 2, exactly as `toGridConfig` scales a chosen size — so an app is one
        // visual cell here just as it is on the pager.
        assertEquals(2, config.rows)
        assertEquals(8, config.cols)
    }

    @Test
    fun `a height too short for the stored rows shows fewer of them`() {
        // The dock's rule: a cell is `height ÷ rows`, so 96dp cannot carry two 56dp rows however many are stored.
        // The *write* that makes this permanent is the dock screen's on commit; this is the read that keeps the
        // drawn grid honest in the meantime.
        val short = DockGrid.fitGridConfig(GridArea(360f, 96f), cols = 4, rows = 2, metrics = metrics, labelHeightDp = labelHeight)
        val tall = DockGrid.fitGridConfig(GridArea(360f, 140f), cols = 4, rows = 2, metrics = metrics, labelHeightDp = labelHeight)

        assertEquals(1, short.visualRows)
        assertEquals(2, tall.visualRows)
    }

    @Test
    fun `a column count past what fits is clamped, and a wider area gives it back`() {
        // The property that lets the column clamp stay a *read*: nothing is written, so the count the user chose
        // returns the moment there is room for it. L1 wrote every clamp back, and the preference was gone for good.
        val narrow = DockGrid.fitGridConfig(GridArea(360f, 96f), cols = 9, rows = 1, metrics = metrics, labelHeightDp = labelHeight)
        val wide = DockGrid.fitGridConfig(GridArea(720f, 96f), cols = 9, rows = 1, metrics = metrics, labelHeightDp = labelHeight)

        assertEquals("360dp fits six 56dp cells, not nine", 6, narrow.visualCols)
        assertEquals(9, wide.visualCols)
    }

    @Test
    fun `a dock too short for a full cell still has one row`() {
        // `maxCells` floors at one: a strip the user has shrunk past a usable cell is still a dock, and reporting zero
        // rows would fail `GridConfig`'s own invariant rather than degrade.
        val config = DockGrid.fitGridConfig(GridArea(360f, 8f), 4, 1, metrics, labelHeight)

        assertEquals(1, config.visualRows)
    }

    @Test
    fun `a count below the blueprint's floor is raised to it`() {
        val config = DockGrid.fitGridConfig(GridArea(360f, 96f), cols = 0, rows = 0, metrics = metrics, labelHeightDp = labelHeight)

        assertEquals(DockGrid.editRange!!.minCols, config.visualCols)
        assertEquals(DockGrid.editRange!!.minRows, config.visualRows)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a scrolling grid has no fixed size to resolve`() {
        // Its rows are however many its content reaches, so there is no config to fit — the same precondition
        // `toGridConfig` states, raised here rather than silently inventing a row count.
        AppsScrollGrid.fitGridConfig(GridArea(360f, 800f), 4, 4, metrics, labelHeight)
    }

    @Test
    fun `every editable grid yields a non-empty range on a realistic phone`() {
        // The bound the settings editor relies on: whatever slot it shows, there is something to offer.
        GridSlot.entries.forEach { slot ->
            val range = slot.blueprint.editableRangeIn(GridArea(360f, 780f), metrics, labelHeight) ?: return@forEach

            assertTrue("$slot offered an empty column range", !range.cols.isEmpty())
            range.rows?.let { assertTrue("$slot offered an empty row range", !it.isEmpty()) }
        }
    }
}
