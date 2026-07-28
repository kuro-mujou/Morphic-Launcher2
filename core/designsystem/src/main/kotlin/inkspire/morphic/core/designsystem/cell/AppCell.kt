package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.model.AppInfo

/**
 * The grid cell for one app: [IconLabelCell] handles the icon+label sizing/arrangement, wrapping the baked
 * [LauncherIcon]. The cell converts the resolved icon size to pixels and passes it as `sizePx`, so the layout
 * (via [IconMetrics]) owns the bake resolution — no magic default.
 *
 * Interaction comes from [itemGestures] (the enclosing
 * [inkspire.morphic.core.designsystem.grid.LauncherDragCell]'s tap / long-press / drag contract), applied by
 * [IconLabelCell] to the icon+label group. The cell has no `onClick` of its own: one gesture owner per item is
 * the whole point of the shared contract, and a second `clickable` here would compete for the same pointer.
 */
@Composable
fun AppCell(
    app: AppInfo,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    itemGestures: Modifier = Modifier,
) {
    // TODO(B9 data:icons): per-app icon-size override — IconOverride.iconSizeFor(surface, layout) applied as a
    //  graphicsLayer scale on the icon. Dropped until per-app icon overrides exist.
    // TODO(P7 gestures): support a `hidden` placeholder slot (an app whose cell is reserved but not drawn).
    // TODO(per-surface sizing): read LocalIconSurface + a layout key so a surface can pick its own
    //  IconMetrics + per-(surface, layout) size override. For now metrics come from LocalIconMetrics only.
    IconLabelCell(label = app.label, modifier = modifier, metrics = metrics, itemGestures = itemGestures) { iconSize ->
        val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
        LauncherIcon(
            component = app.componentKey,
            contentDescription = app.label,
            sizePx = sizePx,
            modifier = Modifier.size(iconSize),
        )
    }
}
