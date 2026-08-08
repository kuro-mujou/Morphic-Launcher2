package inkspire.morphic.core.designsystem.menu

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec for where a context menu goes — the half of the menu that is pure arithmetic, and the half worth pinning:
 * the placement rule reads plausibly in either direction, and the clamping is only ever exercised at screen edges,
 * which is exactly where it is hardest to notice being wrong.
 *
 * A "phone" frame is 1000 tall × 400 wide with a 50px inset all round (a status bar's worth), so the usable area is
 * `(50, 50)–(350, 950)`; the landscape one is its transpose.
 */
class MenuAnchoringTest {

    private val portraitFrame = IntRect(left = 50, top = 50, right = 350, bottom = 950)
    private val landscapeFrame = IntRect(left = 50, top = 50, right = 950, bottom = 350)
    private val menu = IntSize(200, 300)
    private val gap = 8

    /** A 100×100 item with its top-left at ([x], [y]). */
    private fun item(x: Int, y: Int) = IntRect(x, y, x + 100, y + 100)

    @Test
    fun `a tall frame stacks the menu, opening downward from an item in the top half`() {
        assertEquals(MenuPlacement.BELOW, menuPlacementFor(item(100, 200), portraitFrame))
    }

    @Test
    fun `a tall frame opens upward from an item in the bottom half`() {
        assertEquals(MenuPlacement.ABOVE, menuPlacementFor(item(100, 800), portraitFrame))
    }

    @Test
    fun `a wide frame puts the menu beside the item, on whichever side has room`() {
        assertEquals(MenuPlacement.RIGHT, menuPlacementFor(item(100, 100), landscapeFrame))
        assertEquals(MenuPlacement.LEFT, menuPlacementFor(item(800, 100), landscapeFrame))
    }

    @Test
    fun `the halves are judged against the usable area, not the window`() {
        // An item at y = 480 is above the *window's* midpoint (500) but below the frame's (50 + 900/2 = 500)…
        // a frame with a tall bottom inset moves that line, and the menu must follow it rather than the window's.
        val shortFrame = IntRect(left = 50, top = 50, right = 350, bottom = 550)
        assertEquals(MenuPlacement.ABOVE, menuPlacementFor(item(100, 480), shortFrame))
        assertEquals(MenuPlacement.BELOW, menuPlacementFor(item(100, 100), shortFrame))
    }

    @Test
    fun `a stacked menu sits below the item, centred on it, one gap away`() {
        val offset = menuOffsetFor(item(150, 200), menu, portraitFrame, MenuPlacement.BELOW, gap)
        // Centred: the item's centre is x = 200 and the menu is 200 wide, so its left edge lands at 100 — inside
        // the frame, so nothing is clamped. Vertically it starts one gap under the item's bottom edge (300).
        assertEquals(IntOffset(100, 308), offset)
    }

    @Test
    fun `a menu beside the item is vertically centred on it`() {
        val offset = menuOffsetFor(item(100, 100), menu, landscapeFrame, MenuPlacement.RIGHT, gap)
        // One gap right of the item's right edge; centred on its middle (y = 150) → top at 0, clamped to 58.
        assertEquals(208, offset.x)
        assertEquals(58, offset.y)
    }

    @Test
    fun `a menu that would overhang the frame is pushed back inside it`() {
        // An item hard against the right edge: centring the menu on it would put its right edge past the frame.
        val offset = menuOffsetFor(item(240, 200), menu, portraitFrame, MenuPlacement.BELOW, gap)
        assertEquals(portraitFrame.right - menu.width - gap, offset.x)
        assertTrue("stays inside the frame", offset.x >= portraitFrame.left + gap)
    }

    @Test
    fun `a menu with no room on its chosen side is clamped rather than drawn off-screen`() {
        // ABOVE an item near the top: the raw position is negative, so it lands on the frame's top edge and
        // overlaps the item — the honest outcome when there is nowhere else to be.
        val offset = menuOffsetFor(item(100, 60), menu, portraitFrame, MenuPlacement.ABOVE, gap)
        assertEquals(portraitFrame.top + gap, offset.y)
    }

    @Test
    fun `a menu larger than the frame pins to the top-left instead of inverting`() {
        // max < min for both axes here; the clamp must not throw, which `coerceIn(min, maxOf(min, max))` is for.
        val huge = IntSize(portraitFrame.width + 500, portraitFrame.height + 500)
        val offset = menuOffsetFor(item(100, 200), huge, portraitFrame, MenuPlacement.BELOW, gap)
        assertEquals(IntOffset(portraitFrame.left + gap, portraitFrame.top + gap), offset)
    }

    @Test
    fun `a surface menu docks to the side of the screen the press was on`() {
        assertEquals(MenuDock.LEFT, menuDockFor(IntOffset(120, 500), portraitFrame))
        assertEquals(MenuDock.RIGHT, menuDockFor(IntOffset(300, 500), portraitFrame))
    }

    @Test
    fun `a docked menu hugs its edge and centres vertically on the press`() {
        val left = dockedMenuOffsetFor(IntOffset(120, 500), menu, portraitFrame, MenuDock.LEFT, gap)
        assertEquals(portraitFrame.left + gap, left.x)
        // Centred on the press: 500 - 300/2.
        assertEquals(350, left.y)

        val right = dockedMenuOffsetFor(IntOffset(300, 500), menu, portraitFrame, MenuDock.RIGHT, gap)
        assertEquals(portraitFrame.right - menu.width - gap, right.x)
        assertEquals(350, right.y)
    }

    @Test
    fun `a docked menu pressed near the top or bottom is pushed back inside the frame`() {
        // The x is pinned either way — a docked menu is *meant* to touch its edge — so only y moves.
        val high = dockedMenuOffsetFor(IntOffset(120, 60), menu, portraitFrame, MenuDock.LEFT, gap)
        assertEquals(portraitFrame.top + gap, high.y)

        val low = dockedMenuOffsetFor(IntOffset(120, 940), menu, portraitFrame, MenuDock.LEFT, gap)
        assertEquals(portraitFrame.bottom - menu.height - gap, low.y)
    }

    @Test
    fun `each placement grows from the edge nearest the item`() {
        assertEquals(0f, MenuPlacement.BELOW.transformOrigin().pivotFractionY)
        assertEquals(1f, MenuPlacement.ABOVE.transformOrigin().pivotFractionY)
        assertEquals(0f, MenuPlacement.RIGHT.transformOrigin().pivotFractionX)
        assertEquals(1f, MenuPlacement.LEFT.transformOrigin().pivotFractionX)
    }
}
