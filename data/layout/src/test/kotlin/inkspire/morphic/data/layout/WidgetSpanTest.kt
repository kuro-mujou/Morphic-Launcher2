package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec for [WidgetSpan] — the arithmetic that turns a provider's stated pixel size into a footprint on the lattice.
 *
 * The grid throughout is a home-shaped one: 8×10 logical cells at `cellMultiplier = 2`, i.e. 4×5 visual cells of
 * 200×200px, so one logical cell is 100×100px.
 */
class WidgetSpanTest {

    private val config = GridConfig(cols = 8, rows = 10, cellMultiplier = 2)
    private val cell = 100f

    private fun spanFor(minWidthPx: Int, minHeightPx: Int) =
        WidgetSpan.forMinSize(minWidthPx, minHeightPx, cell, cell, config)

    @Test
    fun `an exact fit takes exactly that many cells`() {
        assertEquals(WidgetSpan(4, 2), spanFor(400, 200))
    }

    @Test
    fun `a widget needing part of a further cell takes the whole cell`() {
        assertEquals(WidgetSpan(5, 3), spanFor(401, 201))
    }

    @Test
    fun `a widget smaller than one visual cell still occupies one`() {
        // 10px is a tenth of a logical cell, but the floor is the multiplier — a widget cannot be half an icon.
        assertEquals(WidgetSpan(2, 2), spanFor(10, 10))
    }

    @Test
    fun `a widget larger than the grid is offered at the largest span that fits`() {
        // Clamping down rather than returning null: the picker should show a size that could be placed, and the
        // provider's minimum is a request rather than a requirement the grid has to honor.
        assertEquals(WidgetSpan(8, 10), spanFor(5_000, 5_000))
    }

    private fun widgetSpan(targetCols: Int, targetRows: Int, minWidthPx: Int = 400, minHeightPx: Int = 200) =
        WidgetSpan.forWidget(targetCols, targetRows, minWidthPx, minHeightPx, cell, cell, config)

    @Test
    fun `a declared target size wins over the min-pixel derivation`() {
        // The widget says "2 x 2 cells", so it lands at 2 visual cells regardless of what its minimums would round
        // to. 2 visual cells is 4 logical, on a multiplier-2 grid.
        assertEquals(WidgetSpan(4, 4), widgetSpan(targetCols = 2, targetRows = 2, minWidthPx = 401, minHeightPx = 201))
    }

    @Test
    fun `no declared target falls back to the min-pixel span`() {
        assertEquals(spanFor(400, 200), widgetSpan(targetCols = 0, targetRows = 0))
    }

    @Test
    fun `a target larger than the grid is clamped to it`() {
        assertEquals(WidgetSpan(8, 10), widgetSpan(targetCols = 20, targetRows = 20))
    }

    @Test
    fun `a target survives an unmeasured grid, since cells do not enter into it`() {
        assertEquals(
            WidgetSpan(4, 4),
            WidgetSpan.forWidget(2, 2, 400, 200, cellWidthPx = 0f, cellHeightPx = 0f, config = config),
        )
    }

    @Test
    fun `an unmeasured grid answers nothing`() {
        assertNull(WidgetSpan.forMinSize(400, 200, cellWidthPx = 0f, cellHeightPx = cell, config = config))
        assertNull(WidgetSpan.forMinSize(400, 200, cellWidthPx = cell, cellHeightPx = 0f, config = config))
    }

    @Test
    fun `the label counts visual cells, not logical ones`() {
        // 4x2 logical on a multiplier-2 grid is the 2x1 a user sees.
        assertEquals("2 × 1", WidgetSpan(4, 2).visualLabel(config))
    }

    @Test
    fun `the label never claims a widget occupies no cells`() {
        assertEquals("1 × 1", WidgetSpan(1, 1).visualLabel(config))
    }
}
