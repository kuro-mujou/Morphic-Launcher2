package inkspire.morphic.feature.apps.layout.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderOverlay
import inkspire.morphic.core.designsystem.folder.FolderPhase
import inkspire.morphic.core.designsystem.folder.rememberFolderHostState
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.ordered.cellFractionX
import inkspire.morphic.core.designsystem.ordered.Third
import inkspire.morphic.core.designsystem.ordered.movingGap
import inkspire.morphic.core.designsystem.ordered.thirdInCell
import inkspire.morphic.core.designsystem.pager.EdgeFlipEffect
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.feature.apps.AppsItem
import inkspire.morphic.feature.apps.asIconItem
import inkspire.morphic.feature.apps.gridItem
import inkspire.morphic.feature.apps.iconItem
import inkspire.morphic.feature.apps.layout.rememberAppsGestureConfig
import inkspire.morphic.feature.apps.layout.rememberAppsItemMenu
import kotlin.math.roundToInt

/**
 * This surface's drop zone — the pager viewport, exactly as home's paged main area registers one.
 *
 * `internal` rather than file-private because [dropFootprintCell] reads it too: a footprint may only be painted for
 * a drag that is actually over *this* surface.
 */
internal val PagerZoneId = ZoneId("apps-pager")

/**
 * The plan this surface reports for every hover it accepts: droppable, painting nothing.
 *
 * Like a folder's, and for the same reason — an ordered surface previews a drop by reflowing its own cells around
 * the migrating gap, so there is no target cell to shadow. [DropIntent.REORDER] says exactly that, which is why
 * the footprint below goes unread.
 */
internal val PagerReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)

/**
 * The **pager** layout of the APPS surface ([inkspire.morphic.core.model.AppsLayout.PAGER]): the app collection
 * across swipeable pages, in an order the **user** owns and rearranges by dragging.
 *
 * **The first APPS layout that stores anything.** The list and grid are derived — recomputed A–Z from the app
 * cache — so they take a flat list and own nothing. This one takes [pages] already arranged, because the
 * arrangement is data (`apps_pager_item`, via `AppsOrderRepository`).
 *
 * **Fixed pages, not a scrolling grid.** Each page is a [LauncherGrid] in FIXED_PAGER mode at the size [config]
 * gives, so a page holds exactly `rows × cols` and a page boundary is a real boundary — which is what the store's
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
 * **Crossing to HOME** is the same idea one level out. Carry the app into the eject band at the top of the screen
 * (`TopActionZone`, registered by `feature:shell`) and the drawer closes with the drag still in flight; the app then
 * lands on home's grid, committed by *home's* zone. Nothing here participates: the coordinator is the launcher's, and
 * this surface stays composed behind HOME so the lifted cell keeps its pointer stream — it simply stops being
 * `LocalSurfacePresented`, which unregisters its zone and stops it drawing the proxy. The app is **not** taken out of
 * the drawer on the way: it lives in this arrangement *and* may sit on home, so there is nothing to remove.
 *
 * The band rather than eject-on-lift (which is what the A–Z list and grid do) because this surface **stores an
 * arrangement**: a drag on it means "rearrange" until the user says otherwise, and the band is how they say it.
 *
 * **What lives elsewhere in this package.** The leaves it draws — [AppsPagerCell] and [dropFootprintCell] — and the
 * pure maths — [pageDisplayOrder], [entryFor], [appInPages], [folderAt], [canMergeInto]. What stays here is the part
 * that is genuinely one machine: the drag state, the planner that writes it, and the drop that reads it.
 *
 * @param pages the arrangement to draw: pages in order, each dense from its first slot.
 * @param onMove commits a plain drop — the item, and the page and slot it landed at.
 * @param onMerge commits a merge-ring drop onto an app (making a folder) or a folder (joining it).
 * @param onDropExtracted commits an app dragged out of a folder onto a page; [onMergeExtracted] is the same drag
 *   landing on another entry's merge ring, which is how an app moves folder→folder in one gesture.
 * @param metrics a page's icon sizing, resolved from `GridSlot.APPS_PAGER`'s blueprint and the user's overrides.
 * @param folderMetrics an *open folder's* icon sizing (`GridSlot.FOLDER`). A separate value on purpose: a folder is
 *   its own grid with its own configuration, so it must not inherit the page's — which is exactly what it would do if
 *   this surface published one ambient `LocalIconMetrics` for everything inside it.
 * @param horizontalPadding the margin at each page's left and right edge. Applied *above* the geometry publisher, so
 *   the drop zone and the cell maths follow it without either being adjusted; the caller must subtract the same amount
 *   before fitting [config], since page capacity is decided by the width the cells actually get.
 * @param config the page's grid, resolved from `GridSlot.APPS_PAGER`'s blueprint and the user's overrides. Passed
 *   rather than resolved here because **the same number paginates the store**: `rows × cols` is a page's capacity, so
 *   a page drawn at one size while the arrangement was paginated at another would put entries on pages that do not
 *   exist. One resolution, one owner.
 */
@Composable
fun AppsPager(
    pages: List<List<AppsItem>>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (item: IconItem, toPage: Int, toSlot: Int) -> Unit,
    onMerge: (dragged: ComponentKey, target: IconItem) -> Unit,
    onReorderFolder: (folderId: Long, order: List<ComponentKey>) -> Unit,
    onAddToFolder: (folderId: Long, order: List<ComponentKey>, incoming: ComponentKey, from: Long?) -> Unit,
    onDropExtracted: (from: Long, app: ComponentKey, toPage: Int, toSlot: Int) -> Unit,
    onMergeExtracted: (from: Long, app: ComponentKey, target: IconItem) -> Unit,
    metrics: IconMetrics,
    folderMetrics: IconMetrics,
    config: GridConfig,
    horizontalPadding: Dp,
    wraps: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val perPage = config.rows * config.cols

    val gestureConfig = rememberAppsGestureConfig()
    // One menu handler for every app on this surface, apps in the open folder included — see `rememberAppsItemMenu`.
    val showItemMenu = rememberAppsItemMenu()
    // Held in a state so the count lambda reads the *current* pages: `rememberLauncherPagerState` remembers the
    // lambda once, so capturing the parameter directly would freeze the pager at however many pages existed on the
    // first composition — which is none, since the store isn't paged until the surface reports its device.
    val livePages = rememberUpdatedState(pages)
    // Held live for the reason `livePages` is, stated above: the factory remembers its lambdas once, so reading the
    // parameter directly would freeze the pager at the blueprint default the store had not yet replaced.
    val liveWraps = rememberUpdatedState(wraps)
    val pagerState = rememberLauncherPagerState(
        pageCount = { livePages.value.size.coerceAtLeast(1) },
        infiniteScroll = { liveWraps.value },
    )

    // **Where this pager is resting, for the surface swipe.** Reaching HOME from a LEFT or RIGHT binding crosses
    // these pages, so the pan claims only on the page nearest the edge being swiped toward — the first page for a
    // swipe back to a LEFT-bound HOME, the last for a RIGHT-bound one. Nothing scrolls vertically, so a TOP or
    // BOTTOM binding closes freely.
    ReportScrollEdges { ScrollEdges(atLeft = pagerState.atFirstPage, atRight = pagerState.atLastPage) }

    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    var viewport by remember { mutableStateOf<Rect?>(null) }

    // The live reorder: where the dragged item would land, and on which page. `gapPage` matters because pages are
    // hard boundaries — a gap is an index *within one page*, so carrying an item to another page starts a new one
    // rather than continuing the old.
    var gap by remember { mutableIntStateOf(-1) }
    var gapPage by remember { mutableIntStateOf(-1) }

    // The entry a merge would fold into, resolved by **identity** rather than re-derived from a slot index later.
    // A slot is not a stable name for an entry here: the page renders the gap-shifted display order, so the same
    // index means different things to the planner and to the commit. Naming the entry is the same discipline the
    // store's ops already follow (`CreateFolder` takes the target, not its slot).
    var mergeTarget by remember { mutableStateOf<AppsItem?>(null) }

    val planner = remember(config) {
        DropPlanner { item, fingerInRoot ->
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
            // Resolved against the **display** order, not the stored one: with a gap open the icons have shifted,
            // and the user aims at what they can see. Reading `stored[slot]` here was the bug behind "the footprint
            // says merge but the target has moved away" — the planner named one entry and the screen showed another.
            val displayed = pageDisplayOrder(
                stored = stored,
                dragged = if (page == gapPage) entryFor(livePages.value, item) else null,
                gap = gap,
                perPage = perPage,
            )
            val over = displayed.getOrNull(slot)
            if (over != null && over.gridItem != item && geo.thirdInCell(fingerInRoot) == Third.CENTER &&
                canMergeInto(item, over)
            ) {
                // The gap is deliberately left where it is. Collapsing it would reflow every icon the instant the
                // finger crossed into a centre third — including the one being aimed at, which would slide out
                // from under the footprint that had just appeared on it.
                mergeTarget = over
                return@DropPlanner PlacementPlan(GridPlacement(page, cell.row, cell.col), DropIntent.MERGE)
            }
            mergeTarget = null
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
    // The launcher's one coordinator, provided by `feature:shell` — not this surface's. That is what lets an app
    // lifted here be carried onto home: both surfaces' zones sit in one registry, so the eject band merely gets the
    // drawer out of the way and the same drag lands on a home grid.
    val coordinator = requireDragCoordinator()
    val presented = LocalSurfacePresented.current
    val session = coordinator.session

    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) { gap = -1; gapPage = -1; mergeTarget = null }
    }

    // Folder hosting, the same lifecycle home uses — `FolderHostState` is surface-independent, and the one thing
    // it can't know is which folder a merge plan targets. On a coordinate surface that answer compares placements;
    // here it compares **slots**, which is exactly the split its KDoc anticipated. A merge plan's footprint names
    // the hovered cell, so page + (row, col) resolves to the entry under the finger.
    val folderHost = rememberFolderHostState<Long>(coordinator) { zoneId, _ ->
        // Reads the target the planner just resolved rather than re-deriving one from the plan's footprint: the
        // footprint names a *cell*, and turning a cell back into an entry is the slot-vs-display mismatch above.
        // The planner runs on the move that produced this plan, so the answer is always the current one.
        if (zoneId != PagerZoneId) null else (mergeTarget as? AppsItem.Folder)?.folder?.id
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
    // What a landing **on one of this pager's pages** means. The same cases home resolves, in the same order,
    // because the question is the surface-independent one — and, like home's, it is the *zone's* handler rather than
    // the releasing cell's, so it runs whoever lifted the app.
    fun commitLanding(outcome: DropOutcome) {
        // Read here rather than after any write: `dragSourceFolderId` is cleared when the drag ends, and this runs
        // inside `coordinator.drop()`, i.e. before the effect that notices.
        val sourceFolderId = folderHost.dragSourceFolderId

        // 1. Released on a page while a folder is still on screen. Leaving is a deliberate dwell, so a release out
        //    here is "never mind" — and it could not be honoured anyway: an app carried inside a folder has no slot,
        //    so placing it would leave it in the folder *and* on a page.
        if (folderHost.openFolderId != null) {
            folderHost.close()
            return
        }
        // A merge commits against the entry the planner named, not the cell it painted on.
        val target = mergeTarget?.takeIf { outcome.plan.intent == DropIntent.MERGE }

        // 2. The drag started inside a folder and has landed out here: it is placed and removed from that folder
        //    in one batch — including onto a merge ring, which is how it moves straight into another folder.
        if (sourceFolderId != null) {
            val app = (outcome.item as? GridItem.App)?.component ?: return
            if (target != null) {
                onMergeExtracted(sourceFolderId, app, target.iconItem)
            } else {
                // No gap means the finger never rested on a cell of this pager (a release over the slack below a
                // short page). Nothing was removed on the way out, so declining here simply leaves the app in its
                // folder — the same "no valid landing" outcome home reaches.
                val page = gapPage.takeIf { it >= 0 } ?: return
                onDropExtracted(sourceFolderId, app, page, gap.coerceAtLeast(0))
            }
            return
        }
        // 3. An ordinary rearrangement.
        if (target != null) {
            (outcome.item as? GridItem.App)?.let { onMerge(it.component, target.iconItem) }
        } else {
            val item = outcome.item.asIconItem() ?: return
            val page = gapPage.takeIf { it >= 0 } ?: return
            onMove(item, page, gap.coerceAtLeast(0))
        }
    }

    // A cell of this surface released the finger — that only ends the drag. The landing belongs to whichever zone it
    // fell in, which may be a page of this pager, a folder open over it, or one of **home's** grids if the drag was
    // ejected. What is left here is the source-side bookkeeping: a folder on screen with nothing landed under it.
    fun handleRelease() {
        val presentedFolderId = folderHost.openFolderId
        val outcome = coordinator.drop()
        if (presentedFolderId != null && outcome == null) folderHost.close()
    }

    // **One drop zone, the whole viewport**, as on every paged surface here. Registered from state rather than from
    // the layout callback that measures it, because being registered also depends on this surface being the one on
    // screen: it stays composed behind HOME once a drag has been ejected, and a zone left in the registry from
    // off-stage would go on claiming the finger from under home's grids.
    RegisterDropZone(
        coordinator = coordinator,
        zone = viewport?.let {
            DropZone(
                id = PagerZoneId,
                bounds = it,
                z = 0,
                planner = planner,
                accepts = { item -> item.asIconItem() != null },
                onDrop = ::commitLanding,
            )
        },
    )

    // The app being carried into the open folder, resolved once and held. Keyed on the component alone,
    // deliberately not on `pages`: committing takes it off the page optimistically, so re-deriving afterwards
    // would resolve to null and the app would vanish from both surfaces until the write came back. Searched among
    // the pages *and* folder contents, since it may be arriving from another folder and so be on no page at all.
    val incomingApp = remember(folderHost.incomingComponent) {
        folderHost.incomingComponent?.let { component -> appInPages(pages, component) }
    }

    val draggedItem = session?.item
    // Resolved once per frame rather than per page: a drag recomposes this on every finger move, and searching
    // every page for the dragged entry inside each page's own content would be that search N times over.
    val draggedEntry = remember(pages, draggedItem) { draggedItem?.let { entryFor(pages, it) } }

    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        Box(modifier.fillMaxSize()) {
            LauncherPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(uiInsets)
                    // The grid's own margin, and it must sit **before** `onGloballyPositioned` in this chain — which
                    // is what makes drag and drop correct rather than something to fix afterwards. The bounds read
                    // below become the *padded* ones, so the published `GridGeometry` and the registered drop zone
                    // both describe the box actually drawn; a finger→cell read against the unpadded width would name
                    // a column up to a whole cell away near the right edge.
                    .padding(horizontal = horizontalPadding)
                    // Gated during a drag so a page swipe and an item drag never fight; the edge dwell above is
                    // how pages change while carrying something.
                    .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        viewport = bounds
                        geometry = GridGeometry(
                            originInRoot = Offset(bounds.left, bounds.top),
                            cellW = bounds.width / config.cols,
                            cellH = bounds.height / config.rows,
                            cols = config.cols,
                            rows = config.rows,
                        )
                    },
                // Keep off-screen pages placed while dragging, so the lifted cell's pointer stream survives a flip.
                keepAllPagesPlaced = coordinator.isDragging,
            ) { pageIndex ->
                val display = pageDisplayOrder(
                    stored = pages.getOrNull(pageIndex).orEmpty(),
                    dragged = if (pageIndex == gapPage) draggedEntry else null,
                    gap = gap,
                    perPage = perPage,
                )
                LauncherGrid(config = config, modifier = Modifier.fillMaxSize()) {
                    // The drop shadow, declared before the cells so it sits behind them, and inside the page grid
                    // so it travels with the page rather than hanging in root space during a flip.
                    dropFootprintCell(session, pageIndex, gap, gapPage, config)
                    flowItems(items = display, itemKey = { it.gridItem }) { item, cellModifier ->
                        LauncherDragCell(
                            coordinator = coordinator,
                            item = item.gridItem,
                            gestureConfig = gestureConfig,
                            onRelease = ::handleRelease,
                            modifier = cellModifier,
                            onOpen = {
                                when (item) {
                                    is AppsItem.App -> onLaunch(item.info.componentKey)
                                    is AppsItem.Folder -> folderHost.open(item.folder.id)
                                }
                            },
                            // A folder shows none: the verbs it would offer (rename, dissolve) have no ops on the
                            // APPS order store yet, and a menu of one disabled row is worse than no menu.
                            onShowMenu = { anchor ->
                                (item as? AppsItem.App)?.let { showItemMenu(it.info, anchor) }
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
            if (presented && session != null && geo != null && draggedEntry != null &&
                folderHost.openFolderId == null
            ) {
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
                        // Its own grid, so its own sizing — not the page's, which is what the ambient local holds.
                        metrics = folderMetrics,
                        incoming = if (presenting) incomingApp else null,
                        presenting = presenting,
                        onLaunch = { component -> onLaunch(component); folderHost.close() },
                        onReorder = { order ->
                            // Only an inject still in flight adds membership; once committed this is a plain
                            // reorder, because the app is already a member even if the store hasn't said so yet.
                            val incoming = (folderHost.phase as? FolderPhase.Injecting<*>)?.app
                            if (incoming != null && order.contains(incoming)) {
                                onAddToFolder(folder.folder.id, order, incoming, folderHost.dragSourceFolderId)
                                folderHost.injectCommitted()
                            } else {
                                onReorderFolder(folder.folder.id, order)
                            }
                        },
                        onLeave = folderHost::leaveFolder,
                        onRelease = ::handleRelease,
                        onShowMenu = showItemMenu,
                        onDismiss = { folderHost.close() },
                    )
                }
            }
        }
    }
}
