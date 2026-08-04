package inkspire.morphic.core.designsystem.cell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cell's own icon geometry, checked without an emulator — which is why the label height is a parameter.
 *
 * These numbers are `IconLabelCell`'s, and the point of the tests is that they stay its: the settings preview draws
 * guides from them over a real cell, so a drift of one padding value is a visibly misaligned outline.
 */
class CellIconLayoutTest {

    private val metrics = IconMetrics(iconPercent = 1f, minIconDp = 24.dp, maxIconDp = 48.dp)
    private val labelHeight = 16.dp

    /** `CellPadV`, and `LabelGap` — the cell's own constants, restated here only because they are internal to it. */
    private val padV = 4.dp
    private val gap = 4.dp

    @Test
    fun `with a label the icon is centred above the cell's own centre`() {
        // A 90 × 140 cell: 132dp of content, of which the label block takes 20 (gap + row), leaving a 112dp icon area
        // that the 48dp guardrail caps. The group is 48 + 4 + 16 = 68 tall, centred in the 132 → top at 4 + 32 = 36.
        val layout = cellIconLayout(cellWidth = 90.dp, cellHeight = 140.dp, metrics = metrics, labelHeight = labelHeight)

        assertEquals(48.dp, layout.iconSize)
        assertEquals(labelHeight, layout.labelHeight)
        assertEquals(60.dp, layout.iconCenterY)
        assertTrue("a labelled cell's icon must sit above the cell centre", layout.iconCenterY < 140.dp / 2)
    }

    @Test
    fun `with no label the icon is centred in the cell and the label block disappears`() {
        val bare = metrics.copy(showLabel = false)

        val layout = cellIconLayout(cellWidth = 90.dp, cellHeight = 140.dp, metrics = bare, labelHeight = labelHeight)

        assertEquals(0.dp, layout.labelHeight)
        assertEquals(70.dp, layout.iconCenterY)
        // And the supplied label height is ignored rather than merely unused, as in the cell itself.
        assertEquals(layout, cellIconLayout(90.dp, 140.dp, bare, labelHeight = 999.dp))
    }

    @Test
    fun `the icon is the smaller bound times the fraction, inside the guardrails`() {
        // Width-bound: a 40dp-wide cell leaves 32dp of inner width, under the 48dp cap, so the fraction decides.
        assertEquals(32.dp, cellIconLayout(40.dp, 200.dp, metrics, labelHeight).iconSize)
        // Height-bound: a 60dp-tall cell leaves 32dp of icon area once the padding (8) and the label block (20) come
        // off, which is less than its 192dp of width — so the height decides.
        assertEquals(32.dp, cellIconLayout(200.dp, 60.dp, metrics, labelHeight).iconSize)
        // Fraction: half of the 32dp inner width, since that is still inside the guardrails.
        assertEquals(24.dp, cellIconLayout(56.dp, 200.dp, metrics.copy(iconPercent = 0.5f), labelHeight).iconSize)
    }

    @Test
    fun `a guardrail larger than the cell overflows a labelled cell's icon area but not its clamp`() {
        // The labelled branch clamps to the icon area, so a 48dp floor in a tiny cell reports the area, not the floor.
        val floor = metrics.copy(minIconDp = 48.dp, maxIconDp = 48.dp)
        val labelled = cellIconLayout(90.dp, 40.dp, floor, labelHeight)

        val iconArea = 40.dp - padV * 2 - gap - labelHeight
        assertEquals(iconArea, labelled.iconSize)
    }

    @Test
    fun `with no label a guardrail larger than the cell is reported as the overflow it is`() {
        // The no-label branch does *not* clamp — mirroring the cell — so the preview can show a guardrail that no
        // longer fits rather than hiding it. This is the asymmetry worth pinning, since it looks like a bug otherwise.
        val floor = metrics.copy(showLabel = false, minIconDp = 60.dp, maxIconDp = 60.dp)

        val layout = cellIconLayout(cellWidth = 90.dp, cellHeight = 40.dp, metrics = floor, labelHeight = labelHeight)

        assertEquals(60.dp, layout.iconSize)
        assertTrue("the icon is larger than the cell that holds it", layout.iconSize > 40.dp)
    }
}
