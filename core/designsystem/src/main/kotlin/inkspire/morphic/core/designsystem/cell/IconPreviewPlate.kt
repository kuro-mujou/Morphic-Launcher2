package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * A rounded backing plate holding a 2×2 preview of up to four apps' baked icons, [size] on a side.
 *
 * **"A collection of apps, drawn as one icon-sized tile."** Two things need exactly that and are otherwise
 * unrelated, which is why this is its own composable rather than private to either:
 * - [FolderCell] — a folder on a grid, with its label beneath.
 * - the APPS **category card**'s overflow cluster — the "and N more" tile that opens the category.
 *
 * Extracted when the second consumer arrived rather than kept as a copy: the two would otherwise drift apart on
 * corner radius, padding and plate alpha, and a folder tile and a cluster tile that *nearly* match read as a bug.
 *
 * Fewer than four [apps] simply leaves the trailing slots empty, and the plate is still drawn — a tile with one icon
 * in it is a legitimate state for the cluster (a category of exactly five apps), and [FolderCell]'s own model
 * guarantees at least two.
 *
 * @param size the plate's edge length; the four icon slots are derived from it, so a caller sizes the tile and
 *   nothing else.
 *
 * TODO(launcher backing plate): a plain translucent surface for now. Replace with the themed skin/backing-plate
 *  (the deferred live-Compose backdrop) when that subsystem lands.
 */
@Composable
fun IconPreviewPlate(
    apps: List<AppInfo>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    // Derived from the plate's own size so the tile scales as one thing: a home folder's plate and a card cluster's
    // plate are wildly different sizes, and fixed dp gaps would swamp the small one.
    val gap = size * PREVIEW_GAP_FRACTION
    val pad = size * PREVIEW_PADDING_FRACTION
    val slot = (size - pad * 2 - gap) / 2
    Column(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * CORNER_FRACTION))
            .background(colors.surface.copy(alpha = BACKING_ALPHA))
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        PreviewRow(apps.getOrNull(0), apps.getOrNull(1), slot, gap)
        PreviewRow(apps.getOrNull(2), apps.getOrNull(3), slot, gap)
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

/** A single preview icon, or an empty [slot]-sized gap when the collection has no app for this position. */
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
