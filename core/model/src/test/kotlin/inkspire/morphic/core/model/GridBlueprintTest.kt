package inkspire.morphic.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The invariants that make [GridBlueprints] safe to look up blindly.
 *
 * `data:settings` resolves every stored override against `slot.blueprint`, so a slot with no blueprint — or two
 * blueprints claiming one slot, which `associateBy` would silently collapse — is a runtime failure the first time
 * someone reads a setting. Checking it here means the mistake fails the build instead of the launcher.
 */
class GridBlueprintTest {

    @Test
    fun `every slot has a blueprint`() {
        assertEquals(GridSlot.entries.toSet(), GridBlueprints.keys)
    }

    @Test
    fun `no two blueprints claim the same slot`() {
        // `associateBy` keeps the last of a duplicate pair, so a collision shows up as a map smaller than the list.
        val declared = listOf(
            HomePagerGrid, DockGrid, AppsPagerGrid, AppsScrollGrid, AppsListGrid,
            AppsCategoryGrid, AppsCardGrid, FolderGrid,
        )

        assertEquals(declared.size, GridBlueprints.size)
    }

    @Test
    fun `a blueprint is reachable from its own slot`() {
        GridBlueprints.forEach { (slot, blueprint) -> assertEquals(slot, blueprint.slot) }
    }

    @Test
    fun `every grid that draws icon cells declares its icon sizing`() {
        // The 1:1 rule this design rests on: an independent grid config gets an independent icon config. The one
        // exception is a grid of tiles rather than of icons, which says so with a null instead of carrying a value
        // nothing reads.
        val withoutIcon = GridBlueprints.values.filter { it.icon == null }.map { it.slot }

        assertEquals(listOf(GridSlot.APPS_CARD), withoutIcon)
    }

    @Test
    fun `a scrolling grid has no fixed row count and a paged one does`() {
        // Not new behaviour — but it is the precondition `toGridConfig` throws on, and the icon/grid resolvers now
        // read these blueprints per slot, so it is worth pinning that the two sizings stay distinguishable by data.
        GridBlueprints.values.forEach { blueprint ->
            val rows = blueprint.defaults.values.map { it.rows }
            when (blueprint.sizing) {
                GridSizing.FIXED_PAGER -> assertEquals(
                    "FIXED_PAGER ${blueprint.slot} must give every device a row count",
                    emptyList<Int?>(),
                    rows.filter { it == null },
                )
                GridSizing.SCROLL_GRID -> assertEquals(
                    "SCROLL_GRID ${blueprint.slot} derives rows at runtime, so it must not declare any",
                    emptyList<Int?>(),
                    rows.filterNotNull(),
                )
            }
        }
    }
}
