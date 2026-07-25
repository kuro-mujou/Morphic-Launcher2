package inkspire.morphic.launcher.dev

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.layout.FreeGridPlanner
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
                val cell = geo.cellAt(fingerInRoot) ?: return@DropPlanner null
                val span = placements[item] ?: return@DropPlanner null
                // Clamp the top-left so a multi-cell footprint stays inside the grid instead of reading as an
                // off-grid INVALID whenever the finger nears the right/bottom edge.
                val col = cell.col.coerceIn(0, (config.cols - span.colSpan).coerceAtLeast(0))
                val row = cell.row.coerceIn(0, (config.rows - span.rowSpan).coerceAtLeast(0))
                val footprint = GridPlacement(0, row, col, span.rowSpan, span.colSpan)
                val occupants = placements.filterKeys { it != item }
                FreeGridPlanner.plan(footprint, occupants, config)
            }
        }
        val coordinator = rememberDragCoordinator(planner)
        val gestureConfig = remember {
            ItemGestureConfig(touchSlopPx = cellPx * 0.25f, longPressTimeoutMillis = 400L)
        }

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
                                    onDrop = { coordinator.drop()?.let { applyDrop(placements, it.item, it.plan) } },
                                    onCancelDrag = { coordinator.cancel() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ItemTile(item, Modifier.fillMaxSize())
                        }
                    }
                }
            }

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

/** Applies a committed drop: shift the pushed occupants, then drop the dragged item onto its footprint. */
private fun applyDrop(
    placements: SnapshotStateMap<GridItem, GridPlacement>,
    item: GridItem,
    plan: PlacementPlan,
) {
    plan.moves.forEach { (moved, placement) -> placements[moved] = placement }
    placements[item] = plan.footprint
}

/** The grid's placement in root/window space, plus the maths mapping a finger to a cell and back. */
private data class GridGeometry(
    val originInRoot: Offset,
    val cellPx: Float,
    val cols: Int,
    val rows: Int,
) {
    fun cellAt(rootPosition: Offset): Cell? {
        val lx = rootPosition.x - originInRoot.x
        val ly = rootPosition.y - originInRoot.y
        if (lx < 0f || ly < 0f) return null
        val col = (lx / cellPx).toInt()
        val row = (ly / cellPx).toInt()
        return if (row in 0 until rows && col in 0 until cols) Cell(row, col) else null
    }

    fun topLeftInRoot(row: Int, col: Int): Offset =
        Offset(originInRoot.x + col * cellPx, originInRoot.y + row * cellPx)
}

private data class Cell(val row: Int, val col: Int)

private const val ROWS = 6
private const val COLS = 4
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
