package inkspire.morphic.core.widget

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetManipulationTest {

    private val parent = IntSize(900, 300)
    private val density = 3f
    private val layer = WidgetLayerSpec(WidgetSource.Text("x"))

    private fun box(left: Int, top: Int, width: Int = 120, height: Int = 60) =
        IntRect(IntOffset(left, top), IntSize(width, height))

    /** Where the moved layer is drawn, through the renderer's own placement. */
    private fun drawnAt(moved: WidgetLayerSpec, width: Int, height: Int) = IntOffset(
        WidgetPlacement.position(parent.width, width, WidgetPlacement.horizontalBias(moved.anchor), moved.offsetX * density),
        WidgetPlacement.position(parent.height, height, WidgetPlacement.verticalBias(moved.anchor), moved.offsetY * density),
    )

    @Test
    fun `a block lands exactly where it was dropped`() {
        // The one property that matters: the renderer draws the re-pinned layer at the box the drag ended on.
        listOf(box(10, 10), box(400, 120), box(760, 230), box(-40, 250), box(700, 5, width = 300)).forEach { box ->
            val moved = layer.movedTo(box, parent, density)
            assertEquals("$box", box.topLeft, drawnAt(moved, box.width, box.height))
        }
    }

    @Test
    fun `it is pinned to the anchor its center is nearest`() {
        assertEquals(WidgetAnchor.TOP_LEFT, layer.movedTo(box(10, 10), parent, density).anchor)
        assertEquals(WidgetAnchor.CENTER, layer.movedTo(box(390, 120), parent, density).anchor)
        assertEquals(WidgetAnchor.BOTTOM_RIGHT, layer.movedTo(box(770, 230), parent, density).anchor)
        assertEquals(WidgetAnchor.RIGHT, layer.movedTo(box(770, 120), parent, density).anchor)
    }

    @Test
    fun `the offset is in dp`() {
        val moved = layer.movedTo(box(30, 12), parent, density)
        assertEquals(10f, moved.offsetX, 1e-4f)
        assertEquals(4f, moved.offsetY, 1e-4f)
    }

    @Test
    fun `everything but the placement is kept`() {
        val named = layer.copy(name = "Time", scale = 1.5f, opacity = 0.5f)
        val moved = named.movedTo(box(10, 10), parent, density)
        assertEquals(named.copy(anchor = moved.anchor, offsetX = moved.offsetX, offsetY = moved.offsetY), moved)
    }

    @Test
    fun `a pinch multiplies the scale, within the renderer's limits`() {
        assertEquals(1.5f, layer.scaledBy(1.5f).scale, 1e-6f)
        assertEquals(3f, layer.copy(scale = 2f).scaledBy(1.5f).scale, 1e-6f)
        assertEquals(WidgetPlacement.MaxScale, layer.copy(scale = 3f).scaledBy(10f).scale)
        assertEquals(WidgetPlacement.MinScale, layer.scaledBy(0.01f).scale)
    }
}
