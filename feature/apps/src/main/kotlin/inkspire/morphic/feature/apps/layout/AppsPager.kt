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
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.ordered.cellFractionX
import inkspire.morphic.core.designsystem.ordered.movingGap
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
 * through the list and lets the flow densify (see `core:designsystem/ordered`). Two zones per cell here rather
 * than three: with no merge yet, a centre third would be dead space where the user's aim does nothing. Folders —
 * and with them the merge ring — are the next part.
 *
 * **Crossing pages** works the way home's does: one drop zone is the whole viewport, page-swipe is gated off while
 * an item is in flight so the two gestures never fight, holding near an edge flips a page on a dwell, and
 * `keepAllPagesPlaced` keeps the source page composed so the lifted cell keeps its pointer stream across the flip.
 *
 * @param pages the arrangement to draw: pages in order, each dense from its first slot.
 * @param onMove commits a drop — the item, and the page and slot it landed at.
 * @param onGridResolved reports the resolved page grid up to the ViewModel, which needs the capacity to page the
 *   store. The device is a `@Composable` read, so the UI is the only place that can resolve it.
 */
@Composable
fun AppsPager(
    pages: List<List<AppsItem>>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (item: IconItem, toPage: Int, toSlot: Int) -> Unit,
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

    val planner = remember(config) {
        DropPlanner { zone, item, fingerInRoot ->
            if (zone.id != PagerZoneId) return@DropPlanner null
            val geo = geometry ?: return@DropPlanner null
            val page = pagerState.currentPage
            val stored = livePages.value.getOrNull(page).orEmpty()
            val others = stored.map { it.gridItem }.filterNot { it == item }
            // Off a cell (the slack below a short page) holds the current gap rather than snapping it: the finger
            // is between targets, and moving the preview there would strobe.
            val cell = geo.cellAt(fingerInRoot) ?: return@DropPlanner PagerReorderPlan
            val slot = cell.row * config.cols + cell.col
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

    // Edge-dwell page flip. Scoped to this surface's own zone, so a drag that belongs elsewhere can't flip
    // these pages behind it.
    EdgeFlipEffect(
        pagerState = pagerState,
        viewport = viewport,
        fingerInRoot = session?.takeIf { it.activeZone == PagerZoneId }?.fingerInRoot,
    )

    fun handleDrop() {
        val outcome = coordinator.drop() ?: return
        val item = outcome.item.asIconItem() ?: return
        val page = gapPage.takeIf { it >= 0 } ?: return
        onMove(item, page, gap.coerceAtLeast(0))
    }

    val draggedItem = session?.item
    // Resolved once per frame rather than per page: a drag recomposes this on every finger move, and searching
    // every page for the dragged entry inside each page's own content would be that search N times over.
    val draggedEntry = remember(pages, draggedItem) {
        draggedItem?.let { id -> pages.firstNotNullOfOrNull { page -> page.firstOrNull { it.gridItem == id } } }
    }
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
                            onOpen = { (item as? AppsItem.App)?.let { onLaunch(it.info.componentKey) } },
                        ) { itemGestures ->
                            AppsPagerCell(item = item, modifier = Modifier.fillMaxSize(), itemGestures = itemGestures)
                        }
                    }
                }
            }

            // The floating proxy: the icon under the finger, at one cell's size. The lifted cell itself is drawn
            // invisible by `LauncherDragCell`, so this is the only thing the user sees moving.
            val geo = geometry
            if (session != null && geo != null && draggedEntry != null) {
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
