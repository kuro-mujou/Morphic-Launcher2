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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.designsystem.grid.coordinateItems
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.LayoutChange
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private val HomeZone = ZoneId("home")
private const val PUSH_DWELL_MS = 200L

/**
 * The real HOME surface: placed apps from [HomeViewModel] on a [LauncherGrid] (coordinate strategy), with
 * **drag-to-rearrange that persists**. Long-press an app to lift it; the free-grid planner ([FreeGridPlanner])
 * pushes occupants out of the way (previewed live, dwelled so a fast drag doesn't strobe), and the drop commits
 * through [HomeViewModel.applyChanges] as `Move` commands — which update the surface instantly (optimistic)
 * and persist to Room.
 *
 * This is the same drag stack the dev harness proved (coordinator + geometry seam + `FreeGridPlanner` +
 * `animatePlacement`), now on live data. First cut: apps only, no folder-merge, no directional-push
 * refinement, portrait only. A tap launches the app (via [HomeViewModel.launch]); the tap is handled by the
 * gesture layer's `onOpen`, so [AppCell]'s own `onClick` stays a no-op here. Extracting a reusable coordinate
 * drag-grid (shared with the harness) is a later cleanup.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    val config = remember { GridConfig(rows = HomeViewModel.ROWS, cols = HomeViewModel.COLS) }
    val gestureConfig = remember {
        ItemGestureConfig(touchSlopPx = with(density) { 20.dp.toPx() }, longPressTimeoutMillis = 400L)
    }

    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    // The current placement map, read live by the planner while a drag is in flight.
    val placements = state.apps.associate { GridItem.App(it.info.componentKey) as GridItem to it.placement }
    val livePlacements = rememberUpdatedState(placements)

    // Free-grid plan for the hovered cell: snap the (1×1) footprint, push occupants aside. Same value the
    // preview and the drop both read, so the shadow never lies about the result.
    val planner = remember(config) {
        DropPlanner { _, item, fingerInRoot ->
            val geo = geometry ?: return@DropPlanner null
            val occupants = livePlacements.value.filterKeys { it != item }
            val topLeft = geo.snapTopLeftCell(fingerInRoot, colSpan = 1, rowSpan = 1)
            FreeGridPlanner.plan(GridPlacement(0, topLeft.row, topLeft.col), occupants, config)
        }
    }
    val coordinator = rememberDragCoordinator(planner)

    // Dwelled push preview: only reflow occupants once the finger rests on the same plan (see the harness).
    val session = coordinator.session
    val livePlan = session?.takeIf { it.activeZone == HomeZone }?.plan
    var dwelledPlan by remember { mutableStateOf<inkspire.morphic.core.model.PlacementPlan?>(null) }
    LaunchedEffect(livePlan) {
        if (livePlan == null) dwelledPlan = null else { delay(PUSH_DWELL_MS); dwelledPlan = livePlan }
    }

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
            LauncherGrid(
                config = config,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        geometry = GridGeometry(
                            originInRoot = Offset(b.left, b.top),
                            cellW = b.width / config.cols,
                            cellH = b.height / config.rows,
                            cols = config.cols,
                            rows = config.rows,
                        )
                        coordinator.registerZone(DropZone(HomeZone, b, z = 0) { true })
                    },
            ) {
                coordinateItems(
                    items = state.apps,
                    itemKey = { it.info.componentKey },
                    // Occupants render at their previewed (pushed) cell while a drag hovers; else their stored cell.
                    placement = { placed ->
                        dwelledPlan?.moves?.get(GridItem.App(placed.info.componentKey)) ?: placed.placement
                    },
                ) { placed, cellModifier ->
                    val item = GridItem.App(placed.info.componentKey)
                    val isDragged = session?.item == item
                    Box(
                        cellModifier
                            .then(if (isDragged) Modifier else Modifier.animatePlacement())
                            .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                            .launcherItemGestures(
                                config = gestureConfig,
                                edgeActions = emptySet(),
                                onOpen = { viewModel.launch(placed.info.componentKey) },
                                onEdgeAction = {},
                                onShowMenu = {},
                                onDismissMenu = {},
                                onBeginDrag = { root -> coordinator.start(item, root) },
                                onDragTo = { root -> coordinator.moveTo(root) },
                                onDrop = { handleDrop() },
                                onCancelDrag = { coordinator.cancel() },
                            ),
                    ) {
                        AppCell(app = placed.info, onClick = {}, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            // Drag overlay (root space): the drop shadow in the grid + the floating proxy on the finger.
            val geo = geometry
            if (session != null && geo != null) {
                session.plan?.let { plan ->
                    val topLeft = geo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            .size(with(density) { geo.cellW.toDp() }, with(density) { geo.cellH.toDp() }),
                    )
                }
                val draggedApp = state.apps.firstOrNull { GridItem.App(it.info.componentKey) == session.item }?.info
                if (draggedApp != null) {
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            (finger.x - geo.cellW / 2f).roundToInt(),
                            (finger.y - geo.cellH / 2f).roundToInt(),
                        ),
                        size = DpSize(with(density) { geo.cellW.toDp() }, with(density) { geo.cellH.toDp() }),
                    ) {
                        AppCell(app = draggedApp, onClick = {}, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
