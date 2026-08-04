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
 * **The row heights the icon guardrails can honour**, in dp — the inverse of [AppRowCell]'s own sizing, and what a
 * row-height control offers.
 *
 * A row's height is the one cell dimension a user sets outright (a list is one lane, so nothing derives it), and
 * `AppRowCell` then sizes the icon against the row's inner height, clamped to the guardrails and finally to the row
 * itself. Read backwards, the guardrails give both ends directly: a row shorter than `minIconDp` plus its padding
 * cannot hold the smallest icon the user allowed (so the row wins and the guardrail is quietly broken), and one taller
 * than `maxIconDp` plus its padding is height the largest allowed icon cannot fill. **So the range is the guardrail
 * range, shifted by the row's own inset** — which is the same shape as a grid cell's floor
 * (`CellFit.minCellWidthDp`), and deliberately so: one rule for how an icon's limits bound the cell around it.
 *
 * **`iconPercent` is not in it, and that is what keeps the pair of controls from fighting.** The fraction scales the
 * icon *within* the guardrails, so it cannot change which heights are honourable. An earlier cut divided both ends by
 * it — "how tall must the row be for the fraction to be honoured un-clamped" — which inverted the control: asking for
 * a *smaller* icon (a lower percent) raised the floor and so pushed the row **taller**, clamping a 56dp row up to 72dp.
 * That is the same inversion `CellFit` had on the grid axes, from the same division.
 *
 * **The icon range therefore has priority, and the row height follows it.** Widening the guardrails widens what this
 * offers; narrowing them narrows it, and a stored height outside the result is clamped on read by [fitRowHeightDp] and
 * never written back — so the user's number returns when the guardrails widen again, exactly as a grid's column count
 * does. Consequence worth knowing: the way to get a *taller* row is to raise `maxIconDp`, because a row taller than its
 * icon's own ceiling is whitespace this range declines to offer.
 *
 * **Derived rather than a stated range**, which is why it lives here beside the padding it has to add back rather than
 * as two numbers in a settings screen: a range picked by hand would drift from the cell, and — more to the point — it
 * would not move when the user changes the guardrails it is a function of. The dock's height slider states its bounds
 * (L1's `80f..320f`) because a strip's extent genuinely is a matter of screen share; a row's is not.
 *
 * `minOf`/`maxOf` on the guardrails mirrors `resolveIconSize`, which is order-safe, and the upper bound is forced above
 * the lower so equal guardrails still give a usable control rather than an empty one.
 *
 * One limit, stated because it is invisible: with `showIcon = false` the row holds only its label, so nothing here
 * applies and the floor is really the label's line height. It is left as is — an icon-derived floor merely stops a
 * text-only list being tighter than ~44dp — and wants a `labelHeightDp` parameter, as `minCellHeightDp` has, when
 * someone asks for a denser text list.
 */
fun rowHeightRangeDp(metrics: IconMetrics): ClosedFloatingPointRange<Float> {
    val padding = RowPadV.value * 2
    val floor = minOf(metrics.minIconDp.value, metrics.maxIconDp.value) + padding
    val ceiling = maxOf(metrics.minIconDp.value, metrics.maxIconDp.value) + padding
    return floor..maxOf(ceiling, floor + 1f)
}

/**
 * **The row height a stored value actually produces** under [metrics] — the list's `CellFit.fitCols`.
 *
 * The same read-side clamp every other stored dimension gets, and it is what makes the coupling above safe to have: the
 * guardrails can move under a height that was chosen before them, and the list draws the honoured value rather than
 * the stored one. Nothing is written, so widening the guardrails again brings the user's height back.
 *
 * Both callers matter and they must agree: the list clamps what it draws, and the settings slider shows the same
 * clamped value — otherwise the control would sit at a number the surface is not using.
 */
fun fitRowHeightDp(rowHeightDp: Float, metrics: IconMetrics): Float =
    rowHeightDp.coerceIn(rowHeightRangeDp(metrics))

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
