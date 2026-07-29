package inkspire.morphic.launcher.dev

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.SwipeDirection
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.layout.FreeGridPlanner
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Harness for **pager × drag** integration (P2). A bounded [LauncherPager] of paged free-placement grids; a
 * single item drag can cross pages. The three integration points:
 * - **page-swipe is gated off during a drag** (`launcherPagerSwipe(enabled = { !isDragging })`), so a page
 *   swipe and an item drag never fight;
 * - **one drop zone = the viewport**, whose footprint page is [rememberLauncherPagerState]'s `currentPage`;
 * - **edge-dwell page-flip**: holding the dragged item at the left/right edge flips pages, and
 *   `keepAllPagesPlaced` keeps the source page placed so the drag survives it scrolling away.
 *
 * Placement/push comes from `FreeGridPlanner` (nearest-edge — the full directional partition + merge are
 * already validated in the drag harness; this screen is about paging). Items live in one map keyed by
 * `GridItem`, their `GridPlacement.page` deciding which page they render on.
 */
@Composable
fun PagerDragPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        val context = LocalContext.current
        val density = LocalDensity.current
        val config = remember { GridConfig(rows = ROWS, cols = COLS) }

        val placements = remember {
            mutableStateMapOf(
                demoApp("A") to GridPlacement(0, 0, 0),
                demoApp("B") to GridPlacement(0, 0, 1),
                demoApp("C") to GridPlacement(0, 1, 0, rowSpan = 2, colSpan = 2),
                demoApp("D") to GridPlacement(0, 3, 3),
                demoApp("E") to GridPlacement(1, 0, 0),
                demoApp("F") to GridPlacement(1, 1, 1),
                demoApp("G") to GridPlacement(1, 2, 2),
                demoApp("H") to GridPlacement(2, 0, 3),
                demoApp("I") to GridPlacement(2, 2, 0),
            )
        }

        var viewport by remember { mutableStateOf<Rect?>(null) }
        var geometry by remember { mutableStateOf<PageGeometry?>(null) }

        val pagerState = rememberLauncherPagerState(pageCount = { PAGES }, infiniteScroll = { false })

        // One zone = the viewport; the footprint's page is whichever page is currently centred.
        val planner = remember {
            DropPlanner { _, item, fingerInRoot ->
                val geo = geometry ?: return@DropPlanner null
                val span = placements[item] ?: return@DropPlanner null
                val page = pagerState.currentPage
                val topLeft = geo.snapTopLeftCell(fingerInRoot, span.colSpan, span.rowSpan)
                val footprint = GridPlacement(page, topLeft.row, topLeft.col, span.rowSpan, span.colSpan)
                val occupants = placements.filter { it.key != item && it.value.page == page }
                FreeGridPlanner.plan(footprint, occupants, config)
            }
        }
        val coordinator = rememberDragCoordinator(planner)
        val gestureConfig = remember {
            ItemGestureConfig(touchSlopPx = with(density) { 20.dp.toPx() }, longPressTimeoutMillis = 400L)
        }

        // One zone for the whole pager viewport, unregistered when the screen leaves.
        DisposableEffect(coordinator) {
            onDispose { coordinator.unregisterZone(PagerZoneId) }
        }

        fun handleDrop() {
            val outcome = coordinator.drop() ?: return
            outcome.plan.moves.forEach { (moved, placement) -> placements[moved] = placement }
            placements[outcome.item] = outcome.plan.footprint
        }

        // Edge-dwell page-flip: while the finger is held near a viewport edge during a drag, flip a page each
        // dwell, stopping at the ends (bounded).
        val edge: FlipEdge? = run {
            val session = coordinator.session
            val vp = viewport
            val edgePx = with(density) { EDGE_DP.toPx() }
            if (session == null || vp == null) {
                null
            } else {
                val x = session.fingerInRoot.x
                when {
                    x < vp.left + edgePx -> FlipEdge.LEFT
                    x > vp.right - edgePx -> FlipEdge.RIGHT
                    else -> null
                }
            }
        }
        LaunchedEffect(edge) {
            val active = edge ?: return@LaunchedEffect
            while (true) {
                delay(EDGE_DWELL_MS.milliseconds)
                val target = pagerState.currentPage + if (active == FlipEdge.LEFT) -1 else 1
                if (target < 0 || target >= PAGES) break
                pagerState.animateToPage(target)
            }
        }

        Box(modifier.fillMaxSize().background(colors.background)) {
            LauncherPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        viewport = b
                        geometry = PageGeometry(
                            originInRoot = Offset(b.left, b.top),
                            cellW = b.width / COLS,
                            cellH = b.height / ROWS,
                        )
                        coordinator.registerZone(DropZone(PagerZoneId, b, z = 0) { true })
                    },
                keepAllPagesPlaced = coordinator.isDragging,
            ) { page ->
                PageGrid(page, placements, geometry, coordinator, gestureConfig, toast(context), ::handleDrop)
            }

            // Drag overlay (root space): footprint (on the fixed viewport) + the floating proxy.
            val session = coordinator.session
            val geo = geometry
            if (session != null && geo != null) {
                session.plan?.let { plan ->
                    val topLeft = geo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            .size(
                                width = with(density) { (geo.cellW * plan.footprint.colSpan).toDp() },
                                height = with(density) { (geo.cellH * plan.footprint.rowSpan).toDp() },
                            ),
                    )
                }
                val span = placements[session.item]
                if (span != null) {
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            x = (finger.x - geo.cellW * span.colSpan / 2f).roundToInt(),
                            y = (finger.y - geo.cellH * span.rowSpan / 2f).roundToInt(),
                        ),
                        size = androidx.compose.ui.unit.DpSize(
                            with(density) { (geo.cellW * span.colSpan).toDp() },
                            with(density) { (geo.cellH * span.rowSpan).toDp() },
                        ),
                    ) {
                        ItemTile(session.item)
                    }
                }
            }

            Text(
                text = "page ${pagerState.currentPage + 1}/$PAGES",
                color = colors.contentMuted,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}

/** One page: renders the items whose [GridPlacement.page] is this page, positioned by the shared geometry. */
@Composable
private fun PageGrid(
    page: Int,
    placements: SnapshotStateMap<GridItem, GridPlacement>,
    geometry: PageGeometry?,
    coordinator: inkspire.morphic.core.designsystem.drag.DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onToast: (String) -> Unit,
    onDrop: () -> Unit,
) {
    val density = LocalDensity.current
    val colors = LocalMorphicColors.current

    Box(Modifier.fillMaxSize()) {
        val geo = geometry ?: return@Box
        val cellWDp = with(density) { geo.cellW.toDp() }
        val cellHDp = with(density) { geo.cellH.toDp() }
        for ((item, placement) in placements) {
            if (placement.page != page) continue
            val isDragged = coordinator.session?.item == item
            key(item) {
                Box(
                    Modifier
                        .offset {
                            IntOffset((placement.col * geo.cellW).roundToInt(), (placement.row * geo.cellH).roundToInt())
                        }
                        .size(width = cellWDp * placement.colSpan, height = cellHDp * placement.rowSpan)
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        .launcherItemGestures(
                            config = gestureConfig,
                            edgeActions = setOf(SwipeDirection.UP, SwipeDirection.DOWN),
                            onOpen = { onToast("open ${label(item)}") },
                            onEdgeAction = { onToast("swipe $it on ${label(item)}") },
                            onShowMenu = { onToast("menu: ${label(item)}") },
                            onDismissMenu = {},
                            onBeginDrag = { root -> coordinator.start(item, root) },
                            onDragTo = { root -> coordinator.moveTo(root) },
                            onDrop = { onDrop() },
                            onCancelDrag = { coordinator.cancel() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ItemTile(item)
                }
            }
        }
        // Faint page label behind the tiles.
        Text(
            text = "Page ${page + 1}",
            color = colors.contentDisabled,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

@Composable
private fun ItemTile(item: GridItem) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tileColor(label(item))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label(item).take(1), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** The viewport's geometry: maps a root finger position to a cell, and a cell back to root. Cells may be
 * non-square (viewport width/COLS × height/ROWS). */
private class PageGeometry(
    val originInRoot: Offset,
    val cellW: Float,
    val cellH: Float,
) {
    fun snapTopLeftCell(fingerInRoot: Offset, colSpan: Int, rowSpan: Int): PageCell {
        val tlx = fingerInRoot.x - originInRoot.x - colSpan * cellW / 2f
        val tly = fingerInRoot.y - originInRoot.y - rowSpan * cellH / 2f
        val col = (tlx / cellW).roundToInt().coerceIn(0, (COLS - colSpan).coerceAtLeast(0))
        val row = (tly / cellH).roundToInt().coerceIn(0, (ROWS - rowSpan).coerceAtLeast(0))
        return PageCell(row, col)
    }

    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellW, originInRoot.y + row * cellH)
}

private data class PageCell(val row: Int, val col: Int)

private enum class FlipEdge { LEFT, RIGHT }

private const val PAGES = 3
private const val COLS = 4
private const val ROWS = 5
private val EDGE_DP = 44.dp
private const val EDGE_DWELL_MS = 450L
private val PagerZoneId = ZoneId("pager-viewport")

private fun toast(context: android.content.Context): (String) -> Unit =
    { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

private fun demoApp(name: String): GridItem = GridItem.App(ComponentKey("demo", name))

private fun label(item: GridItem): String = when (item) {
    is GridItem.App -> item.component.className
    else -> "?"
}

private val TilePalette = listOf(
    Color(0xFF4F6D7A), Color(0xFF56A3A6), Color(0xFF6B8F71),
    Color(0xFF9A6FB0), Color(0xFFC08552), Color(0xFFC26D6D),
)

private fun tileColor(label: String): Color = TilePalette[abs(label.hashCode()) % TilePalette.size]
