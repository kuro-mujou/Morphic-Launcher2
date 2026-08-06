package inkspire.morphic.core.designsystem.grid

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.SideZoneEdge
import inkspire.morphic.core.model.sideZoneEdge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The side-zone split: which dimension the zone's extent comes off, and what the main area is left with.
 *
 * Worth pinning because three callers depend on the two halves agreeing — the home surface draws them, and both
 * settings sections bound their grids against them — and because getting the axis wrong is silent: every number is
 * still a plausible dp, the grid is simply fitted to a dimension the surface will not give it.
 */
class SideZoneSplitTest {

    private val phonePortrait = GridArea(widthDp = 400f, heightDp = 800f)
    private val phoneLandscape = GridArea(widthDp = 800f, heightDp = 360f)

    @Test
    fun `a strip takes height and leaves the full width, at either end`() {
        // Both ends, because the arithmetic must not know the difference: where the zone is *drawn* is the surface's
        // `Column` ordering, and a split that differed by end would make a top strip and a bottom strip two grids.
        for (edge in listOf(SideZoneEdge.BOTTOM, SideZoneEdge.TOP)) {
            val split = phonePortrait.splitForSideZone(96f, edge)

            assertEquals(400f, split.main.widthDp, 0f)
            assertEquals(704f, split.main.heightDp, 0f)
            assertEquals(400f, split.side.widthDp, 0f)
            assertEquals(96f, split.side.heightDp, 0f)
        }
    }

    @Test
    fun `a rail takes width and leaves the full height, at either end`() {
        for (edge in listOf(SideZoneEdge.END, SideZoneEdge.START)) {
            val split = phoneLandscape.splitForSideZone(96f, edge)

            assertEquals(704f, split.main.widthDp, 0f)
            assertEquals(360f, split.main.heightDp, 0f)
            assertEquals(96f, split.side.widthDp, 0f)
            assertEquals(360f, split.side.heightDp, 0f)
        }
    }

    @Test
    fun `an extent larger than the window still leaves the main area something to fit against`() {
        // Reachable: the extent slider's ceiling is in dp and this window's is not, so a 320dp dock on a short
        // landscape phone can exceed it. A zero area would have `maxCells` reporting cells for a zone with no room.
        val split = GridArea(widthDp = 200f, heightDp = 300f).splitForSideZone(400f, SideZoneEdge.BOTTOM)

        assertEquals(1f, split.main.heightDp, 0f)
        assertEquals(400f, split.side.heightDp, 0f)
    }

    @Test
    fun `the fraction is read off the axis the split used`() {
        assertEquals(0.12f, phonePortrait.sideZoneFraction(96f, SideZoneEdge.BOTTOM), 1e-6f)
        assertEquals(0.12f, phoneLandscape.sideZoneFraction(96f, SideZoneEdge.END), 1e-6f)
    }

    @Test
    fun `only a phone in landscape gets the rail`() {
        val dock = HomeLayout.PAGER_WITH_DOCK
        assertEquals(SideZoneEdge.BOTTOM, DeviceConfiguration.PHONE_PORTRAIT.sideZoneEdge(dock))
        assertEquals(SideZoneEdge.END, DeviceConfiguration.PHONE_LANDSCAPE.sideZoneEdge(dock))
        assertEquals(SideZoneEdge.BOTTOM, DeviceConfiguration.TABLET_PORTRAIT.sideZoneEdge(dock))
        // The one that would be caught by keying on `isLandscape` instead: a tablet in landscape has the height for a
        // bottom strip, which is why the rule reads the whole configuration.
        assertEquals(SideZoneEdge.BOTTOM, DeviceConfiguration.TABLET_LANDSCAPE.sideZoneEdge(dock))
    }

    @Test
    fun `the widget area mirrors the dock, edge for edge`() {
        // Same rule, opposite ends: the posture decides strip-or-rail and the layout decides which end. Pinned because
        // the two halves are stated in one `when` and a copy-paste there would put the widget area under the list.
        val area = HomeLayout.LIST_WITH_WIDGET_AREA
        assertEquals(SideZoneEdge.TOP, DeviceConfiguration.PHONE_PORTRAIT.sideZoneEdge(area))
        assertEquals(SideZoneEdge.START, DeviceConfiguration.PHONE_LANDSCAPE.sideZoneEdge(area))
        assertEquals(SideZoneEdge.TOP, DeviceConfiguration.TABLET_PORTRAIT.sideZoneEdge(area))
        assertEquals(SideZoneEdge.TOP, DeviceConfiguration.TABLET_LANDSCAPE.sideZoneEdge(area))
    }

    @Test
    fun `a leading zone is a top strip or a start rail`() {
        // What the surface stacks on and what the editor draws the companion from, so both halves are pinned here
        // rather than re-derived from the edge at each call site.
        assertEquals(listOf(true, false, true, false), SideZoneEdge.entries.map { it.isLeading })
        assertEquals(listOf(true, true, false, false), SideZoneEdge.entries.map { it.isStrip })
    }
}
