package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.SwipeDirection

/**
 * One draggable coordinate cell — the per-item wiring every free-placement surface repeats, shared by the
 * single-zone [CoordinateDragGrid] and the paged [CoordinateDragPager] so there is one copy of it.
 *
 * Given the cell's placement [modifier] (from the grid layout), it applies the drag-state visuals:
 * - occupants glide to their (previewed) cells via [animatePlacement]; the **lifted** item skips the animation
 *   and is drawn invisible (`alpha = 0`) — the floating proxy in the surface's overlay stands in for it.
 * - so does an item whose placement the finger is driving *in place* ([tracksFinger]) — a live resize. It is the
 *   lifted item's reason without the proxy: a spring settling toward where the finger already is reads as lag,
 *   not as motion, and here the resize frame is drawn over the cell so the two would visibly disagree.
 *
 * **The gestures are handed to [content], not applied to the cell.** A cell is a *layout* footprint and is
 * usually much larger than the item drawn in it — a home cell is a whole 2×2 visual slot around an icon and a
 * one-line label. If the cell claimed the gestures, every pixel of that slack would lift/launch the item, and on
 * a full page there would be nowhere left to press for the surface's own long-press (the wallpaper / home
 * options menu). So [content] receives `itemGestures` and decides what is actually touchable: an icon cell puts
 * it on the icon+label group (see [inkspire.morphic.core.designsystem.cell.IconLabelCell]), while content that
 * genuinely fills its cell — a widget, a harness tile — just `.then()`s it onto its own root. Whatever is left
 * uncovered stays free: [launcherItemGestures] never consumes a down, so those events reach the surface beneath.
 *
 * Handing down a modifier (rather than reading it from a composition local) keeps the choice visible in the
 * signature: content that forgets to apply it is silently un-draggable, and a parameter says so where a local
 * would not.
 *
 * A tap fires [onOpen], a long-press [onShowMenu], and a press-and-swipe in [edgeActions] fires [onEdgeAction] —
 * all only within whatever bounds [content] gave the gestures.
 *
 * @param tracksFinger this item's placement is being driven directly by the finger right now (a live resize), so
 *   it must land on each new cell at once rather than glide there.
 * @param onRelease the finger came up on this cell, ending the drag. It is **not** where the drop is committed —
 *   that belongs to the zone the drag landed in ([inkspire.morphic.core.designsystem.drag.DropZone.onDrop]), which
 *   may be on a different surface entirely from this cell. What the surface does here is its own bookkeeping about
 *   a drag *leaving* it, and calling `coordinator.drop()` so the landing is dispatched at all.
 */
@Composable
fun LauncherDragCell(
    coordinator: DragCoordinator,
    item: GridItem,
    gestureConfig: ItemGestureConfig,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    edgeActions: Set<SwipeDirection> = emptySet(),
    tracksFinger: Boolean = false,
    onOpen: () -> Unit = {},
    onShowMenu: (anchorInRoot: Rect) -> Unit = {},
    onEdgeAction: (SwipeDirection) -> Unit = {},
    content: @Composable (itemGestures: Modifier) -> Unit,
) {
    val isDragged = coordinator.session?.item == item
    Box(
        modifier
            .then(if (isDragged || tracksFinger) Modifier else Modifier.animatePlacement())
            .graphicsLayer { alpha = if (isDragged) 0f else 1f },
    ) {
        content(
            Modifier.launcherItemGestures(
                config = gestureConfig,
                edgeActions = edgeActions,
                onOpen = onOpen,
                onEdgeAction = onEdgeAction,
                onShowMenu = onShowMenu,
                onBeginDrag = { root -> coordinator.start(item, root) },
                onDragTo = { root -> coordinator.moveTo(root) },
                onDrop = { onRelease() },
                onCancelDrag = { coordinator.cancel() },
            ),
        )
    }
}
