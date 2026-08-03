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
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.model.AppInfo

/** Row insets and the icon→label gap. Cell-internal styling, the counterpart of [CellPadH] for a grid cell. */
private val RowPadH = 24.dp
private val RowPadV = 8.dp
private val IconLabelGap = 16.dp

/**
 * **The span of row heights over which the height still changes the icon**, in dp — the inverse of [AppRowCell]'s own
 * sizing, and what a row-height control offers.
 *
 * A row's height is the one cell dimension a user sets outright (a list is one lane, so nothing derives it), and
 * `AppRowCell` then resolves the icon as `iconPercent` of the row's inner height, clamped to the guardrails. Read
 * backwards, that gives both ends: below `minIcon / iconPercent` the icon is clamped *up* and would overflow the row,
 * and above `maxIcon / iconPercent` it has stopped growing and the extra height is whitespace. So the interesting
 * range is exactly between them.
 *
 * **Derived rather than a stated range**, which is why it lives here beside the padding it has to add back rather than
 * as two numbers in a settings screen: a range picked by hand would drift from the cell, and — more to the point — it
 * would not move when the user changes the guardrails it is a function of. The dock's height slider states its bounds
 * (L1's `80f..320f`) because a strip's extent genuinely is a matter of screen share; a row's is not.
 *
 * The upper bound is forced above the lower, so equal guardrails still give a usable control rather than an empty one.
 */
fun rowHeightRangeDp(metrics: IconMetrics): ClosedFloatingPointRange<Float> {
    val percent = metrics.iconPercent.coerceAtLeast(MIN_ROW_ICON_PERCENT)
    val padding = RowPadV.value * 2
    val floor = minOf(metrics.minIconDp.value, metrics.maxIconDp.value) / percent + padding
    val ceiling = maxOf(metrics.minIconDp.value, metrics.maxIconDp.value) / percent + padding
    return floor..maxOf(ceiling, floor + 1f)
}

/** Guards the division in [rowHeightRangeDp] only; `IconSizingRanges.IconPercent` floors what a user can choose. */
private const val MIN_ROW_ICON_PERCENT = 0.05f

/** Fallback line height for a type style that declares none, matching what `CellLabel` assumes for a grid label. */
private const val DEFAULT_LINE_HEIGHT_RATIO = 1.2f

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
 * **[metrics] is honoured except for `showLabel`, and the exception is structural rather than an omission.** A row
 * *is* its label — the icon is an adornment beside it — so a row with no label would not be a row at all, and the
 * settings section correspondingly does not offer that switch for a list. `showIcon` is honoured, because dropping
 * the icon leaves exactly the pure-text list it promises (L1 offered the same toggle, gated on its `listMode` flag).
 * `labelScale` is honoured too: it multiplies the row's text as it multiplies a grid cell's, which is what makes the
 * text-size control mean the same thing wherever it appears.
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
        val baseStyle = MaterialTheme.typography.bodyLarge
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(itemGestures)
                .padding(horizontal = RowPadH, vertical = RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A pure-text list drops the icon *and* the gap after it, so the label starts at the row's own inset
            // rather than where the icon used to end.
            if (metrics.showIcon) {
                LauncherIcon(
                    component = app.componentKey,
                    contentDescription = app.label,
                    sizePx = with(LocalDensity.current) { iconSize.roundToPx() },
                    modifier = Modifier.size(iconSize),
                )
                Spacer(Modifier.width(IconLabelGap))
            }
            Text(
                text = app.label,
                style = baseStyle.copy(
                    fontSize = baseStyle.fontSize * metrics.labelScale,
                    lineHeight = if (baseStyle.lineHeight.isSpecified) {
                        baseStyle.lineHeight * metrics.labelScale
                    } else {
                        baseStyle.fontSize * metrics.labelScale * DEFAULT_LINE_HEIGHT_RATIO
                    },
                ),
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
