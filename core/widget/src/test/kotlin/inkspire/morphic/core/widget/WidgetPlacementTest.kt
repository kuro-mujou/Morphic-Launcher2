package inkspire.morphic.core.widget

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPlacementTest {

    @Test
    fun `a dp extent scales with density and ignores the parent`() {
        assertEquals(120, WidgetPlacement.extentPx(WidgetExtent.Dp(40f), available = 1000, density = 3f))
        assertEquals(120, WidgetPlacement.extentPx(WidgetExtent.Dp(40f), available = 10, density = 3f))
    }

    @Test
    fun `a fraction extent follows the parent`() {
        assertEquals(500, WidgetPlacement.extentPx(WidgetExtent.Fraction(0.5f), available = 1000, density = 3f))
        assertEquals(1000, WidgetPlacement.extentPx(WidgetExtent.Fill, available = 1000, density = 3f))
    }

    @Test
    fun `a fraction of an unbounded parent falls back to content`() {
        assertNull(WidgetPlacement.extentPx(WidgetExtent.Fraction(0.5f), available = Int.MAX_VALUE, density = 3f))
    }

    @Test
    fun `a negative extent is no size rather than a crash`() {
        assertEquals(0, WidgetPlacement.extentPx(WidgetExtent.Dp(-5f), available = 100, density = 2f))
    }

    @Test
    fun `content asks for nothing`() {
        assertNull(WidgetPlacement.extentPx(WidgetExtent.Content, available = 1000, density = 3f))
    }

    @Test
    fun `every anchor lands its own point on the parent's`() {
        val parent = 300
        val child = 100
        val expected = mapOf(
            WidgetAnchor.TOP_LEFT to (0 to 0), WidgetAnchor.TOP to (100 to 0), WidgetAnchor.TOP_RIGHT to (200 to 0),
            WidgetAnchor.LEFT to (0 to 100), WidgetAnchor.CENTER to (100 to 100), WidgetAnchor.RIGHT to (200 to 100),
            WidgetAnchor.BOTTOM_LEFT to (0 to 200), WidgetAnchor.BOTTOM to (100 to 200),
            WidgetAnchor.BOTTOM_RIGHT to (200 to 200),
        )
        WidgetAnchor.entries.forEach { anchor ->
            val x = WidgetPlacement.position(parent, child, WidgetPlacement.horizontalBias(anchor), 0f)
            val y = WidgetPlacement.position(parent, child, WidgetPlacement.verticalBias(anchor), 0f)
            assertEquals(anchor.name, expected.getValue(anchor), x to y)
        }
    }

    @Test
    fun `the offset moves from the anchor, and the same offset keeps a corner layer in its corner at any size`() {
        // Re-lay, not scale: a layer 12px in from the right edge stays 12px in when the parent grows.
        val right = WidgetPlacement.horizontalBias(WidgetAnchor.RIGHT)
        assertEquals(188, WidgetPlacement.position(parent = 300, child = 100, bias = right, offsetPx = -12f))
        assertEquals(488, WidgetPlacement.position(parent = 600, child = 100, bias = right, offsetPx = -12f))
    }

    @Test
    fun `a child larger than its centered parent overhangs both sides equally`() {
        assertEquals(-50, WidgetPlacement.position(parent = 100, child = 200, bias = 0.5f, offsetPx = 0f))
    }
}
