package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsListGrid
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeListGrid
import inkspire.morphic.core.model.blueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two lists' row heights: resolution, per-grid and per-configuration scope, and shrink-back on reset.
 *
 * The same three properties [SideZoneExtentTest] pins, because it is the same shape of setting — a stored size rather
 * than a count. What differs is why it exists, and that is worth a test of its own rather than a second case in that
 * file: a strip's extent is a whole zone's, which its rows divide, while this is one row of a grid with no total
 * height at all. Bounds are absent here for the reason they are absent there — a floor needs the current icon sizing,
 * which this layer cannot see.
 */
class RowHeightTest {

    private val phone = DeviceConfiguration.PHONE_PORTRAIT
    private val base = 56

    @Test
    fun `exactly the two one-lane lists declare a row height`() {
        // The invariant behind `SettingsRepository.rowHeight`'s `requireNotNull`: every other grid takes its cell
        // height from something already chosen, so asking it for one has no honest answer.
        val lists = GridSlot.entries.filter { it.blueprint.rowHeightDp != null }

        assertEquals(listOf(GridSlot.HOME_LIST, GridSlot.APPS_LIST), lists)
        // The two numbers differ on purpose: a home list holds the handful of apps you chose, a drawer holds all of
        // them, so reach is worth more on one and density on the other. L1's own pair.
        assertEquals(56, AppsListGrid.rowHeightDp)
        assertEquals(64, HomeListGrid.rowHeightDp)
    }

    @Test
    fun `nothing stored resolves to the blueprint's row height`() {
        assertEquals(base, SurfaceMetrics.Default.rowHeight(GridSlot.APPS_LIST, phone, base))
    }

    @Test
    fun `a stored row height applies to its own grid and device configuration only`() {
        val metrics = SurfaceMetrics.Default.withRowHeight(GridSlot.APPS_LIST, phone, 72)

        assertEquals(72, metrics.rowHeight(GridSlot.APPS_LIST, phone, base))
        assertEquals(base, metrics.rowHeight(GridSlot.APPS_LIST, DeviceConfiguration.PHONE_LANDSCAPE, base))
        assertEquals(base, metrics.rowHeight(GridSlot.HOME_LIST, phone, base))
    }

    @Test
    fun `clearing a row height removes the entry rather than storing one`() {
        val metrics = SurfaceMetrics.Default
            .withRowHeight(GridSlot.APPS_LIST, phone, 72)
            .withRowHeight(GridSlot.APPS_LIST, phone, null)

        assertEquals(SurfaceMetrics.Default, metrics)
        assertTrue(metrics.rowHeightDp.isEmpty())
    }

    @Test
    fun `the row height leaves the other three maps alone`() {
        // Four maps in one slice, so it stays worth pinning that a write to one does not disturb the rest.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_LIST, phone) { copy(iconPercent = 0.5f) }
            .withExtent(GridSlot.HOME_DOCK, phone, 140)
            .withRowHeight(GridSlot.APPS_LIST, phone, 72)

        assertEquals(72, metrics.rowHeight(GridSlot.APPS_LIST, phone, base))
        assertEquals(140, metrics.extent(GridSlot.HOME_DOCK, phone, 96))
        assertEquals(0.5f, metrics.icon.getValue(GridSlot.APPS_LIST).getValue(phone).iconPercent!!, 0f)
    }
}
