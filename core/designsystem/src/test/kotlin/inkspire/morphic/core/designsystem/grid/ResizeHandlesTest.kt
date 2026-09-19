package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec for the resize geometry — which grips an item gets, and what dragging one does to a placement.
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
    fun `a resizable item gets both grips`() {
        assertEquals(listOf(ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_RIGHT), handlesFor(bothAxes))
    }

    @Test
    fun `a single-axis item still gets both grips`() {
        // A grip moves an edge on each axis, but only the permitted one actually moves (see below).
        val bounds = ResizeBounds(horizontal = false, vertical = true, minColSpan = 1, minRowSpan = 1)
        assertEquals(listOf(ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_RIGHT), handlesFor(bounds))
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
    fun `a sideways drag on a corner moves only the width`() {
        // The finger stays on the bottom boundary (y = 400 rounds to row 4), so the height is untouched; x = 620
        // rounds to column 6. This is what makes two corner grips enough — no edge grip is needed for one axis.
        assertEquals(
            GridPlacement(0, row = 2, col = 2, rowSpan = 2, colSpan = 4),
            resize(ResizeHandle.BOTTOM_RIGHT, x = 620f, y = 410f),
        )
    }

    @Test
    fun `dragging the top-left corner keeps the bottom-right one fixed`() {
        assertEquals(
            GridPlacement(0, row = 2, col = 0, rowSpan = 2, colSpan = 4),
            resize(ResizeHandle.TOP_LEFT, x = 10f, y = 190f),
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
            resize(ResizeHandle.BOTTOM_RIGHT, x = 0f, y = 400f, bounds = bounds),
        )
    }

    @Test
    fun `an axis the provider refuses does not move`() {
        val bounds = ResizeBounds(horizontal = false, vertical = true, minColSpan = 1, minRowSpan = 1)
        // A corner drag on a height-only widget changes the height and leaves the width alone.
        assertEquals(
            GridPlacement(0, row = 0, col = 2, rowSpan = 4, colSpan = 2),
            resize(ResizeHandle.TOP_LEFT, x = 20f, y = 20f, bounds = bounds),
        )
    }

    @Test
    fun `the growing edge is allowed to overshoot the grid`() {
        // Deliberately unclamped: the caller compares this with `clampToGrid` to tell an over-drag from a legal
        // resize that happens to end at the edge.
        val over = resize(ResizeHandle.BOTTOM_RIGHT, x = 1_200f, y = 400f)
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
    fun `a grip sits at the midpoint of its rounded corner`() {
        val inset = cornerArcInset(radius = 20f)
        // r(1 - 1/sqrt 2): the 45-degree point of a quarter circle of radius 20, measured from the square corner.
        assertEquals(5.858f, inset, 0.001f)

        val topLeft = handleCenter(ResizeHandle.TOP_LEFT, left = 0f, top = 0f, right = 100f, bottom = 200f, inset = inset)
        assertEquals(Offset(inset, inset), topLeft)
        val bottomRight =
            handleCenter(ResizeHandle.BOTTOM_RIGHT, left = 0f, top = 0f, right = 100f, bottom = 200f, inset = inset)
        assertEquals(Offset(100f - inset, 200f - inset), bottomRight)
    }
}
