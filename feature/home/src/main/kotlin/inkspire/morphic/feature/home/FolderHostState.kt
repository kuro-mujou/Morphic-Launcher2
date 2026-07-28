package inkspire.morphic.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderDragDelegate
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan
import kotlinx.coroutines.delay

/** How long a dragged app must dwell on a folder's merge ring before that folder opens mid-drag to take it in. */
private const val OPEN_FOLDER_DWELL_MS = 500L

/**
 * Where a surface's folder interaction currently *is* — one value, so the states that cannot coexist cannot be
 * written down. There is one finger, so an app can be on its way out of a folder or on its way in, never both; and
 * neither is possible with no folder open. As three independent nullable flags those combinations were all
 * expressible and had to be held apart by convention.
 *
 * @property folderId the folder this phase concerns, or null in [Closed].
 */
sealed interface FolderPhase {

    val folderId: Long?

    /** No folder is open. */
    data object Closed : FolderPhase {
        override val folderId: Long? get() = null
    }

    /** [folderId]'s overlay is open and idle. */
    data class Open(override val folderId: Long) : FolderPhase

    /**
     * [app] is being dragged *out* of [folderId]: the drag has dwelled off the folder's inner zone and been handed
     * to the surface, which will commit it on drop. Until then the app is still a member — nothing has been removed.
     */
    data class Extracting(override val folderId: Long, val app: ComponentKey) : FolderPhase

    /**
     * The phases where [app] is being brought into [folderId] from the surface and is therefore **in neither place's
     * own data**: it is off the surface (or about to be) and not yet in the folder's persisted contents. Whoever
     * renders the folder has to be handed the app explicitly for as long as this holds, or it is invisible.
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
 * The folder-interaction state a surface needs to **host** folders: which folder is open, and the two mid-drag
 * hand-offs between the surface and the open folder (an app on its way *out* of a folder, and one on its way
 * *in*). Pure UI state — nothing here persists; the surface's own write path commits the outcomes.
 *
 * Extracted from `HomeScreen` because none of it is actually home-specific: a folder is opened, reordered,
 * extracted from, and injected into the same way wherever it sits, and [FolderOverlay][inkspire.morphic.core.designsystem.folder.FolderOverlay]
 * is already surface-agnostic. Home is simply the first surface to host folders; the APPS pager and category card
 * are the next two, which is why the one genuinely surface-specific question — *"which folder sits at the target
 * this drop would land on?"* — is a lambda ([rememberFolderHostState]) rather than a placement comparison baked in
 * here. (It stays in `feature:home` until a second consumer exists to shape the seam.)
 *
 * Held as a class rather than loose `remember`s in the composable so the transitions have names and can be reasoned
 * about in one place, over a single [FolderPhase] rather than flags that have to be kept consistent with each other.
 *
 * The open folder's published [FolderDragDelegate] is deliberately *not* held here, even though it belongs to the
 * same concern: the surface's `DropPlanner` reads it, and that planner has to be constructed *before* the
 * [DragCoordinator] it is given to — while this state is created *after* the coordinator, since its effects observe
 * the drag. So the delegate's hand-off box stays with the surface, which is the only place that can hold something
 * both sides of that construction order can see.
 */
@Stable
class FolderHostState {

    /** Where the interaction currently is. The single source of truth; the properties below just read it. */
    var phase: FolderPhase by mutableStateOf(FolderPhase.Closed)
        private set

    /** The open folder's id, or null when none is open — true of every phase but [FolderPhase.Closed]. */
    val openFolderId: Long? get() = phase.folderId

    /**
     * The app being brought into the open folder that the folder's own data can't render yet — mid-drag *or* just
     * committed (see [FolderPhase.Incoming]). Null otherwise.
     */
    val incomingComponent: ComponentKey? get() = (phase as? FolderPhase.Incoming)?.app

    /** Open [folderId]'s overlay (a tap on its cell — so no drag can be in flight, and nothing is discarded). */
    fun open(folderId: Long) {
        phase = FolderPhase.Open(folderId)
    }

    /**
     * Close the overlay: back, a tap on the scrim, launching an app from inside, or an extract drag resolving (the
     * app has landed on the surface, or failed to and stayed in the folder — either way the folder is done).
     */
    fun close() {
        phase = FolderPhase.Closed
    }

    /** The drag dwelled off the folder's inner zone: [app] is leaving [folderId], and the surface now owns the drag. */
    fun beginExtract(folderId: Long, app: ComponentKey) {
        phase = FolderPhase.Extracting(folderId, app)
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
     * Any drag ended. The hand-offs resolve differently, which is the whole reason this is one value and not
     * several flags:
     * - an **extract** that reaches here was *cancelled* (a committed one goes through [close] on drop), and since
     *   nothing was removed, the app is still in the folder — so the folder stays open around it;
     * - an **inject still in flight** was never committed, and the folder was opened *by that gesture*, so it goes
     *   away with it;
     * - an **inject already committed** outlives the drag: the app is landing, and the folder stays open to show it.
     *
     * A folder the user opened by tapping is untouched by a drag ending elsewhere.
     */
    fun onDragEnd() {
        phase = when (val current = phase) {
            is FolderPhase.Extracting -> FolderPhase.Open(current.folderId)
            is FolderPhase.Injecting -> FolderPhase.Closed
            is FolderPhase.Injected, is FolderPhase.Open, FolderPhase.Closed -> current
        }
    }
}

/**
 * Remembers a [FolderHostState] and hosts the two effects that drive it from the drag itself:
 * - **open-to-inject**: holding a dragged app on a folder's merge ring for [OPEN_FOLDER_DWELL_MS] opens that folder
 *   mid-drag, so the app can be dropped straight into it (the inverse of extract, which the overlay detects). The
 *   dwell is keyed on the *target* folder, so drifting off it and back restarts the timer.
 * - **drag-end cleanup**: releasing anywhere clears the hand-off state via [FolderHostState.onDragEnd].
 *
 * @param coordinator the shared drag coordinator the surface and its folders both run on.
 * @param folderIdAt resolves a merge plan to the folder it targets, or null when the target isn't a folder. This is
 *   the surface's own geometry question — a coordinate surface compares placements, an ordered one compares slots —
 *   and the only thing here that isn't surface-independent.
 */
@Composable
fun rememberFolderHostState(
    coordinator: DragCoordinator,
    folderIdAt: (PlacementPlan) -> Long?,
): FolderHostState {
    val host = remember { FolderHostState() }

    // The folder currently being hovered for a merge, if any. Null (which cancels the dwell below) when the drag
    // isn't over a merge target, or when a folder is already open — that drag is the folder's own business.
    val mergeTargetId: Long? = run {
        val plan = coordinator.session?.plan?.takeIf { it.intent == DropIntent.MERGE } ?: return@run null
        if (host.openFolderId != null) return@run null
        folderIdAt(plan)
    }
    LaunchedEffect(mergeTargetId) {
        val folderId = mergeTargetId ?: return@LaunchedEffect
        delay(OPEN_FOLDER_DWELL_MS)
        val app = (coordinator.session?.item as? GridItem.App)?.component ?: return@LaunchedEffect
        host.beginInject(folderId, app)
    }

    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) host.onDragEnd()
    }

    return host
}
