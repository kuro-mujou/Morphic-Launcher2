package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderDragDelegate
import inkspire.morphic.core.designsystem.folder.FolderOverlay
import inkspire.morphic.core.designsystem.folder.FolderPhase
import inkspire.morphic.core.designsystem.folder.rememberFolderHostState
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.ordered.cellFractionX
import inkspire.morphic.core.designsystem.ordered.Third
import inkspire.morphic.core.designsystem.ordered.movingGap
import inkspire.morphic.core.designsystem.ordered.thirdInCell
import inkspire.morphic.core.designsystem.pager.EdgeFlipEffect
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.model.AppsPagerGrid
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.feature.apps.AppsItem
import inkspire.morphic.feature.apps.asIconItem
import inkspire.morphic.feature.apps.gridItem
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** This surface's drop zone — the pager viewport, exactly as home's paged main area registers one. */
private val PagerZoneId = ZoneId("apps-pager")

/**
 * The plan this surface reports for every hover it accepts: droppable, painting nothing.
 *
 * Like a folder's, and for the same reason — an ordered surface previews a drop by reflowing its own cells around
 * the migrating gap, so there is no target cell to shadow. [DropIntent.REORDER] says exactly that, which is why
 * the footprint below goes unread.
 */
private val PagerReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)

/**
 * The pager's own icon proportion.
 *
 * Denser than home's 0.88 for the same reason the vertical grid's is — a page packs four to eight columns where a
 * home cell is a 2×2 slot around one icon — and a placeholder in the same sense: the value is a starting point,
 * the *mechanism* (a per-surface [IconMetrics] through [LocalIconMetrics]) is the answer.
 */
private val PagerIconMetrics = IconMetrics(iconPercent = 0.75f)

/**
 * The **pager** layout of the APPS surface ([inkspire.morphic.core.model.AppsLayout.PAGER]): the app collection
 * across swipeable pages, in an order the **user** owns and rearranges by dragging.
 *
 * **The first APPS layout that stores anything.** The list and grid are derived — recomputed A–Z from the app
 * cache — so they take a flat list and own nothing. This one takes [pages] already arranged, because the
 * arrangement is data (`apps_pager_item`, via `AppsOrderRepository`).
 *
 * **Fixed pages, not a scrolling grid.** Each page is a [LauncherGrid] in FIXED_PAGER mode at [AppsPagerGrid]'s
 * size, so a page holds exactly `rows × cols` and a page boundary is a real boundary — which is what the store's
 * page + slot means, and why this uses `LauncherGrid` where the vertical grid deliberately does not.
 *
 * **Dragging is MovingGap, not push.** A coordinate surface shoves occupants aside; an ordered one migrates a gap
 * through the list and lets the flow densify (see `core:designsystem/ordered`). Three zones per cell, because this
 * surface holds folders: the outer thirds insert the gap before or after the hovered entry, and the centre third is
 * a merge ring — drop to fold the two together, or dwell to open the target and place the app at a chosen slot.
 * (The category pager, which holds no folders, reads halves instead: with nothing to merge into, a centre third
 * would be dead space where the user's aim does nothing.)
 *
 * **Folders behave exactly as they do on home**, because the lifecycle is the same `FolderHostState`: tap to open,
 * dwell on a ring to enter mid-drag, dwell outside the card to leave, drag an app back out onto any page or
 * straight into another folder, and auto-dissolve when the second-last app leaves. The one surface-specific answer
 * this file supplies is *"which folder does this merge plan target?"* — a **slot** match here, where home compares
 * placements.
 *
 * **Crossing pages** works the way home's does: one drop zone is the whole viewport, page-swipe is gated off while
 * an item is in flight so the two gestures never fight, holding near an edge flips a page on a dwell, and
 * `keepAllPagesPlaced` keeps the source page composed so the lifted cell keeps its pointer stream across the flip.
 *
 * @param pages the arrangement to draw: pages in order, each dense from its first slot.
 * @param onMove commits a plain drop — the item, and the page and slot it landed at.
 * @param onMerge commits a merge-ring drop onto an app (making a folder) or a folder (joining it).
 * @param onDropExtracted commits an app dragged out of a folder onto a page; [onMergeExtracted] is the same drag
 *   landing on another entry's merge ring, which is how an app moves folder→folder in one gesture.
 * @param onGridResolved reports the resolved page grid up to the ViewModel, which needs the capacity to page the
 *   store. The device is a `@Composable` read, so the UI is the only place that can resolve it.
 */
@Composable
fun AppsPager(
    pages: List<List<AppsItem>>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (item: IconItem, toPage: Int, toSlot: Int) -> Unit,
    onMerge: (dragged: ComponentKey, targetPage: Int, targetSlot: Int) -> Unit,
    onReorderFolder: (folderId: Long, order: List<ComponentKey>) -> Unit,
    onAddToFolder: (folderId: Long, order: List<ComponentKey>, incoming: ComponentKey, from: Long?) -> Unit,
    onDropExtracted: (from: Long, app: ComponentKey, toPage: Int, toSlot: Int) -> Unit,
    onMergeExtracted: (from: Long, app: ComponentKey, targetPage: Int, targetSlot: Int) -> Unit,
    onGridResolved: (GridConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val device = currentDeviceConfiguration()
    val config = remember(device) { AppsPagerGrid.toGridConfig(device) }
    val perPage = config.rows * config.cols
    LaunchedEffect(config) { onGridResolved(config) }

    val gestureConfig = rememberAppsGestureConfig()
    // Held in a state so the count lambda reads the *current* pages: `rememberLauncherPagerState` remembers the
    // lambda once, so capturing the parameter directly would freeze the pager at however many pages existed on the
    // first composition — which is none, since the store isn't paged until `onGridResolved` lands.
    val livePages = rememberUpdatedState(pages)
    val pagerState = rememberLauncherPagerState(
        pageCount = { livePages.value.size.coerceAtLeast(1) },
        infiniteScroll = { false },
    )

    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    var viewport by remember { mutableStateOf<Rect?>(null) }

    // The live reorder: where the dragged item would land, and on which page. `gapPage` matters because pages are
    // hard boundaries — a gap is an index *within one page*, so carrying an item to another page starts a new one
    // rather than continuing the old.
    var gap by remember { mutableIntStateOf(-1) }
    var gapPage by remember { mutableIntStateOf(-1) }

    // The open folder's drag hooks (null when none is open). It lives above the coordinator because the planner
    // reads it and must be built first, while the folder host is created after — the same construction-order
    // squeeze home documents.
    val folderDelegate = remember { mutableStateOf<FolderDragDelegate?>(null) }

    val planner = remember(config) {
        DropPlanner { zone, item, fingerInRoot ->
            // Anything that isn't this pager's zone is the open folder's, which plans its own reorder.
            if (zone.id != PagerZoneId) return@DropPlanner folderDelegate.value?.onHover(item, fingerInRoot)
            val geo = geometry ?: return@DropPlanner null
            val page = pagerState.currentPage
            val stored = livePages.value.getOrNull(page).orEmpty()
            val others = stored.map { it.gridItem }.filterNot { it == item }
            // Off a cell (the slack below a short page) holds the current gap rather than snapping it: the finger
            // is between targets, and moving the preview there would strobe.
            val cell = geo.cellAt(fingerInRoot) ?: return@DropPlanner PagerReorderPlan
            val slot = cell.row * config.cols + cell.col
            // **Three zones, because this surface holds folders.** The centre third of an occupied cell is a merge
            // ring: dropping there folds the two together, and dwelling there opens the target to receive the app.
            // Unlike the reorder plan, a merge plan's footprint is *meaningful* — it names the hovered cell, which
            // is how the folder host resolves which folder is being aimed at (`folderIdAt` below).
            val over = stored.getOrNull(slot)
            if (over != null && over.gridItem != item && geo.thirdInCell(fingerInRoot) == Third.CENTER &&
                canMergeInto(item, over)
            ) {
                return@DropPlanner PlacementPlan(GridPlacement(page, cell.row, cell.col), DropIntent.MERGE)
            }
            gap = if (page != gapPage) {
                // Arriving on a page seeds the gap at the slot under the finger; `movingGap` refines from there as
                // the finger keeps moving. Seeding through `movingGap` instead would start from index 0, because
                // the item it is asked about isn't in this page's list at all.
                slot.coerceIn(0, others.size)
            } else {
                movingGap(others, item, gap, slot, geo.cellFractionX(fingerInRoot) < 0.5f)
            }
            gapPage = page
            PagerReorderPlan
        }
    }
    val coordinator = rememberDragCoordinator(planner)
    val session = coordinator.session

    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) { gap = -1; gapPage = -1 }
    }
    DisposableEffect(coordinator) { onDispose { coordinator.unregisterZone(PagerZoneId) } }

    // Folder hosting, the same lifecycle home uses — `FolderHostState` is surface-independent, and the one thing
    // it can't know is which folder a merge plan targets. On a coordinate surface that answer compares placements;
    // here it compares **slots**, which is exactly the split its KDoc anticipated. A merge plan's footprint names
    // the hovered cell, so page + (row, col) resolves to the entry under the finger.
    val folderHost = rememberFolderHostState(coordinator) { zoneId, plan ->
        if (zoneId != PagerZoneId) return@rememberFolderHostState null
        val slot = plan.footprint.row * config.cols + plan.footprint.col
        (livePages.value.getOrNull(plan.footprint.page)?.getOrNull(slot) as? AppsItem.Folder)?.folder?.id
    }

    // Edge-dwell page flip. Scoped to this surface's own zone, so a drag that belongs elsewhere can't flip
    // these pages behind it.
    EdgeFlipEffect(
        pagerState = pagerState,
        viewport = viewport,
        fingerInRoot = session?.takeIf { it.activeZone == PagerZoneId }?.fingerInRoot,
    )

    // Where a drag comes to rest — the same four landings home resolves, in the same order, because the question
    // is the surface-independent one: is this the open folder's drop, a release outside it, an app leaving a
    // folder, or an ordinary rearrangement?
    fun handleDrop() {
        // Read before dropping: the source is cleared when the drag ends, which the drop is.
        val sourceFolderId = folderHost.dragSourceFolderId
        val presentedFolderId = folderHost.openFolderId
        val outcome = coordinator.drop()

        // 1. Inside the open folder → its own business: a reorder, which is also how an inject commits.
        if (outcome != null && outcome.zone != PagerZoneId) {
            folderDelegate.value?.commitReorder(outcome.item)
            return
        }
        // 2. Released outside the folder that is on screen. Leaving is a deliberate dwell, so a release out here
        //    is "never mind" — and it could not be honoured anyway: an app carried inside a folder has no slot, so
        //    placing it would leave it in the folder *and* on a page.
        if (presentedFolderId != null) {
            folderHost.close()
            return
        }
        if (outcome == null) return
        val plan = outcome.plan
        val merging = plan.intent == DropIntent.MERGE
        val targetPage = plan.footprint.page
        val targetSlot = plan.footprint.row * config.cols + plan.footprint.col

        // 3. The drag started inside a folder and has landed out here: it is placed and removed from that folder
        //    in one batch — including onto a merge ring, which is how it moves straight into another folder.
        if (sourceFolderId != null) {
            val app = (outcome.item as? GridItem.App)?.component ?: return
            if (merging) {
                onMergeExtracted(sourceFolderId, app, targetPage, targetSlot)
            } else {
                // No gap means the finger never rested on a cell of this pager (a release over the slack below a
                // short page). Nothing was removed on the way out, so declining here simply leaves the app in its
                // folder — the same "no valid landing" outcome home reaches.
                val page = gapPage.takeIf { it >= 0 } ?: return
                onDropExtracted(sourceFolderId, app, page, gap.coerceAtLeast(0))
            }
            return
        }
        // 4. An ordinary rearrangement.
        if (merging) {
            (outcome.item as? GridItem.App)?.let { onMerge(it.component, targetPage, targetSlot) }
        } else {
            val item = outcome.item.asIconItem() ?: return
            val page = gapPage.takeIf { it >= 0 } ?: return
            onMove(item, page, gap.coerceAtLeast(0))
        }
    }

    // The app being carried into the open folder, resolved once and held. Keyed on the component alone,
    // deliberately not on `pages`: committing takes it off the page optimistically, so re-deriving afterwards
    // would resolve to null and the app would vanish from both surfaces until the write came back. Searched among
    // the pages *and* folder contents, since it may be arriving from another folder and so be on no page at all.
    val incomingApp = remember(folderHost.incomingComponent) {
        folderHost.incomingComponent?.let { component ->
            pages.firstNotNullOfOrNull { page ->
                page.firstNotNullOfOrNull { entry ->
                    when (entry) {
                        is AppsItem.App -> entry.info.takeIf { it.componentKey == component }
                        is AppsItem.Folder -> entry.apps.firstOrNull { it.componentKey == component }
                    }
                }
            }
        }
    }

    val draggedItem = session?.item
    // Resolved once per frame rather than per page: a drag recomposes this on every finger move, and searching
    // every page for the dragged entry inside each page's own content would be that search N times over.
    val draggedEntry = remember(pages, draggedItem) { draggedItem?.let { entryFor(pages, it) } }
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    CompositionLocalProvider(LocalIconMetrics provides PagerIconMetrics) {
        Box(modifier.fillMaxSize()) {
            LauncherPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(safeInsets)
                    // Gated during a drag so a page swipe and an item drag never fight; the edge dwell above is
                    // how pages change while carrying something.
                    .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        viewport = b
                        geometry = GridGeometry(
                            originInRoot = Offset(b.left, b.top),
                            cellW = b.width / config.cols,
                            cellH = b.height / config.rows,
                            cols = config.cols,
                            rows = config.rows,
                        )
                        coordinator.registerZone(DropZone(PagerZoneId, b, z = 0) { it.asIconItem() != null })
                    },
                // Keep off-screen pages placed while dragging, so the lifted cell's pointer stream survives a flip.
                keepAllPagesPlaced = coordinator.isDragging,
            ) { pageIndex ->
                val display = displayOrder(
                    stored = pages.getOrNull(pageIndex).orEmpty(),
                    dragged = if (pageIndex == gapPage) draggedEntry else null,
                    gap = gap,
                    perPage = perPage,
                )
                LauncherGrid(config = config, modifier = Modifier.fillMaxSize()) {
                    flowItems(items = display, itemKey = { it.gridItem }) { item, cellModifier ->
                        LauncherDragCell(
                            coordinator = coordinator,
                            item = item.gridItem,
                            gestureConfig = gestureConfig,
                            onDrop = { handleDrop() },
                            modifier = cellModifier,
                            onOpen = {
                                when (item) {
                                    is AppsItem.App -> onLaunch(item.info.componentKey)
                                    is AppsItem.Folder -> folderHost.open(item.folder.id)
                                }
                            },
                        ) { itemGestures ->
                            AppsPagerCell(item = item, modifier = Modifier.fillMaxSize(), itemGestures = itemGestures)
                        }
                    }
                }
            }

            // The floating proxy: the icon under the finger, at one cell's size. The lifted cell itself is drawn
            // invisible by `LauncherDragCell`, so this is the only thing the user sees moving.
            val geo = geometry
            // The proxy belongs to whichever surface is presenting the drag: while a folder is on screen that is
            // the folder, drawing the app at its own cell size. Exactly one of them paints.
            if (session != null && geo != null && draggedEntry != null && folderHost.openFolderId == null) {
                val finger = session.fingerInRoot
                FloatingDragIcon(
                    rootOffset = IntOffset(
                        (finger.x - geo.cellW / 2f).roundToInt(),
                        (finger.y - geo.cellH / 2f).roundToInt(),
                    ),
                    size = DpSize(with(density) { geo.cellW.toDp() }, with(density) { geo.cellH.toDp() }),
                ) {
                    // No `itemGestures`: the proxy follows the finger, it is not a touch target.
                    AppsPagerCell(item = draggedEntry, modifier = Modifier.fillMaxSize(), itemGestures = Modifier)
                }
            }

            // Folder overlays, above the pages. Usually one; a drag that started inside a folder keeps that folder
            // composed for its whole life even once another is on screen, because the cell driving the drag is in
            // its grid and a pointer stream cannot move to another node. That one is the **pointer holder**
            // (`presenting = false`): invisible, zone-less, no proxy.
            val openFolder = folderHost.openFolderId?.let { id -> folderAt(pages, id) }
            val holderFolder = folderHost.dragSourceFolderId
                ?.takeIf { it != folderHost.openFolderId }
                ?.let { id -> folderAt(pages, id) }

            // Report the presented folder's persisted membership back, so the host knows a just-injected app landed.
            val openMembers = openFolder?.folder?.apps
            LaunchedEffect(openMembers) { folderHost.onMembersChanged(openMembers.orEmpty()) }

            // Holder first so it sits *below* the presented folder, and both from this **one** keyed call site: a
            // folder moving between the two roles must keep its composition, and a second call site is a different
            // composition position, which disposes it and kills the drag it exists to preserve.
            val overlays = listOfNotNull(holderFolder?.let { it to false }, openFolder?.let { it to true })
            overlays.forEach { (folder, presenting) ->
                key(folder.folder.id) {
                    FolderOverlay(
                        label = folder.folder.label,
                        apps = folder.apps,
                        coordinator = coordinator,
                        gestureConfig = gestureConfig,
                        incoming = if (presenting) incomingApp else null,
                        presenting = presenting,
                        onLaunch = { component -> onLaunch(component); folderHost.close() },
                        onReorder = { order ->
                            // Only an inject still in flight adds membership; once committed this is a plain
                            // reorder, because the app is already a member even if the store hasn't said so yet.
                            val incoming = (folderHost.phase as? FolderPhase.Injecting)?.app
                            if (incoming != null && order.contains(incoming)) {
                                onAddToFolder(folder.folder.id, order, incoming, folderHost.dragSourceFolderId)
                                folderHost.injectCommitted()
                            } else {
                                onReorderFolder(folder.folder.id, order)
                            }
                        },
                        onLeave = folderHost::leaveFolder,
                        onDrop = { handleDrop() },
                        onPublishDelegate = { folderDelegate.value = it },
                        onDismiss = { folderHost.close() },
                    )
                }
            }
        }
    }
}

/**
 * What one page draws while a drag is in flight: its stored entries, with the dragged item lifted to the gap.
 *
 * **The dragged item stays composed on its source page even after the finger has carried it to another**, which is
 * why this can return it twice across two pages. That is not a glitch to tidy up: the cell on the source page owns
 * the gesture's pointer stream, and disposing it mid-drag kills the drag (the drag toolkit's standing rule). Both
 * copies are drawn invisible by `LauncherDragCell`, so the user sees only the floating proxy — the far copy exists
 * purely to occupy the gap so the other icons flow around it.
 *
 * Truncated to [perPage] because the destination page may not have room: the surplus is what the repository will
 * cascade onto the next page, so previewing it here would promise a layout the commit won't produce.
 */
private fun displayOrder(
    stored: List<AppsItem>,
    dragged: AppsItem?,
    gap: Int,
    perPage: Int,
): List<AppsItem> {
    if (dragged == null) return stored
    val others = stored.filterNot { it.gridItem == dragged.gridItem }
    val at = gap.coerceIn(0, others.size)
    return (others.take(at) + dragged + others.drop(at)).take(perPage)
}

/** One entry on a page — an app or a folder, drawn as home draws them. */
@Composable
private fun AppsPagerCell(item: AppsItem, modifier: Modifier, itemGestures: Modifier) {
    when (item) {
        is AppsItem.App -> AppCell(app = item.info, modifier = modifier, itemGestures = itemGestures)
        // TODO(P5b): opening a folder needs a FolderOverlay hosted here, which arrives with the merge that can
        //  create one. Until then no folder can exist on this surface to tap.
        is AppsItem.Folder -> FolderCell(
            label = item.folder.label,
            apps = item.apps,
            modifier = modifier,
            itemGestures = itemGestures,
        )
    }
}

/** The folder entry with [folderId], wherever it sits. */
private fun folderAt(pages: List<List<AppsItem>>, folderId: Long): AppsItem.Folder? =
    pages.firstNotNullOfOrNull { page ->
        page.filterIsInstance<AppsItem.Folder>().firstOrNull { it.folder.id == folderId }
    }

/**
 * Whether [dragged] can be folded into [target].
 *
 * Apps combine with apps (making a folder) and drop into folders; a **folder cannot go into a folder**, because
 * folders don't nest — the model says so in [inkspire.morphic.core.model.Folder], whose contents are apps.
 */
private fun canMergeInto(dragged: GridItem, target: AppsItem): Boolean =
    dragged is GridItem.App && (target is AppsItem.App || target is AppsItem.Folder)

/**
 * The entry a drag is carrying: an item sitting on a page, **or an app inside a folder**.
 *
 * The second half is what an extract needs. An app dragged out of a folder is on no page at all — it is still a
 * member, since nothing is written until the drop — so a page-only lookup would find nothing, and both the
 * floating proxy and the reorder preview would have no icon to draw.
 */
private fun entryFor(pages: List<List<AppsItem>>, dragged: GridItem): AppsItem? {
    pages.forEach { page -> page.firstOrNull { it.gridItem == dragged }?.let { return it } }
    val component = (dragged as? GridItem.App)?.component ?: return null
    pages.forEach { page ->
        page.filterIsInstance<AppsItem.Folder>().forEach { folder ->
            folder.apps.firstOrNull { it.componentKey == component }?.let { return AppsItem.App(it) }
        }
    }
    return null
}
