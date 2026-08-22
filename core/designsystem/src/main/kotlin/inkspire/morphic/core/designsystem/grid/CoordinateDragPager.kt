package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.pager.EdgeFlipEffect
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.LauncherPagerState
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The **paged** free-placement drag surface: a [LauncherPager] of coordinate grids where a single item drag can
 * cross pages. This is the home MAIN zone; the non-paged sibling is [CoordinateDragGrid].
 *
 * Paging changes the drop model versus a single grid, so this is a distinct composable rather than N
 * [CoordinateDragGrid]s. Following the pattern validated in the dev harness:
 * - **one drop zone = the whole viewport**; the dragged footprint's page is simply [LauncherPagerState.currentPage]
 *   (the caller's planner reads it), so the coordinator never has to hit-test scrolling per-page zones.
 * - **page-swipe is gated off during an item drag** (`launcherPagerSwipe(enabled = { !isDragging })`), so a page
 *   swipe and an item drag never fight.
 * - **edge-dwell page-flip**: holding the dragged item near the left/right edge flips a page each dwell (bounded),
 *   and `keepAllPagesPlaced` keeps the source page placed so the lifted tile keeps its pointer stream while pages
 *   scroll. This is how an item is carried to another page — including a brand-new trailing page, when the caller
 *   grows [LauncherPagerState.pageCount] by one during a drag.
 *
 * Each page renders the items whose stored [placement] page is that page, on a [LauncherGrid], through the shared
 * [LauncherDragCell]. The push preview is dwelled exactly as in the single grid.
 *
 * @param T the caller's item type; [dragItem] projects it to its [GridItem] drag identity, [placement] to its cell.
 * @param pagerState the pager position/among; the caller owns it because its planner reads `currentPage`.
 * @param onGeometryChange receives the *viewport* geometry (all pages share it) for the caller's planner + overlay.
 * @see CoordinateDragGrid for the non-paged version and the shared parameter meanings.
 */
@Composable
fun <T> CoordinateDragPager(
    items: List<T>,
    config: GridConfig,
    pagerState: LauncherPagerState,
    coordinator: DragCoordinator,
    zoneId: ZoneId,
    gestureConfig: ItemGestureConfig,
    dragItem: (T) -> GridItem,
    placement: (T) -> GridPlacement,
    planner: DropPlanner,
    onLand: (DropOutcome) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    edgeActions: (T) -> Set<SwipeDirection> = { emptySet() },
    doubleTap: (T) -> Boolean = { false },
    trackedItem: GridItem? = null,
    acceptsItem: (GridItem) -> Boolean = { true },
    onGeometryChange: (GridGeometry) -> Unit = {},
    onOpen: (T) -> Unit = {},
    onShowMenu: (T, anchorInRoot: Rect) -> Unit = { _, _ -> },
    onEdgeAction: (T, SwipeDirection) -> Unit = { _, _ -> },
    onDoubleTap: (T) -> Unit = {},
    itemContent: @Composable (item: T, cellModifier: Modifier, itemGestures: Modifier) -> Unit,
) {
    val session = coordinator.session

    // Dwelled push preview for the active page (see CoordinateDragGrid); the moves apply to occupants of
    // whichever page the drag is over, which is the same page they are stored on, so per-page filtering is safe.
    val livePlan = session?.takeIf { it.activeZone == zoneId }?.plan
    var dwelledPlan by remember { mutableStateOf<PlacementPlan?>(null) }
    LaunchedEffect(livePlan) {
        if (livePlan == null) dwelledPlan = null else {
            delay(PUSH_DWELL_MS.milliseconds); dwelledPlan = livePlan
        }
    }

    // Edge-dwell page-flip, shared with every other paged drag surface. Scoped to *this* pager's zone: on a
    // shared coordinator another surface's drag (reordering inside an open folder, or one being carried over a
    // different screen entirely) must not flip these pages behind it, which is what passing a null finger says.
    var viewport by remember { mutableStateOf<Rect?>(null) }
    // The viewport doubles as the drop zone's rectangle; registration is driven from state rather than from the
    // layout callback because it also depends on this surface being on screen (see [RegisterDropZone]).
    RegisterDropZone(
        coordinator = coordinator,
        zone = viewport?.let {
            DropZone(zoneId, it, z = 0, planner = planner, accepts = acceptsItem, onDrop = onLand)
        },
    )
    EdgeFlipEffect(
        pagerState = pagerState,
        viewport = viewport,
        fingerInRoot = session?.takeIf { it.activeZone == zoneId }?.fingerInRoot,
    )

    LauncherPager(
        state = pagerState,
        modifier = modifier
            .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                viewport = b
                onGeometryChange(
                    GridGeometry(
                        originInRoot = Offset(b.left, b.top),
                        cellW = b.width / config.cols,
                        cellH = b.height / config.rows,
                        cols = config.cols,
                        rows = config.rows,
                    ),
                )
            },
        // While dragging, keep off-screen pages placed so the lifted tile's pointer stream survives a page flip.
        keepAllPagesPlaced = coordinator.isDragging,
    ) { page ->
        // The lattice, per page — each page is its own grid box, so the markers are drawn in the page's own
        // coordinates and a mid-flip page shows them where its own cells are. The finger is only ever over one
        // page, so the others draw nothing: their local finger is outside the buffer.
        val markerSpan = livePlan?.footprint?.let { GridSpan(it.colSpan, it.rowSpan) }
            ?: GridSpan(config.cellMultiplier, config.cellMultiplier)
        var pageBounds by remember { mutableStateOf<Rect?>(null) }

        LauncherGrid(
            config = config,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { pageBounds = it.boundsInRoot() }
                .gridSnapMarkers(
                    config = config,
                    localFinger = {
                        val origin = pageBounds?.topLeft
                        val finger = coordinator.session?.takeIf { it.activeZone == zoneId }?.fingerInRoot
                        if (origin == null || finger == null) null else finger - origin
                    },
                    draggedSpan = { markerSpan },
                ),
        ) {
            coordinateItems(
                items = items.filter { placement(it).page == page },
                itemKey = { dragItem(it) },
                placement = { item -> dwelledPlan?.moves?.get(dragItem(item)) ?: placement(item) },
            ) { item, cellModifier ->
                LauncherDragCell(
                    coordinator = coordinator,
                    item = dragItem(item),
                    gestureConfig = gestureConfig,
                    onRelease = onRelease,
                    modifier = cellModifier,
                    edgeActions = edgeActions(item),
                    doubleTap = doubleTap(item),
                    tracksFinger = dragItem(item) == trackedItem,
                    onOpen = { onOpen(item) },
                    onShowMenu = { anchor -> onShowMenu(item, anchor) },
                    onEdgeAction = { direction -> onEdgeAction(item, direction) },
                    onDoubleTap = { onDoubleTap(item) },
                ) { itemGestures ->
                    itemContent(item, Modifier.fillMaxSize(), itemGestures)
                }
            }
        }
    }
}
