package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.SwipeDirection
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.model.GridItem

/**
 * One draggable coordinate cell — the per-item wiring every free-placement surface repeats, shared by the
 * single-zone [CoordinateDragGrid] and the paged [CoordinateDragPager] so there is one copy of it.
 *
 * Given the cell's placement [modifier] (from the grid layout), it applies the drag-state visuals and the
 * [launcherItemGestures] that lift/drag/drop [item] through [coordinator]:
 * - occupants glide to their (previewed) cells via [animatePlacement]; the **lifted** item skips the animation
 *   and is drawn invisible (`alpha = 0`) — the floating proxy in the surface's overlay stands in for it;
 * - a tap fires [onOpen], a long-press [onShowMenu], and a press-and-swipe in [edgeActions] fires [onEdgeAction].
 *
 * [content] is the cell's visual (an app icon, a tile), laid out to fill the cell.
 */
@Composable
fun LauncherDragCell(
    coordinator: DragCoordinator,
    item: GridItem,
    gestureConfig: ItemGestureConfig,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
    edgeActions: Set<SwipeDirection> = emptySet(),
    onOpen: () -> Unit = {},
    onShowMenu: () -> Unit = {},
    onEdgeAction: (SwipeDirection) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val isDragged = coordinator.session?.item == item
    Box(
        modifier
            .then(if (isDragged) Modifier else Modifier.animatePlacement())
            .graphicsLayer { alpha = if (isDragged) 0f else 1f }
            .launcherItemGestures(
                config = gestureConfig,
                edgeActions = edgeActions,
                onOpen = onOpen,
                onEdgeAction = onEdgeAction,
                onShowMenu = onShowMenu,
                onDismissMenu = {},
                onBeginDrag = { root -> coordinator.start(item, root) },
                onDragTo = { root -> coordinator.moveTo(root) },
                onDrop = { onDrop() },
                onCancelDrag = { coordinator.cancel() },
            ),
    ) {
        content()
    }
}
