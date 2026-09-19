package inkspire.morphic.core.designsystem.drag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * The floating proxy of the item under the finger — the thing that visually follows the drag. It is placed by
 * a **root-level** overlay (so it can travel across surfaces and above folders), centred on [centerInRoot] in
 * root/window pixels and sized to [size]; [content] draws the item itself (an app icon, a folder preview, …),
 * so this stays agnostic to what's being dragged.
 *
 * **Placed by its centre, and every surface passes [DragSession.itemCenterInRoot]** — the same point the planners
 * snap the drop shadow from. That is the shared derivation keeping the proxy and the shadow in agreement; each
 * surface computing its own top-left from the finger is how the two came apart.
 *
 * **It is drawn at true size — there is no lift scale, and that is deliberate.** This launcher draws a **drop
 * footprint** at the same time, stating the size the item will actually land at, and home passes an item's *whole
 * footprint* here rather than an icon size — so a proxy floated at 110% disagreed with the shadow beneath it about
 * how big it was, and read as the item resizing rather than lifting. A cue that says "picked up" has to be one that
 * does not contradict the footprint: elevation, a shadow, alpha.
 *
 * Positioning uses the layout-phase `offset { }` lambda, so the proxy re-lays-out without recomposing on every
 * finger move.
 *
 * @param size the proxy's size: the source cell for a single icon, and the item's **full footprint** where it spans
 *   more than one — home's widgets and containers are the second case.
 */
@Composable
fun FloatingDragIcon(
    centerInRoot: Offset,
    size: DpSize,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .offset {
                IntOffset(
                    (centerInRoot.x - size.width.toPx() / 2f).roundToInt(),
                    (centerInRoot.y - size.height.toPx() / 2f).roundToInt(),
                )
            }
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
