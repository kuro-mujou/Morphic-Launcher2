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
import androidx.compose.ui.unit.dp
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
 * @param backing whether to draw the rounded translucent plate behind the icons, **and the inset that goes with it**.
 *   The two are one flag rather than two because the inset exists only to keep the icons off the plate's rounded edge —
 *   with no plate there is nothing to inset from, and the 2×2 fills the tile edge to edge.
 *
 *   True for a folder on a grid, which needs the plate to read as one object among loose icons. False for the category
 *   card's overflow cluster, which sits *inside* a tile that already has a fill: a second plate there is a box within a
 *   box, and it made the cluster the only slot on the card with a visible container.
 *
 * TODO(launcher backing plate): a plain translucent surface for now. Replace with the themed skin/backing-plate
 *  (the deferred live-Compose backdrop) when that subsystem lands.
 */
@Composable
fun IconPreviewPlate(
    apps: List<AppInfo>,
    size: Dp,
    modifier: Modifier = Modifier,
    backing: Boolean = true,
) {
    val colors = LocalMorphicColors.current
    // Derived from the plate's own size so the tile scales as one thing: a home folder's plate and a card cluster's
    // plate are wildly different sizes, and fixed dp gaps would swamp the small one.
    val gap = size * PREVIEW_GAP_FRACTION
    val pad = if (backing) size * PREVIEW_PADDING_FRACTION else 0.dp
    val slot = (size - pad * 2 - gap) / 2
    Column(
        modifier = modifier
            .size(size)
            .then(
                if (backing) {
                    Modifier
                        .clip(RoundedCornerShape(size * CORNER_FRACTION))
                        .background(colors.surface.copy(alpha = BACKING_ALPHA))
                } else {
                    Modifier
                },
            )
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
