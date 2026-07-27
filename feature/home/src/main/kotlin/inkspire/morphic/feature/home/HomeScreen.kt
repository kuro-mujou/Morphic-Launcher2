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
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
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
 * This screen keeps only what is home-specific: the [DropPlanner] (span-snapped push onto the current page), the
 * root drag overlay, and the tap→launch wiring; the pager, per-page grids, gestures, dwelled preview, and
 * edge-flip live in [CoordinateDragPager]. First cut: apps only, no folder-merge, no directional-push
 * refinement, portrait only. A tap launches the app (via [HomeViewModel.launch]); the tap is handled by the
 * gesture layer's `onOpen`, so [AppCell]'s own `onClick` stays a no-op here.
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
    // The current placement map, read live by the planner while a drag is in flight.
    val placements = state.apps.associate { GridItem.App(it.info.componentKey) as GridItem to it.placement }
    val livePlacements = rememberUpdatedState(placements)

    // Pager: page count is (highest occupied page + 1), plus one trailing empty page *while dragging* so an app
    // can be carried onto a brand-new page. `draggingPages` is synced from the coordinator below, because the
    // coordinator doesn't exist yet here and so can't be read directly inside the count lambda.
    val maxPage = rememberUpdatedState(state.apps.maxOfOrNull { it.placement.page } ?: 0)
    var draggingPages by remember { mutableStateOf(false) }
    val pagerState = rememberLauncherPagerState(
        pageCount = { maxPage.value + 1 + if (draggingPages) 1 else 0 },
        infiniteScroll = { false },
    )

    // Free-grid plan for the hovered cell on the *current* page: snap the app's (span × span) footprint to the
    // visual lattice (step = the multiplier, so full-cell icons never land on a half-cell), push that page's
    // occupants aside. Same value the preview and the drop both read, so the shadow never lies about the result.
    val planner = remember(config) {
        val span = config.cellMultiplier
        DropPlanner { _, item, fingerInRoot ->
            val geo = geometry ?: return@DropPlanner null
            val page = pagerState.currentPage
            val topLeft = geo.snapTopLeftCell(fingerInRoot, colSpan = span, rowSpan = span, step = span)
            val footprint = GridPlacement(page, topLeft.row, topLeft.col, rowSpan = span, colSpan = span)
            val occupants = livePlacements.value.filterKeys { it != item }.filterValues { it.page == page }
            FreeGridPlanner.plan(footprint, occupants, config)
        }
    }
    val coordinator = rememberDragCoordinator(planner)
    LaunchedEffect(coordinator.isDragging) { draggingPages = coordinator.isDragging }

    // The dragged item + hovered plan drive the root overlay below; the dwelled push preview + edge page-flip
    // live inside CoordinateDragPager.
    val session = coordinator.session

    fun handleDrop() {
        val outcome = coordinator.drop() ?: return
        if (outcome.plan.intent == DropIntent.INVALID) return // no room — leave it where it was
        val moves = outcome.plan.moves.map { (moved, to) -> LayoutChange.Move(moved, to) } +
            LayoutChange.Move(outcome.item, outcome.plan.footprint)
        viewModel.applyChanges(moves)
    }

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        Box(modifier.fillMaxSize().background(colors.background)) {
            CoordinateDragPager(
                items = state.apps,
                config = config,
                pagerState = pagerState,
                coordinator = coordinator,
                zoneId = HomeZone,
                gestureConfig = gestureConfig,
                dragItem = { GridItem.App(it.info.componentKey) },
                placement = { it.placement },
                onDrop = { handleDrop() },
                modifier = Modifier.fillMaxSize().padding(16.dp),
                onGeometryChange = { geometry = it },
                onOpen = { viewModel.launch(it.info.componentKey) },
            ) { placed, cellModifier ->
                AppCell(app = placed.info, onClick = {}, modifier = cellModifier)
            }

            // Drag overlay (root space): the drop shadow in the grid + the floating proxy on the finger.
            val geo = geometry
            if (session != null && geo != null) {
                // The footprint spans `cellSpan` logical cells per axis (one visual cell), so size the overlays
                // to that extent, not a single logical cell.
                val footprintW = geo.cellW * cellSpan
                val footprintH = geo.cellH * cellSpan
                session.plan?.let { plan ->
                    val topLeft = geo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            .size(with(density) { footprintW.toDp() }, with(density) { footprintH.toDp() }),
                    )
                }
                val draggedApp = state.apps.firstOrNull { GridItem.App(it.info.componentKey) == session.item }?.info
                if (draggedApp != null) {
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            (finger.x - footprintW / 2f).roundToInt(),
                            (finger.y - footprintH / 2f).roundToInt(),
                        ),
                        size = DpSize(with(density) { footprintW.toDp() }, with(density) { footprintH.toDp() }),
                    ) {
                        AppCell(app = draggedApp, onClick = {}, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
