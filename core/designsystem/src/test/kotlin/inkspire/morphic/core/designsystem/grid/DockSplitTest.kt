package inkspire.morphic.core.designsystem.grid

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DockEdge
import inkspire.morphic.core.model.dockEdge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dock split: which dimension the dock's extent comes off, and what the pager is left with.
 *
 * Worth pinning because three callers depend on the two halves agreeing — the home surface draws them, and both
 * settings sections bound their grids against them — and because getting the axis wrong is silent: every number is
 * still a plausible dp, the grid is simply fitted to a dimension the surface will not give it.
 */
class DockSplitTest {

    private val phonePortrait = GridArea(widthDp = 400f, heightDp = 800f)
    private val phoneLandscape = GridArea(widthDp = 800f, heightDp = 360f)

    @Test
    fun `a bottom strip takes height and leaves the full width`() {
        val split = phonePortrait.splitForDock(96f, DockEdge.BOTTOM)

        assertEquals(400f, split.main.widthDp, 0f)
        assertEquals(704f, split.main.heightDp, 0f)
        assertEquals(400f, split.dock.widthDp, 0f)
        assertEquals(96f, split.dock.heightDp, 0f)
    }

    @Test
    fun `a rail takes width and leaves the full height`() {
        val split = phoneLandscape.splitForDock(96f, DockEdge.END)

        assertEquals(704f, split.main.widthDp, 0f)
        assertEquals(360f, split.main.heightDp, 0f)
        assertEquals(96f, split.dock.widthDp, 0f)
        assertEquals(360f, split.dock.heightDp, 0f)
    }

    @Test
    fun `an extent larger than the window still leaves the pager something to fit against`() {
        // Reachable: the extent slider's ceiling is in dp and this window's is not, so a 320dp dock on a short
        // landscape phone can exceed it. A zero area would have `maxCells` reporting cells for a zone with no room.
        val split = GridArea(widthDp = 200f, heightDp = 300f).splitForDock(400f, DockEdge.BOTTOM)

        assertEquals(1f, split.main.heightDp, 0f)
        assertEquals(400f, split.dock.heightDp, 0f)
    }

    @Test
    fun `the fraction is read off the axis the split used`() {
        assertEquals(0.12f, phonePortrait.dockFraction(96f, DockEdge.BOTTOM), 1e-6f)
        assertEquals(0.12f, phoneLandscape.dockFraction(96f, DockEdge.END), 1e-6f)
    }

    @Test
    fun `only a phone in landscape gets the rail`() {
        assertEquals(DockEdge.BOTTOM, DeviceConfiguration.PHONE_PORTRAIT.dockEdge)
        assertEquals(DockEdge.END, DeviceConfiguration.PHONE_LANDSCAPE.dockEdge)
        assertEquals(DockEdge.BOTTOM, DeviceConfiguration.TABLET_PORTRAIT.dockEdge)
        // The one that would be caught by keying on `isLandscape` instead: a tablet in landscape has the height for a
        // bottom strip, which is why the rule reads the whole configuration.
        assertEquals(DockEdge.BOTTOM, DeviceConfiguration.TABLET_LANDSCAPE.dockEdge)
    }
}
