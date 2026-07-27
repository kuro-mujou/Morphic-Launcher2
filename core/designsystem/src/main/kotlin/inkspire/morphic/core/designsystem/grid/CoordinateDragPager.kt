package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.SwipeDirection
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.LauncherPagerState
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import kotlinx.coroutines.delay

/** How long the finger must rest on a push before occupants reflow (matches [CoordinateDragGrid]). */
private const val PUSH_DWELL_MS = 200L

/** How near a viewport edge the dragged item must be held to trigger a page-flip, and the dwell between flips. */
private val EDGE_FLIP_DP = 44.dp
private const val EDGE_FLIP_DWELL_MS = 450L

private enum class FlipEdge { LEFT, RIGHT }

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
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
    edgeActions: Set<SwipeDirection> = emptySet(),
    acceptsItem: (GridItem) -> Boolean = { true },
    onGeometryChange: (GridGeometry) -> Unit = {},
    onOpen: (T) -> Unit = {},
    onShowMenu: (T) -> Unit = {},
    onEdgeAction: (T, SwipeDirection) -> Unit = { _, _ -> },
    itemContent: @Composable (item: T, cellModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val session = coordinator.session

    // Dwelled push preview for the active page (see CoordinateDragGrid); the moves apply to occupants of
    // whichever page the drag is over, which is the same page they are stored on, so per-page filtering is safe.
    val livePlan = session?.takeIf { it.activeZone == zoneId }?.plan
    var dwelledPlan by remember { mutableStateOf<PlacementPlan?>(null) }
    LaunchedEffect(livePlan) {
        if (livePlan == null) dwelledPlan = null else { delay(PUSH_DWELL_MS); dwelledPlan = livePlan }
    }

    DisposableEffect(coordinator, zoneId) {
        onDispose { coordinator.unregisterZone(zoneId) }
    }

    // Edge-dwell page-flip: which edge (if any) the dragged finger is currently held near. Only while the drag
    // is actually over *this* pager's zone — on a shared coordinator another surface's drag (e.g. reordering
    // inside an open folder) must not flip these pages behind it.
    var viewport by remember { mutableStateOf<Rect?>(null) }
    val flipEdge: FlipEdge? = run {
        val active = session?.takeIf { it.activeZone == zoneId } ?: return@run null
        val vp = viewport ?: return@run null
        val finger = active.fingerInRoot
        val edgePx = with(density) { EDGE_FLIP_DP.toPx() }
        when {
            finger.x < vp.left + edgePx -> FlipEdge.LEFT
            finger.x > vp.right - edgePx -> FlipEdge.RIGHT
            else -> null
        }
    }
    LaunchedEffect(flipEdge) {
        val active = flipEdge ?: return@LaunchedEffect
        while (true) {
            delay(EDGE_FLIP_DWELL_MS)
            val target = pagerState.currentPage + if (active == FlipEdge.LEFT) -1 else 1
            if (target < 0 || target >= pagerState.pageCount) break
            pagerState.animateToPage(target)
        }
    }

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
                coordinator.registerZone(DropZone(zoneId, b, z = 0, accepts = acceptsItem))
            },
        // While dragging, keep off-screen pages placed so the lifted tile's pointer stream survives a page flip.
        keepAllPagesPlaced = coordinator.isDragging,
    ) { page ->
        LauncherGrid(config = config, modifier = Modifier.fillMaxSize()) {
            coordinateItems(
                items = items.filter { placement(it).page == page },
                itemKey = { dragItem(it) },
                placement = { item -> dwelledPlan?.moves?.get(dragItem(item)) ?: placement(item) },
            ) { item, cellModifier ->
                LauncherDragCell(
                    coordinator = coordinator,
                    item = dragItem(item),
                    gestureConfig = gestureConfig,
                    onDrop = onDrop,
                    modifier = cellModifier,
                    edgeActions = edgeActions,
                    onOpen = { onOpen(item) },
                    onShowMenu = { onShowMenu(item) },
                    onEdgeAction = { direction -> onEdgeAction(item, direction) },
                ) {
                    itemContent(item, Modifier.fillMaxSize())
                }
            }
        }
    }
}
