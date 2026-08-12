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
