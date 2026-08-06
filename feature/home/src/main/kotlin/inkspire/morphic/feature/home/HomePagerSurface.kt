package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.drag.DragSession
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderDragDelegate
import inkspire.morphic.core.designsystem.folder.FolderOverlay
import inkspire.morphic.core.designsystem.folder.FolderPhase
import inkspire.morphic.core.designsystem.folder.rememberFolderHostState
import inkspire.morphic.core.designsystem.grid.CoordinateDragGrid
import inkspire.morphic.core.designsystem.grid.CoordinateDragPager
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.splitForSideZone
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DockGrid
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.LayoutChange
import kotlin.math.roundToInt

/**
 * The drag identities of home's two coordinate zones. Named for the *zone*, not the screen: both live on the same
 * surface and the same [DragCoordinator], so "home" alone no longer distinguishes them — and the name is now free
 * for [HomeZone], the persisted zone each maps to via [homeZoneOf].
 */
private val MainZoneId = ZoneId("home-main")
private val DockZoneId = ZoneId("home-dock")

/**
 * Which persisted [HomeZone] a drop zone writes to, or null when the zone is not one of home's own grids — today
 * that means the open folder's zone, whose drops are the folder's own reorder rather than a placement.
 *
 * This is the whole of "cross-zone drag": a drop carries the zone it landed in, that zone names a [HomeZone], and
 * the resulting `Move` writes it. Because the placement tables key on the item (not on item + zone), the same row
 * is simply re-stamped with the new zone — dragging an app from the pager into the dock needs no extra command and
 * no schema change.
 */
private fun homeZoneOf(zoneId: ZoneId): HomeZone? = when (zoneId) {
    MainZoneId -> HomeZone.MAIN
    DockZoneId -> HomeZone.DOCK
    else -> null
}

/**
 * **`HomeLayout.PAGER_WITH_DOCK`** — two coordinate zones stacked in one lattice: a **paged**
 * [CoordinateDragPager] main area beside a single, non-paged [CoordinateDragGrid] **dock**, with
 * **drag-to-rearrange that persists**. Long-press an app to lift it; the free-grid planner ([FreeGridPlanner])
 * pushes the hovered zone's occupants out of the way (previewed live, dwelled so a fast drag doesn't strobe), and the
 * drop commits through [HomeViewModel.applyChanges] as `Move` commands — which update the surface instantly
 * (optimistic) and persist to Room. Dragging to a side edge flips pages; a trailing empty page appears mid-drag so an
 * app can be carried onto a new page.
 *
 * One of two HOME arrangements, chosen by [HomeScreen]; the other is [HomeListSurface]. They share this module's
 * ViewModel and state and nothing else, because they share no gesture, no store and no planner — a coordinate
 * surface asks "which cell, and who gets shoved aside?" where an ordered one asks "which index?".
 *
 * **Why both zones share one `DragCoordinator`.** Because they do, dragging an app from the pager into the dock (or
 * back) is not a special case at all: it is one uninterrupted gesture, the drop reports *which* zone it landed in,
 * and [homeZoneOf] turns that into the [HomeZone] the `Move` writes. Nothing hands the drag over, and there is no
 * second gesture recogniser to keep in sync — the structural fix for L1's `HomeDragBridge` re-tracking hack. The
 * same is true of the open folder, which is simply a third zone on the same coordinator.
 *
 * This screen keeps only what is home-specific: the [DropPlanner] (which zone is being hovered, and that zone's
 * geometry/occupants — the planning itself is shared, see [planCoordinateDrop]), the root drag overlay, and the
 * tap→launch / merge-drop wiring; the grids, gestures, dwelled preview, and edge-flip live in [CoordinateDragPager]
 * / [CoordinateDragGrid], and the folder-interaction lifecycle in [FolderHostState]. Dropping an app onto another
 * (finger in its centre ring) **merges** them into a folder; folders render as a [FolderCell] and tapping one opens
 * a [FolderOverlay]. A tap on an app launches it (via [HomeViewModel.launch]) through the gesture layer's `onOpen`,
 * so cells carry no click handler of their own.
 *
 * **Folders are places one drag passes through, not destinations it commits to.** Holding a dragged app on a folder's
 * merge ring opens that folder and the drag carries on inside it; holding outside the card closes it again and the
 * drag carries on over the grids — repeatable, in either direction, over any number of folders, because neither half
 * writes anything. Only the drop does, and [handleDrop] decides what it meant from *where the drag started* rather
 * than from a trail of hand-offs: a drag out of a folder is placed-and-removed, a drag off a grid is moved.
 *
 * The dock is a **peer of the main area, not a lesser strip**: it takes apps, folders and (once they exist) widgets,
 * it merges into folders, and a folder living in it opens, reorders, and hands apps in and out exactly as one in the
 * pager does — every one of those paths is zone-generic rather than duplicated per zone.
 *
 * First cut: apps + folders, portrait only. The dock **starts empty** (the first-run seed deliberately fills only the
 * main area) and is filled by dragging into it.
 *
 * @param device reported by [HomeScreen], which is the layer that can read the window; everything sized per device is
 *   resolved in the ViewModel from it.
 */
@Composable
internal fun HomePagerSurface(
    viewModel: HomeViewModel,
    state: HomeState,
    device: DeviceConfiguration,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Everything sized per device — both grids, both zones' icon sizing — is resolved in the ViewModel, from the
    // settings store, and arrives in the state; the device itself is reported by [HomeScreen], which is the one
    // layer that can read the window. An app occupies one whole visual cell, i.e. a cellMultiplier × cellMultiplier
    // logical footprint.
    //
    // **The blueprint is a fallback for the first frame, not the source.** This screen used to resolve
    // `HomePagerGrid.toGridConfig(device)` and draw that, so the home grid section wrote a size nothing read — and
    // moved the placements to match a grid nothing was drawing. The store answers a frame or two later; until it
    // does, the blueprint's default is the same number it would resolve to for a user who has changed nothing.
    val blueprintConfig = remember(device) { HomePagerGrid.toGridConfig(device) }

    // The area the grids are actually given: the window minus the insets the column below pads by. **One value feeds
    // every use**, which is the point — L1 derived its home area from settings in one place (`homeGridArea`) and
    // measured it in another (`pagerBoundsInWindow`), so the size it fitted the grid to and the size it drew into
    // could disagree. Here they cannot: `uiInsets` is the same expression throughout, and the measurement itself is
    // now the shared `usableWindowArea` that the settings sections and the APPS surface read — so a bound computed for
    // this screen somewhere else describes the same screen.
    val window = usableWindowArea(uiInsets)

    // **Where the dock sits, which decides everything below it.** A bottom strip on three configurations and a rail on
    // the trailing edge in phone landscape — the one posture with no height to spare. The split is a single expression
    // the two settings sections read as well, so the area home draws into and the area they bound its rows against
    // cannot disagree.
    val dockEdge = device.sideZoneEdge(HomeLayout.PAGER_WITH_DOCK)
    val dockMetrics = state.metricsFor(GridSlot.HOME_DOCK)
    val dockSizing = state.side
    val dockExtent = (dockSizing?.extentDp ?: checkNotNull(DockGrid.extentDp)).dp
    val split = window.splitForSideZone(dockExtent.value, dockEdge)

    // **Padding is width the grid does not get, so it is subtracted before anything is fitted.** Each zone has its
    // own — a user may inset the pager without touching the dock — and each is taken off *twice*, once per edge. Doing
    // it here rather than at the draw call is the whole point: `fitGridConfig` decides how many columns the icons can
    // occupy, and fitting against the full width would size cells the grid then has no room to draw.
    //
    // Horizontal on both zones whichever edge the dock is on: a rail's margin insets *within* its width, which is what
    // a "side margin" means on a vertical strip too.
    val mainPadding = state.paddingFor(GridSlot.HOME_MAIN).dp
    val dockPadding = state.paddingFor(GridSlot.HOME_DOCK).dp
    val mainArea = split.main.copy(widthDp = (split.main.widthDp - mainPadding.value * 2).coerceAtLeast(1f))
    val dockArea = split.side.copy(widthDp = (split.side.widthDp - dockPadding.value * 2).coerceAtLeast(1f))

    // The dock is the one grid with an extent of its own: the user sets how thick the strip is *and* how many rows and
    // columns divide it, and `fitGridConfig` clamps those counts to what the extent and the icon size actually allow.
    // On a rail that bound falls on the columns rather than the rows, which needs no branch here — the extent is in
    // the area's width, and `CellFit` fits each axis to the dimension it is given.
    //
    // The blueprint stands in until the store answers, which is the fallback role `DockGrid.defaults` exists for.
    val dockBlueprintConfig = remember(device) { DockGrid.toGridConfig(device) }
    val dockConfig = if (dockSizing == null) {
        dockBlueprintConfig
    } else {
        DockGrid.fitGridConfig(
            area = dockArea,
            cols = dockSizing.cols,
            rows = dockSizing.rows,
            metrics = dockMetrics,
        )
    }
    val dockCellSpan = dockConfig.cellMultiplier

    // **The pager is fitted to what the dock leaves it**, through the same `fitGridConfig` the dock reads its own
    // counts with — which is the half that was missing. The main area is the only grid that was drawn at its stored
    // size regardless of the space available, so raising the dock's height simply squeezed home's rows into whatever
    // was left instead of reducing them, and past a point the cells were shorter than the icon they hold.
    //
    // The area is the window minus the dock, and it is deliberately the same expression the Home settings section
    // computes its bounds from: the section says how many rows may be *chosen*, this says how many are *drawn*, and
    // one formula is what keeps those two answers the same.
    //
    // **Clamped on read, never written back**, exactly as the dock's columns are: the count the user chose survives,
    // so shortening the dock again brings the rows straight back. Only the *items* the smaller grid cannot hold are
    // written, by `fitMainTo` below — and they go to a further page rather than being dropped, since home always has
    // one (the dock, a single strip, has to evict to home instead).
    val mainMetrics = state.metricsFor(GridSlot.HOME_MAIN)
    val storedMain = (state.main as? HomeMainSizing.Pager)?.config
    val config = if (storedMain == null) {
        blueprintConfig
    } else {
        HomePagerGrid.fitGridConfig(
            area = mainArea,
            cols = storedMain.visualCols,
            rows = storedMain.visualRows,
            metrics = mainMetrics,
        )
    }
    val cellSpan = config.cellMultiplier

    // Both zones re-settle whenever their grid changes, and for the same reason: a smaller grid has cells that may
    // hold items. Idempotent — a grid everything already fits writes nothing — which is why neither needs a "did it
    // shrink?" test, and why a dock-height change can simply flow through both.
    //
    // **Neither runs until the store has answered for that zone**, and the guard is load-bearing rather than tidy: a
    // blueprint fallback is a *smaller* grid than a user who has grown theirs, so settling against it on the frame
    // before the first emission would re-home items to fit a size nobody chose — and the write would outlive the
    // frame that caused it.
    if (storedMain != null) LaunchedEffect(config) { viewModel.fitMainTo(config) }
    if (dockSizing != null) LaunchedEffect(dockConfig) { viewModel.fitDockTo(dockConfig) }

    val gestureConfig = remember {
        ItemGestureConfig(touchSlopPx = with(density) { 20.dp.toPx() }, longPressTimeoutMillis = 400L)
    }

    // One measured geometry per zone — each grid publishes its own, and a planner or overlay must read the one
    // belonging to the zone it is describing (they have different origins, cell sizes, and dimensions).
    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    var dockGeometry by remember { mutableStateOf<GridGeometry?>(null) }
    // Derived views of the placed items, keyed on the list so they survive a recomposition that didn't change it.
    // This matters more than it looks: every cell reads the coordinator's session, so a drag recomposes this
    // screen on *every finger move* — anything derived here without a `remember` is rebuilt at frame rate.
    //
    // Scoped to MAIN: each zone is its own coordinate space, so a zone's grid, planner occupants, and page count
    // must all be built from that zone's items alone — mixing zones would have items collide at equal placements.
    val mainItems = remember(state.items) { state.inZone(HomeZone.MAIN) }
    val dockItems = remember(state.items) { state.inZone(HomeZone.DOCK) }
    val placements = remember(mainItems) { mainItems.associate { it.gridItem to it.placement } }
    val dockPlacements = remember(dockItems) { dockItems.associate { it.gridItem to it.placement } }
    // Folders across *every* zone: they are opened, hovered, and merged into the same way wherever they sit, so the
    // zone is a field to match on rather than a reason to keep separate lists (an earlier cut scoped this to MAIN and
    // a tapped dock folder had its id set on the host with nothing to render).
    val folders = remember(state.items) { state.items.filterIsInstance<HomeItem.Folder>() }
    // The current placement maps, read live by the planner while a drag is in flight.
    val livePlacements = rememberUpdatedState(placements)
    val liveDockPlacements = rememberUpdatedState(dockPlacements)

    // Pager: page count is (highest occupied page + 1), plus one trailing empty page while a *home* drag is in
    // flight, so an app can be carried onto a brand-new page. `draggingPages` is synced from the coordinator
    // below, because the coordinator doesn't exist yet here and so can't be read directly inside the count lambda.
    val maxPage = rememberUpdatedState(remember(mainItems) { mainItems.maxOfOrNull { it.placement.page } ?: 0 })
    var draggingPages by remember { mutableStateOf(false) }
    val pagerState = rememberLauncherPagerState(
        pageCount = { maxPage.value + 1 + if (draggingPages) 1 else 0 },
        infiniteScroll = { false },
    )

    // **Where HOME is scrolled, for the surface swipe.** A one-finger swipe toward a side surface crosses this pager
    // first, so it may only leave HOME once the pager has no page left in that direction — L1's
    // `swipeRightOpensLeft = currentPage == 0`, now stated as where the content is rather than as which gesture is
    // released. Vertically nothing scrolls (the dock is a fixed strip), so the pair defaults to "at both edges" and
    // a swipe onto a TOP or BOTTOM surface is free.
    ReportScrollEdges { ScrollEdges(atLeft = pagerState.atFirstPage, atRight = pagerState.atLastPage) }

    // The open folder's drag hooks (null when no folder is open). The one shared coordinator runs over both
    // surfaces; its planner + drop dispatch the folder zone to this delegate, keeping folder reorder logic in
    // the overlay (its order/gap aren't hoisted here). It lives here rather than on the folder host below because
    // the planner reads it and must exist before the coordinator, which the host is created after.
    val folderDelegate = remember { mutableStateOf<FolderDragDelegate?>(null) }

    // Shared planner, dispatched by zone: home's two coordinate grids run the same [planCoordinateDrop] over their
    // own geometry/config/occupants, and anything else is the open folder's, which plans its own reorder. The dock
    // pins `page = 0` — it is a single grid, so it has no other page.
    val planner = remember(config, dockConfig) {
        DropPlanner { zone, item, fingerInRoot ->
            when (zone.id) {
                MainZoneId -> {
                    val geo = geometry ?: return@DropPlanner null
                    val page = pagerState.currentPage
                    planCoordinateDrop(
                        geo = geo,
                        config = config,
                        page = page,
                        occupants = livePlacements.value.filterKeys { it != item }.filterValues { it.page == page },
                        item = item,
                        fingerInRoot = fingerInRoot,
                    )
                }
                DockZoneId -> {
                    val geo = dockGeometry ?: return@DropPlanner null
                    planCoordinateDrop(
                        geo = geo,
                        config = dockConfig,
                        page = 0,
                        occupants = liveDockPlacements.value.filterKeys { it != item },
                        item = item,
                        fingerInRoot = fingerInRoot,
                    )
                }
                else -> folderDelegate.value?.onHover(item, fingerInRoot)
            }
        }
    }
    val coordinator = rememberDragCoordinator(planner)

    // The dragged item + hovered plan drive the root overlay below; the dwelled push preview + edge page-flip
    // live inside CoordinateDragPager.
    val session = coordinator.session

    // Folder hosting: which folder is on screen, and what the drag in flight owes the folder it started in.
    // `folderIdAt` is home's answer to "which folder does this merge plan target?" — a match on **zone + placement**,
    // because home is a coordinate surface whose zones each have their own coordinate space, so the placement alone
    // would match a folder sitting at the same cell of the *other* grid.
    //
    // Answering for both zones is what makes the folder hand-offs continuous across the dock: hovering a dock folder
    // arms the same dwell that opens it mid-drag. The outbound half needs nothing here — closing the folder drops its
    // zone, so the drag simply lands on whichever home grid is underneath, and `handleDrop` commits to the zone it
    // reports.
    val folderHost = rememberFolderHostState<Long>(coordinator) { zoneId, plan ->
        val zone = homeZoneOf(zoneId) ?: return@rememberFolderHostState null
        folders.firstOrNull { it.zone == zone && it.placement == plan.footprint }?.folder?.id
    }
    // The app being carried into the open folder, resolved for the overlay to render. Keyed on the *component only*,
    // deliberately not on `state.items`: an inject begins with the app still placed on home, and committing it takes
    // it off the grid optimistically — so re-deriving from the items afterwards would resolve to null and the app
    // would vanish from both surfaces until the write came back. Resolve once, at the start, and hold it.
    // [appInfo] searches folder contents as well as placements, for the app that arrives from another folder.
    val incomingApp = remember(folderHost.incomingComponent) {
        folderHost.incomingComponent?.let(state::appInfo)
    }
    // The app under the finger, for home's floating proxy — resolved once per drag and held, for the same reason as
    // `incomingApp` above: it may be a folder member with no placement, and the commit that lands it optimistically
    // rewrites the items it would otherwise be re-derived from.
    val draggedComponent = (session?.item as? GridItem.App)?.component
    val draggedApp = remember(draggedComponent) { draggedComponent?.let(state::appInfo) }

    // Is a drag in flight that could still land on home? Not one inside an open folder — a reorder in there is that
    // surface's gesture and must not grow home's pager behind it. Once the folder closes (the leave dwell) the same
    // drag is home's again and the trailing page appears, which is exactly when it can be carried onto a new page.
    val homeDragInFlight = coordinator.isDragging && folderHost.openFolderId == null
    LaunchedEffect(homeDragInFlight) { draggingPages = homeDragInFlight }

    // Where a drag comes to rest. Four landings, in the order they have to be told apart; every one of them is
    // zone-generic — the outcome names the drop zone, `homeZoneOf` turns it into the zone to write, and the same
    // commit serves the pager and the dock. That is what makes a home↔dock drag ordinary rather than a special case,
    // and it is why one coordinator spans both zones. (`coordinator.drop()` already reports a null outcome for a
    // release over no zone or an INVALID plan, so anything non-null here is a real landing.)
    fun handleDrop() {
        // Read before dropping: the source is cleared when the drag ends, which the drop is.
        val sourceFolderId = folderHost.dragSourceFolderId
        val presentedFolderId = folderHost.openFolderId
        val outcome = coordinator.drop()
        val zone = outcome?.let { homeZoneOf(it.zone) }

        // 1. Inside the open folder → its own business: a reorder, which is also how an inject commits.
        if (outcome != null && zone == null) {
            folderDelegate.value?.commitReorder(outcome.item)
            return
        }
        // 2. Released outside the folder that is on screen. Leaving a folder is a deliberate dwell, so a release out
        //    here is "never mind": close it and write nothing. It could not be honoured anyway — an app being carried
        //    inside a folder has no grid placement, so *placing* it would leave it in the folder **and** on the grid.
        if (presentedFolderId != null) {
            folderHost.close()
            return
        }
        if (outcome == null || zone == null) return
        val plan = outcome.plan
        // 3. The drag started inside a folder and has landed out here. The app has no placement to move, so it is
        //    placed and removed from that folder in one batch — including onto a merge ring, which is how it moves
        //    straight into another folder (or combines with an app to make one) in a single gesture.
        if (sourceFolderId != null) {
            val app = (outcome.item as? GridItem.App)?.component ?: return
            if (plan.intent == DropIntent.MERGE) {
                viewModel.mergeExtractedApp(sourceFolderId, app, plan.footprint, zone)
            } else {
                viewModel.dropExtractedApp(sourceFolderId, app, plan, zone)
            }
            return
        }
        // 4. An ordinary grid drag.
        if (plan.intent == DropIntent.MERGE) {
            viewModel.mergeChanges(outcome.item, plan.footprint, zone)?.let(viewModel::applyChanges)
        } else {
            // The pushed occupants are already in the drop zone, so they move within it; the dragged item may be
            // arriving from the other zone, and this is the write that re-stamps it.
            val moves = plan.moves.map { (moved, to) -> LayoutChange.Move(moved, to, zone) } +
                LayoutChange.Move(outcome.item, plan.footprint, zone)
            viewModel.applyChanges(moves)
        }
    }

    // Opening an item is the same wherever it sits, so both zones share one handler.
    val openItem: (HomeItem) -> Unit = { item ->
        when (item) {
            is HomeItem.App -> viewModel.launch(item.info.componentKey)
            is HomeItem.Folder -> folderHost.open(item.folder.id)
        }
    }

    // No `LauncherTheme` here: the launcher **zone** is themed once by `feature:shell`'s `LauncherShell`, which is
    // also the only layer that knows the launcher's real dark/light input (wallpaper brightness, not the system
    // setting). A screen that themed itself could not be told to disagree with the shell.
    //
    // **And no background either — home is the wallpaper.** The launcher's window shows it (`windowShowWallpaper` in
    // `app`'s theme), so painting anything opaque here would hide the thing the user chose. It was opaque only while
    // the window was: a placeholder for a wallpaper that could not appear yet. What is drawn over it stays legible by
    // its own means — cell labels carry a shadow, and the folder's scrim is its own.
    Box(modifier.fillMaxSize()) {
        // **The two zones, stacked along the dock's own axis** — see [HomeZoneScaffold], which owns the
        // arrangement, the `uiInsets` padding on the pair, and each zone's own horizontal margin (S4g). The margins
        // reaching each grid's *own* modifier is what keeps drag and drop correct for free: both drag surfaces
        // publish their geometry from an `onGloballyPositioned` placed after the caller's modifier
        // (`CoordinateDragGrid`'s KDoc says so in as many words), so the bounds they report are already the padded
        // ones.
        HomeZoneScaffold(
            edge = dockEdge,
            extent = dockExtent,
            mainPadding = mainPadding,
            sidePadding = dockPadding,
            // The dock: a single, non-paged coordinate zone on the *same* coordinator, so a drag between it and the
            // pager is one gesture with no hand-off. Its extent is the user's setting, and the count it divides that
            // extent into is clamped to it rather than stored — see `dockConfig` above.
            side = { zoneModifier ->
                CoordinateDragGrid(
                    items = dockItems,
                    config = dockConfig,
                    coordinator = coordinator,
                    zoneId = DockZoneId,
                    gestureConfig = gestureConfig,
                    dragItem = { it.gridItem },
                    placement = { it.placement },
                    onDrop = { handleDrop() },
                    modifier = zoneModifier,
                    onGeometryChange = { dockGeometry = it },
                    onOpen = openItem,
                ) { item, cellModifier, itemGestures ->
                    HomeItemCell(item, session, cellModifier, itemGestures, dockMetrics)
                }
            },
            main = { zoneModifier ->
                CoordinateDragPager(
                    items = mainItems,
                    config = config,
                    pagerState = pagerState,
                    coordinator = coordinator,
                    zoneId = MainZoneId,
                    gestureConfig = gestureConfig,
                    dragItem = { it.gridItem },
                    placement = { it.placement },
                    onDrop = { handleDrop() },
                    modifier = zoneModifier,
                    onGeometryChange = { geometry = it },
                    onOpen = openItem,
                ) { item, cellModifier, itemGestures ->
                    HomeItemCell(item, session, cellModifier, itemGestures, mainMetrics)
                }
            },
        )

        // Drag overlay (root space): the drop shadow in the grid + the floating proxy on the finger. The two
        // are gated separately, because they answer different questions — "is there a cell of *this* grid to
        // shadow?" and "whose job is it to draw the icon under the finger?".
        val geo = geometry
        if (session != null && geo != null) {
            // The proxy spans `cellSpan` logical cells per axis (one visual cell) of the *pager's* grid, and
            // deliberately keeps that size across the whole drag: the icon under the finger must not resize as
            // the drag crosses into the dock, whose cells are a different height.
            val footprintW = geo.cellW * cellSpan
            val footprintH = geo.cellH * cellSpan
            // The shadow, unlike the proxy, belongs to the zone being hovered, so it is drawn from *that*
            // zone's geometry and cell span — the two grids have different origins and cell sizes, and a
            // footprint is only meaningful in the grid that produced it.
            //
            // Anything that isn't one of home's grids paints nothing. On the shared coordinator the open
            // folder previews a reorder by reflowing its own cells and returns a token plan whose footprint is
            // meaningless, which would otherwise paint a shadow at cell (0,0) behind the folder (invisible
            // today only because that backdrop is opaque black — it won't be once the frosted backdrop lands).
            // Extract is unaffected: its active zone really is a home grid, which is exactly when the landing
            // cell should show.
            val shadow = when (session.activeZone) {
                MainZoneId -> geo to cellSpan
                DockZoneId -> dockGeometry?.let { it to dockCellSpan }
                else -> null
            }
            if (shadow != null) {
                val (shadowGeo, shadowSpan) = shadow
                session.plan?.let { plan ->
                    val topLeft = shadowGeo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            .size(
                                with(density) { (shadowGeo.cellW * shadowSpan).toDp() },
                                with(density) { (shadowGeo.cellH * shadowSpan).toDp() },
                            ),
                    )
                }
            }
            // The proxy belongs to whichever surface is *presenting* the drag: while a folder is on screen that is
            // the folder, which draws the app at its own cell size; the moment it closes, home takes the icon
            // back. Exactly one of them paints, so a hand-off never puts two icons under one finger and never
            // leaves none. Note this is deliberately not gated on the active zone: a home drag can be held
            // somewhere no zone covers (the system-bar inset below the dock), and its proxy must still follow the
            // finger.
            //
            // The dragged *folder* still resolves by placement, but the dragged *app* cannot: once a drag has left
            // a folder the app is a member with no cell of its own, so it is looked up through [appInfo], which
            // searches folder contents too. Without that the icon vanishes the instant the folder closes.
            val draggedFolder = state.items.firstOrNull { it.gridItem == session.item } as? HomeItem.Folder
            if ((draggedApp != null || draggedFolder != null) && folderHost.openFolderId == null) {
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
                    if (draggedApp != null) {
                        AppCell(app = draggedApp, modifier = Modifier.fillMaxSize())
                    } else if (draggedFolder != null) {
                        FolderCell(
                            label = draggedFolder.folder.label,
                            apps = draggedFolder.apps,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Folder overlays, drawn above the grids. Resolved live from state so their contents track edits.
        val openFolder = folderHost.openFolderId?.let { id -> folders.firstOrNull { it.folder.id == id } }
        // Report the folder's persisted membership back: it is what tells the host that a just-injected app has
        // landed, so it can stop being carried separately.
        val openFolderMembers = openFolder?.folder?.apps
        LaunchedEffect(openFolderMembers) { folderHost.onMembersChanged(openFolderMembers.orEmpty()) }

        // Usually one overlay. But a drag that started inside a folder keeps that folder composed for its whole
        // life, even while a different one — or none — is on screen: the cell driving the drag is in its grid, and
        // a pointer stream can't move to another node. It is rendered as a pointer holder (`presenting = false`):
        // invisible, zone-less, no proxy. See `FolderHostState.dragSourceFolderId`.
        val holderFolder = folderHost.dragSourceFolderId
            ?.takeIf { it != folderHost.openFolderId }
            ?.let { id -> folders.firstOrNull { it.folder.id == id } }
        // Holder first so it sits *below* the presented folder. Both come from this **one** call site on purpose:
        // when a folder stops being the presented one and becomes the holder, a second call site would be a
        // different composition position and Compose would dispose it — killing the very drag this preserves.
        // Keyed by folder id so each folder still gets its own instance and none inherits another's remembered
        // state (reorder gap, optimistic order, measured geometry, and — most visibly — pager position, which
        // would otherwise render a 1-page folder scrolled past its end).
        val overlays = listOfNotNull(holderFolder?.let { it to false }, openFolder?.let { it to true })
        overlays.forEach { (folder, presenting) ->
            key(folder.folder.id) {
                FolderOverlay(
                    label = folder.folder.label,
                    apps = folder.apps,
                    coordinator = coordinator,
                    gestureConfig = gestureConfig,
                    // Only the presented folder carries the app being brought in; to the holder it is still a member.
                    incoming = if (presenting) incomingApp else null,
                    presenting = presenting,
                    onLaunch = { component -> viewModel.launch(component); folderHost.close() },
                    onReorder = { order ->
                        // Only an inject *still in flight* adds membership; once committed this is a plain reorder
                        // (the app is already a member, even if the store hasn't said so yet).
                        val incoming = (folderHost.phase as? FolderPhase.Injecting<*>)?.app
                        if (incoming != null && order.contains(incoming)) {
                            // The app landed in this folder at its chosen slot. `from` is the folder the drag
                            // started in, if any — that is the folder-to-folder move, committed as one batch; null
                            // when it came off a grid instead, and this folder itself when the drag left and came
                            // back, which `addToFolder` recognises as the plain reorder it is.
                            viewModel.addToFolder(
                                folderId = folder.folder.id,
                                reported = order,
                                incoming = incoming,
                                from = folderHost.dragSourceFolderId,
                            )
                            folderHost.injectCommitted()
                        } else {
                            viewModel.reorderFolder(folder.folder.id, order)
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

/**
 * The cell for one placed home item, shared by every zone that renders them — the pager and the dock draw an app
 * or a folder identically, and a cell that differed per zone would make an item change appearance simply by being
 * dragged across the screen.
 *
 * @param session the live drag, needed only to hide a dragged-out app from a folder's tile preview (see below).
 * @param cellModifier fills the cell's layout footprint.
 * @param itemGestures must be handed to whatever should be *touchable*; the cells pass it to their icon+label
 *   group, leaving the surrounding slack free for the surface's own gestures.
 */
@Composable
private fun HomeItemCell(
    item: HomeItem,
    session: DragSession?,
    cellModifier: Modifier,
    itemGestures: Modifier,
    metrics: IconMetrics,
) {
    when (item) {
        is HomeItem.App ->
            AppCell(app = item.info, modifier = cellModifier, metrics = metrics, itemGestures = itemGestures)
        is HomeItem.Folder -> {
            // Hide the app currently being dragged (e.g. extracted out of this folder) from the tile preview, so it
            // isn't shown in the folder icon and under the finger at the same time. The real folder removal commits
            // on drop — removing it now would dispose the dragged cell (it lives in the folder's grid) and kill the
            // drag. (Mirrors L1's removedFromFolder.)
            val dragged = (session?.item as? GridItem.App)?.component
            val preview = if (dragged == null) item.apps else item.apps.filterNot { it.componentKey == dragged }
            FolderCell(
                label = item.folder.label,
                apps = preview,
                metrics = metrics,
                modifier = cellModifier,
                itemGestures = itemGestures,
            )
        }
    }
}
