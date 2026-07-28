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
 * about in one place: they are mutually exclusive in ways scattered flags don't express (one finger cannot be
 * extracting *and* injecting), and the surface's drop handler has to interrogate them in a specific order.
 *
 * The open folder's published [FolderDragDelegate] is deliberately *not* held here, even though it belongs to the
 * same concern: the surface's `DropPlanner` reads it, and that planner has to be constructed *before* the
 * [DragCoordinator] it is given to — while this state is created *after* the coordinator, since its effects observe
 * the drag. So the delegate's hand-off box stays with the surface, which is the only place that can hold something
 * both sides of that construction order can see.
 */
@Stable
class FolderHostState {

    /** The open folder's id, or null when none is open. */
    var openFolderId: Long? by mutableStateOf(null)
        private set

    /**
     * An app being dragged *out* of a folder (`folderId` → the app), set once the drag has dwelled off the folder's
     * inner zone and been handed to the surface. The drop commits it; until then the app is still in the folder.
     */
    var extractingFrom: Pair<Long, ComponentKey>? by mutableStateOf(null)
        private set

    /**
     * An app being dragged *into* the open folder from the surface. It isn't a member yet — the folder renders it
     * as an extra cell so the drop can report an order that includes it.
     */
    var incomingComponent: ComponentKey? by mutableStateOf(null)
        private set

    /** Open [folderId]'s overlay (a tap on its cell). */
    fun open(folderId: Long) {
        openFolderId = folderId
    }

    /** Close the overlay (back, a tap on the scrim, or launching an app from inside). */
    fun close() {
        openFolderId = null
    }

    /** The drag dwelled off the folder's inner zone: [app] is leaving [folderId], and the surface now owns the drag. */
    fun beginExtract(folderId: Long, app: ComponentKey) {
        extractingFrom = folderId to app
    }

    /** The extract drag was released — stop tracking it and close the folder, whether or not the app found a home. */
    fun endExtract() {
        extractingFrom = null
        openFolderId = null
    }

    /** The drag dwelled on [folderId]'s merge ring: open it and carry [app] in as the incoming item. */
    fun beginInject(folderId: Long, app: ComponentKey) {
        incomingComponent = app
        openFolderId = folderId
    }

    /** The injected app has been committed to the folder. The folder stays open so the user sees it land. */
    fun injectCommitted() {
        incomingComponent = null
    }

    /**
     * Any drag ended: drop the transient hand-off state. A folder that was opened *to inject* closes with the drag
     * (it was opened by the gesture, so it goes away with it); one the user opened by tapping stays open.
     */
    fun onDragEnd() {
        extractingFrom = null
        if (incomingComponent != null) {
            incomingComponent = null
            openFolderId = null
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
