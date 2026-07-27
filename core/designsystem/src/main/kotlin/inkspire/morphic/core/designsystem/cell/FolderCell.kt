package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.model.AppInfo

/**
 * The grid cell for a folder: a rounded "backing plate" holding a 2×2 preview of its first apps' baked icons,
 * with the folder [label] under it. Mirrors [AppCell]'s structure — both wrap [IconLabelCell], so a folder and
 * an app cell line up on the grid and share the icon/label sizing.
 *
 * The preview shows at most the first four [apps]; a folder with fewer just leaves the trailing slots empty
 * (the model guarantees a folder holds ≥ 2). [onClick] opens the folder.
 */
@Composable
fun FolderCell(
    label: String,
    apps: List<AppInfo>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
) {
    val colors = LocalMorphicColors.current
    IconLabelCell(label = label, modifier = modifier, metrics = metrics) { iconSize ->
        // TODO(launcher backing plate): a plain translucent surface for now. Replace with the themed
        //  skin/backing-plate (the deferred live-Compose backdrop) when that subsystem lands.
        val gap = iconSize * PREVIEW_GAP_FRACTION
        val pad = iconSize * PREVIEW_PADDING_FRACTION
        val slot = (iconSize - pad * 2 - gap) / 2
        Column(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * CORNER_FRACTION))
                .background(colors.surface.copy(alpha = BACKING_ALPHA))
                .clickable(onClick = onClick)
                .padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            PreviewRow(apps.getOrNull(0), apps.getOrNull(1), slot, gap)
            PreviewRow(apps.getOrNull(2), apps.getOrNull(3), slot, gap)
        }
    }
}

/** One row of the 2×2 preview: two icon slots (or empty spacers) sized to [slot]. */
@Composable
private fun PreviewRow(left: AppInfo?, right: AppInfo?, slot: Dp, gap: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        PreviewIcon(left, slot)
        PreviewIcon(right, slot)
    }
}

/** A single preview icon, or an empty [slot]-sized gap when the folder has no app for this position. */
@Composable
private fun PreviewIcon(app: AppInfo?, slot: Dp) {
    if (app == null) {
        Box(Modifier.size(slot))
        return
    }
    val sizePx = with(LocalDensity.current) { slot.roundToPx() }
    LauncherIcon(
        component = app.componentKey,
        contentDescription = app.label,
        sizePx = sizePx,
        modifier = Modifier.size(slot),
    )
}

private const val PREVIEW_GAP_FRACTION = 0.08f
private const val PREVIEW_PADDING_FRACTION = 0.12f
private const val CORNER_FRACTION = 0.24f
private const val BACKING_ALPHA = 0.55f
