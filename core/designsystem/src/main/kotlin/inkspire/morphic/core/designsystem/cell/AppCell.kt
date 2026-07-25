package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.clickable
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
 */
@Composable
fun AppCell(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
) {
    // TODO(B9 data:icons): per-app icon-size override — IconOverride.iconSizeFor(surface, layout) applied as a
    //  graphicsLayer scale on the icon. Dropped until per-app icon overrides exist.
    // TODO(P7 gestures): gate the tap with a LocalItemInteractionEnabled, and support a `hidden` placeholder
    //  slot, so releasing after a long-press that opened a menu / armed a drag doesn't also fire a tap.
    //  Always-clickable, never-hidden until the drag system lands.
    // TODO(per-surface sizing): read LocalIconSurface + a layout key so a surface can pick its own
    //  IconMetrics + per-(surface, layout) size override. For now metrics come from LocalIconMetrics only.
    IconLabelCell(label = app.label, modifier = modifier, metrics = metrics) { iconSize ->
        val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
        LauncherIcon(
            component = app.componentKey,
            contentDescription = app.label,
            sizePx = sizePx,
            modifier = Modifier
                .size(iconSize)
                .clickable(onClick = onClick),
        )
    }
}
