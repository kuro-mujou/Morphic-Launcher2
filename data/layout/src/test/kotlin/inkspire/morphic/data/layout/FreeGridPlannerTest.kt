package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour spec for [FreeGridPlanner] — how a resolved hover becomes a [inkspire.morphic.core.model.PlacementPlan].
 * Confirms each [DropIntent] and that the plan's [moves] mirror what [FreePush] returned.
 */
class FreeGridPlannerTest {

    private val config = GridConfig(rows = 4, cols = 4)

    private fun app(name: String): GridItem = GridItem.App(ComponentKey("pkg", name))

    @Test
    fun `landing on empty cells is a plain PLACE`() {
        val footprint = GridPlacement(0, 0, 0)
        val plan = FreeGridPlanner.plan(footprint, emptyMap(), config)
        assertEquals(DropIntent.PLACE, plan.intent)
        assertEquals(footprint, plan.footprint)
        assertTrue(plan.moves.isEmpty())
    }

    @Test
    fun `landing on an occupant that can be shoved is a PUSH carrying the move`() {
        val footprint = GridPlacement(0, 1, 0, rowSpan = 2, colSpan = 4)
        val occupants = mapOf(app("a") to GridPlacement(0, 1, 1))
        val plan = FreeGridPlanner.plan(footprint, occupants, config)
        assertEquals(DropIntent.PUSH, plan.intent)
        assertEquals(footprint, plan.footprint)
        assertEquals(GridPlacement(0, 0, 1), plan.moves[app("a")])
    }

    @Test
    fun `a drop with nowhere to push is INVALID but keeps the hovered footprint`() {
        val small = GridConfig(rows = 2, cols = 2)
        val footprint = GridPlacement(0, 0, 0, rowSpan = 2, colSpan = 2)
        val occupants = mapOf(app("a") to GridPlacement(0, 0, 0))
        val plan = FreeGridPlanner.plan(footprint, occupants, small)
        assertEquals(DropIntent.INVALID, plan.intent)
        assertEquals(footprint, plan.footprint)  // shadow paints red at the hovered cell
        assertTrue(plan.moves.isEmpty())
    }

    @Test
    fun `the merge ring yields a MERGE that shifts nothing`() {
        val target = GridPlacement(0, 1, 1)
        val occupants = mapOf(app("a") to target)
        val plan = FreeGridPlanner.plan(target, occupants, config, merge = true)
        assertEquals(DropIntent.MERGE, plan.intent)
        assertEquals(target, plan.footprint)
        assertTrue(plan.moves.isEmpty())
    }
}
