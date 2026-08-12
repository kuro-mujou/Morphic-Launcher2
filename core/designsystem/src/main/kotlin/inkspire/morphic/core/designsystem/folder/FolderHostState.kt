package inkspire.morphic.core.designsystem.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** How long a dragged app must dwell on a folder's merge ring before that folder opens mid-drag to take it in. */
private const val OPEN_FOLDER_DWELL_MS = 1000L

/**
 * Which collection is on screen, and whether an app is being carried into it — one value, so the states that cannot
 * coexist cannot be written down.
 *
 * **This says where the interaction *is*, never where the drag *came from*.** A single drag can visit any number of
 * collections (open one, leave it, open the next), so the phase is rewritten at every hand-off; what the drag owes to
 * the one it started in is [FolderHostState.dragSourceFolderId], which outlives all of them.
 *
 * @param Id how the host identifies one collection — a folder's `Long` row id, or the APPS category card's `String`
 *   category id. Generic because the lifecycle below has nothing to do with what a collection *is*: see
 *   [FolderHostState].
 * @property folderId the collection this phase concerns, or null in [Closed].
 */
sealed interface FolderPhase<out Id : Any> {

    val folderId: Id?

    /** No collection is on screen. During a drag this is the app "in transit" over the surface. */
    data object Closed : FolderPhase<Nothing> {
        override val folderId: Nothing? get() = null
    }

    /** [folderId]'s overlay is open and idle. */
    data class Open<out Id : Any>(override val folderId: Id) : FolderPhase<Id>

    /**
     * The phases where [app] is being brought into [folderId] and is therefore **in neither place's own data**: it is
     * off the surface (or about to be) and not yet in the collection's persisted contents. Whoever renders the
     * collection has to be handed the app explicitly for as long as this holds, or it is invisible.
     *
     * A re-entry is the harmless exception: an app carried back into the collection it came out of is still a member,
     * so the overlay already has it and the extra copy is de-duplicated rather than special-cased.
     */
    sealed interface Incoming<out Id : Any> : FolderPhase<Id> {
        val app: ComponentKey
        override val folderId: Id
    }

    /**
     * [app] is mid-drag over [folderId]: not a member yet, rendered as an extra cell so the drop can report an
     * order that includes it.
     */
    data class Injecting<out Id : Any>(override val folderId: Id, override val app: ComponentKey) : Incoming<Id>

    /**
     * [app] has been committed to [folderId], but the store hasn't caught up: the collection's contents still come
     * from the pending write, and on a surface that also dropped it optimistically it is in neither place. The app
     * would fall between the two and blink out of existence, so it stays [Incoming] until
     * [FolderHostState.onMembersChanged] sees it land.
     */
    data class Injected<out Id : Any>(override val folderId: Id, override val app: ComponentKey) : Incoming<Id>
}

/**
 * The interaction state a surface needs to **host** collections of apps opened over it: which one is on screen, and
 * what a drag in flight owes to the one it started in. Pure UI state — nothing here persists; the surface's own write
 * path commits the outcomes.
 *
 * It lives here, beside [FolderOverlay], because none of it is surface-specific: a collection is opened, reordered,
 * left, and entered the same way wherever it sits, and the overlay was already surface-agnostic. Three surfaces host
 * one today — home and the APPS pager host **folders**, and the APPS category card hosts **categories** — which is why
 * the two things that genuinely differ between them are parameters rather than assumptions:
 * - **[Id]**, how a collection is named. A folder is a `Long` row id; a category is a `String`. Nothing in this
 *   lifecycle reads the id beyond comparing it, so making it a type parameter cost nothing and stopped the card layout
 *   from needing a near-copy of the whole machine — the L1 mistake this codebase keeps un-making (`resolveDockDrop`).
 * - ***"which collection sits at the target this drop would land on?"***, a geometry question only the surface can
 *   answer ([rememberFolderHostState]): a coordinate surface compares placements, an ordered one compares slots, and
 *   the card grid compares card bounds.
 *
 * **One drag, any number of collections.** Opening and leaving are symmetric dwells of the same length and are freely
 * repeatable: hold over a merge target to open it and carry on dragging inside, hold outside its card to close it and
 * carry on dragging over the surface, then open the next one — or the same one again. Nothing about a collection is
 * latched for the rest of the drag, which is why [FolderPhase] has no "leaving" state: leaving *is*
 * [FolderPhase.Closed].
 *
 * **What is deliberately not here:** *committing* the outcomes (place the app, add it to a collection, reorder).
 * Each surface writes through its own repository, and the ops differ in kind — home pairs a `Move` with a
 * `RemoveFromFolder`, while a category `Move` re-files atomically and owes nothing — so an interface over them would
 * be shaped by whichever surface got there first.
 *
 * A second exclusion used to be listed here: the open collection's published `FolderDragDelegate`, which a surface
 * had to hold because its planner read it and had to be built *before* the coordinator, while this state is built
 * *after* it. That squeeze is gone — a [DropZone] carries its own planner and its own drop, so the overlay binds them
 * to its own zone and nothing crosses the boundary.
 *
 * Held as a class rather than loose `remember`s in the composable so the transitions have names and can be reasoned
 * about in one place, over a single [FolderPhase] rather than flags that have to be kept consistent with each other.
 *
 * TODO(naming): "folder" in these names is now one of two cases (the other is an APPS category). The honest vocabulary
 *  is a *collection of apps opened over a surface*, which is what this and [FolderOverlay] both actually model — but
 *  renaming reaches `FolderOverlay`, `FolderDragDelegate`, `folderInnerSize`, `FolderReorder` and every call site in
 *  home and `feature:apps`. Worth one mechanical commit of its own; deliberately not mixed into a behavior change.
 */
@Stable
class FolderHostState<Id : Any> {

    /**
     * Where the interaction currently is. The single source of truth; the properties below just read it.
     *
     * The type argument is spelled out rather than inferred: [FolderPhase.Closed] is a `FolderPhase<Nothing>` (there
     * is no collection for it to name), so an inferred delegate would narrow this state to that one phase.
     */
    var phase: FolderPhase<Id> by mutableStateOf<FolderPhase<Id>>(FolderPhase.Closed)
        private set

    /** The on-screen collection's id, or null when none is — true of every phase but [FolderPhase.Closed]. */
    val openFolderId: Id? get() = phase.folderId

    /**
     * The app being brought into the open collection that its own data can't render yet — mid-drag *or* just
     * committed (see [FolderPhase.Incoming]). Null otherwise.
     */
    val incomingComponent: ComponentKey? get() = (phase as? FolderPhase.Incoming<*>)?.app

    /**
     * The collection the in-flight drag **started in**, or null when it started on the surface. Fixed for the whole
     * drag, however many collections it then visits.
     *
     * **Its overlay must stay composed for as long as the drag lives**, and that is what this is for. The cell that
     * received the finger lives in that overlay's grid, and an in-flight pointer stream cannot be handed to another
     * node, so disposing it kills the gesture mid-drag. This is the collection-level form of the drag toolkit's
     * standing rule — *keep a source surface composed while a drag from it is in flight* — so a host renders this one
     * invisibly alongside whichever is being *presented*.
     *
     * **It is deliberately not the answer to "which collection is owed a removal when the drag lands".** That looks
     * like the same question and is not: it asks which collection *holds* the app, where this one asks where the
     * gesture *began*. The two agree for a drag lifted inside a collection on this surface — nothing is written until
     * the drop, so the app is still a member — and diverge the moment an app arrives from **another surface**, having
     * been lifted in the APPS drawer while already sitting in one of home's folders. Answering that from here treated
     * such an app as a stranger and placed it while leaving it in the folder. So a host resolves it from membership
     * instead: `HomeViewModel.folderHolding`, and the category card's own `categoryOf` fallback, which got there
     * first.
     *
     * Deliberately *not* a field on [FolderPhase]: it is a property of the **drag**, not of where the interaction is,
     * and the two are independent (which is why it doesn't reintroduce the flags-that-must-agree problem the phase was
     * built to remove). The phase cannot answer it either — it names whichever collection is on screen *now*, which
     * after one hand-off is no longer the one holding the pointer stream.
     *
     * Set from the phase at [onDragStart] rather than when the drag leaves, which is the distinction that makes
     * re-entry work: an app dragged *in* from the surface and then back out was never lifted inside a collection, so
     * none is pinned and none is barred from being opened again.
     */
    var dragSourceFolderId: Id? by mutableStateOf(null)
        private set

    /** Open [folderId]'s overlay (a tap — so no drag can be in flight, and nothing is discarded). */
    fun open(folderId: Id) {
        phase = FolderPhase.Open(folderId)
    }

    /**
     * Close the overlay: back, a tap on the scrim, launching an app from inside, or a drop resolving outside it.
     */
    fun close() {
        phase = FolderPhase.Closed
    }

    /**
     * The drag dwelled off the open collection's card: it closes and the drag carries on over the surface beneath.
     *
     * Identical to [close] today, and named separately anyway — this is the *gesture* half of a symmetric pair with
     * [beginInject], and reads at the call site as the counterpart of opening. Nothing is recorded because nothing
     * needs to be: what the drag still owes is [dragSourceFolderId], which was fixed when it started.
     */
    fun leaveFolder() {
        phase = FolderPhase.Closed
    }

    /** The drag dwelled on [folderId]'s merge target: open it and carry [app] in as the incoming item. */
    fun beginInject(folderId: Id, app: ComponentKey) {
        phase = FolderPhase.Injecting(folderId, app)
    }

    /**
     * The injected app has been written. The collection stays open so the user sees it land — and the app stays
     * [FolderPhase.Incoming], because the write hasn't come back yet: its contents don't include the app, so
     * releasing it here is what made it blink out. [onMembersChanged] finishes the hand-off.
     */
    fun injectCommitted() {
        val current = phase
        if (current is FolderPhase.Injecting) phase = FolderPhase.Injected(current.folderId, current.app)
    }

    /**
     * The store now reports [members] as the open collection's contents. Once a just-committed injected app is among
     * them, the collection can render it from its own data and the hand-off is over.
     */
    fun onMembersChanged(members: List<ComponentKey>) {
        val current = phase
        if (current is FolderPhase.Injected && current.app in members) phase = FolderPhase.Open(current.folderId)
    }

    /**
     * A drag began. If a collection is on screen the drag necessarily started **in** it — its scrim covers the
     * surface, so no cell underneath can receive the press — and that one is [dragSourceFolderId] for the rest of the
     * gesture.
     */
    fun onDragStart() {
        dragSourceFolderId = openFolderId
    }

    /**
     * Any drag ended.
     * - An **inject still in flight** was never committed, and the collection was opened *by that gesture*, so it
     *   goes away with it.
     * - An **inject already committed** outlives the drag: the app is landing, and it stays open to show it.
     * - A collection the user opened by tapping, or one already closed by a hand-off, is left as it is — a drag ending
     *   somewhere else says nothing about it.
     */
    fun onDragEnd() {
        dragSourceFolderId = null // scoped to one drag; no pointer stream left to preserve, so the holder can go
        phase = when (val current = phase) {
            is FolderPhase.Injecting -> FolderPhase.Closed
            is FolderPhase.Injected, is FolderPhase.Open, FolderPhase.Closed -> current
        }
    }
}

/**
 * Remembers a [FolderHostState] and hosts the two effects that drive it from the drag itself:
 * - **open-to-inject**: holding a dragged app on a collection's merge target for [OPEN_FOLDER_DWELL_MS] opens it
 *   mid-drag, so the app can be dropped straight in (the inverse of leaving, which the overlay detects on the same
 *   dwell). The dwell is keyed on the *target*, so drifting off it and back restarts the timer.
 * - **drag boundaries**: the collection the drag started in is captured on lift ([FolderHostState.onDragStart]) and
 *   every hand-off is cleared on release ([FolderHostState.onDragEnd]).
 *
 * The only collection this refuses to open is the one already on screen — that drag is its own business (a reorder, or
 * an app being carried in). Every other one is a valid target, **including one this drag has already visited and
 * left**, which is what makes open→leave→open repeatable within a single gesture. Re-entry is safe because leaving
 * genuinely closes rather than latching, and because membership is decided at the drop, not on the way.
 *
 * @param coordinator the shared drag coordinator the surface and its collections both run on.
 * @param folderIdAt resolves a merge plan **in a given zone** to the collection it targets, or null when the target
 *   isn't one. This is the surface's own geometry question — a coordinate surface compares placements, an ordered one
 *   compares slots, the category card compares card bounds — and the only thing here that isn't surface-independent.
 *
 *   The zone is passed because a plan alone does not identify a target: a surface may register **several** drop
 *   zones on one coordinator (home is a pager *and* a dock), and each has its own coordinate space — so the same
 *   footprint value names a different cell in each, and matching on the plan alone would resolve a collection in the
 *   wrong zone.
 */
@Composable
fun <Id : Any> rememberFolderHostState(
    coordinator: DragCoordinator,
    folderIdAt: (ZoneId, PlacementPlan) -> Id?,
): FolderHostState<Id> {
    val host = remember { FolderHostState<Id>() }

    // The collection currently being hovered for a merge, if any. Null (which cancels the dwell below) when the drag
    // isn't over a merge target, or when one is already open.
    val mergeTargetId: Id? = run {
        val session = coordinator.session ?: return@run null
        val plan = session.plan?.takeIf { it.intent == DropIntent.MERGE } ?: return@run null
        // A plan only exists for a zone the finger is over, so this is non-null in practice; treat it as required
        // rather than guessing a zone, since guessing wrong resolves the wrong target.
        val zone = session.activeZone ?: return@run null
        // While a collection is on screen the drag is its business — a reorder inside it, or an app being carried into
        // it, must not also be opening something else. Leaving it (the outer dwell) is what makes the drag the
        // surface's again, and from that moment *every* collection is a target once more, this one included.
        if (host.openFolderId != null) return@run null
        folderIdAt(zone, plan)
    }
    LaunchedEffect(mergeTargetId) {
        val folderId = mergeTargetId ?: return@LaunchedEffect
        delay(OPEN_FOLDER_DWELL_MS.milliseconds)
        val app = (coordinator.session?.item as? GridItem.App)?.component ?: return@LaunchedEffect
        host.beginInject(folderId, app)
    }

    LaunchedEffect(coordinator.isDragging) {
        if (coordinator.isDragging) host.onDragStart() else host.onDragEnd()
    }

    return host
}
