package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour spec for [DragCoordinator]'s routing logic (registry hit-testing, z-order, accept-filtering, drop
 * resolution) exercised with a fake [DropPlanner] and hand-placed zones — no gestures or rendering involved.
 */
class DragCoordinatorTest {

    private fun app(name: String): GridItem = GridItem.App(ComponentKey("pkg", name))

    /** A planner that always reports the same plan, so tests isolate the coordinator's own routing. */
    private fun plannerReturning(plan: PlacementPlan?) = DropPlanner { _, _, _ -> plan }

    private val placePlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.PLACE)

    private fun zone(
        id: String,
        bounds: Rect,
        z: Int = 0,
        accepts: (GridItem) -> Boolean = { true },
    ) = DropZone(ZoneId(id), bounds, z, accepts)

    @Test
    fun `starts idle`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        assertFalse(coordinator.isDragging)
        assertNull(coordinator.session)
    }

    @Test
    fun `start resolves the zone under the finger and its plan`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))

        coordinator.start(app("a"), Offset(50f, 50f))

        val session = coordinator.session!!
        assertEquals(ZoneId("home"), session.activeZone)
        assertEquals(placePlan, session.plan)
    }

    @Test
    fun `a finger outside every zone has no active zone or plan`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))

        coordinator.start(app("a"), Offset(500f, 500f))

        assertNull(coordinator.session!!.activeZone)
        assertNull(coordinator.session!!.plan)
    }

    @Test
    fun `overlapping zones resolve to the highest z`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f), z = 0))
        coordinator.registerZone(zone("folder", Rect(0f, 0f, 100f, 100f), z = 10))

        coordinator.start(app("a"), Offset(50f, 50f))

        assertEquals(ZoneId("folder"), coordinator.session!!.activeZone)
    }

    @Test
    fun `a zone that rejects the item is skipped so the finger falls through`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        // The top zone accepts only "b"; dragging "a" must fall through to the home zone beneath it.
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f), z = 0))
        coordinator.registerZone(zone("widgets", Rect(0f, 0f, 100f, 100f), z = 10) { it == app("b") })

        coordinator.start(app("a"), Offset(50f, 50f))

        assertEquals(ZoneId("home"), coordinator.session!!.activeZone)
    }

    @Test
    fun `moveTo re-resolves the active zone as the finger crosses a boundary`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("left", Rect(0f, 0f, 100f, 100f)))
        coordinator.registerZone(zone("right", Rect(100f, 0f, 200f, 100f)))

        coordinator.start(app("a"), Offset(50f, 50f))
        assertEquals(ZoneId("left"), coordinator.session!!.activeZone)

        coordinator.moveTo(Offset(150f, 50f))
        assertEquals(ZoneId("right"), coordinator.session!!.activeZone)
    }

    @Test
    fun `drop over a valid target returns the outcome and clears the session`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(50f, 50f))

        val outcome = coordinator.drop()

        assertEquals(DropOutcome(ZoneId("home"), app("a"), placePlan), outcome)
        assertFalse(coordinator.isDragging)
    }

    @Test
    fun `drop over no zone is a no-op`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(500f, 500f))

        assertNull(coordinator.drop())
        assertFalse(coordinator.isDragging)
    }

    @Test
    fun `drop over an invalid target is a no-op`() {
        val invalid = PlacementPlan(footprint = null, intent = DropIntent.INVALID)
        val coordinator = DragCoordinator(plannerReturning(invalid))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(50f, 50f))

        assertNull(coordinator.drop())
        assertFalse(coordinator.isDragging)
    }

    @Test
    fun `unregistering the active zone drops the finger through to nothing`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(50f, 50f))
        assertEquals(ZoneId("home"), coordinator.session!!.activeZone)

        coordinator.unregisterZone(ZoneId("home"))
        coordinator.moveTo(Offset(50f, 50f))

        assertNull(coordinator.session!!.activeZone)
    }

    @Test
    fun `cancel clears the drag without an outcome`() {
        val coordinator = DragCoordinator(plannerReturning(placePlan))
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(50f, 50f))

        coordinator.cancel()

        assertFalse(coordinator.isDragging)
        assertNull(coordinator.session)
    }
}
