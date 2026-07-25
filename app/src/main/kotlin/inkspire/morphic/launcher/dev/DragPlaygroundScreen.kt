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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dev harness for the drag-and-drop stack. Now with **two free-placement zones** — a home grid and a dock —
 * to exercise cross-surface drag: one [DragCoordinator] hit-tests both registered [DropZone]s, and behaviour
 * travels with the **destination** zone (a drop into the dock uses the dock's grid/geometry, whatever the drag
 * came from). Both zones stay composed, so the dragged tile's own pointer stream still tracks the whole
 * gesture — the root-overlay takeover is only needed once a *source* surface can leave composition mid-drag
 * (a side surface), which is the next step.
 *
 * Items are fake colour tiles standing in for app icons. Try: drag a tile between home and the dock; the drop
 * shadow follows into whichever zone the finger is over. Long-press → menu toast; tap → open toast;
 * press-and-swipe → direction toast. Toggle the zone guides with the "zones" label.
 */
@Composable
fun DragPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        val context = LocalContext.current
        val density = LocalDensity.current

        val cellDp = 76.dp
        val cellPx = with(density) { cellDp.toPx() }
        val gestureConfig = remember {
            ItemGestureConfig(touchSlopPx = cellPx * 0.25f, longPressTimeoutMillis = 400L)
        }
        var showGuides by remember { mutableStateOf(true) }

        // Two zones, each with its own grid + live placements.
        val home = remember {
            DemoSurface(
                id = ZoneId("home"),
                config = GridConfig(rows = HOME_ROWS, cols = COLS),
                placements = mutableStateMapOf(
                    demoApp("Phone") to GridPlacement(0, 0, 0),
                    demoApp("Messages") to GridPlacement(0, 0, 1),
                    demoApp("Weather") to GridPlacement(0, 0, 2, rowSpan = 2, colSpan = 2),
                    demoApp("Camera") to GridPlacement(0, 1, 0),
                    demoApp("Maps") to GridPlacement(0, 1, 1),
                    demoApp("Music") to GridPlacement(0, 2, 0, rowSpan = 1, colSpan = 2),
                    demoApp("Clock") to GridPlacement(0, 2, 2),
                    demoApp("Photos") to GridPlacement(0, 3, 0, rowSpan = 2, colSpan = 2),
                    demoApp("Notes") to GridPlacement(0, 4, 3),
                ),
            )
        }
        val dock = remember {
            DemoSurface(
                id = ZoneId("dock"),
                config = GridConfig(rows = DOCK_ROWS, cols = COLS),
                placements = mutableStateMapOf(
                    demoApp("Dialer") to GridPlacement(0, 0, 0),
                    demoApp("Browser") to GridPlacement(0, 0, 1),
                ),
            )
        }
        val drawer = remember {
            DemoSurface(
                id = ZoneId("drawer"),
                config = GridConfig(rows = 3, cols = 1),
                placements = mutableStateMapOf(
                    demoApp("Gmail") to GridPlacement(0, 0, 0),
                    demoApp("Chrome") to GridPlacement(0, 1, 0),
                    demoApp("Slack") to GridPlacement(0, 2, 0),
                ),
            )
        }
        val surfaces = remember { listOf(home, dock, drawer) }

        // The engine-backed planner dispatches on the destination zone: it plans within *that* surface's grid,
        // using its geometry, occupants, and config. The dragged item's span is looked up wherever it lives.
        val planner = remember {
            DropPlanner { zone, item, fingerInRoot ->
                val surface = surfaces.firstOrNull { it.id == zone.id } ?: return@DropPlanner null
                val geo = surface.geometry ?: return@DropPlanner null
                val span = surfaces.firstNotNullOfOrNull { it.placements[item] } ?: return@DropPlanner null
                planWithin(surface, geo, item, span, fingerInRoot)
            }
        }
        val coordinator = rememberDragCoordinator(planner)

        fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

        fun handleDrop() {
            val outcome = coordinator.drop() ?: return
            if (outcome.plan.intent == DropIntent.MERGE) {
                val dest = surfaces.first { it.id == outcome.zone }
                val onto = dest.placements.entries.firstOrNull {
                    it.key != outcome.item && it.value == outcome.plan.footprint
                }
                toast("merge ${label(outcome.item)} → ${onto?.key?.let(::label) ?: "?"}")
            }
            applyOutcome(surfaces, outcome.item, outcome.zone, outcome.plan)
        }

        // The drawer *unmounts* when a drag that started in it leaves its bounds — the moment its tile leaves
        // composition, only the root DragTrackingOverlay can keep the drag alive. This is the cross-surface
        // case L1 needed HomeDragBridge for. It remounts once the drag ends.
        var drawerMounted by remember { mutableStateOf(true) }
        val activeZone = coordinator.session?.activeZone
        LaunchedEffect(activeZone) {
            val s = coordinator.session
            if (s != null && drawer.placements.containsKey(s.item) && s.activeZone != drawer.id) {
                drawerMounted = false
            }
        }
        LaunchedEffect(coordinator.isDragging) {
            if (!coordinator.isDragging) drawerMounted = true
        }

        Box(modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // Fixed-width slot so home/dock stay put when the drawer unmounts mid-drag (a real side surface
                // floats over a stationary home rather than pushing it around).
                Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                    if (drawerMounted) {
                        GridSurface(drawer, coordinator, cellDp, cellPx, showGuides, gestureConfig, ::toast, ::handleDrop)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                ) {
                    GridSurface(home, coordinator, cellDp, cellPx, showGuides, gestureConfig, ::toast, ::handleDrop)
                    GridSurface(dock, coordinator, cellDp, cellPx, showGuides, gestureConfig, ::toast, ::handleDrop)
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
                            .size(width = cellDp * plan.footprint.colSpan, height = cellDp * plan.footprint.rowSpan),
                    )
                }
                val span = surfaces.firstNotNullOfOrNull { it.placements[session.item] }
                if (span != null) {
                    val finger = session.fingerInRoot
                    FloatingDragIcon(
                        rootOffset = IntOffset(
                            x = (finger.x - cellPx * span.colSpan / 2f).roundToInt(),
                            y = (finger.y - cellPx * span.rowSpan / 2f).roundToInt(),
                        ),
                        size = DpSize(cellDp * span.colSpan, cellDp * span.rowSpan),
                    ) {
                        ItemTile(session.item, Modifier.fillMaxSize())
                    }
                }
            }

            // Root-level tracker: owns the finger once a drag begins, so the drag survives the drawer (or any
            // source surface) leaving composition mid-gesture.
            DragTrackingOverlay(coordinator, ::handleDrop)
        }
    }
}

/**
 * A full-screen, transparent pointer layer at the root. It is **passive until a drag is in flight** (so taps,
 * long-press, and swipes on items work normally), then it drives [DragCoordinator.moveTo] / drop from its own
 * events. Because it lives at the root — not in any surface — it keeps tracking the finger even after the
 * source surface unmounts mid-drag. This is the structural replacement for L1's HomeDragBridge handoff (§5).
 */
@Composable
private fun DragTrackingOverlay(coordinator: DragCoordinator, onDrop: () -> Unit, modifier: Modifier = Modifier) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Do nothing until a drag exists, leaving item gestures untouched.
                        if (!coordinator.isDragging) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val root = coords?.localToRoot(change.position) ?: change.position
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                            onDrop()
                        } else {
                            coordinator.moveTo(root)
                            change.consume() // become the sole mover, so the source tile doesn't double-track
                        }
                    }
                }
            },
    )
}

/** One free-placement drop zone in the harness: its identity, grid, and the live map of what sits where. */
private class DemoSurface(
    val id: ZoneId,
    val config: GridConfig,
    val placements: SnapshotStateMap<GridItem, GridPlacement>,
) {
    /** Where this zone sits in root/window space; set as it measures, read by the planner + overlay. */
    var geometry: GridGeometry? by mutableStateOf(null)
}

/** Renders one zone: registers its [DropZone], lays out its tiles with the gesture modifier, draws guides. */
@Composable
private fun GridSurface(
    surface: DemoSurface,
    coordinator: DragCoordinator,
    cellDp: Dp,
    cellPx: Float,
    showGuides: Boolean,
    gestureConfig: ItemGestureConfig,
    onToast: (String) -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current

    DisposableEffect(surface, coordinator) {
        onDispose { coordinator.unregisterZone(surface.id) }
    }

    Box(
        modifier
            .size(width = cellDp * surface.config.cols, height = cellDp * surface.config.rows)
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .onGloballyPositioned {
                surface.geometry = GridGeometry(it.positionInRoot(), cellPx, surface.config.cols, surface.config.rows)
                coordinator.registerZone(DropZone(surface.id, it.boundsInRoot(), z = 0) { true })
            },
    ) {
        for ((item, placement) in surface.placements) {
            val isDragged = coordinator.session?.item == item
            key(item) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                x = (placement.col * cellPx).roundToInt(),
                                y = (placement.row * cellPx).roundToInt(),
                            )
                        }
                        .size(width = cellDp * placement.colSpan, height = cellDp * placement.rowSpan)
                        // Keep the dragged tile composed (pointerInput must survive the drag) but invisible.
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        .launcherItemGestures(
                            config = gestureConfig,
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
        if (showGuides) ZoneGuides(surface.placements.values.toList(), cellPx, Modifier.matchParentSize())
    }
}

/**
 * Plans a drop within one [surface]: the occupant under the finger (if any) is the push/merge target — its
 * whole rectangle is one 4-way push partition with a central merge ring (§6a). The footprint snaps to the
 * dragged item's own position (half-cell hysteresis). Occupants exclude the dragged item, so dragging it
 * within its home surface doesn't collide with itself.
 */
private fun planWithin(
    surface: DemoSurface,
    geo: GridGeometry,
    item: GridItem,
    span: GridPlacement,
    fingerInRoot: Offset,
): PlacementPlan {
    val config = surface.config
    val occupants = surface.placements.filterKeys { it != item }

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
 * Applies a committed drop across zones. The destination is [zone]; the source is wherever [item] currently
 * lives. A MERGE drops the item into its target (here, removed from the grid). Otherwise the pushed occupants
 * shift **in the destination**, the item leaves its source zone (if different), and lands on its footprint.
 */
private fun applyOutcome(
    surfaces: List<DemoSurface>,
    item: GridItem,
    zone: ZoneId,
    plan: PlacementPlan,
) {
    val dest = surfaces.first { it.id == zone }
    val source = surfaces.first { item in it.placements }

    if (plan.intent == DropIntent.MERGE) {
        source.placements.remove(item)
        return
    }
    plan.moves.forEach { (moved, placement) -> dest.placements[moved] = placement }
    if (source !== dest) source.placements.remove(item)
    dest.placements[item] = plan.footprint
}

/**
 * Debug overlay drawing each **item's** partition so the merge/push zones are visible. Zones belong to
 * occupants, not cells: one item (however many cells it spans) gets one set of diagonals splitting its whole
 * rectangle into four push triangles, one central merge ring, and four arrows showing which way it is shoved
 * from each zone. Empty cells have no zones — nothing to push or merge there.
 */
@Composable
private fun ZoneGuides(rects: List<GridPlacement>, cellPx: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Color.White.copy(alpha = 0.20f)
        val ring = Color(0xFF63C7C9).copy(alpha = 0.6f)
        val arrowColor = Color.White.copy(alpha = 0.45f)
        val thin = 1.dp.toPx()
        for (rect in rects) {
            val l = rect.col * cellPx
            val t = rect.row * cellPx
            val w = rect.colSpan * cellPx
            val h = rect.rowSpan * cellPx
            val cx = l + w / 2f
            val cy = t + h / 2f
            val offX = w * 0.24f
            val offY = h * 0.24f
            val len = minOf(w, h) * 0.14f
            // Diagonals → the four push triangles across the whole item.
            drawLine(line, Offset(l, t), Offset(l + w, t + h), thin)
            drawLine(line, Offset(l + w, t), Offset(l, t + h), thin)
            // A single inner merge ring, scaled by the item's smaller span.
            drawCircle(ring, MERGE_INNER_RADIUS * minOf(w, h), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            // Push arrows (occupant is shoved away from the entered edge).
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

/** Whether [dragged] can merge onto [target]. In the harness everything is an app, so any two distinct apps
 * combine (a folder, in production). Real rules differ by type and would gate the merge ring per §6c. */
private fun canMerge(dragged: GridItem, target: GridItem): Boolean =
    dragged is GridItem.App && target is GridItem.App && dragged != target

/** Whether [cell] falls inside this placement's rectangle. */
private fun GridPlacement.covers(cell: Cell): Boolean =
    cell.row in row until rowEndExclusive && cell.col in col until colEndExclusive

/** A zone's placement in root/window space, plus the maths mapping a finger to a cell / item-relative zone. */
private data class GridGeometry(
    val originInRoot: Offset,
    val cellPx: Float,
    val cols: Int,
    val rows: Int,
) {
    /**
     * The footprint's top-left cell for an item of [colSpan]×[rowSpan] whose proxy is centred on the finger.
     * Uses the item's own top-left **rounded** to the nearest cell — the footprint holds still until the item
     * has moved half a cell, then steps one cell over (half-cell hysteresis). Clamped to stay on the grid.
     */
    fun snapTopLeftCell(fingerInRoot: Offset, colSpan: Int, rowSpan: Int): Cell {
        val topLeftX = fingerInRoot.x - originInRoot.x - colSpan * cellPx / 2f
        val topLeftY = fingerInRoot.y - originInRoot.y - rowSpan * cellPx / 2f
        val col = (topLeftX / cellPx).roundToInt().coerceIn(0, (cols - colSpan).coerceAtLeast(0))
        val row = (topLeftY / cellPx).roundToInt().coerceIn(0, (rows - rowSpan).coerceAtLeast(0))
        return Cell(row, col)
    }

    /** The cell directly under [rootPosition], or null when the finger is outside the grid. */
    fun cellAt(rootPosition: Offset): Cell? {
        val lx = rootPosition.x - originInRoot.x
        val ly = rootPosition.y - originInRoot.y
        if (lx < 0f || ly < 0f) return null
        val col = (lx / cellPx).toInt()
        val row = (ly / cellPx).toInt()
        return if (row in 0 until rows && col in 0 until cols) Cell(row, col) else null
    }

    /** Merge-ring radius (px) for the item occupying [rect] — scaled by its smaller span. */
    fun mergeRadius(rect: GridPlacement): Float = MERGE_INNER_RADIUS * minOf(rect.colSpan, rect.rowSpan) * cellPx

    /**
     * True when the finger sits in the **inner merge ring** of the item occupying [rect] — a single circle at
     * the item's centre. The whole item is one target, so a multi-cell item has exactly one ring (§6a).
     */
    fun inMergeRingOf(fingerInRoot: Offset, rect: GridPlacement): Boolean {
        val dx = fingerInRoot.x - (originInRoot.x + (rect.col + rect.colSpan / 2f) * cellPx)
        val dy = fingerInRoot.y - (originInRoot.y + (rect.row + rect.rowSpan / 2f) * cellPx)
        val radius = mergeRadius(rect)
        return dx * dx + dy * dy < radius * radius
    }

    /**
     * Which way to push the item occupying [rect], from where the finger sits **within that item's rectangle**.
     * The rectangle is split into four triangles by its diagonals; the occupant is shoved away from the edge
     * the finger is nearest — left triangle pushes right, top pushes down, and so on. Relative to the whole
     * item, so a multi-cell item is one 4-way partition, not one per sub-cell (§6a).
     */
    fun pushDirectionInRect(fingerInRoot: Offset, rect: GridPlacement): PushDirection {
        val fx = (fingerInRoot.x - (originInRoot.x + rect.col * cellPx)) / (rect.colSpan * cellPx) - 0.5f
        val fy = (fingerInRoot.y - (originInRoot.y + rect.row * cellPx)) / (rect.rowSpan * cellPx) - 0.5f
        return if (abs(fx) > abs(fy)) {
            if (fx < 0f) PushDirection.RIGHT else PushDirection.LEFT
        } else {
            if (fy < 0f) PushDirection.DOWN else PushDirection.UP
        }
    }

    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellPx, originInRoot.y + row * cellPx)
}

private data class Cell(val row: Int, val col: Int)

private const val HOME_ROWS = 5
private const val DOCK_ROWS = 1
private const val COLS = 4

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
