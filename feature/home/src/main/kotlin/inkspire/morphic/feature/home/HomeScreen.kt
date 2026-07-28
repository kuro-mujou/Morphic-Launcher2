package inkspire.morphic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderDragDelegate
import inkspire.morphic.core.designsystem.folder.FolderOverlay
import inkspire.morphic.core.designsystem.grid.CoordinateDragPager
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.LayoutChange
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private val HomeZone = ZoneId("home")

/**
 * The real HOME surface: placed apps on a **paged** [CoordinateDragPager] (the shared free-placement drag
 * surface), with **drag-to-rearrange that persists**. Long-press an app to lift it; the free-grid planner
 * ([FreeGridPlanner]) pushes that page's occupants out of the way (previewed live, dwelled so a fast drag
 * doesn't strobe), and the drop commits through [HomeViewModel.applyChanges] as `Move` commands — which update
 * the surface instantly (optimistic) and persist to Room. Dragging to a side edge flips pages; a trailing empty
 * page appears mid-drag so an app can be carried onto a new page.
 *
 * This screen keeps only what is home-specific: the [DropPlanner] (span-snapped push, plus the merge-ring /
 * directional-push partition over a hovered occupant), the root drag overlay, and the tap→launch / merge-drop
 * wiring; the pager, per-page grids, gestures, dwelled preview, and edge-flip live in [CoordinateDragPager], and
 * the folder-interaction lifecycle in [FolderHostState]. Dropping an app onto another (finger in its centre ring)
 * **merges** them into a folder; folders render as a [FolderCell] and tapping one opens a [FolderOverlay]. First
 * cut: apps + folders, portrait only. A tap on an app launches it (via [HomeViewModel.launch]) through the gesture
 * layer's `onOpen`, so cells carry no click handler of their own.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    // Resolve the home grid from its blueprint for the detected device (portrait only for now); an app occupies
    // one whole visual cell, i.e. a cellMultiplier × cellMultiplier logical footprint. The ViewModel needs the
    // config to seed the first-run layout, so push it down whenever it changes.
    val device = currentDeviceConfiguration()
    val config = remember(device) { HomePagerGrid.toGridConfig(device) }
    val cellSpan = config.cellMultiplier
    LaunchedEffect(config) { viewModel.setGridConfig(config) }

    val gestureConfig = remember {
        ItemGestureConfig(touchSlopPx = with(density) { 20.dp.toPx() }, longPressTimeoutMillis = 400L)
    }

    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    // Derived views of the placed items, keyed on the list so they survive a recomposition that didn't change it.
    // This matters more than it looks: every cell reads the coordinator's session, so a drag recomposes this
    // screen on *every finger move* — anything derived here without a `remember` is rebuilt at frame rate.
    val placements = remember(state.items) { state.items.associate { it.gridItem to it.placement } }
    val folders = remember(state.items) { state.items.filterIsInstance<HomeItem.Folder>() }
    // The current placement map, read live by the planner while a drag is in flight.
    val livePlacements = rememberUpdatedState(placements)

    // Pager: page count is (highest occupied page + 1), plus one trailing empty page while a *home* drag is in
    // flight, so an app can be carried onto a brand-new page. `draggingPages` is synced from the coordinator
    // below, because the coordinator doesn't exist yet here and so can't be read directly inside the count lambda.
    val maxPage = rememberUpdatedState(remember(state.items) { state.items.maxOfOrNull { it.placement.page } ?: 0 })
    var draggingPages by remember { mutableStateOf(false) }
    val pagerState = rememberLauncherPagerState(
        pageCount = { maxPage.value + 1 + if (draggingPages) 1 else 0 },
        infiniteScroll = { false },
    )

    // The open folder's drag hooks (null when no folder is open). The one shared coordinator runs over both
    // surfaces; its planner + drop dispatch the folder zone to this delegate, keeping folder reorder logic in
    // the overlay (its order/gap aren't hoisted here). It lives here rather than on the folder host below because
    // the planner reads it and must exist before the coordinator, which the host is created after.
    val folderDelegate = remember { mutableStateOf<FolderDragDelegate?>(null) }

    // Shared planner: the folder zone routes to the folder delegate; the home zone plans the free-grid
    // push/merge for the hovered cell on the current page (span footprint snapped to the visual lattice).
    val planner = remember(config) {
        val span = config.cellMultiplier
        DropPlanner { zone, item, fingerInRoot ->
            if (zone.id != HomeZone) return@DropPlanner folderDelegate.value?.onHover(item, fingerInRoot)
            val geo = geometry ?: return@DropPlanner null
            val page = pagerState.currentPage
            val occupants = livePlacements.value.filterKeys { it != item }.filterValues { it.page == page }
            val topLeft = geo.snapTopLeftCell(fingerInRoot, colSpan = span, rowSpan = span, step = span)
            val footprint = GridPlacement(page, topLeft.row, topLeft.col, rowSpan = span, colSpan = span)

            // If the finger is over an occupant, its cell partitions into a centre merge ring + four push
            // triangles: the ring merges (folder), a triangle picks which way that occupant is shoved.
            val target = geo.cellAt(fingerInRoot)?.let { cell -> occupants.entries.firstOrNull { it.value.covers(cell) } }
            if (target != null) {
                if (canMerge(item, target.key) && geo.inMergeRingOf(fingerInRoot, target.value)) {
                    return@DropPlanner FreeGridPlanner.plan(target.value, occupants, config, merge = true)
                }
                return@DropPlanner FreeGridPlanner.plan(footprint, occupants, config, geo.pushDirectionInRect(fingerInRoot, target.value))
            }
            FreeGridPlanner.plan(footprint, occupants, config)
        }
    }
    val coordinator = rememberDragCoordinator(planner)

    // The dragged item + hovered plan drive the root overlay below; the dwelled push preview + edge page-flip
    // live inside CoordinateDragPager.
    val session = coordinator.session

    // Folder hosting: which folder is open, plus the two mid-drag hand-offs between home and the open folder
    // (extract out / inject in) and the dwell that opens a folder to receive a drag. `folderIdAt` is home's answer
    // to "which folder does this merge plan target?" — a placement match, because home is a coordinate surface.
    val folderHost = rememberFolderHostState(coordinator) { plan ->
        folders.firstOrNull { it.placement == plan.footprint }?.folder?.id
    }
    val incomingApp = remember(state.items, folderHost.incomingComponent) {
        folderHost.incomingComponent?.let { c ->
            state.items.filterIsInstance<HomeItem.App>().firstOrNull { it.info.componentKey == c }?.info
        }
    }

    // Is a drag in flight that could still land on home? On the shared coordinator `isDragging` alone can't tell:
    // it is equally true of a reorder happening inside the open folder, which is that surface's gesture and must
    // not grow home's pager behind it. An extract *is* on its way here, so it counts. (Deliberately not the active
    // zone — a home drag held over the pager's padding has no zone, and the page it is being carried to must not
    // vanish from under the pager mid-drag.)
    val homeDragInFlight = coordinator.isDragging &&
        (folderHost.openFolderId == null || folderHost.extractingFrom != null)
    LaunchedEffect(homeDragInFlight) { draggingPages = homeDragInFlight }

    fun handleDrop() {
        val extract = folderHost.extractingFrom
        val outcome = coordinator.drop()
        if (extract != null) { // a drag handed off out of a folder — commit it on home (or leave it in the folder)
            folderHost.endExtract()
            val (folderId, component) = extract
            val plan = outcome
                ?.takeIf { it.zone == HomeZone && it.plan.intent != DropIntent.INVALID && it.plan.intent != DropIntent.MERGE }
                ?.plan
            if (plan != null) viewModel.dropExtractedApp(folderId, component, plan)
            // else: no valid home spot → the app was never removed, so it stays in the folder
            return
        }
        if (outcome == null) return
        if (outcome.zone != HomeZone) { // dropped on the folder zone → the folder commits its reorder
            folderDelegate.value?.commitReorder(outcome.item)
            return
        }
        val plan = outcome.plan
        when (plan.intent) {
            DropIntent.INVALID -> return // no room — leave it where it was
            DropIntent.MERGE ->
                viewModel.mergeChanges(outcome.item, plan.footprint)?.let(viewModel::applyChanges)
            else -> {
                val moves = plan.moves.map { (moved, to) -> LayoutChange.Move(moved, to) } +
                    LayoutChange.Move(outcome.item, plan.footprint)
                viewModel.applyChanges(moves)
            }
        }
    }

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        Box(modifier.fillMaxSize().background(colors.background)) {
            CoordinateDragPager(
                items = state.items,
                config = config,
                pagerState = pagerState,
                coordinator = coordinator,
                zoneId = HomeZone,
                gestureConfig = gestureConfig,
                dragItem = { it.gridItem },
                placement = { it.placement },
                onDrop = { handleDrop() },
                modifier = Modifier.fillMaxSize().padding(16.dp),
                onGeometryChange = { geometry = it },
                onOpen = { item ->
                    when (item) {
                        is HomeItem.App -> viewModel.launch(item.info.componentKey)
                        is HomeItem.Folder -> folderHost.open(item.folder.id)
                    }
                },
            ) { item, cellModifier, itemGestures ->
                when (item) {
                    is HomeItem.App ->
                        AppCell(app = item.info, modifier = cellModifier, itemGestures = itemGestures)
                    is HomeItem.Folder -> {
                        // Hide the app currently being dragged (e.g. extracted out of this folder) from the tile
                        // preview, so it isn't shown in the folder icon and under the finger at the same time.
                        // The real folder removal commits on drop — removing it now would dispose the dragged
                        // cell (it lives in the folder's grid) and kill the drag. (Mirrors L1's removedFromFolder.)
                        val dragged = (session?.item as? GridItem.App)?.component
                        val preview =
                            if (dragged == null) item.apps else item.apps.filterNot { it.componentKey == dragged }
                        FolderCell(
                            label = item.folder.label,
                            apps = preview,
                            modifier = cellModifier,
                            itemGestures = itemGestures,
                        )
                    }
                }
            }

            // Drag overlay (root space): the drop shadow in the grid + the floating proxy on the finger. The two
            // are gated separately, because they answer different questions — "is there a cell of *this* grid to
            // shadow?" and "whose job is it to draw the icon under the finger?".
            val geo = geometry
            if (session != null && geo != null) {
                // The footprint spans `cellSpan` logical cells per axis (one visual cell), so size the overlays
                // to that extent, not a single logical cell.
                val footprintW = geo.cellW * cellSpan
                val footprintH = geo.cellH * cellSpan
                // Only paint the shadow while the drag is over home's *own* zone. On the shared coordinator
                // another zone's plan describes that zone's preview, not a cell here: the open folder previews a
                // reorder by reflowing its cells and returns a token plan whose footprint is meaningless, which
                // would otherwise paint a shadow at cell (0,0) behind the folder (invisible today only because
                // that backdrop is opaque black — it won't be once the frosted backdrop lands). Extract is
                // unaffected: its active zone really is home, which is exactly when the landing cell should show.
                if (session.activeZone == HomeZone) {
                    session.plan?.let { plan ->
                        val topLeft = geo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                        DropFootprint(
                            intent = plan.intent,
                            modifier = Modifier
                                .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                                .size(with(density) { footprintW.toDp() }, with(density) { footprintH.toDp() }),
                        )
                    }
                }
                // The proxy belongs to whichever surface is *presenting* the drag, and while a folder is open that
                // is the folder — it draws the dragged app at its own cell size, and keeps drawing it right through
                // an extract hand-off. So home stands down rather than have both paint an icon under one finger.
                // Note this is deliberately not gated on the active zone: a home drag held over a gap or the
                // surface's padding has no zone, and its proxy must still follow the finger.
                val dragged = state.items.firstOrNull { it.gridItem == session.item }
                if (dragged != null && folderHost.openFolderId == null) {
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            (finger.x - footprintW / 2f).roundToInt(),
                            (finger.y - footprintH / 2f).roundToInt(),
                        ),
                        size = DpSize(with(density) { footprintW.toDp() }, with(density) { footprintH.toDp() }),
                    ) {
                        // No `itemGestures`: the proxy is a rendering that follows the finger, not a touch target
                        // (the lifted cell still owns the pointer stream).
                        when (dragged) {
                            is HomeItem.App -> AppCell(app = dragged.info, modifier = Modifier.fillMaxSize())
                            is HomeItem.Folder -> FolderCell(
                                label = dragged.folder.label,
                                apps = dragged.apps,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // Opened-folder overlay, drawn above the grid. Resolved live from state so its contents track edits.
            val openFolder = folderHost.openFolderId?.let { id -> folders.firstOrNull { it.folder.id == id } }
            // Keyed by folder id: one overlay *instance* per folder, so switching folders doesn't inherit the
            // previous one's remembered state (its reorder gap, optimistic order, measured geometry, and — most
            // visibly — its pager position, which would otherwise render a 1-page folder scrolled past its end).
            if (openFolder != null) key(openFolder.folder.id) {
                FolderOverlay(
                    label = openFolder.folder.label,
                    apps = openFolder.apps,
                    coordinator = coordinator,
                    gestureConfig = gestureConfig,
                    incoming = incomingApp,
                    onLaunch = { component -> viewModel.launch(component); folderHost.close() },
                    onReorder = { order ->
                        val incoming = folderHost.incomingComponent
                        if (incoming != null && order.contains(incoming)) {
                            // Injected an app from home: add it to the folder at its slot + take it off the grid.
                            // Keep the folder open afterwards so the user sees the app land in it.
                            viewModel.addToFolder(openFolder.folder.id, order, incoming)
                            folderHost.injectCommitted()
                        } else {
                            viewModel.reorderFolder(openFolder.folder.id, order)
                        }
                    },
                    onExtractStart = { component -> folderHost.beginExtract(openFolder.folder.id, component) },
                    onDrop = { handleDrop() },
                    onPublishDelegate = { folderDelegate.value = it },
                    onDismiss = { folderHost.close() },
                )
            }
        }
    }
}
