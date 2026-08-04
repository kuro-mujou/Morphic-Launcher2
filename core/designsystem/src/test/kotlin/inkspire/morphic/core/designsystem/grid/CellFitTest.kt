package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CellPadH
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.cellIconLayout
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

    private val metrics = IconMetrics(iconPercent = 0.5f, minIconDp = 24.dp, maxIconDp = 48.dp, showLabel = false)
    private val labelHeight = 16f

    /**
     * The same fixture with a guardrail big enough that a phone width genuinely runs out of columns — 96dp cells
     * against [metrics]' 32dp ones.
     *
     * The clamping cases need it because only the **guardrail** narrows a grid: raising `iconPercent` cannot, which is
     * the point of `the icon fraction does not change how many cells fit`.
     *
     * Both bounds are raised, not just the lower one: the floor is read `minOf(min, max)` to stay order-safe, so a
     * `minIconDp` pushed past the inherited `maxIconDp` would quietly be ignored — which is what the first draft of this
     * fixture did.
     */
    private val chunky = metrics.copy(minIconDp = 88.dp, maxIconDp = 120.dp)

    @Test
    fun `the smallest cell is the icon's floor plus the cell's own padding`() {
        // `resolveIconSize` clamps *up* to minIconDp, so a 24dp guardrail needs 24dp of inner width — plus CellPadH on
        // both sides (4+4). Nothing else can make the icon smaller, so nothing else belongs in the floor.
        assertEquals(32f, minCellWidthDp(metrics), 0.01f)
        // Which is to say: the smallest cell's inner width is exactly the guardrail, so an icon clamped to its floor
        // fits it exactly rather than overflowing.
        assertEquals(metrics.minIconDp.value, minCellWidthDp(metrics) - CellPadH.value * 2, 0.01f)
    }

    @Test
    fun `the icon fraction does not change how many cells fit`() {
        // **The property the whole file turns on.** `iconPercent` scales the icon *within* the guardrails — an icon
        // asked for 30% of a cell too small to honour that is drawn at minIconDp, which fits by construction. So the
        // percent cannot make a cell unusable, and it must not move a grid's bounds.
        //
        // Getting this wrong is not academic: the earlier `minIcon / percent` floor made a 28dp guardrail at 30% demand
        // a 101dp column, so *shrinking* the icons reported fewer columns and the icon controls appeared to resize the
        // grid. L1's own home formula (`gridMaxima`) left the percent out for this reason.
        val area = GridArea(360f, 800f)
        listOf(0.3f, 0.5f, 0.88f, 1f).forEach { percent ->
            val bounds = HomePagerGrid.boundsIn(area, metrics.copy(iconPercent = percent), labelHeight)
            assertEquals("$percent changed the column cap", 11, bounds.maxCols)
            assertEquals("$percent changed the row cap", 25, bounds.maxRows)
        }
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
        val crossed = metrics.copy(minIconDp = 48.dp, maxIconDp = 24.dp)

        assertEquals(minCellWidthDp(metrics), minCellWidthDp(crossed), 0.01f)
    }

    @Test
    fun `a cell's height is its width-driven icon plus the padding, and the label when shown`() {
        // 80dp wide → 72dp inner → 36dp icon at 50%, plus CellPadV on both sides (4+4). Deliberately a width whose
        // fraction lands *inside* the guardrails, since this test is about the width driving the height; the clamped
        // cases are the next test's.
        assertEquals(44f, cellHeightDp(cellWidthDp = 80f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
        // With labels on, the gap (4dp) and the 16dp label row.
        assertEquals(
            64f,
            cellHeightDp(cellWidthDp = 80f, metrics = metrics.copy(showLabel = true), labelHeightDp = labelHeight),
            0.01f,
        )
    }

    @Test
    fun `a cell's height follows the icon guardrails at both ends`() {
        // The point of deriving it at all: the height tracks what the icon settings resolve to. A very wide cell is
        // capped by maxIconDp (48 + 8), a very narrow one floored by minIconDp (24 + 8) rather than collapsing.
        assertEquals(56f, cellHeightDp(cellWidthDp = 400f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
        assertEquals(32f, cellHeightDp(cellWidthDp = 8f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
        assertEquals(32f, cellHeightDp(cellWidthDp = 0f, metrics = metrics, labelHeightDp = labelHeight), 0.01f)
    }

    @Test
    fun `a derived cell draws exactly the icon its height was derived for`() {
        // **The fixed point the derived height has to satisfy**, and the regression guard for a bug that shipped: the
        // height is `chrome + iconPercent × innerWidth`, so a cell handed the *original* metrics applies the fraction
        // again (`iconPercent × min(innerWidth, iconArea)`, and `iconArea` is already multiplied) and draws
        // `iconPercent²` of the width — 24dp inside a row built for 41dp at 50%. `derivedCell` spends the fraction once
        // and hands the cell `iconPercent = 1f`; this asserts the two then agree at every fraction.
        val cellWidth = 90f
        listOf(0.3f, 0.5f, 0.75f, 1f).forEach { percent ->
            val chosen = metrics.copy(iconPercent = percent, showLabel = true)
            val height = cellHeightDp(cellWidth, chosen, labelHeight)
            // What the height was derived for: the fraction of the inner width, inside the guardrails.
            val intended = (cellWidth - CellPadH.value * 2) * percent
            val expected = intended.coerceIn(chosen.minIconDp.value, chosen.maxIconDp.value)

            // What the cell actually draws, given the metrics `derivedCell` hands it.
            val drawn = cellIconLayout(
                cellWidth = cellWidth.dp,
                cellHeight = height.dp,
                metrics = chosen.copy(iconPercent = 1f),
                labelHeight = labelHeight.dp,
            ).iconSize

            assertEquals("at $percent the cell must draw the icon its height was sized for", expected, drawn.value, 0.01f)
        }
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

        assertEquals(11, bounds.maxCols)
        assertNull("rows scroll, so the area cannot bound them", bounds.maxRows)
    }

    @Test
    fun `a paged grid is bounded on both axes`() {
        val bounds = HomePagerGrid.boundsIn(GridArea(360f, 800f), metrics, labelHeight)

        // 32dp cells (a 24dp guardrail plus 4dp padding a side), and no label row to add on this fixture.
        assertEquals(11, bounds.maxCols)
        assertEquals(25, bounds.maxRows)
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
        assertEquals(11, range.cols.last)
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
        // A 32dp minimum cell (a 24dp guardrail plus 4dp padding a side): eleven columns fit 360dp and three rows fit
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
        // The dock's rule: a cell is `height ÷ rows`, so 40dp cannot carry two 32dp rows however many are stored.
        // The *write* that makes this permanent is the dock screen's on commit; this is the read that keeps the
        // drawn grid honest in the meantime.
        val short = DockGrid.fitGridConfig(GridArea(360f, 40f), cols = 4, rows = 2, metrics = metrics, labelHeightDp = labelHeight)
        val tall = DockGrid.fitGridConfig(GridArea(360f, 96f), cols = 4, rows = 2, metrics = metrics, labelHeightDp = labelHeight)

        assertEquals(1, short.visualRows)
        assertEquals(2, tall.visualRows)
    }

    @Test
    fun `a column count past what fits is clamped, and a wider area gives it back`() {
        // The property that lets the column clamp stay a *read*: nothing is written, so the count the user chose
        // returns the moment there is room for it. L1 wrote every clamp back, and the preference was gone for good.
        val narrow = DockGrid.fitGridConfig(GridArea(360f, 96f), cols = 9, rows = 1, metrics = chunky, labelHeightDp = labelHeight)
        val wide = DockGrid.fitGridConfig(GridArea(1080f, 96f), cols = 9, rows = 1, metrics = chunky, labelHeightDp = labelHeight)

        assertEquals("360dp fits three 96dp cells, not nine", 3, narrow.visualCols)
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

    @Test
    fun `a scrolling grid's columns are fitted on their own, with no rows to answer for`() {
        // The axis a SCROLL_GRID does have, clamped the same way `fitGridConfig` clamps its sibling's: 360dp carries
        // three 96dp cells, so a stored three passes through and a stored nine draws three.
        assertEquals(3, AppsScrollGrid.fitCols(areaWidthDp = 360f, cols = 3, metrics = chunky))
        assertEquals(3, AppsScrollGrid.fitCols(areaWidthDp = 360f, cols = 9, metrics = chunky))
        // And nothing is written, so the count returns the moment there is room — the same property that lets the
        // clamp stay a read.
        assertEquals(9, AppsScrollGrid.fitCols(areaWidthDp = 1080f, cols = 9, metrics = chunky))
    }

    @Test
    fun `a scrolling grid's column fit respects its floor and never reports zero`() {
        // Its blueprint's minimum wins on an area too narrow to honour it, exactly as the editable range's floor does —
        // and a grid with no editor at all still gets one column rather than none.
        assertEquals(AppsScrollGrid.editRange!!.minCols, AppsScrollGrid.fitCols(30f, cols = 4, metrics = metrics))
        assertEquals(AppsScrollGrid.editRange!!.minCols, AppsScrollGrid.fitCols(360f, cols = 0, metrics = metrics))
        assertEquals(1, FolderGrid.fitCols(0f, cols = 0, metrics = metrics))
    }

    @Test
    fun `the column fit and the editable range agree, because they are one formula`() {
        // The invariant worth a test rather than a comment: an editor must never offer a column the grid would then
        // clamp away, and a grid must never draw one the editor would refuse.
        listOf(120f, 360f, 480f, 1024f).forEach { width ->
            val range = AppsScrollGrid.editableRangeIn(GridArea(width, 800f), metrics, labelHeight)!!.cols
            assertEquals(range.last, AppsScrollGrid.fitCols(width, cols = 99, metrics = metrics))
            assertEquals(range.first, AppsScrollGrid.fitCols(width, cols = 0, metrics = metrics))
        }
    }

    @Test
    fun `bigger icons mean fewer columns drawn, not just fewer offered`() {
        // The user-visible half of "bigger icons mean fewer columns": raising the **guardrail** has to move what a
        // stored count resolves to, or a settings screen showing the stored number would contradict the surface. It is
        // the guardrail rather than the fraction, which is the distinction the fixture above exists for.
        val small = AppsScrollGrid.fitCols(360f, cols = 9, metrics = metrics.copy(minIconDp = 24.dp))
        val large = AppsScrollGrid.fitCols(360f, cols = 9, metrics = metrics.copy(minIconDp = 48.dp))

        assertEquals(9, small)
        assertTrue("48dp icons should draw fewer than the nine stored columns (got $large)", large < small)
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
