package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.SwipeDirection
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import kotlinx.coroutines.delay

/**
 * How long the finger must rest on a push before occupants reflow — long enough that a fast drag-through won't
 * flicker, short enough that a deliberate hover feels responsive.
 *
 * Shared by [CoordinateDragGrid] and [CoordinateDragPager] rather than declared in each: the two must agree (a
 * drag crosses between them, and the same hover should reflow at the same moment on either), and two constants
 * that must agree are one constant.
 */
internal const val PUSH_DWELL_MS = 200L

/**
 * One **coordinate (free-placement) drag zone** on a [LauncherGrid]: the piece every such surface (home MAIN,
 * dock, widget area, folders) shares. It renders [items] at their stored [placement]s, wires each cell for
 * drag/tap/menu, previews the planner's push live (dwelled so a fast drag doesn't strobe), and publishes the
 * zone's measured [GridGeometry] + registration to [coordinator]. Extracted from the near-identical blocks in
 * `HomeScreen` and the dev harness's `GridSurface` so there is one implementation to reason about.
 *
 * **What stays with the caller — deliberately.** Three things differ per surface, so they are inputs, not baked
 * in: the **planner** (given to the [coordinator] the caller creates — merge/push rules vary), the **drag
 * overlay** (the floating proxy + drop shadow; drawn once at the root, and for a multi-zone surface it spans
 * zones), and the **cell content** ([itemContent]). This composable owns only the per-zone grid + gestures +
 * dwelled preview.
 *
 * The geometry seam: [onGeometryChange] hands back the geometry derived from the grid's *measured* bounds
 * (`origin`, `cellW = width / cols`, `cellH = height / rows`) so the caller's planner and overlay read the exact
 * cells the grid drew — the whole point of publishing it rather than recomputing.
 *
 * @param T the caller's item type (e.g. a placed app); [dragItem] projects it to the [GridItem] drag identity.
 * @param items the items to render, each positioned by [placement].
 * @param config the zone's logical grid dimensions.
 * @param coordinator the shared drag coordinator this zone registers with (one may span several zones).
 * @param zoneId this zone's identity, used for registration and to read back only *this* zone's live plan.
 * @param gestureConfig touch-slop / long-press tuning for each cell's gestures.
 * @param dragItem projects an item to its stable [GridItem] identity (drag key + planner key).
 * @param placement the item's stored placement; a live push preview overrides it while a drag hovers here.
 * @param onDrop invoked when a drag is released — the caller commits the coordinator's outcome (persist / apply).
 * @param modifier applied to the grid; pass sizing/padding here (geometry is measured *after* it).
 * @param edgeActions swipe directions a cell claims as press-and-swipe actions (empty → none).
 * @param acceptsItem gate for what this zone accepts on drop (default: anything).
 * @param onGeometryChange receives the zone's measured geometry on every (re)layout.
 * @param onOpen a completed tap on an item.
 * @param onShowMenu a long-press on an item.
 * @param onEdgeAction a press-and-swipe on an item in one of [edgeActions].
 * @param itemContent renders an item into its cell. `cellModifier` fills the cell (the layout footprint);
 *   `itemGestures` must be applied to whatever should actually be *touchable* — see [LauncherDragCell], which
 *   deliberately does not claim the whole cell so the slack around a small icon stays free for the surface.
 */
@Composable
fun <T> CoordinateDragGrid(
    items: List<T>,
    config: GridConfig,
    coordinator: DragCoordinator,
    zoneId: ZoneId,
    gestureConfig: ItemGestureConfig,
    dragItem: (T) -> GridItem,
    placement: (T) -> GridPlacement,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
    edgeActions: Set<SwipeDirection> = emptySet(),
    acceptsItem: (GridItem) -> Boolean = { true },
    onGeometryChange: (GridGeometry) -> Unit = {},
    onOpen: (T) -> Unit = {},
    onShowMenu: (T) -> Unit = {},
    onEdgeAction: (T, SwipeDirection) -> Unit = { _, _ -> },
    itemContent: @Composable (item: T, cellModifier: Modifier, itemGestures: Modifier) -> Unit,
) {
    val session = coordinator.session

    // Live push preview, dwelled: the plan recomputes every drag frame, but occupants only reflow once the
    // finger has rested on the same plan for PUSH_DWELL_MS. A fast drag keeps changing the plan (restarting the
    // timer), so occupants never strobe; they move only when the user pauses. Dropping still commits the live
    // plan regardless of the dwell. Only this zone's plan counts — a shared coordinator may drive other zones.
    val livePlan = session?.takeIf { it.activeZone == zoneId }?.plan
    var dwelledPlan by remember { mutableStateOf<PlacementPlan?>(null) }
    LaunchedEffect(livePlan) {
        if (livePlan == null) dwelledPlan = null else { delay(PUSH_DWELL_MS); dwelledPlan = livePlan }
    }

    DisposableEffect(coordinator, zoneId) {
        onDispose { coordinator.unregisterZone(zoneId) }
    }

    LauncherGrid(
        config = config,
        modifier = modifier.onGloballyPositioned {
            val b = it.boundsInRoot()
            onGeometryChange(
                GridGeometry(
                    originInRoot = Offset(b.left, b.top),
                    cellW = b.width / config.cols,
                    cellH = b.height / config.rows,
                    cols = config.cols,
                    rows = config.rows,
                ),
            )
            coordinator.registerZone(DropZone(zoneId, b, z = 0, accepts = acceptsItem))
        },
    ) {
        coordinateItems(
            items = items,
            itemKey = { dragItem(it) },
            // Occupants render at their previewed (pushed) cell while a drag hovers this zone; else their stored
            // cell. The dragged item isn't in `moves`, so it stays at its stored cell (drawn invisible below).
            placement = { item -> dwelledPlan?.moves?.get(dragItem(item)) ?: placement(item) },
        ) { item, cellModifier ->
            LauncherDragCell(
                coordinator = coordinator,
                item = dragItem(item),
                gestureConfig = gestureConfig,
                onDrop = onDrop,
                modifier = cellModifier,
                edgeActions = edgeActions,
                onOpen = { onOpen(item) },
                onShowMenu = { onShowMenu(item) },
                onEdgeAction = { direction -> onEdgeAction(item, direction) },
            ) { itemGestures ->
                itemContent(item, Modifier.fillMaxSize(), itemGestures)
            }
        }
    }
}
