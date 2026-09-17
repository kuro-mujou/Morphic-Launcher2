package inkspire.morphic.feature.home

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.data.apps.role.AppRole
import inkspire.morphic.data.layout.LayoutChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FirstRunLayout.plan] — the arrangement a launcher is judged on before the user has touched anything.
 *
 * The failures worth pinning are all silent. A role no device answers must close the gap behind it rather than leave
 * a hole; one app answering two roles must be placed once rather than written twice under the same item id; a page
 * filled to its last row leaves the push engine nowhere to shove an occupant, so a drag on a seeded home would refuse
 * every drop but the one it started from; and the block must sit against the dock with the free rows above it, since
 * that space is what the first widget is placed into.
 */
class FirstRunLayoutTest {

    private val dock = GridConfig(rows = 2, cols = 8, cellMultiplier = 2)
    private val main = GridConfig(rows = 10, cols = 8, cellMultiplier = 2)

    private fun app(name: String) = ComponentKey(packageName = "com.example.$name", className = "Main")

    private fun resolved(vararg pairs: Pair<AppRole, ComponentKey>) = pairs.toMap()

    private fun all() = FirstRunLayout.Roles.associateWith { app(it.name.lowercase()) }

    private fun componentsIn(zone: HomeZone, plan: List<LayoutChange.Move>) =
        plan.filter { it.zone == zone }.map { (it.item as GridItem.App).component }

    @Test
    fun `dock is filled in role order, left to right`() {
        val plan = FirstRunLayout.plan(all(), dock, main)

        assertEquals(
            FirstRunLayout.Dock.map { app(it.name.lowercase()) },
            componentsIn(HomeZone.DOCK, plan),
        )
        assertEquals(listOf(0, 2, 4, 6), plan.filter { it.zone == HomeZone.DOCK }.map { it.to.col })
        assertTrue(plan.filter { it.zone == HomeZone.DOCK }.all { it.to.row == 0 })
    }

    @Test
    fun `a rail fills downwards, because reading order follows the grid rather than the screen`() {
        val rail = GridConfig(rows = 8, cols = 2, cellMultiplier = 2)

        val plan = FirstRunLayout.plan(all(), rail, main).filter { it.zone == HomeZone.DOCK }

        assertEquals(listOf(0, 2, 4, 6), plan.map { it.to.row })
        assertTrue(plan.all { it.to.col == 0 })
    }

    @Test
    fun `an unresolved role leaves no hole`() {
        val plan = FirstRunLayout.plan(all() - AppRole.MESSAGING, dock, main)

        assertEquals(
            listOf(AppRole.PHONE, AppRole.BROWSER, AppRole.CAMERA).map { app(it.name.lowercase()) },
            componentsIn(HomeZone.DOCK, plan),
        )
        assertEquals(listOf(0, 2, 4), plan.filter { it.zone == HomeZone.DOCK }.map { it.to.col })
    }

    @Test
    fun `one app answering two roles is placed once, in the dock`() {
        val shared = app("shared")
        val plan = FirstRunLayout.plan(
            resolved(AppRole.CAMERA to shared, AppRole.GALLERY to shared, AppRole.PHONE to app("phone")),
            dock,
            main,
        )

        assertEquals(listOf(app("phone"), shared), componentsIn(HomeZone.DOCK, plan))
        assertTrue(componentsIn(HomeZone.MAIN, plan).isEmpty())
    }

    @Test
    fun `the main area sits against the dock, leaving the top of the page for a widget`() {
        // Five visual rows of four. Nine apps need three rows, so they occupy the bottom three and the top two stay
        // empty — logical rows 4, 6 and 8 of a grid whose cells are two logical rows tall.
        val plan = FirstRunLayout.plan(all(), dock, main).filter { it.zone == HomeZone.MAIN }

        assertEquals(setOf(4, 6, 8), plan.map { it.to.row }.toSet())
        assertEquals(8, plan.maxOf { it.to.row })
    }

    @Test
    fun `the short row is the top one, so the row against the dock is full`() {
        val plan = FirstRunLayout.plan(all(), dock, main).filter { it.zone == HomeZone.MAIN }

        // Nine over four columns is 1 + 4 + 4 top-down, never 4 + 4 + 1 — a lone icon directly above the dock reads
        // as a mistake rather than an arrangement.
        assertEquals(1, plan.count { it.to.row == 4 })
        assertEquals(4, plan.count { it.to.row == 6 })
        assertEquals(4, plan.count { it.to.row == 8 })
        assertEquals(listOf(0, 2, 4, 6), plan.filter { it.to.row == 8 }.map { it.to.col })
    }

    @Test
    fun `the main area still keeps a visual row free, now at the top`() {
        val short = GridConfig(rows = 4, cols = 2, cellMultiplier = 2)

        val plan = FirstRunLayout.plan(all(), dock, short).filter { it.zone == HomeZone.MAIN }

        // One visual row of one column usable out of two rows: a single app, on the lower of them.
        assertEquals(1, plan.size)
        assertEquals(2, plan.single().to.row)
    }

    @Test
    fun `nothing resolved plans nothing`() {
        assertEquals(emptyList<Any>(), FirstRunLayout.plan(emptyMap(), dock, main))
    }
}
