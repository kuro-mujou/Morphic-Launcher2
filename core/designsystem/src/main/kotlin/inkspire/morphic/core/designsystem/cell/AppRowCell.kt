package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.model.AppInfo

/** Row insets and the icon→label gap. Cell-internal styling, the counterpart of [CellPadH] for a grid cell. */
private val RowPadH = 24.dp
private val RowPadV = 8.dp
private val IconLabelGap = 16.dp

/**
 * The **list row** for one app: icon at the start, label beside it, filling the width. The horizontal sibling of
 * [AppCell], which stacks the same two things for a grid.
 *
 * The caller owns the row's *height* (via [modifier]) and the icon's *proportion* of it (via [metrics], normally
 * supplied for the whole surface through [LocalIconMetrics]) — this cell only arranges them. Keeping the height
 * out here matters because a row's height is a surface metric bound for the settings layer, and a cell that fixed
 * it would quietly own a dimension it has no business owning.
 *
 * **The whole row is the touch target**, unlike a grid cell — and that is the same rule, not an exception to it:
 * an item's target is its *visible extent*, and a row's visible extent genuinely is the full-width strip (that is
 * what makes it read as a row at all). So [itemGestures] goes on the row's root, exactly as
 * [inkspire.morphic.core.designsystem.grid.LauncherDragCell] describes for content that fills its cell. The
 * consequence is real and intended: a list leaves no slack for a surface-level long-press, because in a list
 * every pixel belongs to a row.
 *
 * **[metrics] is read for sizing only.** `showIcon`/`showLabel` are grid-cell switches and are deliberately
 * ignored: a row *is* its label, and an icon-only row is no longer a list. If an icon-less variant is ever wanted
 * it should be its own cell, not a row rendering half of itself.
 */
@Composable
fun AppRowCell(
    app: AppInfo,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    itemGestures: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BoxWithConstraints(modifier) {
        // The icon is bounded by the row's inner box on both axes; in practice the height wins, since a row is
        // far wider than it is tall. Clamped again to that height so a short row can't be overflowed by the
        // metrics' lower guardrail.
        val innerHeight = (maxHeight - RowPadV * 2).coerceAtLeast(0.dp)
        val iconSize = metrics
            .resolveIconSize(availWidth = maxWidth - RowPadH * 2, availHeight = innerHeight)
            .coerceAtMost(innerHeight)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(itemGestures)
                .padding(horizontal = RowPadH, vertical = RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(
                component = app.componentKey,
                contentDescription = app.label,
                sizePx = with(LocalDensity.current) { iconSize.roundToPx() },
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(IconLabelGap))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                // The theme's content colour, not the grid label's white-on-wallpaper: a list is read against
                // the surface's own background, so it has no wallpaper to fight and needs no drop shadow.
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
