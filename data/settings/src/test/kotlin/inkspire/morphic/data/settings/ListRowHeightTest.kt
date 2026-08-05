package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsListGrid
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.blueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The APPS list's row height: resolution, per-configuration scope, and shrink-back on reset.
 *
 * The same three properties [DockExtentTest] pins, because it is the same shape of setting — a stored extent rather
 * than a count. What differs is why it exists, and that is worth a test of its own rather than a second case in that
 * file: the dock's height is a strip's, which its rows divide, while this is one row of a grid with no total height
 * at all. Bounds are absent here for the reason they are absent there — a floor needs the current icon sizing, which
 * this layer cannot see.
 */
class ListRowHeightTest {

    private val phone = DeviceConfiguration.PHONE_PORTRAIT
    private val base = 56

    @Test
    fun `the list is the one grid that declares a row height`() {
        // The invariant behind `SettingsRepository.listRowHeight`'s `requireNotNull`: every other grid takes its cell
        // height from something already chosen, so asking it for one has no honest answer.
        assertNotNull(AppsListGrid.rowHeightDp)
        assertEquals(base, AppsListGrid.rowHeightDp)
        GridSlot.entries
            .filter { it != GridSlot.APPS_LIST }
            .forEach { slot ->
                assertEquals("$slot must not declare a row height", null, slot.blueprint.rowHeightDp)
            }
    }

    @Test
    fun `nothing stored resolves to the blueprint's row height`() {
        assertEquals(base, SurfaceMetrics.Default.listRowHeight(phone, base))
    }

    @Test
    fun `a stored row height applies to its own device configuration only`() {
        val metrics = SurfaceMetrics.Default.withListRowHeight(phone, 72)

        assertEquals(72, metrics.listRowHeight(phone, base))
        assertEquals(base, metrics.listRowHeight(DeviceConfiguration.PHONE_LANDSCAPE, base))
    }

    @Test
    fun `clearing a row height removes the entry rather than storing one`() {
        val metrics = SurfaceMetrics.Default
            .withListRowHeight(phone, 72)
            .withListRowHeight(phone, null)

        assertEquals(SurfaceMetrics.Default, metrics)
        assertTrue(metrics.listRowHeightDp.isEmpty())
    }

    @Test
    fun `the row height leaves the other three maps alone`() {
        // Four maps in one slice now, so it stays worth pinning that a write to one does not disturb the rest.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_LIST, phone) { copy(iconPercent = 0.5f) }
            .withDockExtent(phone, 140)
            .withListRowHeight(phone, 72)

        assertEquals(72, metrics.listRowHeight(phone, base))
        assertEquals(140, metrics.dockExtent(phone, 96))
        assertEquals(0.5f, metrics.icon.getValue(GridSlot.APPS_LIST).getValue(phone).iconPercent!!, 0f)
    }
}
