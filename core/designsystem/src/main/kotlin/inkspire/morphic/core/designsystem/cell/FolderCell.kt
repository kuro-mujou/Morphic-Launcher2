package inkspire.morphic.core.designsystem.cell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.model.AppInfo

/**
 * The grid cell for a folder: an [IconPreviewPlate] of its first apps' baked icons, with the folder [label] under
 * it. Mirrors [AppCell]'s structure — both wrap [IconLabelCell], so a folder and an app cell line up on the grid
 * and share the icon/label sizing.
 *
 * The preview shows at most the first four [apps]; a folder with fewer just leaves the trailing slots empty
 * (the model guarantees a folder holds ≥ 2).
 *
 * Opening the folder is a *tap*, which arrives through [itemGestures] like every other item interaction (see
 * [AppCell]) — the plate carries no `clickable` of its own, so the touch target is the plate + label and nothing
 * more of the cell.
 */
@Composable
fun FolderCell(
    label: String,
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    itemGestures: Modifier = Modifier,
) {
    IconLabelCell(
        label = label,
        modifier = modifier,
        metrics = metrics,
        itemGestures = itemGestures
    ) { iconSize ->
        IconPreviewPlate(
            apps = apps,
            size = iconSize
        )
    }
}
