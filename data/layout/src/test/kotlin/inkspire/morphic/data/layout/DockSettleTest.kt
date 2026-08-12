package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a dock resize does to the items in it: keep what fits, spill the rest onto home, and **never delete**.
 *
 * The rule has two callers with different triggers — the home surface re-fitting after a height change, and the dock
 * settings editor applying an edge edit — so these pin the behavior they share rather than either one's plumbing.
 * L1's equivalent dropped what would not fit (`droppedApps`/`droppedFolders`/`droppedWidgets`, for the caller to
 * delete), which is the outcome most worth having a test against.
 */
class DockSettleTest {

    // Typed as `GridItem`, not `GridItem.App`: `Map` is invariant in its key, so a map of the subtype is not a map
    // of the supertype and every call site would need an explicit annotation instead.
    private fun app(name: String): GridItem = GridItem.App(ComponentKey("pkg", name))
    private val oneRowFour = GridConfig(rows = 1, cols = 4)
    private val homeGrid = GridConfig(rows = 5, cols = 4)

    @Test
    fun `a re-fit keeps what still fits and spills the rest onto home`() {
        // A two-row dock shrunk to one: the second row's occupant has nowhere left on a single-page strip.
        val dock = mapOf(
            app("a") to GridPlacement(0, 0, 0),
            app("b") to GridPlacement(0, 0, 1),
            app("c") to GridPlacement(0, 0, 2),
            app("d") to GridPlacement(0, 0, 3),
            app("e") to GridPlacement(0, 1, 0),
        )

        val moves = settleDock(dock, main = emptyMap(), dockConfig = oneRowFour, mainConfig = homeGrid)

        val spilled = moves.filterIsInstance<LayoutChange.Move>().single { it.zone == HomeZone.MAIN }
        assertEquals(app("e"), spilled.item)
        assertTrue("a spilled item must land on home's grid", spilled.to.fitsIn(homeGrid))
        // The four that still fit are untouched, so nothing is re-stamped for the sake of it.
        assertTrue(moves.none { it is LayoutChange.Move && it.zone == HomeZone.DOCK })
    }

    @Test
    fun `removing the left column shifts the dock left and the leftmost app spills`() {
        // The difference an edge makes, and the reason the editor commits placements itself: a plain re-fit would
        // have kept a, b, c where they were and spilled "d" instead.
        val dock = mapOf(
            app("a") to GridPlacement(0, 0, 0),
            app("b") to GridPlacement(0, 0, 1),
            app("c") to GridPlacement(0, 0, 2),
            app("d") to GridPlacement(0, 0, 3),
        )

        val moves = settleDock(
            dock = dock,
            main = emptyMap(),
            dockConfig = GridConfig(rows = 1, cols = 3),
            mainConfig = homeGrid,
            edit = DockEdit(GridEditorEdge.LEFT, add = false),
        )

        val byItem = moves.filterIsInstance<LayoutChange.Move>().associateBy { it.item }
        assertEquals(HomeZone.MAIN, byItem.getValue(app("a")).zone)
        assertEquals(GridPlacement(0, 0, 0), byItem.getValue(app("b")).to)
        assertEquals(GridPlacement(0, 0, 1), byItem.getValue(app("c")).to)
        assertEquals(GridPlacement(0, 0, 2), byItem.getValue(app("d")).to)
    }

    @Test
    fun `a spilled app is placed around what home already holds`() {
        val dock = mapOf(app("a") to GridPlacement(0, 0, 0), app("spill") to GridPlacement(0, 1, 0))
        val main = mapOf(app("home") to GridPlacement(0, 0, 0))

        val moves = settleDock(dock, main, GridConfig(rows = 1, cols = 1), homeGrid)

        val landed = moves.filterIsInstance<LayoutChange.Move>().single { it.zone == HomeZone.MAIN }
        assertEquals(app("spill"), landed.item)
        assertTrue("it must not land on top of what home already holds", landed.to != GridPlacement(0, 0, 0))
    }

    @Test
    fun `a dock that already fits asks for no writes`() {
        val dock = mapOf(app("a") to GridPlacement(0, 0, 0))

        assertEquals(emptyList<LayoutChange>(), settleDock(dock, emptyMap(), oneRowFour, homeGrid))
    }

    @Test
    fun `growing the dock moves nobody`() {
        val dock = mapOf(app("a") to GridPlacement(0, 0, 0))

        val moves = settleDock(
            dock = dock,
            main = emptyMap(),
            dockConfig = GridConfig(rows = 1, cols = 5),
            mainConfig = homeGrid,
            edit = DockEdit(GridEditorEdge.RIGHT, add = true),
        )

        assertEquals(emptyList<LayoutChange>(), moves)
    }
}
