package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsScrollGrid
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridDefault
import inkspire.morphic.core.model.GridSizing
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.toGridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid-override rules: sparse merge per axis, and the one axis a user cannot invent.
 *
 * Pure data, so no DataStore. The clamp against `GridEditRange` is not here — it lives in the repository, which needs
 * Android; what *is* checked here is the part that would silently produce a wrong layout rather than a rejected write.
 */
class GridOverrideTest {

    private val phone = DeviceConfiguration.PHONE_PORTRAIT
    private val pagerBase = GridDefault(cols = 4, rows = 5)
    private val scrollBase = GridDefault(cols = 4)

    @Test
    fun `nothing overridden resolves to the base`() {
        assertEquals(pagerBase, SurfaceMetrics.Default.gridSize(GridSlot.HOME_MAIN, phone, pagerBase))
    }

    @Test
    fun `one axis can be overridden without pinning the other`() {
        val metrics = SurfaceMetrics.Default
            .withGridOverride(GridSlot.HOME_MAIN, phone) { copy(cols = 6) }

        val resolved = metrics.gridSize(GridSlot.HOME_MAIN, phone, pagerBase)

        assertEquals(6, resolved.cols)
        assertEquals(pagerBase.rows, resolved.rows)
        // The untouched axis must still be *absent* from storage, or a later change to its default stops reaching here.
        assertNull(metrics.grid.getValue(GridSlot.HOME_MAIN).getValue(phone).rows)
    }

    @Test
    fun `a scrolling grid ignores a stored row count`() {
        // Rows there are however many the content reaches, so a fixed count is not a preference the surface could
        // honor. Decided in `resolveAgainst` rather than trusted to every reader.
        val metrics = SurfaceMetrics.Default
            .withGridOverride(GridSlot.APPS_SCROLL, phone) { copy(cols = 5, rows = 9) }

        val resolved = metrics.gridSize(GridSlot.APPS_SCROLL, phone, scrollBase)

        assertEquals(5, resolved.cols)
        assertNull("a scrolling grid must not gain a fixed row count", resolved.rows)
    }

    @Test
    fun `an override applies to one slot and one device only`() {
        val metrics = SurfaceMetrics.Default
            .withGridOverride(GridSlot.HOME_MAIN, phone) { copy(cols = 7) }

        assertEquals(7, metrics.gridSize(GridSlot.HOME_MAIN, phone, pagerBase).cols)
        assertEquals(pagerBase.cols, metrics.gridSize(GridSlot.HOME_DOCK, phone, pagerBase).cols)
        assertEquals(
            pagerBase.cols,
            metrics.gridSize(GridSlot.HOME_MAIN, DeviceConfiguration.PHONE_LANDSCAPE, pagerBase).cols,
        )
    }

    @Test
    fun `an emptied override is removed rather than stored`() {
        val metrics = SurfaceMetrics.Default
            .withGridOverride(GridSlot.HOME_MAIN, phone) { copy(cols = 6) }
            .withGridOverride(GridSlot.HOME_MAIN, phone) { copy(cols = null) }

        assertEquals(SurfaceMetrics.Default, metrics)
        assertTrue(metrics.grid.isEmpty())
    }

    @Test
    fun `icon and grid overrides do not disturb each other`() {
        // One slice, two maps — so it is worth pinning that a write to one leaves the other alone.
        val metrics = SurfaceMetrics.Default
            .withIconOverride(GridSlot.HOME_MAIN, phone) { copy(iconPercent = 0.5f) }
            .withGridOverride(GridSlot.HOME_MAIN, phone) { copy(cols = 6) }

        assertEquals(0.5f, metrics.icon.getValue(GridSlot.HOME_MAIN).getValue(phone).iconPercent!!, 0f)
        assertEquals(6, metrics.grid.getValue(GridSlot.HOME_MAIN).getValue(phone).cols)
    }

    @Test
    fun `a resolved override still produces a valid GridConfig`() {
        // `GridConfig` requires both axes divisible by `cellMultiplier`, and home's is 2. The multiplication happens in
        // `toGridConfig`, so an odd *visual* override must still come out legal rather than throwing at the surface.
        val metrics = SurfaceMetrics.Default
            .withGridOverride(GridSlot.HOME_MAIN, phone) { copy(cols = 5, rows = 7) }

        val config = HomePagerGrid.toGridConfig(metrics.gridSize(GridSlot.HOME_MAIN, phone, pagerBase))

        assertEquals(5, config.visualCols)
        assertEquals(7, config.visualRows)
        assertEquals(10, config.cols)
        assertEquals(14, config.rows)
    }

    @Test
    fun `every scrolling blueprint really has no rows to override`() {
        // The precondition the ignore-rows rule leans on: a SCROLL_GRID declares no row count for any device, so
        // `resolveAgainst` always sees a null base and always keeps it null.
        val scrolling = GridSlot.entries.map { it.blueprint }.filter { it.sizing == GridSizing.SCROLL_GRID }

        assertTrue("expected at least one scrolling blueprint", scrolling.isNotEmpty())
        scrolling.forEach { blueprint ->
            assertEquals(
                "${blueprint.slot} declares rows despite scrolling",
                emptyList<Int>(),
                blueprint.defaults.values.mapNotNull { it.rows },
            )
        }
        assertTrue(AppsScrollGrid.sizing == GridSizing.SCROLL_GRID)
    }
}
