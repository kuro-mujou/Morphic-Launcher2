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
 * The committable result of a successful drop: [item] lands in [zone] as described by [plan]. Handed to that
 * zone's own [DropZone.onDrop], which translates it into repository changes. A no-op drop yields null instead
 * (see [DragCoordinator.drop]).
 */
data class DropOutcome(
    val zone: ZoneId,
    val item: GridItem,
    val plan: PlacementPlan,
)

/**
 * The single, root-owned owner of a drag from lift to drop. Surfaces register themselves as [DropZone]s via
 * [RegisterDropZone]; the gesture layer drives [start] / [moveTo] / [drop] / [cancel].
 *
 * Because **one** coordinator sits above every surface, holds the whole drag, and hit-tests **all** zones in
 * one coordinate space, cross-surface drops and dragging out of a folder need no special handoff — they are
 * the same hit-test. This is the structural replacement for L1's four parallel gesture recognizers and the
 * `HomeDragBridge` re-tracking hack they forced (docs/DRAG_AND_DROP_DESIGN.md §2, §4).
 *
 * **Hosted once, by `feature:shell`.** Each surface used to remember its own, which was enough only while no drag
 * could cross a surface boundary. It can now — an app is lifted in the APPS drawer and lands on home — and two
 * coordinators cannot both own one gesture. So the shell provides this through [LocalDragCoordinator] and every
 * surface reads it; the dev playgrounds provide one of their own, since they stand in for the shell.
 *
 * It carries **no planner of its own** for the same reason: with every surface's zones in one registry, a single
 * planner would have to know about all of them. Each zone answers for itself (see [DropZone]).
 *
 * Not thread-safe; drive it from the main thread (the gesture pipeline).
 */
@Stable
class DragCoordinator {

    private val zones: SnapshotStateMap<ZoneId, DropZone> = mutableStateMapOf()

    /** The current drag, or null when idle. Observable — reading it in composition recomposes on each move. */
    var session: DragSession? by mutableStateOf(null)
        private set

    val isDragging: Boolean get() = session != null

    /** Adds or replaces a zone; [RegisterDropZone] calls this as a surface measures, moves, or comes on screen. */
    fun registerZone(zone: DropZone) {
        zones[zone.id] = zone
    }

    /** Removes a zone whose surface left the screen. A no-op when the id isn't registered. */
    fun unregisterZone(id: ZoneId) {
        zones.remove(id)
    }

    /**
     * Removes [zone] **only if it is still the one registered under its id** — the teardown [RegisterDropZone] uses.
     *
     * The identity check matters because an id can legitimately change hands. Two folder overlays share
     * `ZoneId("folder")`: when a drag opens a second folder, the first becomes an invisible pointer holder and hands
     * the zone over in the same composition. Compose runs disposals before side effects, so the hand-off happens to
     * come out in the right order today — but "happens to" is not an invariant, and getting it wrong pulls the drop
     * zone out from under the folder the user is actually looking at, which is a bug this codebase has already had
     * once. Comparing the instance makes the order irrelevant.
     */
    fun unregisterZone(zone: DropZone) {
        if (zones[zone.id] === zone) zones.remove(zone.id)
    }

    /** Begins dragging [item] with the finger at [fingerInRoot] (root coordinates), resolving the first plan. */
    fun start(item: GridItem, fingerInRoot: Offset) {
        session = DragSession(item, fingerInRoot, activeZone = null, plan = null)
        moveTo(fingerInRoot)
    }

    /** Updates the finger position, re-resolving the active zone and asking *that zone* what a drop would do. */
    fun moveTo(fingerInRoot: Offset) {
        val current = session ?: return
        val zone = activeZoneAt(fingerInRoot, current.item)
        val plan = zone?.planner?.plan(current.item, fingerInRoot)
        session = current.copy(fingerInRoot = fingerInRoot, activeZone = zone?.id, plan = plan)
    }

    /**
     * Ends the drag, clearing [session] and **committing the landing through the zone that owns it**
     * ([DropZone.onDrop]) before returning it. Returns null for a no-op drop: released over no zone, over no
     * droppable target, or over an [DropIntent.INVALID] one — in which case nothing is committed.
     *
     * The commit is dispatched here rather than by the caller because the caller is whichever *cell* released the
     * finger, and that cell may belong to an entirely different surface from the one being dropped into. The return
     * value is still what the releasing surface reads for its own bookkeeping (an open folder closing behind the
     * drag, say), which is a question about where the drag *left*, not where it landed.
     */
    fun drop(): DropOutcome? {
        val current = session ?: return null
        session = null
        val zoneId = current.activeZone ?: return null
        val zone = zones[zoneId] ?: return null
        val plan = current.plan ?: return null
        if (plan.intent == DropIntent.INVALID) return null
        val outcome = DropOutcome(zoneId, current.item, plan)
        zone.onDrop(outcome)
        return outcome
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

/**
 * Provides the app-wide [DragCoordinator] below the drag root; null in scopes where dragging isn't set up.
 *
 * Every launcher surface reads it rather than making one, which is what makes a drag able to leave the surface it
 * started on. [requireDragCoordinator] is the read to use — the null is for previews and for non-launcher scopes
 * (settings), not for a surface to quietly opt out of.
 */
val LocalDragCoordinator = staticCompositionLocalOf<DragCoordinator?> { null }

/**
 * The ambient [DragCoordinator], or an error naming the missing provider.
 *
 * Deliberately not a silent fallback to a locally remembered one: a surface with a private coordinator looks
 * completely normal until a drag tries to leave it, and then simply stops at the boundary with nothing to debug.
 */
@Composable
fun requireDragCoordinator(): DragCoordinator = checkNotNull(LocalDragCoordinator.current) {
    "No DragCoordinator provided — a launcher surface must sit below LauncherShell (or a harness that provides one)."
}

/** Remembers a [DragCoordinator]. Host once at the drag root (`feature:shell`) and expose via the local above. */
@Composable
fun rememberDragCoordinator(): DragCoordinator = remember { DragCoordinator() }
