package inkspire.morphic.core.designsystem.drag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset

/**
 * The floating proxy of the item under the finger — the thing that visually follows the drag. It is placed by
 * a **root-level** overlay (so it can travel across surfaces and above folders), positioned at [rootOffset] in
 * root/window pixels and sized to [size]; [content] draws the item itself (an app icon, a folder preview, …),
 * so this stays agnostic to what's being dragged.
 *
 * A small [liftScale] gives the "picked up" feel. Positioning uses the layout-phase `offset { }` lambda, so
 * the proxy re-lays-out without recomposing on every finger move.
 *
 * @param rootOffset top-left of the proxy in root coordinates — the caller derives it from the finger position
 *   and the grab offset (where within the item it was grabbed), so the icon stays under the fingertip.
 * @param size the proxy's size, normally the source cell's icon size.
 * @param liftScale how much to enlarge the proxy while dragging.
 */
@Composable
fun FloatingDragIcon(
    rootOffset: IntOffset,
    size: DpSize,
    modifier: Modifier = Modifier,
    liftScale: Float = 1.1f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .offset { rootOffset }
            .size(size)
            .graphicsLayer {
                scaleX = liftScale
                scaleY = liftScale
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
