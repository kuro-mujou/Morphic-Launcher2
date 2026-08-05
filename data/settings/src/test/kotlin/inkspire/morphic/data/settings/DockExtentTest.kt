package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dock-extent rules: the same sparse, shrink-back storage its two neighbours use, on the one metric that is a
 * measurement rather than a count.
 *
 * Pure data, so no DataStore. The positivity check lives in the repository (which needs Android) and the real bounds
 * live wherever the screen is measured, so what is checked here is resolution and removal — the parts that would
 * silently produce a wrong dock rather than a rejected write.
 */
class DockExtentTest {

    private val phone = DeviceConfiguration.PHONE_PORTRAIT
    private val base = 96

    @Test
    fun `nothing stored resolves to the blueprint's height`() {
        assertEquals(base, SurfaceMetrics.Default.dockExtent(phone, base))
    }

    @Test
    fun `a stored height applies to its own device configuration only`() {
        // The reason overrides are keyed per configuration at all: a dp height that suits portrait can be most of a
        // landscape screen, so rotating must not carry it across.
        val metrics = SurfaceMetrics.Default.withDockExtent(phone, 140)

        assertEquals(140, metrics.dockExtent(phone, base))
        assertEquals(base, metrics.dockExtent(DeviceConfiguration.PHONE_LANDSCAPE, base))
    }

    @Test
    fun `clearing a height removes the entry rather than storing one`() {
        // "Reset" is a plain write of null, and storage ends exactly as it started — the same property that makes a
        // later change to the blueprint's default still reach a user who has visited this screen.
        val metrics = SurfaceMetrics.Default
            .withDockExtent(phone, 140)
            .withDockExtent(phone, null)

        assertEquals(SurfaceMetrics.Default, metrics)
        assertTrue(metrics.dockExtentDp.isEmpty())
    }

    @Test
    fun `the dock's height leaves the override maps alone`() {
        // Three maps in one slice, so it is worth pinning that a write to one does not disturb the others.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.HOME_DOCK, phone) { copy(iconPercent = 0.5f) }
            .withDockExtent(phone, 140)

        assertEquals(140, metrics.dockExtent(phone, base))
        assertEquals(0.5f, metrics.icon.getValue(GridSlot.HOME_DOCK).getValue(phone).iconPercent!!, 0f)
    }
}
