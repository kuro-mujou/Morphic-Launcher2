package inkspire.morphic.launcher.dev

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.SwipeDirection
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.designsystem.grid.coordinateItems
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.PushDirection
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Dev harness for the drag-and-drop stack, exercising **both reflow families** across several zones:
 * - **Coordinate / free-placement** (home, dock, drawer): items keep exact cells; a drop pushes occupants out
 *   of the way (§6a free-grid partition), spans allowed. Handled by `FreeGridPlanner`.
 * - **Ordered / MovingGap** (the apps pager): items are a 1-D flow; lifting one leaves a *visible gap* that
 *   migrates as you drag (`[left|center|right]` per cell — left/right move the gap, center merges), and the
 *   flow densifies only on drop (§6b MovingGap).
 *
 * One [DragCoordinator] hit-tests every zone; the planner dispatches on the destination zone's model. Cross
 * zone drags carry the item source→dest. Items are fake colour tiles. Toggle coordinate-zone guides via the
 * "zones" label.
 */
@Composable
fun DragPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        val context = LocalContext.current
        val density = LocalDensity.current

        val cellDp = 64.dp
        val cellPx = with(density) { cellDp.toPx() }
        val gestureConfig = remember {
            ItemGestureConfig(touchSlopPx = cellPx * 0.25f, longPressTimeoutMillis = 400L)
        }
        var showGuides by remember { mutableStateOf(true) }

        val home = remember {
            DemoSurface(
                ZoneId("home"), GridConfig(rows = HOME_ROWS, cols = COLS),
                SurfaceModel.Coordinate(
                    mutableStateMapOf(
                        demoApp("Phone") to GridPlacement(0, 0, 0),
                        demoApp("Messages") to GridPlacement(0, 0, 1),
                        demoApp("Weather") to GridPlacement(0, 0, 2, rowSpan = 2, colSpan = 2),
                        demoApp("Camera") to GridPlacement(0, 1, 0),
                        demoApp("Maps") to GridPlacement(0, 1, 1),
                        demoApp("Photos") to GridPlacement(0, 2, 0, rowSpan = 2, colSpan = 2),
                        demoApp("Music") to GridPlacement(0, 2, 2, rowSpan = 1, colSpan = 2),
                        demoApp("Clock") to GridPlacement(0, 3, 2),
                        demoApp("Notes") to GridPlacement(0, 3, 3),
                    ),
                ),
            )
        }
        val dock = remember {
            DemoSurface(
                ZoneId("dock"), GridConfig(rows = DOCK_ROWS, cols = COLS),
                SurfaceModel.Coordinate(
                    mutableStateMapOf(
                        demoApp("Dialer") to GridPlacement(0, 0, 0),
                        demoApp("Browser") to GridPlacement(0, 0, 1),
                    ),
                ),
            )
        }
        val drawer = remember {
            DemoSurface(
                ZoneId("drawer"), GridConfig(rows = 3, cols = 1),
                SurfaceModel.Coordinate(
                    mutableStateMapOf(
                        demoApp("Gmail") to GridPlacement(0, 0, 0),
                        demoApp("Chrome") to GridPlacement(0, 1, 0),
                        demoApp("Slack") to GridPlacement(0, 2, 0),
                    ),
                ),
            )
        }
        val appsPager = remember {
            DemoSurface(
                ZoneId("apps"), GridConfig(rows = 3, cols = COLS),
                SurfaceModel.Ordered(
                    order = mutableStateListOf(
                        demoApp("A"), demoApp("B"), demoApp("C"), demoApp("D"), demoApp("E"),
                        demoApp("F"), demoApp("G"), demoApp("H"), demoApp("I"),
                    ),
                    allowMerge = true,
                ),
            )
        }
        val surfaces = remember { listOf(home, dock, drawer, appsPager) }

        // The planner dispatches on the destination zone's model: free-grid push/merge, or MovingGap reorder.
        val planner = remember {
            DropPlanner { zone, item, fingerInRoot ->
                val surface = surfaces.firstOrNull { it.id == zone.id } ?: return@DropPlanner null
                val geo = surface.geometry ?: return@DropPlanner null
                when (val m = surface.model) {
                    is SurfaceModel.Coordinate -> {
                        val span = spanOf(surfaces, item) ?: return@DropPlanner null
                        planWithin(m.placements, geo, surface.config, item, span, fingerInRoot)
                    }
                    is SurfaceModel.Ordered -> orderedPlan(m, geo, surface.config, item, fingerInRoot)
                }
            }
        }
        val coordinator = rememberDragCoordinator(planner)

        // Reset every MovingGap surface's transient gap once no drag is in flight.
        LaunchedEffect(coordinator.isDragging) {
            if (!coordinator.isDragging) {
                surfaces.forEach { (it.model as? SurfaceModel.Ordered)?.gap = -1 }
            }
        }

        fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

        fun handleDrop() {
            val outcome = coordinator.drop() ?: return
            if (outcome.plan.intent == DropIntent.MERGE) toast("merge ${label(outcome.item)}")
            applyOutcome(surfaces, outcome.item, outcome.zone, outcome.plan)
        }

        Box(modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // Side surface — a normal always-composed zone; a drag out of it is tracked by the tile's own
                // pointer stream (kept composed), so no root overlay is needed.
                Box(Modifier.width(80.dp), contentAlignment = Alignment.Center) {
                    // Fixed-cell coordinate surface: box sized to cellDp, so its measured cells come out 64dp.
                    GridSurface(
                        surface = drawer,
                        coordinator = coordinator,
                        sizeModifier = Modifier.size(width = cellDp * drawer.config.cols, height = cellDp * drawer.config.rows),
                        showGuides = showGuides,
                        gestureConfig = gestureConfig,
                        onToast = ::toast,
                        onDrop = ::handleDrop,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    // Home fills its width: cells are measured from real space (non-square, screen-dependent),
                    // which is exactly what the geometry seam has to survive.
                    GridSurface(
                        surface = home,
                        coordinator = coordinator,
                        sizeModifier = Modifier.fillMaxWidth().height(260.dp),
                        showGuides = showGuides,
                        gestureConfig = gestureConfig,
                        onToast = ::toast,
                        onDrop = ::handleDrop,
                    )
                    GridSurface(
                        surface = dock,
                        coordinator = coordinator,
                        sizeModifier = Modifier.size(width = cellDp * dock.config.cols, height = cellDp * dock.config.rows),
                        showGuides = showGuides,
                        gestureConfig = gestureConfig,
                        onToast = ::toast,
                        onDrop = ::handleDrop,
                    )
                    // Ordered surface now on LauncherGrid too; kept fixed-cell (measured 64dp) to isolate the
                    // grid swap from the responsiveness already proven on home.
                    OrderedSurface(
                        surface = appsPager,
                        coordinator = coordinator,
                        sizeModifier = Modifier.size(width = cellDp * appsPager.config.cols, height = cellDp * appsPager.config.rows),
                        gestureConfig = gestureConfig,
                        onToast = ::toast,
                        onDrop = ::handleDrop,
                    )
                }
            }

            Text(
                text = if (showGuides) "zones: on" else "zones: off",
                color = colors.contentMuted,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clickable { showGuides = !showGuides },
            )

            // ── Drag overlay (root space): the drop shadow (in the destination zone) + the floating proxy ──
            // Both sized from *measured* geometry (cellW/cellH), so they track each zone's real cell size — the
            // whole point of the seam. The footprint uses the destination zone; the proxy falls back to the
            // source zone when the finger is between zones, so it still has a size.
            val session = coordinator.session
            if (session != null) {
                val destGeo = surfaces.firstOrNull { it.id == session.activeZone }?.geometry
                val plan = session.plan
                if (destGeo != null && plan != null) {
                    val topLeft = destGeo.topLeftInRoot(plan.footprint.row, plan.footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            .size(
                                width = with(density) { (destGeo.cellW * plan.footprint.colSpan).toDp() },
                                height = with(density) { (destGeo.cellH * plan.footprint.rowSpan).toDp() },
                            ),
                    )
                }
                val span = spanOf(surfaces, session.item)
                val sizingGeo = destGeo ?: surfaces.firstOrNull { it.contains(session.item) }?.geometry
                if (span != null && sizingGeo != null) {
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            x = (finger.x - sizingGeo.cellW * span.colSpan / 2f).roundToInt(),
                            y = (finger.y - sizingGeo.cellH * span.rowSpan / 2f).roundToInt(),
                        ),
                        size = DpSize(
                            with(density) { (sizingGeo.cellW * span.colSpan).toDp() },
                            with(density) { (sizingGeo.cellH * span.rowSpan).toDp() },
                        ),
                    ) {
                        ItemTile(session.item, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ── Surfaces ─────────────────────────────────────────────────────────────────────────────────────────────

/** One drop zone in the harness: identity, grid, and its placement model. */
private class DemoSurface(val id: ZoneId, val config: GridConfig, val model: SurfaceModel) {
    /** Where this zone sits in root/window space; set as it measures, read by the planner + overlay. */
    var geometry: GridGeometry? by mutableStateOf(null)
}

/** How a surface stores where its items go — the two primitives of the arrangement model. */
private sealed interface SurfaceModel {
    /** Free placement: item → exact [GridPlacement]; a drop pushes occupants. */
    class Coordinate(val placements: SnapshotStateMap<GridItem, GridPlacement>) : SurfaceModel

    /** MovingGap flow: item → ordinal in [order]; a drag migrates a visible gap, densified on drop. */
    class Ordered(val order: SnapshotStateList<GridItem>, val allowMerge: Boolean) : SurfaceModel {
        /** Live insertion index (0..others) while a drag is over this surface; -1 when idle. */
        var gap: Int by mutableStateOf(-1)
    }
}

private val DemoSurface.coordinatePlacements: SnapshotStateMap<GridItem, GridPlacement>?
    get() = (model as? SurfaceModel.Coordinate)?.placements
private val DemoSurface.orderList: SnapshotStateList<GridItem>?
    get() = (model as? SurfaceModel.Ordered)?.order

private fun DemoSurface.contains(item: GridItem): Boolean =
    coordinatePlacements?.containsKey(item) == true || orderList?.contains(item) == true

/** The dragged item's span. Coordinate items keep their placement; ordered items are always 1×1. */
private fun spanOf(surfaces: List<DemoSurface>, item: GridItem): GridPlacement? {
    for (s in surfaces) {
        s.coordinatePlacements?.get(item)?.let { return it }
        if (s.orderList?.contains(item) == true) return GridPlacement(0, 0, 0)
    }
    return null
}

/**
 * Renders a coordinate (free-placement) zone on [LauncherGrid]. The zone box is sized by [sizeModifier]; the
 * grid fills it and lays out each tile from its [GridPlacement] against *measured* cells. The zone's
 * [GridGeometry] is derived from the box's measured bounds (origin + cell size = bounds ÷ cols/rows) — the
 * geometry seam — so the drag layer's finger→cell maths reads the same cell size the grid draws with, and
 * can't drift when the surface resizes. Tiles are no longer given an explicit size; the grid measures them.
 */
@Composable
private fun GridSurface(
    surface: DemoSurface,
    coordinator: DragCoordinator,
    sizeModifier: Modifier,
    showGuides: Boolean,
    gestureConfig: ItemGestureConfig,
    onToast: (String) -> Unit,
    onDrop: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    val placements = (surface.model as SurfaceModel.Coordinate).placements

    DisposableEffect(surface, coordinator) {
        onDispose { coordinator.unregisterZone(surface.id) }
    }

    // Live push preview, dwelled. The plan is recomputed every drag frame, but we only *apply* it to the tiles
    // once the finger has rested on the same plan for PUSH_DWELL_MS: a fast drag keeps changing the plan, which
    // restarts the timer, so occupants never strobe between pushed and home — they reflow only when the user
    // pauses (signalling they might drop here). LaunchedEffect(livePlan) restarts on every plan change because
    // PlacementPlan is a data class, so an unchanged footprint+moves (finger holding still) lets the delay
    // finish. Dropping still commits the live plan regardless of the dwell.
    val session = coordinator.session
    val livePlan = session?.takeIf { it.activeZone == surface.id }?.plan
    var dwelledPlan by remember { mutableStateOf<PlacementPlan?>(null) }
    LaunchedEffect(livePlan) {
        if (livePlan == null) {
            dwelledPlan = null // finger left the zone → occupants return home immediately
        } else {
            delay(PUSH_DWELL_MS)
            dwelledPlan = livePlan
        }
    }

    Box(
        sizeModifier
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                surface.geometry = GridGeometry(
                    originInRoot = Offset(b.left, b.top),
                    cellW = b.width / surface.config.cols,
                    cellH = b.height / surface.config.rows,
                    cols = surface.config.cols,
                    rows = surface.config.rows,
                )
                coordinator.registerZone(DropZone(surface.id, b, z = 0) { true })
            },
    ) {
        LauncherGrid(config = surface.config, modifier = Modifier.fillMaxSize()) {
            // Coordinate strategy: each item sits at an explicit cell — its stored placement, or its dwelled
            // push-preview cell while a drag hovers this zone.
            coordinateItems(
                items = placements.keys.toList(),
                placement = { dwelledPlan?.moves?.get(it) ?: placements.getValue(it) },
            ) { item, cellModifier ->
                val isDragged = session?.item == item
                Box(
                    cellModifier
                        // Occupants glide to their previewed (pushed) cells live; the dragged tile skips it so
                        // it lands at the committed cell without gliding in from where it started.
                        .then(if (isDragged) Modifier else Modifier.animatePlacement())
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        .launcherItemGestures(
                            config = gestureConfig,
                            edgeActions = SwipeDirection.entries.toSet(),
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
                    ItemTile(item, Modifier.fillMaxSize())
                }
            }
        }
        if (showGuides) ZoneGuides(placements.values.toList(), surface.config, Modifier.matchParentSize())
    }
}

/**
 * Renders an ordered (MovingGap) zone **on [LauncherGrid]** — the same grid the coordinate surfaces use, proving
 * the ordered flow reduces to placements. Each item's display slot (from [displaySlots], accounting for the live
 * gap) becomes a `GridPlacement(row = slot / cols, col = slot % cols)`; the grid places it against measured
 * cells, and the geometry seam is derived from the box bounds exactly like the coordinate surfaces. The dragged
 * tile is kept composed but invisible — the floating proxy stands in. Only accepts its own items (reorder);
 * items are extracted to coordinate zones by dropping there.
 *
 * Slot changes currently **snap** (no gap-migration animation): swapping onto the grid dropped the per-tile
 * `animateIntOffsetAsState`. Animation returns for *both* coordinate push and ordered gap through one
 * `LookaheadScope` mechanism in the next phase (grid plan G4) — the plan deliberately unifies them there rather
 * than re-adding a one-off tween here.
 */
@Composable
private fun OrderedSurface(
    surface: DemoSurface,
    coordinator: DragCoordinator,
    sizeModifier: Modifier,
    gestureConfig: ItemGestureConfig,
    onToast: (String) -> Unit,
    onDrop: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    val model = surface.model as SurfaceModel.Ordered
    val cols = surface.config.cols

    DisposableEffect(surface, coordinator) {
        onDispose { coordinator.unregisterZone(surface.id) }
    }

    Box(
        sizeModifier
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                surface.geometry = GridGeometry(
                    originInRoot = Offset(b.left, b.top),
                    cellW = b.width / cols,
                    cellH = b.height / surface.config.rows,
                    cols = cols,
                    rows = surface.config.rows,
                )
                coordinator.registerZone(DropZone(surface.id, b, z = 0) { it in model.order })
            },
    ) {
        val dragged = coordinator.session?.item?.takeIf { it in model.order }
        val gap = when {
            dragged == null -> -1
            model.gap >= 0 -> model.gap
            else -> model.order.indexOf(dragged)
        }
        val slots = displaySlots(model.order, dragged, gap)
        // Flow strategy: sort the order by each item's live slot, and flowItems lays that list out row-major.
        // A migrating gap just reorders this list, so the reflow falls out of the flow itself.
        val displayOrder = model.order.sortedBy { slots.getValue(it) }
        LauncherGrid(config = surface.config, modifier = Modifier.fillMaxSize()) {
            flowItems(displayOrder) { entry, cellModifier ->
                val isDragged = entry == dragged
                Box(
                    cellModifier
                        // Items slide as the gap migrates; the dragged tile skips it (the proxy stands in).
                        .then(if (isDragged) Modifier else Modifier.animatePlacement())
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        .launcherItemGestures(
                            config = gestureConfig,
                            edgeActions = SwipeDirection.entries.toSet(),
                            onOpen = { onToast("open ${label(entry)}") },
                            onEdgeAction = { onToast("swipe $it on ${label(entry)}") },
                            onShowMenu = { onToast("menu: ${label(entry)}") },
                            onDismissMenu = {},
                            onBeginDrag = { root -> coordinator.start(entry, root) },
                            onDragTo = { root -> coordinator.moveTo(root) },
                            onDrop = { onDrop() },
                            onCancelDrag = { coordinator.cancel() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ItemTile(entry, Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ── Planning ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Plans a drop within a coordinate zone: the occupant under the finger (if any) is the push/merge target —
 * its whole rectangle is one 4-way push partition with a central merge ring (§6a). The footprint snaps to the
 * dragged item's own position (half-cell hysteresis).
 */
private fun planWithin(
    placements: SnapshotStateMap<GridItem, GridPlacement>,
    geo: GridGeometry,
    config: GridConfig,
    item: GridItem,
    span: GridPlacement,
    fingerInRoot: Offset,
): PlacementPlan {
    val occupants = placements.filterKeys { it != item }
    val hovered = geo.cellAt(fingerInRoot)
    val target = hovered?.let { h -> occupants.entries.firstOrNull { it.value.covers(h) } }

    val topLeft = geo.snapTopLeftCell(fingerInRoot, span.colSpan, span.rowSpan)
    val footprint = GridPlacement(0, topLeft.row, topLeft.col, span.rowSpan, span.colSpan)

    if (target != null) {
        val rect = target.value
        if (canMerge(item, target.key) && geo.inMergeRingOf(fingerInRoot, rect)) {
            return FreeGridPlanner.plan(rect, occupants, config, merge = true)
        }
        val preferred = geo.pushDirectionInRect(fingerInRoot, rect)
        return FreeGridPlanner.plan(footprint, occupants, config, preferred)
    }
    return FreeGridPlanner.plan(footprint, occupants, config)
}

/**
 * Plans a drop within an ordered (MovingGap) zone, updating the live [SurfaceModel.Ordered.gap]. `others` is
 * the order minus the dragged item; the gap is an insertion index into it. The cell the finger is over maps to
 * a display slot; the item there and which horizontal third decide the new gap — LEFT third → before it,
 * RIGHT → after it, CENTER (if mergeable) → merge. The footprint (for the shadow) is the gap cell, or the
 * target's cell when merging.
 */
private fun orderedPlan(
    model: SurfaceModel.Ordered,
    geo: GridGeometry,
    config: GridConfig,
    item: GridItem,
    fingerInRoot: Offset,
): PlacementPlan? {
    val cols = config.cols
    val cell = geo.cellAt(fingerInRoot) ?: return null
    val others = model.order.filter { it != item }
    if (model.gap < 0) model.gap = model.order.indexOf(item).coerceIn(0, others.size)
    var g = model.gap.coerceIn(0, others.size)
    val slot = cell.row * cols + cell.col

    when {
        slot > others.size -> g = others.size          // empty trailing cell → append
        slot == g -> Unit                               // over the gap itself → no change
        else -> {
            val j = if (slot < g) slot else slot - 1    // others-index of the hovered item
            if (j in others.indices) {
                when (val third = geo.thirdInCell(fingerInRoot)) {
                    Third.CENTER -> if (model.allowMerge && canMerge(item, others[j])) {
                        model.gap = g
                        return PlacementPlan(GridPlacement(0, slot / cols, slot % cols), DropIntent.MERGE)
                    }
                    Third.LEFT -> g = j
                    Third.RIGHT -> g = j + 1
                }
            }
        }
    }
    model.gap = g
    return PlacementPlan(GridPlacement(0, g / cols, g % cols), DropIntent.PLACE)
}

/** Item → display slot when rendering [order] with [dragged] lifted and its gap at index [g]. */
private fun displaySlots(order: List<GridItem>, dragged: GridItem?, g: Int): Map<GridItem, Int> {
    if (dragged == null) return order.withIndex().associate { (i, it) -> it to i }
    val map = HashMap<GridItem, Int>(order.size)
    var k = 0
    for (entry in order) {
        if (entry == dragged) continue
        map[entry] = if (k < g) k else k + 1
        k++
    }
    map[dragged] = g
    return map
}

/**
 * Applies a committed drop. Removes the item from its source (placement map or order), then lands it in the
 * destination: a coordinate zone shifts pushed occupants and places it on the footprint; an ordered zone
 * splices it into the flow at the gap index the footprint encodes. A MERGE just removes it (no real folders).
 */
private fun applyOutcome(surfaces: List<DemoSurface>, item: GridItem, zone: ZoneId, plan: PlacementPlan) {
    val dest = surfaces.first { it.id == zone }
    val source = surfaces.first { it.contains(item) }

    fun removeFromSource() {
        source.coordinatePlacements?.remove(item)
        source.orderList?.remove(item)
    }

    when (val dm = dest.model) {
        is SurfaceModel.Coordinate -> {
            if (plan.intent == DropIntent.MERGE) {
                removeFromSource()
                return
            }
            plan.moves.forEach { (moved, placement) -> dm.placements[moved] = placement }
            removeFromSource()
            dm.placements[item] = plan.footprint
        }
        is SurfaceModel.Ordered -> {
            if (plan.intent == DropIntent.MERGE) {
                removeFromSource()
                return
            }
            val g = plan.footprint.row * dest.config.cols + plan.footprint.col
            removeFromSource() // if same surface, this is the item leaving its old slot
            dm.order.add(g.coerceIn(0, dm.order.size), item)
        }
    }
}

// ── Rendering helpers ────────────────────────────────────────────────────────────────────────────────────

/**
 * Debug overlay drawing each **item's** partition for coordinate zones: the two diagonals split the item's
 * whole rectangle into four push triangles, a single central merge ring, and four arrows for the push
 * directions. Empty cells have no zones.
 */
@Composable
private fun ZoneGuides(rects: List<GridPlacement>, config: GridConfig, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Cell size measured from this overlay (which matches the grid it sits over), not a fixed constant.
        val cellW = size.width / config.cols
        val cellH = size.height / config.rows
        val line = Color.White.copy(alpha = 0.20f)
        val ring = Color(0xFF63C7C9).copy(alpha = 0.6f)
        val arrowColor = Color.White.copy(alpha = 0.45f)
        val thin = 1.dp.toPx()
        for (rect in rects) {
            val l = rect.col * cellW
            val t = rect.row * cellH
            val w = rect.colSpan * cellW
            val h = rect.rowSpan * cellH
            val cx = l + w / 2f
            val cy = t + h / 2f
            val offX = w * 0.24f
            val offY = h * 0.24f
            val len = minOf(w, h) * 0.14f
            drawLine(line, Offset(l, t), Offset(l + w, t + h), thin)
            drawLine(line, Offset(l + w, t), Offset(l, t + h), thin)
            drawCircle(ring, MERGE_INNER_RADIUS * minOf(w, h), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            arrow(arrowColor, Offset(cx, t + offY), Offset(cx, t + offY + len), thin)             // top → down
            arrow(arrowColor, Offset(cx, t + h - offY), Offset(cx, t + h - offY - len), thin)     // bottom → up
            arrow(arrowColor, Offset(l + offX, cy), Offset(l + offX + len, cy), thin)             // left → right
            arrow(arrowColor, Offset(l + w - offX, cy), Offset(l + w - offX - len, cy), thin)     // right → left
        }
    }
}

/** Draws a short line from [from] to [to] with a small arrowhead at [to]. */
private fun DrawScope.arrow(color: Color, from: Offset, to: Offset, width: Float) {
    drawLine(color, from, to, width)
    val d = to - from
    val n = d.getDistance().coerceAtLeast(0.001f)
    val ux = d.x / n
    val uy = d.y / n
    val head = 7.dp.toPx()
    val spread = 0.5f
    val b1 = Offset(to.x - head * (ux + -uy * spread), to.y - head * (uy + ux * spread))
    val b2 = Offset(to.x - head * (ux - -uy * spread), to.y - head * (uy - ux * spread))
    drawLine(color, to, b1, width)
    drawLine(color, to, b2, width)
}

/** A fake app tile standing in for an app icon: a coloured rounded box (span-sized by the caller). */
@Composable
private fun ItemTile(item: GridItem, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tileColor(label(item))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label(item).take(1), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ── Rules + geometry ─────────────────────────────────────────────────────────────────────────────────────

/** Whether [dragged] can merge onto [target]. In the harness everything is an app, so any two distinct apps
 * combine (a folder, in production). Real rules differ by type and would gate the merge per §6c. */
private fun canMerge(dragged: GridItem, target: GridItem): Boolean =
    dragged is GridItem.App && target is GridItem.App && dragged != target

/** Whether [cell] falls inside this placement's rectangle. */
private fun GridPlacement.covers(cell: Cell): Boolean =
    cell.row in row until rowEndExclusive && cell.col in col until colEndExclusive

/** The horizontal third of a cell — the `[left | center | right]` partition of the MovingGap surfaces (§6a). */
private enum class Third { LEFT, CENTER, RIGHT }

/**
 * A zone's placement in root/window space, plus the maths mapping a finger to cells and item-relative zones.
 * Cells may be non-square ([cellW] ≠ [cellH]) — a responsive grid derives each from measured space ÷ cols/rows.
 */
private data class GridGeometry(
    val originInRoot: Offset,
    val cellW: Float,
    val cellH: Float,
    val cols: Int,
    val rows: Int,
) {
    /**
     * The footprint's top-left cell for an item of [colSpan]×[rowSpan] whose proxy is centred on the finger.
     * Uses the item's own top-left **rounded** to the nearest cell — the footprint holds still until the item
     * has moved half a cell, then steps one cell over (half-cell hysteresis). Clamped to stay on the grid.
     */
    fun snapTopLeftCell(fingerInRoot: Offset, colSpan: Int, rowSpan: Int): Cell {
        val topLeftX = fingerInRoot.x - originInRoot.x - colSpan * cellW / 2f
        val topLeftY = fingerInRoot.y - originInRoot.y - rowSpan * cellH / 2f
        val col = (topLeftX / cellW).roundToInt().coerceIn(0, (cols - colSpan).coerceAtLeast(0))
        val row = (topLeftY / cellH).roundToInt().coerceIn(0, (rows - rowSpan).coerceAtLeast(0))
        return Cell(row, col)
    }

    /** The cell directly under [rootPosition], or null when the finger is outside the grid. */
    fun cellAt(rootPosition: Offset): Cell? {
        val lx = rootPosition.x - originInRoot.x
        val ly = rootPosition.y - originInRoot.y
        if (lx < 0f || ly < 0f) return null
        val col = (lx / cellW).toInt()
        val row = (ly / cellH).toInt()
        return if (row in 0 until rows && col in 0 until cols) Cell(row, col) else null
    }

    /** The horizontal third of the hovered cell the finger sits in (MovingGap partition). */
    fun thirdInCell(rootPosition: Offset): Third {
        val lx = rootPosition.x - originInRoot.x
        val fx = lx / cellW - floor(lx / cellW)
        return when {
            fx < 1f / 3f -> Third.LEFT
            fx > 2f / 3f -> Third.RIGHT
            else -> Third.CENTER
        }
    }

    /** Merge-ring radius (px) for the item occupying [rect] — scaled by its smaller side. */
    fun mergeRadius(rect: GridPlacement): Float =
        MERGE_INNER_RADIUS * minOf(rect.colSpan * cellW, rect.rowSpan * cellH)

    /**
     * True when the finger sits in the **inner merge ring** of the item occupying [rect] — a single circle at
     * the item's centre. The whole item is one target, so a multi-cell item has exactly one ring (§6a).
     */
    fun inMergeRingOf(fingerInRoot: Offset, rect: GridPlacement): Boolean {
        val dx = fingerInRoot.x - (originInRoot.x + (rect.col + rect.colSpan / 2f) * cellW)
        val dy = fingerInRoot.y - (originInRoot.y + (rect.row + rect.rowSpan / 2f) * cellH)
        val radius = mergeRadius(rect)
        return dx * dx + dy * dy < radius * radius
    }

    /**
     * Which way to push the item occupying [rect], from where the finger sits **within that item's rectangle**.
     * The rectangle is split into four triangles by its diagonals; the occupant is shoved away from the edge
     * the finger is nearest — left triangle pushes right, top pushes down, and so on (§6a).
     */
    fun pushDirectionInRect(fingerInRoot: Offset, rect: GridPlacement): PushDirection {
        val fx = (fingerInRoot.x - (originInRoot.x + rect.col * cellW)) / (rect.colSpan * cellW) - 0.5f
        val fy = (fingerInRoot.y - (originInRoot.y + rect.row * cellH)) / (rect.rowSpan * cellH) - 0.5f
        return if (abs(fx) > abs(fy)) {
            if (fx < 0f) PushDirection.RIGHT else PushDirection.LEFT
        } else {
            if (fy < 0f) PushDirection.DOWN else PushDirection.UP
        }
    }

    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellW, originInRoot.y + row * cellH)
}

private data class Cell(val row: Int, val col: Int)

private const val HOME_ROWS = 4
private const val DOCK_ROWS = 1
private const val COLS = 4

/** How long the finger must rest on a push before occupants reflow — long enough a fast drag-through won't
 * flicker, short enough a deliberate hover feels responsive. */
private const val PUSH_DWELL_MS = 200L

/** Merge-ring radius as a fraction of the item's smaller span (×cellPx); inside it is the merge target. */
private const val MERGE_INNER_RADIUS = 0.3f

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
