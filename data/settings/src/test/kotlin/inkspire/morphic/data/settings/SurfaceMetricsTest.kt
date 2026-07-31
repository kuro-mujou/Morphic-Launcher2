package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.blueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The override → resolved-value rules: sparse merge, per-key isolation, and storage that shrinks back.
 *
 * Pure functions on plain data, so none of this needs DataStore. These are the properties that make "a default lives
 * in exactly one place" true rather than aspirational, which is the claim the whole settings design rests on.
 */
class SurfaceMetricsTest {

    private val base = IconSizing(iconPercent = 0.75f, labelScale = 1f, showLabel = true)
    private val phone = DeviceConfiguration.PHONE_PORTRAIT

    private fun SurfaceMetrics.resolve(
        slot: GridSlot = GridSlot.APPS_PAGER,
        device: DeviceConfiguration = phone,
    ) = iconSizing(slot, device, base)

    @Test
    fun `nothing overridden resolves to the base`() {
        assertEquals(base, SurfaceMetrics.Default.resolve())
    }

    @Test
    fun `an overridden field wins and the rest keep following the base`() {
        // The whole point of sparse storage: change one control, and a later change to any *other* default still
        // reaches this user.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = 0.5f) }

        val resolved = metrics.resolve()

        assertEquals(0.5f, resolved.iconPercent, 0f)
        assertEquals(base.labelScale, resolved.labelScale, 0f)
        assertEquals(base.showLabel, resolved.showLabel)
        assertEquals(base.minIconDp, resolved.minIconDp)
    }

    @Test
    fun `clearing a field returns it to the base`() {
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = 0.5f) }
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = null) }

        assertEquals(base, metrics.resolve())
    }

    @Test
    fun `an override applies to one slot only`() {
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = 0.5f) }

        assertEquals(0.5f, metrics.resolve(slot = GridSlot.APPS_PAGER).iconPercent, 0f)
        assertEquals(base.iconPercent, metrics.resolve(slot = GridSlot.HOME_DOCK).iconPercent, 0f)
    }

    @Test
    fun `an override applies to one device configuration only`() {
        // Why the key is DeviceConfiguration and not Orientation: the blueprint distinguishes phone-landscape from
        // tablet-landscape, so an override must be able to as well or it would be coarser than what it replaces.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_PAGER, DeviceConfiguration.PHONE_LANDSCAPE) { copy(iconPercent = 0.5f) }

        assertEquals(
            0.5f,
            metrics.resolve(device = DeviceConfiguration.PHONE_LANDSCAPE).iconPercent,
            0f,
        )
        assertEquals(
            base.iconPercent,
            metrics.resolve(device = DeviceConfiguration.TABLET_LANDSCAPE).iconPercent,
            0f,
        )
    }

    @Test
    fun `an emptied override is removed rather than stored`() {
        // Storage must shrink back, not accumulate `{"APPS_PAGER":{"PHONE_PORTRAIT":{}}}` for every screen visited.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = 0.5f) }
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = null) }

        assertEquals(SurfaceMetrics.Default, metrics)
        assertNull(metrics.icon[GridSlot.APPS_PAGER])
        assertTrue(metrics.icon.isEmpty())
    }

    @Test
    fun `clearing one device leaves the other alone`() {
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = 0.5f) }
            .withIconOverride(GridSlot.APPS_PAGER, DeviceConfiguration.PHONE_LANDSCAPE) { copy(labelScale = 2f) }
            .withIconOverride(GridSlot.APPS_PAGER, phone) { copy(iconPercent = null) }

        assertEquals(
            setOf(DeviceConfiguration.PHONE_LANDSCAPE),
            metrics.icon.getValue(GridSlot.APPS_PAGER).keys,
        )
    }

    @Test
    fun `every icon-drawing slot resolves against its own blueprint`() {
        // The registry lookup the repository does for real. If a slot had no blueprint this would throw, which is the
        // failure `GridBlueprintTest` exists to catch earlier. A tile grid (the category card) has no icon sizing at
        // all, and asking for it is a coding mistake the repository rejects — so it is excluded here rather than
        // given a stand-in.
        GridSlot.entries.mapNotNull { slot -> slot.blueprint.icon?.let { slot to it } }
            .forEach { (slot, base) ->
                val resolved = SurfaceMetrics.Default.iconSizing(slot, phone, base)

                assertEquals("$slot should resolve to its blueprint default", base, resolved)
            }
    }
}
