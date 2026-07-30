package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.DragAutoScrollEffect
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.LauncherGridScope
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.ordered.cellFractionX
import inkspire.morphic.core.designsystem.ordered.movingGap
import inkspire.morphic.core.designsystem.pager.EdgeFlipEffect
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.AppsCategoryGrid
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.colsFor
import inkspire.morphic.feature.apps.AppsCategory
import kotlin.math.roundToInt

/** This surface's drop zone — the pager viewport, as on the other paged surfaces. */
private val CategoryZoneId = ZoneId("apps-category-pager")

/**
 * Provisional cell height and header spacing — **placeholders, not design choices**, for the reason the other
 * layouts' are: these are surface metrics bound for the settings layer, and a flat constant says so where derived
 * arithmetic would look like a decision.
 */
private val CellHeight = 96.dp
private val HeaderPadding = 16.dp

/** Denser than home's, matching the other APPS grids — the same starting point, replaced by the icon-size setting. */
private val CategoryIconMetrics = IconMetrics(iconPercent = 0.75f)

/**
 * The plan this surface reports for every hover it accepts: droppable, painting the gap and nothing else.
 *
 * [DropIntent.REORDER], as on every ordered surface — the preview is the reflow, and the footprint is drawn from
 * the surface's own gap rather than from this plan, whose footprint goes unread.
 */
private val CategoryReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)

/**
 * The **category pager** layout of the APPS surface
 * ([inkspire.morphic.core.model.AppsLayout.PAGER_WITH_CATEGORY]): one page per category, each page a
 * vertically-scrolling grid of the apps filed under it, rearranged by dragging.
 *
 * **A page is a category, so dragging across pages re-files the app.** Reordering within a page and moving to
 * another are one operation with a different destination — which is why the store needs a single `Move` op and this
 * file needs no separate "change category" path. Carrying an app to the next page *is* changing its category.
 *
 * **No folders live here**, so cells split into **halves** (insert before / after) with no centre merge ring. A
 * category is already the grouping; a folder inside one would be a second, redundant one, and with nothing to merge
 * into a centre third would be dead space where the user's aim does nothing. That is the one structural difference
 * from the APPS pager's drag — this is that, minus the merge branch and minus the page-capacity cascade.
 *
 * **SCROLL_GRID, unlike `AppsVerticalGrid`** — which renders every installed app and therefore uses a
 * `LazyVerticalGrid`. A category page holds a bounded subset (tens, not hundreds), which is exactly the case
 * `LauncherGrid`'s scroll mode names as its own. Same "right tool per surface" rule, opposite answer, because the
 * input size is different.
 *
 * **Because the pages scroll, geometry comes from each page's grid, not from the viewport.** The other paged
 * surfaces can publish one viewport geometry because a page *is* the viewport there; here the grid slides under it,
 * so a finger→cell read against viewport bounds would name whatever cell used to be at that height. Each page
 * reports its own grid bounds (which move with the scroll) and the planner reads the current page's.
 *
 * **Scrolling is gated off while an item is in flight**, like page-swipe — two vertical gestures competing for one
 * finger otherwise. Content past the fold is reached instead by holding the dragged app near the top or bottom
 * edge, which scrolls it (`DragAutoScrollEffect`): the vertical counterpart of the edge dwell that flips pages.
 *
 * Empty categories still get a page: a category the user emptied keeps its definition, and a page that vanished
 * with its last app could never be dragged back into.
 *
 * @param onMove commits a drop — the app, the category it landed in, and its slot within that category.
 */
@Composable
fun AppsCategoryPager(
    categories: List<AppsCategory>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (app: ComponentKey, toCategory: String, toSlot: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val device = currentDeviceConfiguration()
    val cols = remember(device) { AppsCategoryGrid.colsFor(device) }
    val gestureConfig = rememberAppsGestureConfig()

    // Held in a state so the count lambda reads the current list: `rememberLauncherPagerState` remembers the lambda
    // once, so capturing the parameter would freeze the pager at however many categories existed on the first
    // composition — which is none, before the store's first emission.
    val liveCategories = rememberUpdatedState(categories)
    val pagerState = rememberLauncherPagerState(
        pageCount = { liveCategories.value.size.coerceAtLeast(1) },
        infiniteScroll = { false },
    )

    // One geometry per page rather than one for the surface, because each page's grid scrolls independently — see
    // the class KDoc. Only the current page's is ever read, but every page reports, since `keepAllPagesPlaced` keeps
    // them all laid out during a drag.
    val geometries = remember { mutableStateMapOf<Int, GridGeometry>() }
    var viewport by remember { mutableStateOf<Rect?>(null) }

    // Where the dragged app would land, and on which page. The page matters as much as the index here: it names the
    // *category* the drop writes, which is what makes a cross-page drag a re-file.
    var gap by remember { mutableIntStateOf(-1) }
    var gapPage by remember { mutableIntStateOf(-1) }

    val planner = remember(cols) {
        DropPlanner { zone, item, fingerInRoot ->
            if (zone.id != CategoryZoneId) return@DropPlanner null
            val page = pagerState.currentPage
            val geo = geometries[page] ?: return@DropPlanner null
            val stored = liveCategories.value.getOrNull(page)?.apps.orEmpty().map { GridItem.App(it.componentKey) }
            val others = stored.filterNot { it == item }
            // Off a cell (the slack below a short page) holds the gap where it is rather than snapping it: the
            // finger is between targets, and moving the preview there would strobe.
            val cell = geo.cellAt(fingerInRoot) ?: return@DropPlanner CategoryReorderPlan
            val slot = cell.row * cols + cell.col
            gap = if (page != gapPage) {
                // Arriving on a page seeds the gap at the slot under the finger; `movingGap` refines it from there.
                // Seeding through `movingGap` would start at index 0, since the app isn't in this page's list.
                slot.coerceIn(0, others.size)
            } else {
                // Two zones: halves, not thirds. There is nothing to merge into on this surface.
                movingGap(others, item, gap, slot, geo.cellFractionX(fingerInRoot) < 0.5f)
            }
            gapPage = page
            CategoryReorderPlan
        }
    }
    val coordinator = rememberDragCoordinator(planner)
    val session = coordinator.session

    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) { gap = -1; gapPage = -1 }
    }
    DisposableEffect(coordinator) { onDispose { coordinator.unregisterZone(CategoryZoneId) } }

    EdgeFlipEffect(
        pagerState = pagerState,
        viewport = viewport,
        fingerInRoot = session?.takeIf { it.activeZone == CategoryZoneId }?.fingerInRoot,
    )

    fun handleDrop() {
        val outcome = coordinator.drop() ?: return
        val app = (outcome.item as? GridItem.App)?.component ?: return
        // The page the drop landed on names the category — no separate re-file path, because there is no separate
        // thing happening. A gap of -1 means the finger never rested on a cell, so there is nothing to write.
        val category = gapPage.takeIf { it >= 0 }?.let { categories.getOrNull(it) } ?: return
        onMove(app, category.category.id, gap.coerceAtLeast(0))
    }

    val draggedApp = session?.item?.let { item ->
        (item as? GridItem.App)?.component?.let { component ->
            categories.firstNotNullOfOrNull { it.apps.firstOrNull { app -> app.componentKey == component } }
        }
    }
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    CompositionLocalProvider(LocalIconMetrics provides CategoryIconMetrics) {
        Box(modifier.fillMaxSize()) {
            LauncherPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(safeInsets)
                    .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        viewport = bounds
                        coordinator.registerZone(DropZone(CategoryZoneId, bounds, z = 0) { item -> item is GridItem.App })
                    },
                // Keep off-screen pages placed while dragging, so the lifted cell's pointer stream survives a flip.
                keepAllPagesPlaced = coordinator.isDragging,
            ) { pageIndex ->
                categories.getOrNull(pageIndex)?.let { category ->
                    CategoryPage(
                        category = category,
                        cols = cols,
                        coordinator = coordinator,
                        gestures = gestureConfig,
                        dragged = if (pageIndex == gapPage) draggedApp else null,
                        gap = gap,
                        // Only the page being dragged over scrolls itself; the others must sit still.
                        fingerInRoot = if (pageIndex == gapPage) session?.fingerInRoot else null,
                        onLaunch = onLaunch,
                        onDrop = ::handleDrop,
                        onGeometry = { geometries[pageIndex] = it },
                    )
                }
            }

            // The floating proxy, at one cell's size. The lifted cell is drawn invisible by `LauncherDragCell`, so
            // this is the only thing the user sees moving.
            val geo = geometries[gapPage] ?: geometries[pagerState.currentPage]
            if (session != null && geo != null && draggedApp != null) {
                val finger = session.fingerInRoot
                FloatingDragIcon(
                    rootOffset = IntOffset(
                        (finger.x - geo.cellW / 2f).roundToInt(),
                        (finger.y - geo.cellH / 2f).roundToInt(),
                    ),
                    size = DpSize(with(density) { geo.cellW.toDp() }, with(density) { geo.cellH.toDp() }),
                ) {
                    AppCell(app = draggedApp, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** One category's page: its name, then its apps in a scrolling, draggable grid. */
@Composable
private fun CategoryPage(
    category: AppsCategory,
    cols: Int,
    coordinator: DragCoordinator,
    gestures: ItemGestureConfig,
    dragged: AppInfo?,
    gap: Int,
    fingerInRoot: Offset?,
    onLaunch: (ComponentKey) -> Unit,
    onDrop: () -> Unit,
    onGeometry: (GridGeometry) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val cellHeightPx = with(LocalDensity.current) { CellHeight.toPx() }
    val display = displayOrder(category.apps, dragged, gap)
    val rows = ((display.size + cols - 1) / cols).coerceAtLeast(1)

    val scrollState = rememberScrollState()
    var scrollViewport by remember { mutableStateOf<Rect?>(null) }
    // Reaching apps past the fold while dragging. The manual scroll below is off for the duration, so without this
    // a long category could only be rearranged as far as one screenful. Programmatic scrolling still works with the
    // gesture disabled — `enabled` gates pointer input, not the state.
    DragAutoScrollEffect(scrollState = scrollState, bounds = scrollViewport, fingerInRoot = fingerInRoot)

    Column(Modifier.fillMaxSize()) {
        Text(
            text = category.category.name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = HeaderPadding, vertical = HeaderPadding / 2),
        )
        // The scroll host: the grid inside reports its *content* height, which grows past this box and scrolls. Its
        // own `remember` sits inside the pager's per-page `key`, so each category keeps its own scroll position.
        // Scrolling is disabled during a drag — otherwise the scroll and the drag fight over the same vertical
        // finger. Reaching past the fold mid-drag needs a drag-edge auto-scroll, which is deferred.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { scrollViewport = it.boundsInRoot() }
                .verticalScroll(scrollState, enabled = !coordinator.isDragging),
        ) {
            LauncherGrid(
                // `rows` is unused in scroll mode (height comes from cellHeight × content) but GridConfig requires a
                // positive value, so it is set to what the content actually reaches rather than to a lie.
                config = GridConfig(rows = rows, cols = cols),
                cellHeight = CellHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    // Published from the *grid*, so it travels with the scroll: cell hit-testing has to name the
                    // cells actually drawn under the finger, not the ones that were there before scrolling.
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        onGeometry(
                            GridGeometry(
                                originInRoot = Offset(bounds.left, bounds.top),
                                cellW = bounds.width / cols,
                                cellH = cellHeightPx,
                                cols = cols,
                                rows = rows,
                            ),
                        )
                    },
            ) {
                dropFootprintCell(dragged != null, gap, cols)
                flowItems(items = display, itemKey = { it.componentKey.flatten() }) { app, cellModifier ->
                    LauncherDragCell(
                        coordinator = coordinator,
                        item = GridItem.App(app.componentKey),
                        gestureConfig = gestures,
                        onDrop = onDrop,
                        modifier = cellModifier,
                        onOpen = { onLaunch(app.componentKey) },
                    ) { itemGestures ->
                        AppCell(app = app, modifier = Modifier.fillMaxSize(), itemGestures = itemGestures)
                    }
                }
            }
        }
    }
}

/**
 * What one page draws while a drag is over it: its apps with the dragged one lifted to the gap.
 *
 * **The dragged cell stays composed on its source page even once the finger has carried it to another**, so this
 * can return the same app on two pages at once. Not a glitch to tidy: the cell on the source page owns the
 * gesture's pointer stream, and disposing it mid-drag kills the drag. Both copies are drawn invisible, so the user
 * sees only the floating proxy — the far one exists to occupy the gap so the other icons flow around it.
 *
 * No truncation, unlike the APPS pager's equivalent: a category has no capacity, so nothing can overflow it.
 */
private fun displayOrder(apps: List<AppInfo>, dragged: AppInfo?, gap: Int): List<AppInfo> {
    if (dragged == null) return apps
    val others = apps.filterNot { it.componentKey == dragged.componentKey }
    val at = gap.coerceIn(0, others.size)
    return others.take(at) + dragged + others.drop(at)
}

/**
 * Paints the gap's cell, if this page holds it — the slot the app would land in.
 *
 * Declared before the cells so it sits behind them, and inside the page grid so it scrolls with the content. Only a
 * reorder can happen here, so there is no merge case to prefer over it.
 */
@Suppress("ComposableNaming") // an emitter, named for what it paints rather than as a component
@Composable
private fun LauncherGridScope.dropFootprintCell(draggingHere: Boolean, gap: Int, cols: Int) {
    if (!draggingHere || gap < 0) return
    Box(Modifier.gridPlacement(GridPlacement(0, gap / cols, gap % cols))) {
        DropFootprint(DropIntent.REORDER, Modifier.fillMaxSize())
    }
}
