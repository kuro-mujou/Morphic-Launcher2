package inkspire.morphic.core.designsystem.drag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset

/**
 * The floating proxy of the item under the finger — the thing that visually follows the drag. It is placed by
 * a **root-level** overlay (so it can travel across surfaces and above folders), positioned at [rootOffset] in
 * root/window pixels and sized to [size]; [content] draws the item itself (an app icon, a folder preview, …),
 * so this stays agnostic to what's being dragged.
 *
 * **It is drawn at true size — there is no lift scale, and that is deliberate.** It used to enlarge itself 10% to
 * give a "picked up" feel, which works only while the proxy is one icon and nothing on screen contradicts it. This
 * launcher draws a **drop footprint** at the same time, stating the size the item will actually land at, and home
 * passes an item's *whole footprint* here rather than an icon size — so a widget or a container floated at 110%
 * disagreed with the shadow beneath it about how big it was, and read as the item resizing rather than lifting. A
 * cue that says "picked up" has to be one that does not contradict the footprint: elevation, a shadow, alpha.
 *
 * The parameter is **gone rather than defaulted to 1**, on `GridGeometry.snapTopLeftCell`'s rule — its only ever
 * use was the mistake above, and a knob left behind is an invitation to reach for it again.
 *
 * Positioning uses the layout-phase `offset { }` lambda, so the proxy re-lays-out without recomposing on every
 * finger move.
 *
 * @param rootOffset top-left of the proxy in root coordinates — the caller derives it from the finger position
 *   and the grab offset (where within the item it was grabbed), so the icon stays under the fingertip.
 * @param size the proxy's size: the source cell for a single icon, and the item's **full footprint** where it spans
 *   more than one — home's widgets and containers are the second case.
 */
@Composable
fun FloatingDragIcon(
    rootOffset: IntOffset,
    size: DpSize,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .offset { rootOffset }
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
