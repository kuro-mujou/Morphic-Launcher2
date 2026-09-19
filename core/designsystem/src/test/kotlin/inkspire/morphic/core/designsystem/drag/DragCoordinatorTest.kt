package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
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
 * Behavior spec for [DragCoordinator]'s routing logic (registry hit-testing, z-order, accept-filtering, drop
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
        plan: PlacementPlan? = placePlan,
        accepts: (GridItem) -> Boolean = { true },
        onDrop: (DropOutcome) -> Unit = {},
    ) = DropZone(ZoneId(id), bounds, z, plannerReturning(plan), accepts, onDrop)

    /**
     * Registers [zone] under an owner of its own, standing in for one `RegisterDropZone` call site. Tests that care
     * about ownership pass the token explicitly instead.
     */
    private fun DragCoordinator.register(zone: DropZone) = registerZone(zone, owner = Any())

    @Test
    fun `starts idle`() {
        val coordinator = DragCoordinator()
        assertFalse(coordinator.isDragging)
        assertNull(coordinator.session)
    }

    @Test
    fun `start resolves the zone under the finger and its plan`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))

        coordinator.start(app("a"), Offset(50f, 50f))

        val session = coordinator.session!!
        assertEquals(ZoneId("home"), session.activeZone)
        assertEquals(placePlan, session.plan)
    }

    @Test
    fun `a finger outside every zone has no active zone or plan`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))

        coordinator.start(app("a"), Offset(500f, 500f))

        assertNull(coordinator.session!!.activeZone)
        assertNull(coordinator.session!!.plan)
    }

    @Test
    fun `overlapping zones resolve to the highest z`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f), z = 0))
        coordinator.register(zone("folder", Rect(0f, 0f, 100f, 100f), z = 10))

        coordinator.start(app("a"), Offset(50f, 50f))

        assertEquals(ZoneId("folder"), coordinator.session!!.activeZone)
    }

    @Test
    fun `a zone that rejects the item is skipped so the finger falls through`() {
        val coordinator = DragCoordinator()
        // The top zone accepts only "b"; dragging "a" must fall through to the home zone beneath it.
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f), z = 0))
        coordinator.register(
            zone("widgets", Rect(0f, 0f, 100f, 100f), z = 10, accepts = { it == app("b") }),
        )

        coordinator.start(app("a"), Offset(50f, 50f))

        assertEquals(ZoneId("home"), coordinator.session!!.activeZone)
    }

    @Test
    fun `moveTo re-resolves the active zone as the finger crosses a boundary`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("left", Rect(0f, 0f, 100f, 100f)))
        coordinator.register(zone("right", Rect(100f, 0f, 200f, 100f)))

        coordinator.start(app("a"), Offset(50f, 50f))
        assertEquals(ZoneId("left"), coordinator.session!!.activeZone)

        coordinator.moveTo(Offset(150f, 50f))
        assertEquals(ZoneId("right"), coordinator.session!!.activeZone)
    }

    @Test
    fun `drop over a valid target returns the outcome and clears the session`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(50f, 50f))

        val outcome = coordinator.drop()

        assertEquals(DropOutcome(ZoneId("home"), app("a"), placePlan), outcome)
        assertFalse(coordinator.isDragging)
    }

    @Test
    fun `drop over no zone is a no-op`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(500f, 500f))

        assertNull(coordinator.drop())
        assertFalse(coordinator.isDragging)
    }

    @Test
    fun `drop over an invalid target is a no-op`() {
        val invalid = PlacementPlan(GridPlacement(0, 0, 0), intent = DropIntent.INVALID)
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f), plan = invalid))
        coordinator.start(app("a"), Offset(50f, 50f))

        assertNull(coordinator.drop())
        assertFalse(coordinator.isDragging)
    }

    @Test
    fun `unregistering the active zone drops the finger through to nothing`() {
        val owner = Any()
        val coordinator = DragCoordinator()
        coordinator.registerZone(zone("home", Rect(0f, 0f, 100f, 100f)), owner)
        coordinator.start(app("a"), Offset(50f, 50f))
        assertEquals(ZoneId("home"), coordinator.session!!.activeZone)

        coordinator.unregisterZone(ZoneId("home"), owner)
        coordinator.moveTo(Offset(50f, 50f))

        assertNull(coordinator.session!!.activeZone)
    }

    /**
     * The regression this exists for: a registrar republishes a *fresh* [DropZone] every composition, so a teardown
     * that identified its registration by the last value it built could be refused and leave the zone stranded. Two
     * folder overlays share one id at `z = 1` over the folder card, so a stranded entry went on winning the finger
     * across the middle of the screen with no folder on screen.
     */
    @Test
    fun `a registrar can withdraw an id it republished under a new zone instance`() {
        val owner = Any()
        val coordinator = DragCoordinator()
        coordinator.registerZone(zone("folder", Rect(0f, 0f, 100f, 100f), z = 1), owner)
        // A later composition: same id, same owner, a new instance carrying freshly-built lambdas.
        coordinator.registerZone(zone("folder", Rect(0f, 0f, 100f, 100f), z = 1), owner)

        coordinator.unregisterZone(ZoneId("folder"), owner)

        coordinator.start(app("a"), Offset(50f, 50f))
        assertNull(coordinator.session!!.activeZone)
    }

    /**
     * The property the ownership test protects, and the reason withdrawal isn't unconditional: an id can change
     * hands within one composition, and the node that has given it up must not delete its successor's registration.
     */
    @Test
    fun `a predecessor's withdrawal leaves the successor's registration alone`() {
        val predecessor = Any()
        val successor = Any()
        val coordinator = DragCoordinator()
        coordinator.registerZone(zone("folder", Rect(0f, 0f, 100f, 100f), z = 1), predecessor)
        coordinator.registerZone(zone("folder", Rect(0f, 0f, 100f, 100f), z = 1), successor)

        coordinator.unregisterZone(ZoneId("folder"), predecessor)

        coordinator.start(app("a"), Offset(50f, 50f))
        assertEquals(ZoneId("folder"), coordinator.session!!.activeZone)
    }

    @Test
    fun `each zone is planned by its own planner`() {
        val leftPlan = PlacementPlan(GridPlacement(0, 1, 1), DropIntent.PLACE)
        val coordinator = DragCoordinator()
        coordinator.register(zone("left", Rect(0f, 0f, 100f, 100f), plan = leftPlan))
        coordinator.register(zone("right", Rect(100f, 0f, 200f, 100f), plan = placePlan))

        coordinator.start(app("a"), Offset(50f, 50f))
        assertEquals(leftPlan, coordinator.session!!.plan)

        coordinator.moveTo(Offset(150f, 50f))
        assertEquals(placePlan, coordinator.session!!.plan)
    }

    @Test
    fun `a drop is committed by the zone it landed in, not the one it started over`() {
        val landed = mutableListOf<String>()
        val coordinator = DragCoordinator()
        coordinator.register(
            zone("source", Rect(0f, 0f, 100f, 100f), onDrop = { landed += "source" }),
        )
        coordinator.register(
            zone("destination", Rect(100f, 0f, 200f, 100f), onDrop = { landed += "destination" }),
        )

        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.moveTo(Offset(150f, 50f))
        coordinator.drop()

        assertEquals(listOf("destination"), landed)
    }

    @Test
    fun `a no-op drop commits nothing`() {
        var committed = false
        val coordinator = DragCoordinator()
        coordinator.register(
            zone("home", Rect(0f, 0f, 100f, 100f), onDrop = { committed = true }),
        )

        coordinator.start(app("a"), Offset(500f, 500f)) // outside every zone
        coordinator.drop()

        assertFalse(committed)
    }

    @Test
    fun `cancel clears the drag without an outcome`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.start(app("a"), Offset(50f, 50f))

        coordinator.cancel()

        assertFalse(coordinator.isDragging)
        assertNull(coordinator.session)
    }

    @Test
    fun `the planner is asked about the item's centre, the zone about the finger`() {
        val asked = mutableListOf<Pair<Offset, Offset>>()
        val coordinator = DragCoordinator()
        coordinator.register(
            DropZone(
                ZoneId("home"),
                Rect(0f, 0f, 100f, 100f),
                z = 0,
                planner = { _, finger, center -> asked += finger to center; placePlan },
                accepts = { true },
                onDrop = {},
            ),
        )

        // Pressed 30px right of and 10px below the item's centre.
        coordinator.start(app("a"), Offset(95f, 50f), grabFromCenter = Offset(30f, 10f))
        coordinator.moveTo(Offset(90f, 60f))

        assertEquals(listOf(Offset(95f, 50f) to Offset(65f, 40f), Offset(90f, 60f) to Offset(60f, 50f)), asked)
        assertEquals(Offset(60f, 50f), coordinator.session?.itemCenterInRoot)
        // The centre alone would still be inside; the finger decides, and it is outside.
        coordinator.moveTo(Offset(110f, 60f))
        assertNull(coordinator.session?.activeZone)
    }

    @Test
    fun `a lift remembers where the item rested`() {
        val coordinator = DragCoordinator()
        coordinator.start(app("a"), Offset(80f, 50f), grabFromCenter = Offset(10f, 0f), restCenterInRoot = Offset(50f, 50f))

        assertEquals(Offset(50f, 50f), coordinator.session?.lift?.centerInRoot)
    }

    @Test
    fun `every ending leaves a landing where the item was carried, and the next lift clears it`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))

        coordinator.start(app("a"), Offset(50f, 50f), grabFromCenter = Offset(5f, 5f))
        coordinator.moveTo(Offset(60f, 70f))
        coordinator.drop()
        assertEquals(app("a"), coordinator.landing?.item)
        assertEquals(Offset(55f, 65f), coordinator.landing?.centerInRoot)

        coordinator.start(app("b"), Offset(50f, 50f))
        assertNull(coordinator.landing)
        coordinator.cancel()
        assertEquals(app("b"), coordinator.landing?.item)
    }

    @Test
    fun `a landing leaves its source only when the drop takes the item elsewhere`() {
        val mergePlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.MERGE)
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))
        coordinator.register(zone("dock", Rect(0f, 100f, 100f, 200f)))
        coordinator.register(zone("folder", Rect(100f, 0f, 200f, 100f), plan = mergePlan))

        // Within the zone it was lifted in: it keeps a place there.
        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.moveTo(Offset(60f, 60f))
        coordinator.drop()
        assertFalse(coordinator.landing!!.leftSource)

        // Into another zone.
        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.moveTo(Offset(50f, 150f))
        coordinator.drop()
        assertTrue(coordinator.landing!!.leftSource)
        // And it remembers where it came from, which is what a holder asks to tell a departure from an arrival.
        assertEquals(ZoneId("home"), coordinator.landing!!.fromZone)

        // Merged into something.
        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.moveTo(Offset(150f, 50f))
        coordinator.drop()
        assertTrue(coordinator.landing!!.leftSource)

        // Released over nothing, or canceled: it goes back where it came from.
        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.moveTo(Offset(500f, 500f))
        coordinator.drop()
        assertFalse(coordinator.landing!!.leftSource)
        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.moveTo(Offset(50f, 150f))
        coordinator.cancel()
        assertFalse(coordinator.landing!!.leftSource)
    }

    @Test
    fun `the icon size a proxy reported travels with the landing, and a new lift forgets it`() {
        val coordinator = DragCoordinator()
        coordinator.register(zone("home", Rect(0f, 0f, 100f, 100f)))

        coordinator.start(app("a"), Offset(50f, 50f))
        coordinator.carriedIconSize = 56.dp
        coordinator.drop()
        assertEquals(56.dp, coordinator.landing?.iconSize)

        coordinator.start(app("b"), Offset(50f, 50f))
        coordinator.drop()
        assertNull(coordinator.landing?.iconSize)
    }
}
