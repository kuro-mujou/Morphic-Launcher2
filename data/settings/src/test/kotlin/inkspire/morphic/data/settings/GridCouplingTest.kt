package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.boardRotates
import inkspire.morphic.core.model.toGridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind a **coupled** grid size, checked on the pieces the repository composes rather than through
 * DataStore.
 *
 * Every one of these is silent when wrong. A transpose that is not a transpose does not throw — it makes a board
 * rotation lossy, and the loss shows up as items landing somewhere unexpected two rotations later.
 */
class GridCouplingTest {

    /**
     * **The two side zones already ship as transposes, and the main area does not** — which is where the whole cost
     * of coupling sits, and the reason it is one number rather than a vague loss.
     *
     * Pinned because it is the kind of thing a later blueprint edit changes without noticing: widening phone
     * landscape's dock alone would make a board rotation lossy on the zone the rail exists for.
     */
    @Test
    fun `the side zones ship as transposes and the main area does not`() {
        listOf(GridSlot.HOME_DOCK, GridSlot.HOME_WIDGET_AREA).forEach { slot ->
            assertEquals(
                "$slot: its landscape default should be portrait's transpose",
                portraitOf(slot).swap(),
                landscapeOf(slot),
            )
        }
        assertTrue(
            "HOME_MAIN now ships as a transpose, so coupling costs nothing and this test has lost its point",
            portraitOf(GridSlot.HOME_MAIN).swap() != landscapeOf(GridSlot.HOME_MAIN),
        )
    }

    /** Swapping twice is the identity, which is what makes a coupled grid reversible at all. */
    @Test
    fun `swapping a grid twice returns it unchanged`() {
        listOf(GridSlot.HOME_MAIN, GridSlot.HOME_DOCK, GridSlot.HOME_WIDGET_AREA).forEach {
            assertEquals(portraitOf(it), portraitOf(it).swap().swap())
        }
    }

    private fun portraitOf(slot: GridSlot) =
        slot.blueprint.toGridConfig(slot.blueprint.defaults.getValue(DeviceConfiguration.PHONE_PORTRAIT))

    private fun landscapeOf(slot: GridSlot) =
        slot.blueprint.toGridConfig(slot.blueprint.defaults.getValue(DeviceConfiguration.PHONE_LANDSCAPE))

    /**
     * The form-factor gate. A tablet's side zone keeps its axis, so nothing is carried across by turning the board
     * and coupling would only cost it the wider landscape default it ships with.
     */
    @Test
    fun `the board rotates on a phone and not on a tablet`() {
        assertTrue(DeviceConfiguration.PHONE_PORTRAIT.boardRotates)
        assertTrue(DeviceConfiguration.PHONE_LANDSCAPE.boardRotates)
        assertFalse(DeviceConfiguration.TABLET_PORTRAIT.boardRotates)
        assertFalse(DeviceConfiguration.TABLET_LANDSCAPE.boardRotates)
    }

    /** What coupling costs, stated as a number so a later change to the blueprint has to face it. */
    @Test
    fun `coupling trades four landscape cells for the exact round trip`() {
        val coupled = portraitOf(GridSlot.HOME_MAIN).swap()
        val own = landscapeOf(GridSlot.HOME_MAIN)

        assertEquals(20, coupled.visualCols * coupled.visualRows)
        assertEquals(24, own.visualCols * own.visualRows)
    }

    /**
     * **Nulls swap with the numbers**, which is what a `copy(cols = rows, rows = cols)` at a call site would get
     * wrong: an override pinning one axis must still pin exactly one axis after the turn, or an axis the user left
     * following the blueprint is silently frozen.
     */
    @Test
    fun `swapping an override exchanges both axes, absent ones included`() {
        assertEquals(GridOverride(cols = 5, rows = 4), GridOverride(cols = 4, rows = 5).swapped())
        assertEquals(GridOverride(cols = null, rows = 4), GridOverride(cols = 4, rows = null).swapped())
        assertEquals(GridOverride(cols = 6, rows = null), GridOverride(cols = null, rows = 6).swapped())
        assertTrue(GridOverride().swapped().isEmpty)
    }

    /** Swapping twice is the identity, which is what makes an edit on a coupled landscape round-trip its storage. */
    @Test
    fun `swapping an override twice returns it unchanged`() {
        listOf(
            GridOverride(cols = 4, rows = 5),
            GridOverride(cols = 4),
            GridOverride(rows = 5),
            GridOverride(),
        ).forEach { assertEquals(it, it.swapped().swapped()) }
    }

    /**
     * The grid gate, read off the blueprint rather than a list — a coordinate is what a transpose acts on, and an
     * ordered surface stores a slot it re-paginates per posture instead.
     */
    @Test
    fun `only free-placement grids are the kind a transpose could keep exact`() {
        listOf(GridSlot.HOME_MAIN, GridSlot.HOME_DOCK, GridSlot.HOME_WIDGET_AREA).forEach {
            assertTrue("$it should couple", it.blueprint.freePlacement)
        }
        listOf(GridSlot.APPS_PAGER, GridSlot.APPS_SCROLL, GridSlot.APPS_LIST, GridSlot.HOME_LIST).forEach {
            assertFalse("$it should not couple", it.blueprint.freePlacement)
        }
    }
}
