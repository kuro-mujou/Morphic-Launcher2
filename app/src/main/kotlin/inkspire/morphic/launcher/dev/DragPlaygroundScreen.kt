package inkspire.morphic.launcher.dev

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
 * Dev harness for the drag-and-drop stack — the first place the whole loop runs together: the
 * [rememberDragCoordinator], one registered [DropZone], the [launcherItemGestures] modifier on each cell, the
 * engine-backed [DropPlanner] (via [FreeGridPlanner]), and the [FloatingDragIcon] / [DropFootprint] visuals.
 *
 * Scope is deliberately one **single free-placement grid** (home-main-like, one page, 1×1 items), so the core
 * push/drop logic can be exercised on a device before the real surfaces, spans, dock, pager, folders, or
 * cross-surface drops exist. Items are fake colour tiles standing in for app icons, so this tests drag logic
 * without pulling in the icon/app pipeline.
 *
 * Try: long-press a tile → a "menu" toast; then move → it lifts and follows the finger, the drop shadow
 * showing where it lands (accent = place, blue = push, red = no room). Release to drop. Tap = an "open" toast;
 * press-and-swipe = a direction toast.
 */
@Composable
fun DragPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        val context = LocalContext.current
        val density = LocalDensity.current

        val cellDp = 76.dp
        val cellPx = with(density) { cellDp.toPx() }
        val config = remember { GridConfig(rows = ROWS, cols = COLS) }

        // The live layout: which item sits where, and how big. Mutated on drop; drives recomposition. A mix
        // of 1×1, wide, and 2×2 items exercises span-aware push.
        val placements = remember {
            mutableStateMapOf(
                demoApp("Phone") to GridPlacement(0, 0, 0),
                demoApp("Messages") to GridPlacement(0, 0, 1),
                demoApp("Weather") to GridPlacement(0, 0, 2, rowSpan = 2, colSpan = 2),
                demoApp("Camera") to GridPlacement(0, 1, 0),
                demoApp("Maps") to GridPlacement(0, 1, 1),
                demoApp("Music") to GridPlacement(0, 2, 0, rowSpan = 1, colSpan = 2),
                demoApp("Clock") to GridPlacement(0, 2, 2),
                demoApp("Notes") to GridPlacement(0, 3, 3),
                demoApp("Photos") to GridPlacement(0, 4, 0, rowSpan = 2, colSpan = 2),
            )
        }

        // Where the grid sits in root/window space, so the planner can map a finger to a cell.
        var geometry by remember { mutableStateOf<GridGeometry?>(null) }

        // The engine-backed planner: finger → cell → FreeGridPlanner. Remembered once over stable refs.
        val planner = remember {
            DropPlanner { _, item, fingerInRoot ->
                val geo = geometry ?: return@DropPlanner null
                val span = placements[item] ?: return@DropPlanner null
                val occupants = placements.filterKeys { it != item }

                // The occupant under the finger (if any) is the push/merge target: its **whole rectangle**
                // forms one 4-way push partition with a single central merge ring — so a 2×2 is one target,
                // not four per-cell partitions (§6a). Eligibility is checked per hover (≈ §6c's precompute).
                val hovered = geo.cellAt(fingerInRoot)
                val target = hovered?.let { h -> occupants.entries.firstOrNull { it.value.covers(h) } }

                // Footprint snaps to the dragged item's own position (finger-centred, rounded to nearest cell —
                // half-cell hysteresis), clamped on-grid.
                val topLeft = geo.snapTopLeftCell(fingerInRoot, span.colSpan, span.rowSpan)
                val footprint = GridPlacement(0, topLeft.row, topLeft.col, span.rowSpan, span.colSpan)

                if (target != null) {
                    val rect = target.value
                    if (canMerge(item, target.key) && geo.inMergeRingOf(fingerInRoot, rect)) {
                        return@DropPlanner FreeGridPlanner.plan(rect, occupants, config, merge = true)
                    }
                    // Which triangle of the target item the finger is in decides the push direction.
                    val preferred = geo.pushDirectionInRect(fingerInRoot, rect)
                    return@DropPlanner FreeGridPlanner.plan(footprint, occupants, config, preferred)
                }

                // No occupant under the finger → plain place (nearest-edge push if the footprint still clips one).
                FreeGridPlanner.plan(footprint, occupants, config)
            }
        }
        val coordinator = rememberDragCoordinator(planner)
        val gestureConfig = remember {
            ItemGestureConfig(touchSlopPx = cellPx * 0.25f, longPressTimeoutMillis = 400L)
        }
        var showGuides by remember { mutableStateOf(true) }

        DisposableEffect(coordinator) {
            onDispose { coordinator.unregisterZone(GridZoneId) }
        }

        fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

        Box(modifier.fillMaxSize().background(colors.background)) {
            // ── The grid ────────────────────────────────────────────────────────────────
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = cellDp * COLS, height = cellDp * ROWS)
                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                    .onGloballyPositioned {
                        geometry = GridGeometry(it.positionInRoot(), cellPx, COLS, ROWS)
                        coordinator.registerZone(
                            DropZone(GridZoneId, it.boundsInRoot(), z = 0, accepts = { true }),
                        )
                    },
            ) {
                for ((item, placement) in placements) {
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
                                // Keep the dragged tile composed (its pointerInput must survive the drag) but
                                // invisible — the floating proxy stands in for it while it's in flight.
                                .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                                .launcherItemGestures(
                                    config = gestureConfig,
                                    onOpen = { toast("open ${label(item)}") },
                                    onEdgeAction = { toast("swipe $it on ${label(item)}") },
                                    onShowMenu = { toast("menu: ${label(item)}") },
                                    onDismissMenu = {},
                                    onBeginDrag = { root -> coordinator.start(item, root) },
                                    onDragTo = { root -> coordinator.moveTo(root) },
                                    onDrop = {
                                        coordinator.drop()?.let { outcome ->
                                            if (outcome.plan.intent == DropIntent.MERGE) {
                                                val onto = placements.entries.firstOrNull {
                                                    it.key != outcome.item && it.value == outcome.plan.footprint
                                                }
                                                toast("merge ${label(outcome.item)} → ${onto?.key?.let(::label) ?: "?"}")
                                            }
                                            applyDrop(placements, outcome.item, outcome.plan)
                                        }
                                    },
                                    onCancelDrag = { coordinator.cancel() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ItemTile(item, Modifier.fillMaxSize())
                        }
                    }
                }

                if (showGuides) ZoneGuides(placements.values.toList(), cellPx, Modifier.matchParentSize())
            }

            // ── Zone debug toggle ───────────────────────────────────────────────────────
            Text(
                text = if (showGuides) "zones: on" else "zones: off",
                color = colors.contentMuted,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clickable { showGuides = !showGuides },
            )

            // ── Drag overlay (root space): the drop shadow + the floating proxy ──────────
            val session = coordinator.session
            val geo = geometry
            if (session != null && geo != null) {
                session.plan?.let { plan ->
                    val footprint = plan.footprint
                    val topLeft = geo.topLeftInRoot(footprint.row, footprint.col)
                    DropFootprint(
                        intent = plan.intent,
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                            .size(width = cellDp * footprint.colSpan, height = cellDp * footprint.rowSpan),
                    )
                }
                // Proxy is sized to the dragged item's span and centred on the finger.
                val span = placements[session.item]
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
        }
    }
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

/**
 * Applies a committed drop. A MERGE drops the dragged item *into* the target — here (no real folders yet) that
 * just removes it from the grid. Otherwise it shifts the pushed occupants, then places the item on its
 * footprint.
 */
private fun applyDrop(
    placements: SnapshotStateMap<GridItem, GridPlacement>,
    item: GridItem,
    plan: PlacementPlan,
) {
    if (plan.intent == DropIntent.MERGE) {
        placements.remove(item)
        return
    }
    plan.moves.forEach { (moved, placement) -> placements[moved] = placement }
    placements[item] = plan.footprint
}

/** Whether [cell] falls inside this placement's rectangle. */
private fun GridPlacement.covers(cell: Cell): Boolean =
    cell.row in row until rowEndExclusive && cell.col in col until colEndExclusive

/**
 * Whether [dragged] can merge onto [target]. In the harness everything is an app, so any two distinct apps
 * combine (a folder, in production). Real rules differ by type — apps combine, widgets combine with widgets,
 * an app drops into a folder — and would gate the merge ring per §6c.
 */
private fun canMerge(dragged: GridItem, target: GridItem): Boolean =
    dragged is GridItem.App && target is GridItem.App && dragged != target

/** The grid's placement in root/window space, plus the maths mapping a finger to a cell and back. */
private data class GridGeometry(
    val originInRoot: Offset,
    val cellPx: Float,
    val cols: Int,
    val rows: Int,
) {
    /**
     * The footprint's top-left cell for an item of [colSpan]×[rowSpan] whose proxy is centred on the finger.
     * Uses the item's own top-left, **rounded** to the nearest cell — so the footprint holds still until the
     * item has moved half a cell, then steps one cell over (the half-cell hysteresis). Clamped so the span
     * stays on the grid.
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
     * the item's centre. The whole item is one target (not per sub-cell), so a multi-cell item has exactly one
     * ring (docs/DRAG_AND_DROP_DESIGN.md §6a).
     */
    fun inMergeRingOf(fingerInRoot: Offset, rect: GridPlacement): Boolean {
        val dx = fingerInRoot.x - (originInRoot.x + (rect.col + rect.colSpan / 2f) * cellPx)
        val dy = fingerInRoot.y - (originInRoot.y + (rect.row + rect.rowSpan / 2f) * cellPx)
        val radius = mergeRadius(rect)
        return dx * dx + dy * dy < radius * radius
    }

    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellPx, originInRoot.y + row * cellPx)

    /**
     * Which way to push the item occupying [rect], from where the finger sits **within that item's rectangle**.
     * The rectangle is split into four triangles by its diagonals; the occupant is shoved away from the edge
     * the finger is nearest — left triangle pushes right, top pushes down, and so on. Being relative to the
     * whole item, a multi-cell item is one 4-way partition, not one per sub-cell (docs/DRAG_AND_DROP_DESIGN.md §6a).
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
}

private data class Cell(val row: Int, val col: Int)

private const val ROWS = 6
private const val COLS = 4
/** Merge-ring radius as a fraction of the item's smaller span (×cellPx); inside it is the merge target. */
private const val MERGE_INNER_RADIUS = 0.3f
private val GridZoneId = ZoneId("playground-grid")

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
