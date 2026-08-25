package inkspire.morphic.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.collection.AppAdditions
import inkspire.morphic.core.designsystem.collection.AppCollectionOverlay
import inkspire.morphic.core.designsystem.collection.AppCollectionPhase
import inkspire.morphic.core.designsystem.collection.rememberAppCollectionHostState
import inkspire.morphic.core.designsystem.drag.DragSession
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.grid.CoordinateDragGrid
import inkspire.morphic.core.designsystem.grid.CoordinateDragPager
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.GridSpan
import inkspire.morphic.core.designsystem.grid.InnerCellItem
import inkspire.morphic.core.designsystem.grid.ResizeBounds
import inkspire.morphic.core.designsystem.grid.ResizeOverlay
import inkspire.morphic.core.designsystem.grid.clampToGrid
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.menu.MenuAction
import inkspire.morphic.core.designsystem.menu.surfaceMenuGestures
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.core.model.asItemGesture
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.WidgetSpan
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.data.widgets.WidgetResizeRules
import inkspire.morphic.feature.home.widgetpicker.WidgetPickerSheet
import inkspire.morphic.feature.home.widgetpicker.rememberWidgetAddFlow
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The drag identities of home's two coordinate zones. Named for the *zone*, not the screen: both live on the same
 * surface and the same [DragCoordinator], so "home" alone no longer distinguishes them — and the name is now free
 * for [HomeZone], the persisted zone each maps to via [homeZoneOf].
 */
private val MainZoneId = ZoneId("home-main")
private val DockZoneId = ZoneId("home-dock")

/**
 * What a folder with no name of its own is called in its context menu.
 *
 * A folder created by dropping one app on another starts unnamed (rename is still to come), and a menu whose title
 * is blank reads as a rendering fault. It is *not* used where the folder is drawn — a cell with no label under it is
 * simply an unnamed folder, and inventing a name there would put a word on screen the user never chose.
 */
internal const val UnnamedFolder = "Folder"

/** The same fallback for a widget whose provider publishes no label. Internal, since a container's pages draw one. */
internal const val UnnamedWidget = "Widget"

/**
 * Menu titles for the two containers.
 *
 * Constants rather than a label read off the item, because **a container has no name**: nothing gives it one and
 * nothing stores one. That is not the [UnnamedFolder] case, where a real (empty) label exists and this stands in
 * for it — here the title simply says what the thing is, which is all a menu needs in order not to open headless.
 */
internal const val IconContainerTitle = "Icon container"
internal const val WidgetContainerTitle = "Widget container"

/**
 * A resize in progress on one of home's coordinate zones — **the resolved outcome, not the finger's request**.
 *
 * Every handle move is turned into one of these by [resolving], so the frame the user sees is always something
 * the grid would actually accept: the request is clamped to the grid, the planner is asked whether the occupants
 * in the way can be pushed clear, and only an accepted rectangle replaces [placement]. A request that fails
 * either test leaves the frame exactly where it was and sets [refused], which is the whole of the "you cannot go
 * there" feedback.
 *
 * @property item what is being resized — held rather than looked up, because a resize outlives any one frame of
 *   the state it started from.
 * @property rules the provider's own limits, in **pixels**. Kept in the provider's units rather than converted to
 *   cells when the menu was built, so the minimums follow the grid: the cells they are measured against can change
 *   under an open frame (a rotation, a settings edit), and a span frozen at menu time would then be wrong.
 * @property placement the last cells the grid accepted: what the frame draws, what the item is drawn at, and what
 *   a commit writes.
 * @property moves where each pushed occupant would go — the **live preview**, and half of what a commit writes.
 *   Emptied by a commit, since the pushed cells are then the stored ones.
 * @property refused the last request was out of the grid, or needed room the planner could not clear. Drawn in
 *   the error color.
 */
internal data class HomeResize(
    val item: GridItem,
    val zone: HomeZone,
    val rules: HomeResizeRules,
    val placement: GridPlacement,
    val moves: Map<GridItem, GridPlacement> = emptyMap(),
    val refused: Boolean = false,
) {
    /** Where [other] renders while this resize is live: its pushed cell, the frame's own cells, or nowhere new. */
    fun previewOf(other: GridItem): GridPlacement? = if (other == item) placement else moves[other]
}

/**
 * This resize with [candidate] taken into account — the one place a handle drag becomes a decision.
 *
 * Two tests, and failing either keeps [HomeResize.placement] where it is rather than moving the frame somewhere
 * that could not be committed: the rectangle is pulled inside [config] (an over-drag), and [plan] is asked what
 * the push would cost (null meaning the occupants cannot be cleared). Note that a clamped rectangle is still
 * *accepted* — the frame follows the finger up to the grid's edge and reports the clamp as [HomeResize.refused] —
 * where a blocked one is not.
 *
 * @param plan the planner, taking the clamped rectangle and answering with the moves it would make, or null.
 */
internal fun HomeResize.resolving(
    candidate: GridPlacement,
    config: GridConfig,
    plan: (GridPlacement) -> Map<GridItem, GridPlacement>?,
): HomeResize {
    val clamped = clampToGrid(candidate, config)
    val moves = plan(clamped) ?: return copy(refused = true)
    return copy(placement = clamped, moves = moves, refused = clamped != candidate)
}

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
 * **Why both zones share one `DragCoordinator` — and why it is the launcher's, not this screen's.** Because they
 * share one, dragging an app from the pager into the dock (or back) is not a special case at all: it is one
 * uninterrupted gesture, the drop reports *which* zone it landed in, and [homeZoneOf] turns that into the [HomeZone]
 * the `Move` writes. The coordinator is provided by `feature:shell` now, which extends exactly that property across
 * the *surface* boundary: an app lifted in the APPS drawer lands here through the same hit-test, with no hand-off and
 * no second recognizer, and so no drag bridge. The open folder is simply another zone on it.
 *
 * This screen keeps only what is home-specific: a [DropPlanner] **per zone** (that zone's geometry and occupants —
 * the planning itself is shared, see [planCoordinateDrop]), what a landing in one of its grids *writes*
 * ([commitLanding], which the coordinator dispatches to whichever zone the finger came to rest in), the root drag
 * overlay, and the tap→launch wiring; the grids, gestures, dwelled preview, and edge-flip live in
 * [CoordinateDragPager] / [CoordinateDragGrid], and the folder-interaction lifecycle in [AppCollectionHostState].
 * Dropping an app onto another
 * (finger in its center ring) **merges** them into a folder; folders render as a [FolderCell] and tapping one opens
 * an [AppCollectionOverlay]. A tap on an app launches it (via [HomeViewModel.launch]) through the gesture layer's `onOpen`,
 * so cells carry no click handler of their own.
 *
 * **Folders are places one drag passes through, not destinations it commits to.** Holding a dragged app on a folder's
 * merge ring opens that folder and the drag carries on inside it; holding outside the card closes it again and the
 * drag carries on over the grids — repeatable, in either direction, over any number of folders, because neither half
 * writes anything. Only the drop does, and [commitLanding] decides what it meant from *where the drag started*
 * rather than from a trail of hand-offs: a drag out of a folder is placed-and-removed, a drag off a grid is moved,
 * and an app arriving from the APPS surface — which started in neither — is simply placed, since `Move` is an
 * upsert.
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
    onOpenIconContainerSettings: (Long) -> Unit = {},
    onOpenWidgetContainerSettings: (Long) -> Unit = {},
    /**
     * Opens the action picker for one gesture on one item — a full-screen destination, so `app` performs
     * the navigation and this surface only says which gesture was chosen.
     */
    onAssignGesture: (GridItem, ItemGesture) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current

    // Everything sized per device — both grids, both zones' icon sizing — is resolved in the ViewModel, from the
    // settings store, and arrives in the state; the device itself is reported by [HomeScreen], which is the one
    // layer that can read the window. An app occupies one whole visual cell, i.e. a cellMultiplier × cellMultiplier
    // logical footprint.
    //
    // Turning that into two grid sizes is [rememberHomePagerLayout] — derivation and nothing else, which is what
    // lets it live away from the drag state, the gestures and the measured geometry below.
    val layout = rememberHomePagerLayout(state, device)
    val config = layout.config
    val dockConfig = layout.dockConfig

    // Both zones re-settle whenever their grid changes, and for the same reason: a smaller grid has cells that may
    // hold items. Idempotent — a grid everything already fits writes nothing — which is why neither needs a "did it
    // shrink?" test, and why a dock-height change can simply flow through both.
    //
    // **Neither runs until the store has answered for that zone**, and the guard is load-bearing rather than tidy: a
    // blueprint fallback is a *smaller* grid than a user who has grown theirs, so settling against it on the frame
    // before the first emission would re-home items to fit a size nobody chose — and the write would outlive the
    // frame that caused it.
    if (layout.mainFromStore) LaunchedEffect(config) { viewModel.fitMainTo(config) }
    if (layout.dockFromStore) LaunchedEffect(dockConfig) { viewModel.fitDockTo(dockConfig) }

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

    /**
     * **The dragged item's footprint** — its own placement's spans, or one visual cell of [zoneConfig] when it has
     * no placement at all (an app arriving from the APPS drawer, which is nowhere until it lands).
     *
     * Read live by both planners, because a widget's size is a property of the *item* and not of the grid: an app
     * and a folder are always one visual cell, so assuming that worked until widgets existed and then quietly
     * resized every one of them to a single cell on drop.
     */
    val liveSpanOf = rememberUpdatedState<(GridItem, GridConfig) -> GridSpan>({ item, zoneConfig ->
        state.items.firstOrNull { it.gridItem == item }?.placement
            ?.let { GridSpan(colSpan = it.colSpan, rowSpan = it.rowSpan) }
            ?: GridSpan(zoneConfig.cellMultiplier, zoneConfig.cellMultiplier)
    })
    val liveDockPlacements = rememberUpdatedState(dockPlacements)

    // Pager: page count is (highest occupied page + 1), plus one trailing empty page while a *home* drag is in
    // flight, so an app can be carried onto a brand-new page. `draggingPages` is synced from the coordinator
    // below, because the coordinator doesn't exist yet here and so can't be read directly inside the count lambda.
    val maxPage = rememberUpdatedState(remember(mainItems) { mainItems.maxOfOrNull { it.placement.page } ?: 0 })
    var draggingPages by remember { mutableStateOf(false) }
    // Held live for `rememberLauncherPagerState`'s reason, stated at `maxPage` above: the factory remembers its
    // lambdas once, so reading `state` directly would freeze the pager at whatever was stored on the first
    // composition — which is the blueprint's `false`, before the store has answered.
    val liveWraps = rememberUpdatedState((state.main as? HomeMainSizing.Pager)?.wraps == true)
    val pagerState = rememberLauncherPagerState(
        pageCount = { maxPage.value + 1 + if (draggingPages) 1 else 0 },
        infiniteScroll = { liveWraps.value },
    )

    // **Where HOME is scrolled, for the surface swipe.** A one-finger swipe toward a side surface crosses this pager
    // first, so it may only leave HOME once the pager has no page left in that direction — stated as where the
    // content *is* rather than as which gesture is released.
    // Vertically nothing scrolls (the dock is a fixed strip), so the pair defaults to "at both edges" and
    // a swipe onto a TOP or BOTTOM surface is free.
    ReportScrollEdges { ScrollEdges(atLeft = pagerState.atFirstPage, atRight = pagerState.atLastPage) }

    // **One planner per zone, and no dispatch left.** Both grids run the same [planCoordinateDrop] over their own
    // geometry/config/occupants; the dock pins `page = 0`, being a single grid with no other page. This used to be one
    // `DropPlanner` with a `when (zone.id)` over three cases, the third handing off to the open folder — a shape that
    // stopped scaling the moment the coordinator became the *launcher's* rather than this screen's, since the `when`
    // would then have had to know about the APPS drawer too. A planner now travels with the zone that answers it, so
    // the folder's arm went to `AppCollectionOverlay` and these two are the whole of home's.
    val mainPlanner = remember(config) {
        DropPlanner { item, fingerInRoot ->
            val geo = geometry ?: return@DropPlanner null
            val page = pagerState.currentPage
            planCoordinateDrop(
                geo = geo,
                config = config,
                page = page,
                occupants = livePlacements.value.filterKeys { it != item }.filterValues { it.page == page },
                item = item,
                span = liveSpanOf.value(item, config),
                fingerInRoot = fingerInRoot,
            )
        }
    }
    val dockPlanner = remember(dockConfig) {
        DropPlanner { item, fingerInRoot ->
            val geo = dockGeometry ?: return@DropPlanner null
            planCoordinateDrop(
                geo = geo,
                config = dockConfig,
                page = 0,
                occupants = liveDockPlacements.value.filterKeys { it != item },
                item = item,
                span = liveSpanOf.value(item, dockConfig),
                fingerInRoot = fingerInRoot,
            )
        }
    }
    // **The launcher's one coordinator, provided by the shell** — not this screen's any more. That is what makes an
    // app dragged out of the APPS drawer land here: its zones and home's are in a single registry, hit-tested in one
    // space, so there is no boundary for the drag to cross.
    val coordinator = requireDragCoordinator()

    // The dragged item + hovered plan drive the root overlay below; the dwelled push preview + edge page-flip
    // live inside CoordinateDragPager.
    val session = coordinator.session

    // Folder hosting: which folder is on screen, and what the drag in flight owes the folder it started in.
    // `collectionIdAt` is home's answer to "which folder does this merge plan target?" — a match on **zone + placement**,
    // because home is a coordinate surface whose zones each have their own coordinate space, so the placement alone
    // would match a folder sitting at the same cell of the *other* grid.
    //
    // Answering for both zones is what makes the folder hand-offs continuous across the dock: hovering a dock folder
    // arms the same dwell that opens it mid-drag. The outbound half needs nothing here — closing the folder drops its
    // zone, so the drag simply lands on whichever home grid is underneath, and `commitLanding` commits to the zone it
    // reports.
    val folderHost = rememberAppCollectionHostState<Long>(coordinator) { zoneId, plan ->
        val zone = homeZoneOf(zoneId) ?: return@rememberAppCollectionHostState null
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
    //
    // **And not one happening on a side surface either.** With one coordinator for the whole launcher, `isDragging`
    // is true while the user rearranges the APPS drawer; home is composed behind it and would grow a page nobody can
    // see. The moment that drag is *ejected* the surface closes, home becomes the presented one, and the page
    // appears — which is precisely when an app carried in from the drawer can be dropped onto a new one.
    val presented = LocalSurfacePresented.current
    val homeDragInFlight = presented && coordinator.isDragging && folderHost.openCollectionId == null
    LaunchedEffect(homeDragInFlight) { draggingPages = homeDragInFlight }

    // **What a landing in one of home's grids means.** Three cases, in the order they have to be told apart; all of
    // them zone-generic — the outcome names the drop zone, `homeZoneOf` turns it into the [HomeZone] to write, and
    // the same commit serves the pager and the dock. That is what makes a home↔dock drag ordinary rather than a
    // special case.
    //
    // **It is the zone's handler, not the releasing cell's**, which is the change that lets a drag arrive from
    // somewhere else entirely. An app carried in from the APPS drawer is released by a cell in `feature:apps`, and
    // that cell knows nothing about placements; the coordinator dispatches the landing to whichever zone it fell in,
    // so this runs for a drag lifted on home, in one of its folders, or on another surface, without telling the three
    // apart. `Move` is an upsert, so an app with no placement of its own needs no separate "add" path.
    fun commitLanding(outcome: DropOutcome) {
        val zone = homeZoneOf(outcome.zone) ?: return
        // **Which folder this landing has to take the app out of**, asked of *membership* rather than of where the
        // drag started. For an app lifted inside one of home's folders the two answers agree — nothing is written
        // until the drop, so it is still a member — but they diverge for an app arriving from the APPS drawer, which
        // may already be in a home folder while having been lifted somewhere else. Treating that one as a stranger
        // put it on the grid *and* left it in the folder (the same app twice), and made a merge silently do nothing,
        // because `folder_item` is uniquely indexed by component. Read before any write, since the state below is
        // updated optimistically.
        val landingApp = (outcome.item as? GridItem.App)?.component
        val sourceFolderId = landingApp?.let(viewModel::folderHolding)
        // 1. Released on a grid while a folder is still on screen. Leaving a folder is a deliberate dwell, so a
        //    release out here is "never mind": write nothing. It could not be honored anyway — an app being carried
        //    inside a folder has no grid placement, so *placing* it would leave it in the folder **and** on the grid.
        if (folderHost.openCollectionId != null) {
            folderHost.close()
            return
        }
        val plan = outcome.plan
        // 2. The app is in one of home's folders and has landed out here — whether it was lifted from inside that
        //    folder or carried in from another surface. It is placed and removed from the folder in one batch,
        //    including onto a merge ring, which is how it moves straight into another folder (or combines with an
        //    app to make one) in a single gesture. Dropped back on its own folder this is a no-op, which
        //    `mergeExtractedApp` recognizes.
        if (sourceFolderId != null && landingApp != null) {
            if (plan.intent == DropIntent.MERGE) {
                viewModel.mergeExtractedApp(sourceFolderId, landingApp, plan.footprint, zone)
            } else {
                viewModel.dropExtractedApp(sourceFolderId, landingApp, plan, zone)
            }
            return
        }
        // 3. The item was lifted out of one of home's icon containers. Like case 2 it must leave its holder in the
        //    same batch as it lands, or it is drawn twice — once in the container and once on the grid. Unlike
        //    case 2 it can be a folder as well as an app, which is why it is keyed on the whole item.
        val sourceContainerId = viewModel.iconContainerHolding(outcome.item)
        if (sourceContainerId != null) {
            viewModel.dropExtractedIcon(sourceContainerId, outcome.item, plan, zone)
            return
        }
        // 4. An ordinary grid drag — or an app arriving from the APPS surface that no folder here holds.
        if (plan.intent == DropIntent.MERGE) {
            viewModel.mergeChanges(outcome.item, plan.footprint, zone)?.let(viewModel::applyChanges)
        } else {
            // The pushed occupants are already in the drop zone, so they move within it; the dragged item may be
            // arriving from the other zone (or off another surface), and this is the write that stamps it.
            val moves = plan.moves.map { (moved, to) -> LayoutChange.Move(moved, to, zone) } +
                LayoutChange.Move(outcome.item, plan.footprint, zone)
            viewModel.applyChanges(moves)
        }
    }

    // A cell *of this surface* released the finger. All it does is end the drag — the landing is committed by
    // whichever zone it fell in, which may be one of home's, an open folder's, or none at all. What is left here is
    // the one thing only the releasing surface knows: a folder is on screen and the drag came to rest nowhere, which
    // reads the same as case 1 above and closes it.
    fun handleRelease() {
        val presentedFolderId = folderHost.openCollectionId
        val outcome = coordinator.drop()
        if (presentedFolderId != null && outcome == null) folderHost.close()
    }

    // The widget host, read here because the item menu asks it whether a widget may be resized at all — a live
    // read of the provider rather than anything the layout stores.
    val widgetHost = koinInject<AppWidgetHostController>()

    // The resize frame, when one is up. Null the rest of the time, which is what keeps it out of the way — the
    // overlay consumes every event it sees, so it must exist only while a resize is genuinely being made. It
    // survives each commit and is cleared by a press outside the item, because resizing is several drags rather
    // than one.
    var resizing by remember { mutableStateOf<HomeResize?>(null) }

    // **The resize's live preview, per zone.** A resize is not a drag, so the push preview `CoordinateDragGrid`
    // already runs off the coordinator's plan does not apply — but the picture should be the same one: the
    // occupants a resize would displace stand aside while the finger is still down, and the item itself is drawn
    // at the cells it is being given. Only the zone holding the item previews anything; the other one is not part
    // of this resize at all.
    //
    // **Undwelled, where a drag's push waits `PUSH_DWELL_MS`** — and the difference is in the gesture rather than
    // in the preference. A drag sweeps across cells it does not mean, so its plan changes constantly and the dwell
    // is what stops occupants strobing; a resize edge is deliberate, moves one cell at a time and is *drawn* by
    // the frame the whole while, so occupants that waited would simply sit inside a rectangle that has already
    // claimed them.
    fun resizePreviewIn(zone: HomeZone): HomeResize? = resizing?.takeIf { it.zone == zone }

    // Opening an item is the same wherever it sits, so both zones share one handler.
    val openItem: (HomeItem) -> Unit = { item ->
        when (item) {
            is HomeItem.App -> viewModel.launch(item.info.componentKey)
            is HomeItem.Folder -> folderHost.open(item.folder.id)
            // A widget handles its own taps — its content is another app's views, and every button in it is
            // theirs. The launcher's job here is to *not* intercept.
            is HomeItem.Widget -> Unit
            // **Neither container opens, and that is the design rather than a gap.** A container has no expanded
            // view: its contents are already on screen, so what a tap means is whatever it landed on. An icon
            // container's slots launch and open for themselves (`IconContainerCell`), a widget container's pages
            // are the widget's own — and a tap on the gaps between them is a tap on the container itself, which
            // has nothing to do.
            is HomeItem.IconContainer, is HomeItem.WidgetContainer -> Unit
        }
    }

    // **The context menu, on the launcher's one host.** Home supplies only the verb home owns — *Remove*, because
    // home is where an item is placed — and the host adds the ones every surface shares (the app's own shortcuts,
    // App info, Uninstall). Both zones share this handler for `openItem`'s reason: what an item offers does not
    // depend on which of home's two grids it happens to sit in.
    // Whether the widget picker is up. Surface-local rather than hoisted to `HomeScreen`, for the reason the menu
    // row states: the sheet is told the grid it sizes widgets against, and that grid is this surface's.
    var widgetPickerOpen by remember { mutableStateOf(false) }

    // **What a folder's Add cell offers**: every installed app, sorted, built once for every folder on this
    // surface — the overlay subtracts whatever the folder it is drawn in already holds. Sorted here because
    // `AppPicker` does not re-sort, and a picker over every installed app is unusable in any other order.
    //
    // A folder taking an app in **moves** it rather than copying: `addAppsToFolder` takes it off its home page, or
    // out of the folder that held it, because an app is in one place.
    val offerableApps = remember(state.catalog) { state.catalog.values.sortedBy { it.label.lowercase() } }
    val folderAdditions: (Long) -> AppAdditions = { folderId ->
        AppAdditions(offerableApps) { picked -> viewModel.addAppsToFolder(folderId, picked) }
    }

    // **The page an add just filed something on, held until the pager can reach it.**
    //
    // Every add here searches for room (`HomeViewModel.freeRect`) rather than being given a cell, so a widget or a
    // container routinely lands on a page the user is not looking at — and on a page that *did not exist* a moment
    // ago, since the search grows one rather than refusing. That is the whole reason this is a pending value and not
    // a call: `pageCount` is derived from the items the store has echoed back, and `animateToPage` clamps, so
    // scrolling in the same breath as the write would clamp to the last page that already existed and silently land
    // one short. Held instead, and spent by the effect below on the first composition where the page is real.
    var pageToReveal by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pageToReveal, maxPage.value) {
        val page = pageToReveal ?: return@LaunchedEffect
        // Not yet: the store has not echoed the new page. Re-runs when `maxPage` moves, which is that echo.
        if (page > maxPage.value) return@LaunchedEffect
        pagerState.animateToPage(page)
        pageToReveal = null
    }

    // The item whose Gestures sheet is open, or null. Held here rather than in the menu because the menu is a
    // transient host and the sheet outlives it — the menu closes as the row is chosen.
    var gesturesFor by remember { mutableStateOf<HomeItem?>(null) }

    // **What each item has taken, and what happens when it fires.** Both zones read the one resolver, so the dock
    // and the pager cannot disagree about an item's claims — and the claims are what the surface pan asks about
    // before it takes a swipe of its own, so a disagreement would show up as a swipe that opens a surface on one
    // zone and not the other.
    val claimedOn: (HomeItem) -> Set<SwipeDirection> = { state.itemGestures.swipesOn(it.gridItem) }
    val doubleTapOn: (HomeItem) -> Boolean = { state.itemGestures.hasDoubleTapOn(it.gridItem) }
    val fireDoubleTap: (HomeItem) -> Unit = { viewModel.runGesture(it.gridItem, ItemGesture.DOUBLE_TAP) }
    val fireGesture: (HomeItem, SwipeDirection) -> Unit = { item, direction ->
        viewModel.runGesture(item.gridItem, direction.asItemGesture())
    }

    // **What a press on an icon container is actually on** — one of its icons if the finger came down on one, and
    // the container itself everywhere else. Only a container answers anything but itself, which is why this is one
    // lambda for the whole surface rather than a property of each cell.
    //
    // The slack matters as much as the icons do: a ring's hollow middle and the space around a short arc are how a
    // container is picked up, resized, or long-pressed for its own menu, so [indexAt] answers on *containment*
    // rather than handing back the least-far icon.
    val liftedInCell: (HomeItem, Offset, IntSize) -> InnerCellItem? = { item, local, size ->
        (item as? HomeItem.IconContainer)?.let { container ->
            val slots = iconContainerSlots(
                arrangement = container.container.arrangement,
                count = container.icons.size,
                widthPx = size.width.toFloat(),
                heightPx = size.height.toFloat(),
                density = density,
            )
            slots.indexAt(local)?.let { i ->
                val slot = slots[i]
                InnerCellItem(
                    item = container.icons[i].asIconItem().asGridItem(),
                    bounds = Rect(slot.x, slot.y, slot.x + slot.width, slot.y + slot.height),
                )
            }
        }
    }

    // A tap on a container's icon does what a tap on that icon does anywhere else. It arrives here rather than
    // through a `clickable` on the slot so that it fires **only** for a gesture the machine resolved as a tap —
    // never after a long-press, and never on the release that ends a reorder.
    val openInner: (GridItem) -> Unit = { inner ->
        when (inner) {
            is GridItem.App -> viewModel.launch(inner.component)
            is GridItem.Folder -> folderHost.open(inner.folderId)
            else -> Unit
        }
    }

    val menuHost = LocalMenuHost.current
    // **The icon's own menu, not its container's.** A long-press inside a container is aimed at what is under the
    // finger, so it offers that app's or folder's verbs — plus the one verb that only makes sense here, taking it
    // out of the container. `showFolderAppMenu` below is the same idea one holder over.
    val showInnerMenu: (GridItem, Rect) -> Unit = { inner, anchor ->
        val held = state.items.filterIsInstance<HomeItem.IconContainer>().firstNotNullOfOrNull { container ->
            container.icons.firstOrNull { it.asIconItem().asGridItem() == inner }
                ?.let { container.container.id to it }
        }
        val remove = held?.let { (containerId, icon) ->
            MenuAction("Remove from container") { viewModel.removeIconFromContainer(containerId, icon.asIconItem()) }
        }
        when (val held2 = held?.second) {
            is ContainerIcon.App -> menuHost?.showApp(
                component = held2.info.componentKey,
                label = held2.info.label,
                anchor = anchor,
                surfaceActions = listOfNotNull(remove),
            )

            is ContainerIcon.Folder -> menuHost?.show(
                title = held2.folder.label,
                anchor = anchor,
                actions = listOfNotNull(remove),
            )

            null -> Unit
        }
    }

    val showMenu: (HomeItem, Rect) -> Unit = { item, anchor ->
        showHomeItemMenu(
            item = item,
            anchor = anchor,
            menuHost = menuHost,
            viewModel = viewModel,
            widgetHost = widgetHost,
            onResize = { resizing = it },
            onOpenIconContainerSettings = onOpenIconContainerSettings,
            onOpenWidgetContainerSettings = onOpenWidgetContainerSettings,
            onOpenGestures = { gesturesFor = it },
        )
    }

    // **The add flow, and the placement it reports back to.** The flow owns the activity-result choreography
    // (bind, then the provider's configuration screen); *where* the widget goes is this surface's, because only it
    // knows the grid and the cells it was measured at. Returning false releases the id — see `WidgetAddFlow`.
    val addWidget = rememberWidgetAddFlow(widgetHost) { bound ->
        val info = WidgetInfo(
            appWidgetId = bound.appWidgetId,
            providerPackage = bound.provider.packageName,
            providerClass = bound.provider.className,
            label = bound.label,
        )
        val geo = geometry
        val span = geo?.let {
            WidgetSpan.forMinSize(bound.minWidthPx, bound.minHeightPx, it.cellW, it.cellH, config)
        }
        val at = span?.let { viewModel.placeWidget(widget = info, span = it, zone = HomeZone.MAIN, config = config) }
        pageToReveal = at?.page
        at != null
    }

    // An app *inside* a folder is offered less, and the missing verb is the point: it has no grid placement, so a
    // "Remove" here would be a row that does nothing (`RemoveFromGrid` on a folder member deletes no rows). Taking
    // an app out of a folder is a drag, which is the gesture this menu's own long-press leads into.
    val showFolderAppMenu: (AppInfo, Rect) -> Unit = { app, anchor ->
        // **Over the film, unlike home's own item menu two zones below.** An open folder puts a full-screen frost
        // over home, so this menu renders flat; the same app long-pressed on the grid outside the folder frosts, and
        // that difference is the whole of what the flag says.
        menuHost?.showApp(component = app.componentKey, label = app.label, anchor = anchor, overFrost = true)
    }

    // No `LauncherTheme` here: the launcher **zone** is themed once by `feature:shell`'s `LauncherShell`, which is
    // also the only layer that knows the launcher's real dark/light input (wallpaper brightness, not the system
    // setting). A screen that themed itself could not be told to disagree with the shell.
    //
    // **And no background either — home is the wallpaper.** The launcher's window shows it (`windowShowWallpaper` in
    // `app`'s theme), so painting anything opaque here would hide the thing the user chose. It was opaque only while
    // the window was: a placeholder for a wallpaper that could not appear yet. What is drawn over it stays legible by
    // its own means — cell labels carry a shadow, and the folder's scrim is its own.
    Box(
        modifier
            .fillMaxSize()
            // **Long-press on empty space → the surface menu.** On the root, so it covers both zones and the margins
            // between them; `surfaceMenuGestures` owns why a press that lands on an icon does not reach it. Gated on
            // being the surface on screen for the floating proxy's reason — a surface panned off to one side must not
            // answer a press meant for the one in front of it.
            //
            // **One detector rather than one per zone** (home, dock, widget area). Three would be worth it only if
            // each offered a *different* action set; ours all resolve to the same single row today, so splitting them
            // would be three ways to say one thing. The split returns with the first verb that is not launcher-wide.
            .surfaceMenuGestures(gestureConfig, enabled = presented) { position ->
                menuHost?.showSurface(
                    position = position,
                    // **The one verb HOME owns today.** The host appends Settings itself; every other row that
                    // belongs here waits on something unbuilt (an app picker, page management). The picker is hosted
                    // by *this* surface rather than by `HomeScreen` because the size labels it shows are measured
                    // against the grid a widget would land on, and only the surface drawing that grid knows it.
                    surfaceActions = listOf(MenuAction("Widgets") { widgetPickerOpen = true }),
                )
            },
    ) {
        // **The two zones, stacked along the dock's own axis** — see [HomeZoneScaffold], which owns the
        // arrangement, the `uiInsets` padding on the pair, and each zone's own horizontal margin (S4g). The margins
        // reaching each grid's *own* modifier is what keeps drag and drop correct for free: both drag surfaces
        // publish their geometry from an `onGloballyPositioned` placed after the caller's modifier
        // (`CoordinateDragGrid`'s KDoc says so in as many words), so the bounds they report are already the padded
        // ones.
        HomeZoneScaffold(
            edge = layout.dockEdge,
            extent = layout.dockExtent,
            mainPadding = layout.mainPadding,
            sidePadding = layout.dockPadding,
            // The dock: a single, non-paged coordinate zone on the *same* coordinator, so a drag between it and the
            // pager is one gesture with no hand-off. Its extent is the user's setting, and the count it divides that
            // extent into is clamped to it rather than stored — see `dockConfig` above.
            side = { zoneModifier ->
                val dockResize = resizePreviewIn(HomeZone.DOCK)
                CoordinateDragGrid(
                    edgeActions = claimedOn,
                    doubleTap = doubleTapOn,
                    onEdgeAction = fireGesture,
                    onDoubleTap = fireDoubleTap,
                    items = dockItems,
                    config = dockConfig,
                    coordinator = coordinator,
                    zoneId = DockZoneId,
                    gestureConfig = gestureConfig,
                    dragItem = { it.gridItem },
                    placement = { dockResize?.previewOf(it.gridItem) ?: it.placement },
                    trackedItem = dockResize?.item,
                    planner = dockPlanner,
                    onLand = ::commitLanding,
                    onRelease = ::handleRelease,
                    modifier = zoneModifier,
                    onGeometryChange = { dockGeometry = it },
                    onOpen = openItem,
                    onShowMenu = showMenu,
                    innerItemAt = liftedInCell,
                    onOpenInner = openInner,
                    onShowInnerMenu = showInnerMenu,
                ) { item, cellModifier, itemGestures ->
                    HomeItemCell(
                        item = item,
                        session = session,
                        cellModifier = cellModifier,
                        itemGestures = itemGestures,
                        metrics = layout.dockMetrics,
                        onAddWidgetToContainer = onOpenWidgetContainerSettings,
                        onAddIconToContainer = onOpenIconContainerSettings,
                        onReorderContainer = viewModel::reorderIconContainer,
                        onInsertIntoContainer = viewModel::insertIntoIconContainer,
                    )
                }
            },
            main = { zoneModifier ->
                val mainResize = resizePreviewIn(HomeZone.MAIN)
                CoordinateDragPager(
                    edgeActions = claimedOn,
                    doubleTap = doubleTapOn,
                    onEdgeAction = fireGesture,
                    onDoubleTap = fireDoubleTap,
                    items = mainItems,
                    config = config,
                    pagerState = pagerState,
                    coordinator = coordinator,
                    zoneId = MainZoneId,
                    gestureConfig = gestureConfig,
                    dragItem = { it.gridItem },
                    placement = { mainResize?.previewOf(it.gridItem) ?: it.placement },
                    trackedItem = mainResize?.item,
                    planner = mainPlanner,
                    onLand = ::commitLanding,
                    onRelease = ::handleRelease,
                    modifier = zoneModifier,
                    onGeometryChange = { geometry = it },
                    onOpen = openItem,
                    onShowMenu = showMenu,
                    innerItemAt = liftedInCell,
                    onOpenInner = openInner,
                    onShowInnerMenu = showInnerMenu,
                ) { item, cellModifier, itemGestures ->
                    HomeItemCell(
                        item = item,
                        session = session,
                        cellModifier = cellModifier,
                        itemGestures = itemGestures,
                        metrics = layout.mainMetrics,
                        onAddWidgetToContainer = onOpenWidgetContainerSettings,
                        onAddIconToContainer = onOpenIconContainerSettings,
                        onReorderContainer = viewModel::reorderIconContainer,
                        onInsertIntoContainer = viewModel::insertIntoIconContainer,
                    )
                }
            },
        )

        // Drag overlay (root space): the drop shadow in the grid + the floating proxy on the finger. The two
        // are gated separately, because they answer different questions — "is there a cell of *this* grid to
        // shadow?" and "whose job is it to draw the icon under the finger?".
        val geo = geometry
        // Gated on this being the surface on screen, for the same reason the trailing page is: a drag inside the
        // APPS drawer must not have home painting a second proxy under the same finger from behind it.
        if (presented && session != null && geo != null) {
            // The proxy is the dragged item's own footprint in the *pager's* cells, and deliberately keeps that
            // size across the whole drag: what is under the finger must not resize as the drag crosses into the
            // dock, whose cells are a different height. It was one visual cell until widgets existed, which drew
            // a 4x2 widget as a single icon-sized square.
            val draggedSpan = liveSpanOf.value(session.item, config)
            val footprintW = geo.cellW * draggedSpan.colSpan
            val footprintH = geo.cellH * draggedSpan.rowSpan
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
            val shadowGeo = when (session.activeZone) {
                MainZoneId -> geo
                DockZoneId -> dockGeometry
                else -> null
            }
            if (shadowGeo != null) {
                session.plan?.let { plan ->
                    val topLeft = shadowGeo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            // **Sized from the plan, not from a cell count.** The plan already states the
                            // footprint it resolved, spans included, so reading it here is what makes a widget's
                            // shadow the widget's size — and removes the second, guessed derivation that made
                            // every shadow one visual cell.
                            .size(
                                with(density) { (shadowGeo.cellW * plan.footprint.colSpan).toDp() },
                                with(density) { (shadowGeo.cellH * plan.footprint.rowSpan).toDp() },
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
            // Resolved once: everything but a loose app is identified by its own grid item, since only an app can
            // be mid-flight with no placement of its own.
            val draggedItem = state.items.firstOrNull { it.gridItem == session.item }
            val draggedFolder = draggedItem as? HomeItem.Folder
            // An **icon container** is re-drawn like any other cell — its contents are icons the launcher owns, so
            // there is nothing here it cannot paint a second time.
            val draggedIconContainer = draggedItem as? HomeItem.IconContainer
            // A widget cannot be re-drawn the way a cell can — its content is another app's views — so the proxy
            // is a snapshot of the one on screen, taken once when the drag starts. See
            // `AppWidgetHostController.snapshot`.
            val draggedWidget = session.item as? GridItem.Widget
            val widgetShot = remember(draggedWidget) { draggedWidget?.let { widgetHost.snapshot(it.appWidgetId) } }
            // A **widget container** is the same problem one level up, and the page to capture answers itself:
            // `snapshot` returns null for a widget that is not currently composed, and a pager composes the page it
            // is showing — so the first non-null is the page the user was looking at. Null for an empty container,
            // which `WidgetContainerProxy` draws as the bare panel it really is.
            val draggedWidgetContainer = draggedItem as? HomeItem.WidgetContainer
            val containerShot = remember(draggedWidgetContainer) {
                draggedWidgetContainer?.container?.widgetIds?.firstNotNullOfOrNull { widgetHost.snapshot(it) }
            }
            val hasProxy = draggedApp != null || draggedFolder != null || widgetShot != null ||
                draggedIconContainer != null || draggedWidgetContainer != null
            if (hasProxy && folderHost.openCollectionId == null) {
                val finger = session.fingerInRoot
                FloatingDragIcon(
                    rootOffset = IntOffset(
                        (finger.x - footprintW / 2f).roundToInt(),
                        (finger.y - footprintH / 2f).roundToInt(),
                    ),
                    size = DpSize(with(density) { footprintW.toDp() }, with(density) { footprintH.toDp() }),
                ) {
                    // **The pager's metrics, on the same terms as the footprint above**: what is under the finger
                    // must not resize as the drag crosses into the dock, and it must not differ from the cell it was
                    // lifted out of either. Left ambient, these cells resolved against `IconMetrics()` — a 48dp
                    // ceiling and nothing the user had set — so a container full of icons visibly shrank the moment
                    // it left the grid. It read as a zoom because a container shows the difference once per icon;
                    // an app and a folder had the same gap and showed it once.
                    //
                    // Provided rather than passed per cell, which is how the list surface's own proxy does it: a
                    // cell added here later cannot be the one that forgets.
                    CompositionLocalProvider(LocalIconMetrics provides layout.mainMetrics) {
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
                        } else if (draggedIconContainer != null) {
                            // The real cell, with no click handlers passed: a proxy is a rendering that follows the
                            // finger, and its slots are not targets — the lifted cell still owns the pointer stream.
                            IconContainerCell(
                                icons = draggedIconContainer.icons,
                                arrangement = draggedIconContainer.container.arrangement,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (draggedWidgetContainer != null) {
                            WidgetContainerProxy(snapshot = containerShot, modifier = Modifier.fillMaxSize())
                        } else if (widgetShot != null) {
                            Image(
                                bitmap = widgetShot.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // Folder overlays, drawn above the grids. Resolved live from state so their contents track edits.
        val openFolder = folderHost.openCollectionId?.let { id -> folders.firstOrNull { it.folder.id == id } }
        // Report the folder's persisted membership back: it is what tells the host that a just-injected app has
        // landed, so it can stop being carried separately.
        val openFolderMembers = openFolder?.folder?.apps
        LaunchedEffect(openFolderMembers) { folderHost.onMembersChanged(openFolderMembers.orEmpty()) }

        // Usually one overlay. But a drag that started inside a folder keeps that folder composed for its whole
        // life, even while a different one — or none — is on screen: the cell driving the drag is in its grid, and
        // a pointer stream can't move to another node. It is rendered as a pointer holder (`presenting = false`):
        // invisible, zone-less, no proxy. See `AppCollectionHostState.dragSourceCollectionId`.
        val holderFolder = folderHost.dragSourceCollectionId
            ?.takeIf { it != folderHost.openCollectionId }
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
                AppCollectionOverlay(
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
                        val incoming = (folderHost.phase as? AppCollectionPhase.Injecting<*>)?.app
                        if (incoming != null && order.contains(incoming)) {
                            // The app landed in this folder at its chosen slot. `from` is the folder that *holds*
                            // it — the folder-to-folder move, committed as one batch; null when it came off a grid
                            // instead, and this folder itself when the drag left and came back, which `addToFolder`
                            // recognizes as the plain reorder it is. Membership rather than
                            // `dragSourceCollectionId` for `commitLanding`'s reason: an app carried in from the APPS
                            // drawer may already be in one of home's folders without the drag having started there.
                            viewModel.addToFolder(
                                folderId = folder.folder.id,
                                reported = order,
                                incoming = incoming,
                                from = viewModel.folderHolding(incoming),
                            )
                            folderHost.injectCommitted()
                        } else {
                            viewModel.reorderFolder(folder.folder.id, order)
                        }
                    },
                    onLeave = folderHost::leaveCollection,
                    onRelease = ::handleRelease,
                    onDismiss = { folderHost.close() },
                    onShowMenu = showFolderAppMenu,
                    // **Everything not already in this folder** — including apps on a home page and apps filed in
                    // another folder, both of which `addAppsToFolder` moves rather than copies, because an app is in
                    // one place. Only the presented folder offers it: a pointer holder is invisible, and an Add cell
                    // it drew would be a target nobody can see.
                    additions = if (presenting) folderAdditions(folder.folder.id) else null,
                )
            }
        }

        // **The resize frame**, above the grids so its handles are reachable over any cell. It draws in the zone
        // the item lives in, so a widget in the dock is framed by the dock's cells.
        resizing?.let { session ->
            val zoneConfig = if (session.zone == HomeZone.DOCK) dockConfig else config
            val zoneGeometry = geometryFor(session.zone, geometry, dockGeometry) ?: return@let
            ResizeOverlay(
                placement = session.placement,
                geometry = zoneGeometry,
                bounds = session.rules.asResizeBounds(zoneGeometry, zoneConfig),
                refused = session.refused,
                // Read back from state rather than from the captured `session`: several pointer events can arrive
                // between two compositions, and each must resolve against the outcome of the one before it.
                onResize = { candidate ->
                    resizing = resizing?.resolving(candidate, zoneConfig) { rect ->
                        viewModel.planResize(session.item, rect, session.zone, zoneConfig)
                    }
                },
                onCommit = {
                    // Whatever is committed is a rectangle the planner already accepted, because that is the only
                    // kind `resolving` lets through — so the write is exactly the push that has been on screen.
                    val settled = resizing ?: session
                    viewModel.resizeItem(settled.item, settled.placement, settled.zone, zoneConfig)
                    // The frame stays up for the next drag — see `ResizeOverlay`. The preview is now the stored
                    // truth, so it is dropped rather than replayed over the top of it.
                    resizing = settled.copy(moves = emptyMap(), refused = false)
                },
                onDismiss = { resizing = null },
            )
        }

        // **The widget picker**, last in the stack so it covers everything including an open folder. It sizes its
        // "3 × 2" labels against the pager's grid, which is where a widget dropped on this pairing goes — and
        // against the pager's *measured* cells, so before the first layout it shows no size rather than one
        // derived from a second, guessed geometry.
        if (widgetPickerOpen) {
            val geo = geometry
            WidgetPickerSheet(
                grid = config,
                cellWidthPx = geo?.cellW ?: 0f,
                cellHeightPx = geo?.cellH ?: 0f,
                onDismiss = { widgetPickerOpen = false },
                // Closing first is what lets the system's bind dialog and the provider's configuration screen come
                // up over the launcher rather than over a sheet that would still be there when they returned.
                onAddWidget = { provider ->
                    widgetPickerOpen = false
                    addWidget.start(provider.component)
                },
                onAddIconContainer = {
                    widgetPickerOpen = false
                    pageToReveal = viewModel.createIconContainer(HomeZone.MAIN, config)?.page
                },
                onAddWidgetContainer = {
                    widgetPickerOpen = false
                    pageToReveal = viewModel.createWidgetContainer(HomeZone.MAIN, config)?.page
                },
                // Nothing is ever too big for this grid to take: the search grows a page rather than refusing, and
                // the only refusal left is an item wider or taller than the grid itself. Asked all the same, so the
                // picker's Add row is absent for exactly the widgets that could not be placed — see the sheet.
                hasRoomFor = { span ->
                    viewModel.hasRoomFor(span.rowSpan, span.colSpan, HomeZone.MAIN, config)
                },
            )
        }

        // **The gestures sheet, in the overlay stack rather than beside the menu that opens it.** Composed next to
        // `showMenu` it drew *under* the grids — a sibling earlier in the same `Box` paints first, so a screenful of
        // icons rendered straight over the panel. Every other sheet on this surface is stacked here for the same
        // reason.
        gesturesFor?.let { target ->
            HomeItemGestureSheet(
                label = target.menuLabel,
                assigned = state.itemGestures.actionsOn(target.gridItem),
                describe = { describeGestureAction(it, state.catalog) },
                onPick = { gesture ->
                    gesturesFor = null
                    onAssignGesture(target.gridItem, gesture)
                },
                onDismiss = { gesturesFor = null },
            )
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
 *   group, leaving the surrounding slack free for the surface's own gestures. The two **containers** are the stated
 *   exception: like a widget, a container genuinely fills its cell, so it takes these on its root.
 * @param onLaunch a tap on an app **inside an icon container**. The cell's own taps arrive through the gesture
 *   contract instead; a container's slots are the one place a cell needs a click of its own, because the container
 *   is the item and its slots are not.
 * @param onOpenFolder the same, for a folder nested in an icon container.
 * @param onAddWidgetToContainer the "+" of an *empty* widget container, by container id.
 * @param onAddIconToContainer the same, for an empty icon container.
 */
@Composable
private fun HomeItemCell(
    item: HomeItem,
    session: DragSession?,
    cellModifier: Modifier,
    itemGestures: Modifier,
    metrics: IconMetrics,
    onAddWidgetToContainer: (Long) -> Unit = {},
    onAddIconToContainer: (Long) -> Unit = {},
    onReorderContainer: (Long, List<IconItem>) -> Unit = { _, _ -> },
    onInsertIntoContainer: (Long, IconItem, Int) -> Unit = { _, _, _ -> },
) {
    when (item) {
        is HomeItem.App ->
            AppCell(app = item.info, modifier = cellModifier, metrics = metrics, itemGestures = itemGestures)

        is HomeItem.Widget -> WidgetCell(
            appWidgetId = item.info.appWidgetId,
            label = item.info.label.ifBlank { UnnamedWidget },
            modifier = cellModifier,
            itemGestures = itemGestures,
        )

        is HomeItem.Folder -> {
            // Hide the app currently being dragged (e.g. extracted out of this folder) from the tile preview, so it
            // isn't shown in the folder icon and under the finger at the same time. The real folder removal commits
            // on drop — removing it now would dispose the dragged cell (it lives in the folder's grid) and kill the
            // drag.
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

        is HomeItem.IconContainer -> IconContainerCell(
            // **The whole membership, including whatever is being carried right now** — unlike the folder tile
            // above, which filters the dragged app out of its preview. The container keeps it: hiding it here
            // would shrink the list, re-flow the arrangement around a gap that is about to be filled, and leave
            // the cell unable to recognize its own icon coming back. It draws it invisible in place instead.
            icons = item.icons,
            arrangement = item.container.arrangement,
            modifier = cellModifier,
            itemGestures = itemGestures,
            // The zone's own metrics, as every other cell here gets — a container's icons answer to the same
            // guardrails as the icons on the grid around it.
            metrics = metrics,
            onAddIcon = { onAddIconToContainer(item.container.id) },
            containerId = item.container.id,
            onReorder = { items -> onReorderContainer(item.container.id, items) },
            onInsert = { icon, index -> onInsertIntoContainer(item.container.id, icon, index) },
        )

        is HomeItem.WidgetContainer -> WidgetContainerCell(
            widgets = item.widgets,
            axis = item.container.axis,
            modifier = cellModifier,
            itemGestures = itemGestures,
            autoRotate = item.container.autoRotate,
            resetOnReturn = item.container.resetOnReturn,
            onAddWidget = { onAddWidgetToContainer(item.container.id) },
        )
    }
}

/**
 * The measured geometry of [zone] on this surface, or null before it has been laid out.
 *
 * A widget can sit in either coordinate zone, and a resize frame has to be drawn in the cells of the one it is
 * actually in — the two grids have different origins and different cell sizes, so a frame drawn from the wrong
 * one is not merely misaligned, it is a different size.
 */
private fun geometryFor(zone: HomeZone, main: GridGeometry?, dock: GridGeometry?): GridGeometry? =
    if (zone == HomeZone.DOCK) dock else main

/**
 * **What floors a resize, and the two kinds of thing HOME can resize differ in where that floor comes from.**
 *
 * A widget's is the *provider's*, stated in pixels, which only the measured grid can turn into cells — so it is
 * carried unresolved and converted at the one place holding both the geometry and the zone's config. A container has
 * no provider to ask, so its floor is the launcher's own answer to "how small is still a container".
 *
 * A sealed type rather than a nullable widget rule, so a third resizable thing has to say what bounds it rather than
 * inheriting a widget's by omission.
 */
internal sealed interface HomeResizeRules {

    /** A widget, bounded by what its provider says it can be drawn at. */
    data class Widget(val rules: WidgetResizeRules) : HomeResizeRules

    /** An icon or widget container, bounded by the grid rather than by any provider. */
    data object Container : HomeResizeRules
}

/**
 * These rules as grid [ResizeBounds], against the measured [geometry] and the zone's [config].
 *
 * **A widget's minimum is `ceil`ed**, because one needing part of a further cell needs the whole cell: rounding down
 * would offer a size the provider has already said it cannot draw at. Floored at one cell, since a footprint of
 * nothing is not a thing the grid can express.
 *
 * **A container's floor is one *visual* cell** — `cellMultiplier` logical ones — which is the smallest footprint
 * anything on this grid occupies: a single app icon's. Below that a container would be smaller than one of the icons
 * it exists to hold, which is not a size worth being able to reach. It is not `ContainerSpan`: that is where a
 * container *lands*, and a default placement is not a minimum.
 */
private fun HomeResizeRules.asResizeBounds(geometry: GridGeometry, config: GridConfig): ResizeBounds = when (this) {
    // Both axes always: the provider's `resizeMode` is not honored here — see `WidgetResizeRules`.
    is HomeResizeRules.Widget -> ResizeBounds(
        horizontal = true,
        vertical = true,
        minColSpan = if (geometry.cellW > 0f) ceil(rules.minWidthPx / geometry.cellW).toInt().coerceAtLeast(1) else 1,
        minRowSpan = if (geometry.cellH > 0f) ceil(rules.minHeightPx / geometry.cellH).toInt().coerceAtLeast(1) else 1,
    )

    HomeResizeRules.Container -> ResizeBounds(
        horizontal = true,
        vertical = true,
        minColSpan = config.cellMultiplier.coerceAtLeast(1),
        minRowSpan = config.cellMultiplier.coerceAtLeast(1),
    )
}

