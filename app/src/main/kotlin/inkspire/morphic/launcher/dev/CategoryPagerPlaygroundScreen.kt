package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.SwipeDirection
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicColors
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Harness for **APPS × pager × category** with **drag-to-reorder**. A horizontal [LauncherPager] of categories,
 * each page a scrollable [LauncherGrid] (SCROLL_GRID) of that category's apps via [flowItems]. It shows:
 *
 * 1. **Perpendicular gestures** — horizontal swipe pages categories; vertical drag scrolls the grid; long-press
 *    starts an item drag. The pager is gated off while dragging.
 * 2. **Both display gravities** on one grid (top vs bottom-grow), via the [CategoryPage] wrapper.
 * 3. **MovingGap reorder, 2-zone** — the same flow as the drag harness's `OrderedSurface`, except each cell is
 *    split into **halves** (left → gap before, right → gap after) with **no merge zone**: this surface holds
 *    no folders, so there's nothing to merge into. The reorder is within one category (cross-category and
 *    drag-edge auto-scroll are deferred).
 */
@Composable
fun CategoryPagerPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        val density = LocalDensity.current
        val cols = 4
        val cellHeight = 96.dp
        val cellHeightPx = with(density) { cellHeight.toPx() }
        var bottomGravity by remember { mutableStateOf(false) }
        val gestureConfig = remember {
            ItemGestureConfig(touchSlopPx = with(density) { 18.dp.toPx() }, longPressTimeoutMillis = 350L)
        }

        val categories = remember { demoCategories() }
        val pagerState = rememberLauncherPagerState(
            pageCount = { categories.size },
            infiniteScroll = { false },
        )

        // The planner always targets the current category (the pager is frozen during a drag, so it can't
        // change). Its geometry is the current grid's — updated on scroll, valid because no scroll happens
        // mid-drag.
        var zoneBounds by remember { mutableStateOf<Rect?>(null) }
        val planner = remember {
            DropPlanner { item, finger ->
                val cat = categories[pagerState.currentPage]
                val geo = cat.geometry ?: return@DropPlanner null
                categoryReorderPlan(cat, geo, cols, item, finger)
            }
        }
        val coordinator = rememberDragCoordinator()

        // Clear every transient gap once the drag ends.
        LaunchedEffect(coordinator.isDragging) {
            if (!coordinator.isDragging) categories.forEach { it.gap = -1 }
        }

        fun commitLanding(outcome: DropOutcome) {
            val cat = categories[pagerState.currentPage]
            val g = outcome.plan.footprint.row * cols + outcome.plan.footprint.col
            cat.apps.remove(outcome.item)
            cat.apps.add(g.coerceIn(0, cat.apps.size), outcome.item)
        }

        Box(modifier.fillMaxSize().background(colors.background)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    categories.forEachIndexed { i, category ->
                        val active = i == pagerState.currentPage
                        Text(
                            text = category.name,
                            color = if (active) colors.content else colors.contentDisabled,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Text(
                        text = "gravity: ${if (bottomGravity) "bottom" else "top"}",
                        color = colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { bottomGravity = !bottomGravity }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                LauncherPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                        .onGloballyPositioned {
                            zoneBounds = it.boundsInRoot()
                        },
                ) { page ->
                    CategoryPage(
                        category = categories[page],
                        cols = cols,
                        cellHeight = cellHeight,
                        cellHeightPx = cellHeightPx,
                        bottomGravity = bottomGravity,
                        colors = colors,
                        coordinator = coordinator,
                        gestureConfig = gestureConfig,
                        onRelease = { coordinator.drop() },
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(categories.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            Modifier
                                .size(if (active) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (active) colors.accent else colors.contentDisabled),
                        )
                    }
                }
            }

            RegisterDropZone(
                coordinator = coordinator,
                zone = zoneBounds?.let {
                    DropZone(PagerZone, it, z = 0, planner = planner, onDrop = ::commitLanding)
                },
            )

            // Drag overlay (root space): the drop-shadow in the current grid + the floating proxy on the finger.
            val session = coordinator.session
            if (session != null) {
                val cat = categories[pagerState.currentPage]
                val geo = cat.geometry
                val plan = session.plan
                if (geo != null) {
                    val cellWDp = with(density) { geo.cellW.toDp() }
                    if (plan != null) {
                        val tl = geo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                        DropFootprint(
                            intent = DropIntent.PLACE,
                            modifier = Modifier
                                .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                                .size(width = cellWDp, height = cellHeight),
                        )
                    }
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            (finger.x - geo.cellW / 2f).roundToInt(),
                            (finger.y - cellHeightPx / 2f).roundToInt(),
                        ),
                        size = DpSize(cellWDp, cellHeight),
                    ) {
                        AppTile(appLabel(session.item), Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * One category page: header + its scrolling grid, in the chosen [bottomGravity] mode (see the earlier writeup).
 * Both modes host the same drag-enabled [CategoryGrid]; only the wrapper differs.
 */
@Composable
private fun CategoryPage(
    category: DemoCategory,
    cols: Int,
    cellHeight: Dp,
    cellHeightPx: Float,
    bottomGravity: Boolean,
    colors: MorphicColors,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onRelease: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Text(
            "${category.name} · ${category.apps.size} apps",
            color = colors.contentMuted,
            modifier = Modifier.padding(8.dp),
        )
        if (bottomGravity) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val viewportHeight = maxHeight
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = viewportHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        CategoryGrid(category, cols, cellHeight, cellHeightPx, coordinator, gestureConfig, onRelease, Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            CategoryGrid(
                category, cols, cellHeight, cellHeightPx, coordinator, gestureConfig, onRelease,
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * The category's apps on a SCROLL_GRID via the flow strategy, draggable to reorder. The dragged app is spliced
 * into the display order at the live gap, so a migrating gap simply reorders the list [flowItems] lays out —
 * the same trick the drag harness's ordered surface uses. Geometry is published from the grid's measured bounds
 * (which move with scroll), so finger→cell reads the same cells drawn.
 */
@Composable
private fun CategoryGrid(
    category: DemoCategory,
    cols: Int,
    cellHeight: Dp,
    cellHeightPx: Float,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onRelease: () -> Unit,
    modifier: Modifier,
) {
    val rows = maxOf(1, ceil(category.apps.size / cols.toFloat()).toInt())
    val config = GridConfig(rows = rows, cols = cols)

    val dragged = coordinator.session?.item?.takeIf { it in category.apps }
    val displayOrder = if (dragged == null) {
        category.apps.toList()
    } else {
        val others = category.apps.filter { it != dragged }
        val g = (if (category.gap >= 0) category.gap else category.apps.indexOf(dragged)).coerceIn(0, others.size)
        others.toMutableList().apply { add(g, dragged) }
    }

    LauncherGrid(
        config = config,
        cellHeight = cellHeight,
        modifier = modifier.onGloballyPositioned {
            val b = it.boundsInRoot()
            category.geometry = ScrollGridGeometry(
                originInRoot = Offset(b.left, b.top),
                cellW = b.width / cols,
                cellH = cellHeightPx,
                cols = cols,
                rows = rows,
            )
        },
    ) {
        flowItems(displayOrder) { app, cellModifier ->
            val isDragged = app == dragged
            Box(
                cellModifier
                    .then(if (isDragged) Modifier else Modifier.animatePlacement())
                    .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                    .launcherItemGestures(
                        config = gestureConfig,
                        edgeActions = emptySet(),
                        onOpen = {},
                        onEdgeAction = {},
                        onShowMenu = {},
                        onBeginDrag = { root -> coordinator.start(app, root) },
                        onDragTo = { root -> coordinator.moveTo(root) },
                        onDrop = { onRelease() },
                        onCancelDrag = { coordinator.cancel() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppTile(appLabel(app), Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Plans a reorder within a category — the ordered/MovingGap flow, but **2-zone**: the hovered cell splits into
 * left/right halves (gap before / after the item), with **no merge zone** (no folders here). Mirrors
 * `orderedPlan` in the drag harness minus the `CENTER`/merge branch.
 */
private fun categoryReorderPlan(
    category: DemoCategory,
    geo: ScrollGridGeometry,
    cols: Int,
    item: GridItem,
    finger: Offset,
): PlacementPlan? {
    val cell = geo.cellAt(finger) ?: return null
    val others = category.apps.filter { it != item }
    if (category.gap < 0) category.gap = category.apps.indexOf(item).coerceIn(0, others.size)
    var g = category.gap.coerceIn(0, others.size)
    val slot = cell.row * cols + cell.col

    when {
        slot > others.size -> g = others.size            // empty trailing cell → append
        slot == g -> Unit                                 // over the gap itself → no change
        else -> {
            val j = if (slot < g) slot else slot - 1      // others-index of the hovered item
            if (j in others.indices) {
                g = if (geo.inLeftHalf(finger)) j else j + 1   // 2 zones: left → before, right → after
            }
        }
    }
    category.gap = g
    return PlacementPlan(GridPlacement(0, g / cols, g % cols), DropIntent.PLACE)
}

// ── Model + geometry ─────────────────────────────────────────────────────────────────────────────────────

/** A simulated app category: a name, a mutable ordered app list, plus the transient drag gap + geometry. */
private class DemoCategory(val name: String, val apps: SnapshotStateList<GridItem>) {
    /** Live insertion index while a drag is over this category; -1 when idle. */
    var gap by mutableIntStateOf(-1)

    /** This category grid's measured geometry (moves with scroll), read by the planner + overlay. */
    var geometry by mutableStateOf<ScrollGridGeometry?>(null)
}

/** Root-space geometry of a scrolling grid: finger→cell, cell halves (2-zone), and cell→root. */
private class ScrollGridGeometry(
    val originInRoot: Offset,
    val cellW: Float,
    val cellH: Float,
    val cols: Int,
    val rows: Int,
) {
    fun cellAt(p: Offset): CellPos? {
        val lx = p.x - originInRoot.x
        val ly = p.y - originInRoot.y
        if (lx < 0f || ly < 0f) return null
        val col = (lx / cellW).toInt()
        val row = (ly / cellH).toInt()
        return if (row in 0 until rows && col in 0 until cols) CellPos(row, col) else null
    }

    /** True when the finger sits in the left half of its cell (the "gap before" zone). */
    fun inLeftHalf(p: Offset): Boolean {
        val fx = (p.x - originInRoot.x) / cellW
        return fx - floor(fx) < 0.5f
    }

    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellW, originInRoot.y + row * cellH)
}

private data class CellPos(val row: Int, val col: Int)

// ── Cells + demo data ────────────────────────────────────────────────────────────────────────────────────

/** A single app cell: a coloured tile labelled with its category initial + index. */
@Composable
private fun AppTile(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileColorFor(label)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun appLabel(item: GridItem): String = when (item) {
    is GridItem.App -> item.component.className
    else -> "?"
}

private val PagerZone = ZoneId("category-pager")

/** Mixed sizes on purpose: the big ones exercise scroll, the small ones make bottom-gravity visible. */
private fun demoCategories(): List<DemoCategory> = listOf(
    "Social" to 40, "Games" to 27, "Tools" to 6, "Media" to 40, "Finance" to 3,
).map { (name, count) ->
    val apps = (1..count).map { GridItem.App(ComponentKey("cat", "${name.first()}$it")) as GridItem }.toMutableStateList()
    DemoCategory(name, apps)
}

private val TilePalette = listOf(
    Color(0xFF4F6D7A), Color(0xFF56A3A6), Color(0xFF6B8F71),
    Color(0xFF9A6FB0), Color(0xFFC08552), Color(0xFFC26D6D),
)

private fun tileColorFor(label: String): Color = TilePalette[abs(label.hashCode()) % TilePalette.size]
