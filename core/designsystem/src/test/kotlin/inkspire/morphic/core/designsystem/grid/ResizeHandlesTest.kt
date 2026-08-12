package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec for the resize geometry — which handles a provider gets, and what dragging one does to a placement.
 *
 * The grid throughout is 8×10 logical cells of 100px, and the item under test occupies cells (2,2) to (4,4)
 * exclusive — a 2×2 footprint whose left edge is at x = 200 and whose right edge is at x = 400.
 */
class ResizeHandlesTest {

    private val config = GridConfig(cols = 8, rows = 10, cellMultiplier = 2)
    private val cell = 100f
    private val base = GridPlacement(page = 0, row = 2, col = 2, rowSpan = 2, colSpan = 2)
    private val bothAxes = ResizeBounds(horizontal = true, vertical = true, minColSpan = 1, minRowSpan = 1)

    private fun resize(handle: ResizeHandle, x: Float, y: Float, bounds: ResizeBounds = bothAxes) =
        resizedPlacement(base, handle, Offset(x, y), cell, cell, bounds)

    @Test
    fun `a provider that resizes both ways gets every handle`() {
        assertEquals(ResizeHandle.entries.toSet(), handlesFor(bothAxes).toSet())
    }

    @Test
    fun `a height-only provider gets the two vertical handles and no corners`() {
        val bounds = ResizeBounds(horizontal = false, vertical = true, minColSpan = 1, minRowSpan = 1)
        // A corner moves an edge on *each* axis, so it is offered only when both are permitted.
        assertEquals(listOf(ResizeHandle.TOP, ResizeHandle.BOTTOM), handlesFor(bounds))
    }

    @Test
    fun `bounds that permit neither axis offer no handles at all`() {
        // Nothing in the launcher passes these today — every widget is offered both axes, whatever its
        // `resizeMode` says — but the geometry stays able to express a frame with nothing to grab, so that a
        // future caller with a genuinely fixed item gets an empty frame rather than a misleading one.
        val bounds = ResizeBounds(horizontal = false, vertical = false, minColSpan = 1, minRowSpan = 1)
        assertEquals(emptyList<ResizeHandle>(), handlesFor(bounds))
    }

    @Test
    fun `dragging the right edge moves only that edge`() {
        // Finger at x = 620 rounds to column 6; the left edge stays at 2.
        assertEquals(
            GridPlacement(0, row = 2, col = 2, rowSpan = 2, colSpan = 4),
            resize(ResizeHandle.RIGHT, x = 620f, y = 300f),
        )
    }

    @Test
    fun `dragging the left edge keeps the right one fixed`() {
        assertEquals(
            GridPlacement(0, row = 2, col = 0, rowSpan = 2, colSpan = 4),
            resize(ResizeHandle.LEFT, x = 10f, y = 300f),
        )
    }

    @Test
    fun `a corner moves one edge on each axis`() {
        assertEquals(
            GridPlacement(0, row = 0, col = 0, rowSpan = 4, colSpan = 4),
            resize(ResizeHandle.TOP_LEFT, x = 20f, y = 20f),
        )
    }

    @Test
    fun `an edge cannot be dragged through its opposite`() {
        // Pulling the right edge far to the left still leaves the minimum width, measured from the *left* edge.
        val bounds = bothAxes.copy(minColSpan = 2)
        assertEquals(
            GridPlacement(0, row = 2, col = 2, rowSpan = 2, colSpan = 2),
            resize(ResizeHandle.RIGHT, x = 0f, y = 300f, bounds = bounds),
        )
    }

    @Test
    fun `an axis the provider refuses does not move`() {
        val bounds = ResizeBounds(horizontal = false, vertical = true, minColSpan = 1, minRowSpan = 1)
        // A corner drag on a height-only widget changes the height and leaves the width alone. (The overlay
        // would not offer this handle; the maths refuses it independently, which is the belt to that braces.)
        assertEquals(
            GridPlacement(0, row = 0, col = 2, rowSpan = 4, colSpan = 2),
            resize(ResizeHandle.TOP_LEFT, x = 20f, y = 20f, bounds = bounds),
        )
    }

    @Test
    fun `the growing edge is allowed to overshoot the grid`() {
        // Deliberately unclamped: the caller compares this with `clampToGrid` to tell an over-drag from a legal
        // resize that happens to end at the edge.
        val over = resize(ResizeHandle.RIGHT, x = 1_200f, y = 300f)
        assertEquals(12, over.colEndExclusive)
        assertEquals(GridPlacement(0, row = 2, col = 2, rowSpan = 2, colSpan = 6), clampToGrid(over, config))
    }

    @Test
    fun `clamping keeps at least one cell on each axis`() {
        val outside = GridPlacement(page = 0, row = 9, col = 7, rowSpan = 5, colSpan = 5)
        assertEquals(
            GridPlacement(0, row = 9, col = 7, rowSpan = 1, colSpan = 1),
            clampToGrid(outside, config),
        )
    }

    @Test
    fun `a handle sits inside the frame, on the edges it moves`() {
        val center = handleCenter(ResizeHandle.TOP_RIGHT, left = 0f, top = 0f, right = 100f, bottom = 200f, inset = 10f)
        assertEquals(90f, center.x)
        assertEquals(10f, center.y)

        // An edge handle is centered on the axis it does not move.
        val bottom = handleCenter(ResizeHandle.BOTTOM, left = 0f, top = 0f, right = 100f, bottom = 200f, inset = 10f)
        assertEquals(50f, bottom.x)
        assertEquals(190f, bottom.y)
    }
}
