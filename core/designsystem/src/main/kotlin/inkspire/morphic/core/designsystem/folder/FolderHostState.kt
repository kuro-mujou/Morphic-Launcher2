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
 * Which folder is on screen, and whether an app is being carried into it — one value, so the states that cannot
 * coexist cannot be written down.
 *
 * **This says where the interaction *is*, never where the drag *came from*.** A single drag can visit any number of
 * folders (open one, leave it, open the next), so the phase is rewritten at every hand-off; what the drag owes to the
 * folder it started in is [FolderHostState.dragSourceFolderId], which outlives all of them.
 *
 * @property folderId the folder this phase concerns, or null in [Closed].
 */
sealed interface FolderPhase {

    val folderId: Long?

    /** No folder is on screen. During a drag this is the app "in transit" over the surface. */
    data object Closed : FolderPhase {
        override val folderId: Long? get() = null
    }

    /** [folderId]'s overlay is open and idle. */
    data class Open(override val folderId: Long) : FolderPhase

    /**
     * The phases where [app] is being brought into [folderId] and is therefore **in neither place's own data**: it is
     * off the surface (or about to be) and not yet in the folder's persisted contents. Whoever renders the folder has
     * to be handed the app explicitly for as long as this holds, or it is invisible.
     *
     * A re-entry is the harmless exception: an app carried back into the folder it came out of is still a member, so
     * the overlay already has it and the extra copy is de-duplicated rather than special-cased.
     */
    sealed interface Incoming : FolderPhase {
        val app: ComponentKey
        override val folderId: Long
    }

    /**
     * [app] is mid-drag over [folderId]: not a member yet, rendered as an extra cell so the drop can report an
     * order that includes it.
     */
    data class Injecting(override val folderId: Long, override val app: ComponentKey) : Incoming

    /**
     * [app] has been committed to [folderId], but the store hasn't caught up: the surface already dropped it
     * optimistically, while the folder's contents still come from the pending write. The app would fall between the
     * two and blink out of existence, so it stays [Incoming] until [FolderHostState.onMembersChanged] sees it land.
     */
    data class Injected(override val folderId: Long, override val app: ComponentKey) : Incoming
}

/**
 * The folder-interaction state a surface needs to **host** folders: which folder is on screen, and what a drag in
 * flight owes to the folder it started in. Pure UI state — nothing here persists; the surface's own write path
 * commits the outcomes.
 *
 * It lives here, beside [FolderOverlay], because none of it is home-specific: a folder is opened, reordered, left,
 * and entered the same way wherever it sits, and the overlay was already surface-agnostic. Home is simply the first
 * surface to host folders — the APPS pager and category card are next — so the one genuinely surface-specific
 * question, *"which folder sits at the target this drop would land on?"*, is a lambda ([rememberFolderHostState]): a
 * coordinate surface compares placements, an ordered one compares slots.
 *
 * **One drag, any number of folders.** Opening and leaving are symmetric dwells of the same length and are freely
 * repeatable: hold over a folder's merge ring to open it and carry on dragging inside, hold outside its card to close
 * it and carry on dragging over the surface, then open the next one — or the same one again. Nothing about a folder
 * is latched for the rest of the drag, which is why [FolderPhase] has no "leaving" state: leaving *is* [Closed].
 *
 * **What is deliberately not here.** Two things a surface keeps for itself:
 * - *Committing* the outcomes (place the app, add it to a folder, reorder). Each surface writes through its own
 *   repository, and there is no shared caller to dispatch through, so an interface over them would have one
 *   implementor and no user. It can be introduced when APPS gives it a second one to be shaped by.
 * - The open folder's published [FolderDragDelegate]. It belongs to this concern, but the surface's `DropPlanner`
 *   reads it and must be constructed *before* the [DragCoordinator] it is given to — while this state is created
 *   *after* the coordinator, since its effects observe the drag. The surface is the only place that can hold
 *   something both sides of that construction order can see.
 *
 * Held as a class rather than loose `remember`s in the composable so the transitions have names and can be reasoned
 * about in one place, over a single [FolderPhase] rather than flags that have to be kept consistent with each other.
 */
@Stable
class FolderHostState {

    /** Where the interaction currently is. The single source of truth; the properties below just read it. */
    var phase: FolderPhase by mutableStateOf(FolderPhase.Closed)
        private set

    /** The on-screen folder's id, or null when none is — true of every phase but [FolderPhase.Closed]. */
    val openFolderId: Long? get() = phase.folderId

    /**
     * The app being brought into the open folder that the folder's own data can't render yet — mid-drag *or* just
     * committed (see [FolderPhase.Incoming]). Null otherwise.
     */
    val incomingComponent: ComponentKey? get() = (phase as? FolderPhase.Incoming)?.app

    /**
     * The folder the in-flight drag **started in**, or null when it started on the surface. Fixed for the whole drag,
     * however many folders it then visits.
     *
     * Two separate things need it, and they are the same folder for the same reason — the drag *began* on one of its
     * cells:
     * - **Its overlay must stay composed for as long as the drag lives.** That cell received the finger, and an
     *   in-flight pointer stream cannot be handed to another node, so disposing it kills the gesture mid-drag. This is
     *   the folder-level form of the drag toolkit's standing rule — *keep a source surface composed while a drag from
     *   it is in flight* — so a host renders this folder invisibly alongside whichever folder is being *presented*.
     * - **It is owed a removal when the drag lands.** The app is a member here and nowhere else, so wherever it comes
     *   to rest — an empty cell, another folder, a new folder made by merging — it has to leave this one.
     *
     * Deliberately *not* a field on [FolderPhase]: it is a property of the **drag**, not of where the interaction is,
     * and the two are independent (which is why it doesn't reintroduce the flags-that-must-agree problem the phase was
     * built to remove). The phase cannot answer it either — it names whichever folder is on screen *now*, which after
     * one hand-off is no longer the one still owed a removal.
     *
     * Set from the phase at [onDragStart] rather than when the drag leaves the folder, which is the distinction that
     * makes re-entry work: an app dragged *in* from the surface and then back out is owed to nobody, so its folder is
     * neither pinned nor barred from being opened again.
     */
    var dragSourceFolderId: Long? by mutableStateOf(null)
        private set

    /** Open [folderId]'s overlay (a tap on its cell — so no drag can be in flight, and nothing is discarded). */
    fun open(folderId: Long) {
        phase = FolderPhase.Open(folderId)
    }

    /**
     * Close the overlay: back, a tap on the scrim, launching an app from inside, or a drop resolving outside the
     * folder.
     */
    fun close() {
        phase = FolderPhase.Closed
    }

    /**
     * The drag dwelled off the open folder's card: it closes and the drag carries on over the surface beneath.
     *
     * Identical to [close] today, and named separately anyway — this is the *gesture* half of a symmetric pair with
     * [beginInject], and reads at the call site as the counterpart of opening. Nothing is recorded because nothing
     * needs to be: what the drag still owes is [dragSourceFolderId], which was fixed when it started.
     */
    fun leaveFolder() {
        phase = FolderPhase.Closed
    }

    /** The drag dwelled on [folderId]'s merge ring: open it and carry [app] in as the incoming item. */
    fun beginInject(folderId: Long, app: ComponentKey) {
        phase = FolderPhase.Injecting(folderId, app)
    }

    /**
     * The injected app has been written. The folder stays open so the user sees it land — and the app stays
     * [FolderPhase.Incoming], because the write hasn't come back yet: the surface has already dropped it
     * optimistically and the folder's contents don't include it, so releasing it here is what made it blink out.
     * [onMembersChanged] finishes the hand-off.
     */
    fun injectCommitted() {
        val injecting = phase as? FolderPhase.Injecting ?: return
        phase = FolderPhase.Injected(injecting.folderId, injecting.app)
    }

    /**
     * The store now reports [members] as the open folder's contents. Once a just-committed injected app is among
     * them, the folder can render it from its own data and the hand-off is over.
     */
    fun onMembersChanged(members: List<ComponentKey>) {
        val injected = phase as? FolderPhase.Injected ?: return
        if (injected.app in members) phase = FolderPhase.Open(injected.folderId)
    }

    /**
     * A drag began. If a folder is on screen the drag necessarily started **in** it — its scrim covers the surface, so
     * no cell underneath can receive the press — and that folder is [dragSourceFolderId] for the rest of the gesture.
     */
    fun onDragStart() {
        dragSourceFolderId = openFolderId
    }

    /**
     * Any drag ended.
     * - An **inject still in flight** was never committed, and the folder was opened *by that gesture*, so it goes
     *   away with it.
     * - An **inject already committed** outlives the drag: the app is landing, and the folder stays open to show it.
     * - A folder the user opened by tapping, or one already closed by a hand-off, is left as it is — a drag ending
     *   somewhere else says nothing about it.
     */
    fun onDragEnd() {
        dragSourceFolderId = null // scoped to one drag; the pointer holder is free to go and nothing is owed
        phase = when (val current = phase) {
            is FolderPhase.Injecting -> FolderPhase.Closed
            is FolderPhase.Injected, is FolderPhase.Open, FolderPhase.Closed -> current
        }
    }
}

/**
 * Remembers a [FolderHostState] and hosts the two effects that drive it from the drag itself:
 * - **open-to-inject**: holding a dragged app on a folder's merge ring for [OPEN_FOLDER_DWELL_MS] opens that folder
 *   mid-drag, so the app can be dropped straight into it (the inverse of leaving, which the overlay detects on the
 *   same dwell). The dwell is keyed on the *target* folder, so drifting off it and back restarts the timer.
 * - **drag boundaries**: the folder the drag started in is captured on lift ([FolderHostState.onDragStart]) and every
 *   hand-off is cleared on release ([FolderHostState.onDragEnd]).
 *
 * The only folder this refuses to open is the one already on screen — that drag is its own business (a reorder, or an
 * app being carried in). Every other folder is a valid target, **including one this drag has already visited and left**,
 * which is what makes open→leave→open repeatable within a single gesture. Re-entry is safe because leaving genuinely
 * closes a folder rather than latching it, and because the app's membership is decided at the drop, not on the way.
 *
 * @param coordinator the shared drag coordinator the surface and its folders both run on.
 * @param folderIdAt resolves a merge plan **in a given zone** to the folder it targets, or null when the target
 *   isn't a folder. This is the surface's own geometry question — a coordinate surface compares placements, an
 *   ordered one compares slots — and the only thing here that isn't surface-independent.
 *
 *   The zone is passed because a plan alone does not identify a target: a surface may register **several** drop
 *   zones on one coordinator (home is a pager *and* a dock), and each has its own coordinate space — so the same
 *   footprint value names a different cell in each, and matching on the plan alone would resolve a folder in the
 *   wrong zone.
 */
@Composable
fun rememberFolderHostState(
    coordinator: DragCoordinator,
    folderIdAt: (ZoneId, PlacementPlan) -> Long?,
): FolderHostState {
    val host = remember { FolderHostState() }

    // The folder currently being hovered for a merge, if any. Null (which cancels the dwell below) when the drag
    // isn't over a merge target, or when a folder is already open.
    val mergeTargetId: Long? = run {
        val session = coordinator.session ?: return@run null
        val plan = session.plan?.takeIf { it.intent == DropIntent.MERGE } ?: return@run null
        // A plan only exists for a zone the finger is over, so this is non-null in practice; treat it as required
        // rather than guessing a zone, since guessing wrong resolves the wrong folder.
        val zone = session.activeZone ?: return@run null
        // While a folder is on screen the drag is its business — a reorder inside it, or an app being carried into
        // it, must not also be opening something else. Leaving it (the outer dwell) is what makes the drag the
        // surface's again, and from that moment *every* folder is a target once more, this one included.
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
