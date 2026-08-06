package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.blueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The side-zone extent rules: the same sparse, shrink-back storage its neighbours use, on the metric that is a
 * measurement rather than a count.
 *
 * Pure data, so no DataStore. The positivity check and the "is this grid even a strip?" check live in the repository
 * (which needs Android) and the real bounds live wherever the screen is measured, so what is checked here is
 * resolution and removal — the parts that would silently produce a wrong zone rather than a rejected write.
 */
class SideZoneExtentTest {

    private val phone = DeviceConfiguration.PHONE_PORTRAIT
    private val base = 96

    @Test
    fun `exactly HOME's two side zones declare an extent`() {
        // The invariant behind `SettingsRepository.extent`'s `requireNotNull`, and the reason the map became
        // slot-keyed: there are two grids that can answer, not one, and no more than two.
        val strips = GridSlot.entries.filter { it.blueprint.extentDp != null }

        assertEquals(listOf(GridSlot.HOME_DOCK, GridSlot.HOME_WIDGET_AREA), strips)
        strips.forEach { assertNotNull(it.blueprint.extentDp) }
    }

    @Test
    fun `nothing stored resolves to the blueprint's extent`() {
        assertEquals(base, SurfaceMetrics.Default.extent(GridSlot.HOME_DOCK, phone, base))
    }

    @Test
    fun `a stored extent applies to its own grid and device configuration only`() {
        // Two independent reasons to key: a dp thickness that suits portrait can be most of a landscape screen, and a
        // dock and a widget area are different zones that are never on screen together.
        val metrics = SurfaceMetrics.Default.withExtent(GridSlot.HOME_DOCK, phone, 140)

        assertEquals(140, metrics.extent(GridSlot.HOME_DOCK, phone, base))
        assertEquals(base, metrics.extent(GridSlot.HOME_DOCK, DeviceConfiguration.PHONE_LANDSCAPE, base))
        assertEquals(base, metrics.extent(GridSlot.HOME_WIDGET_AREA, phone, base))
    }

    @Test
    fun `clearing an extent removes the entry at both levels rather than storing one`() {
        // "Reset" is a plain write of null, and storage ends exactly as it started — the same property that makes a
        // later change to the blueprint's default still reach a user who has visited this screen.
        val metrics = SurfaceMetrics.Default
            .withExtent(GridSlot.HOME_DOCK, phone, 140)
            .withExtent(GridSlot.HOME_DOCK, phone, null)

        assertEquals(SurfaceMetrics.Default, metrics)
        assertTrue(metrics.extentDp.isEmpty())
    }

    @Test
    fun `an extent leaves the override maps alone`() {
        // Four maps in one slice, so it is worth pinning that a write to one does not disturb the others.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.HOME_DOCK, phone) { copy(iconPercent = 0.5f) }
            .withExtent(GridSlot.HOME_DOCK, phone, 140)

        assertEquals(140, metrics.extent(GridSlot.HOME_DOCK, phone, base))
        assertEquals(0.5f, metrics.icon.getValue(GridSlot.HOME_DOCK).getValue(phone).iconPercent!!, 0f)
    }
}
