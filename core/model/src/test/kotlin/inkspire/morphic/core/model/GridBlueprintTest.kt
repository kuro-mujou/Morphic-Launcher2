package inkspire.morphic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `only the dock is sized by its extent`() {
        // Every other grid takes the space its parent gives it, so it has no extent of its own to declare. Pinned
        // because the settings layer reads this to decide the dock's whole geometry, and a second grid quietly
        // gaining an extent would be claiming to be a fixed-extent strip too.
        val withExtent = GridBlueprints.values.filter { it.extentDp != null }.map { it.slot }

        assertEquals(listOf(GridSlot.HOME_DOCK), withExtent)
    }

    @Test
    fun `a grid with an extent of its own still edits both axes`() {
        // The extent *bounds* the count it divides rather than replacing it — a cell is `extent ÷ count`, so both
        // axes remain the user's and the editor offers both. Both are required because which one the extent divides
        // depends on where the dock sits (`DockEdge`). (Only the cap moves with the extent, which is a runtime
        // question and so not checkable here.)
        GridBlueprints.values.filter { it.extentDp != null }.forEach { blueprint ->
            val range = blueprint.editRange
            assertNotNull("${blueprint.slot} has an extent to divide, so it must have an editor", range)
            assertNotNull("${blueprint.slot} divides its extent into rows or columns, so both are editable", range!!.minRows)
        }
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
