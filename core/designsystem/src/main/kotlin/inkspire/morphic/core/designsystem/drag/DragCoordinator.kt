package inkspire.morphic.core.designsystem.drag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan

/**
 * A drag in progress — an immutable snapshot replaced on every finger move, so any reader sees a consistent
 * view of "what is being dragged, where, and what a drop would do right now".
 *
 * @property item what is being dragged (identity, for accept-checks and the eventual commit).
 * @property fingerInRoot the finger position in root/window coordinates.
 * @property activeZone the zone currently under the finger that accepts [item], or null (over a gap/edge).
 * @property plan what dropping now would do in [activeZone]; null when there is no zone or no droppable target.
 */
data class DragSession(
    val item: GridItem,
    val fingerInRoot: Offset,
    val activeZone: ZoneId?,
    val plan: PlacementPlan?,
)

/**
 * The committable result of a successful drop: [item] lands in [zone] as described by [plan]. Handed to the
 * feature layer, which translates it into repository changes. A no-op drop yields null instead (see
 * [DragCoordinator.drop]).
 */
data class DropOutcome(
    val zone: ZoneId,
    val item: GridItem,
    val plan: PlacementPlan,
)

/**
 * The single, root-owned owner of a drag from lift to drop. Surfaces register themselves as [DropZone]s via
 * [registerZone]; the gesture layer (built next) drives [start] / [moveTo] / [drop] / [cancel].
 *
 * Because **one** coordinator sits above every surface, holds the whole drag, and hit-tests **all** zones in
 * one coordinate space, cross-surface drops and dragging out of a folder need no special handoff — they are
 * the same hit-test. This is the structural replacement for L1's four parallel gesture recognizers and the
 * `HomeDragBridge` re-tracking hack they forced (docs/DRAG_AND_DROP_DESIGN.md §2, §4).
 *
 * Not thread-safe; drive it from the main thread (the gesture pipeline).
 *
 * @param planner injected placement logic — a fake in tests, the engine-backed planner in `feature:home`.
 */
@Stable
class DragCoordinator(private val planner: DropPlanner) {

    private val zones: SnapshotStateMap<ZoneId, DropZone> = mutableStateMapOf()

    /** The current drag, or null when idle. Observable — reading it in composition recomposes on each move. */
    var session: DragSession? by mutableStateOf(null)
        private set

    val isDragging: Boolean get() = session != null

    /** Adds or replaces a zone; a surface calls this as it measures or moves. [DropZone.id] is the key. */
    fun registerZone(zone: DropZone) {
        zones[zone.id] = zone
    }

    /** Removes a zone whose surface left composition. A no-op when the id isn't registered. */
    fun unregisterZone(id: ZoneId) {
        zones.remove(id)
    }

    /** Begins dragging [item] with the finger at [fingerInRoot] (root coordinates), resolving the first plan. */
    fun start(item: GridItem, fingerInRoot: Offset) {
        session = DragSession(item, fingerInRoot, activeZone = null, plan = null)
        moveTo(fingerInRoot)
    }

    /** Updates the finger position, re-resolving the active zone and its plan. Ignored when not dragging. */
    fun moveTo(fingerInRoot: Offset) {
        val current = session ?: return
        val zone = activeZoneAt(fingerInRoot, current.item)
        val plan = zone?.let { planner.plan(it, current.item, fingerInRoot) }
        session = current.copy(fingerInRoot = fingerInRoot, activeZone = zone?.id, plan = plan)
    }

    /**
     * Ends the drag, clearing [session]. Returns the [DropOutcome] to commit, or null for a no-op drop:
     * released over no zone, over no droppable target, or over an [DropIntent.INVALID] one.
     */
    fun drop(): DropOutcome? {
        val current = session ?: return null
        session = null
        val zoneId = current.activeZone ?: return null
        val plan = current.plan ?: return null
        if (plan.intent == DropIntent.INVALID) return null
        return DropOutcome(zoneId, current.item, plan)
    }

    /** Abandons the drag with no drop (e.g. the gesture was cancelled). */
    fun cancel() {
        session = null
    }

    /** The topmost zone whose bounds contain [finger] and that accepts [item]; null if none qualifies. */
    private fun activeZoneAt(finger: Offset, item: GridItem): DropZone? =
        zones.values
            .filter { it.bounds.contains(finger) && it.accepts(item) }
            .maxByOrNull { it.z }
}

/** Provides the app-wide [DragCoordinator] below the drag root; null in scopes where dragging isn't set up. */
val LocalDragCoordinator = staticCompositionLocalOf<DragCoordinator?> { null }

/** Remembers a [DragCoordinator] bound to [planner]. Host once at the drag root and expose via the local. */
@Composable
fun rememberDragCoordinator(planner: DropPlanner): DragCoordinator =
    remember(planner) { DragCoordinator(planner) }
