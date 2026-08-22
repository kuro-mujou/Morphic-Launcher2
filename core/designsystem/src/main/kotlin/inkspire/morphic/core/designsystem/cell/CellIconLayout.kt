package inkspire.morphic.core.designsystem.cell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the icon lands inside a cell of a given size, and how big it comes out — [IconLabelCell]'s own layout,
 * published as numbers.
 *
 * **Why this exists at all: so a caller that draws *over* a cell can align with it without re-deriving its layout.**
 * The settings icon preview needs exactly that — it outlines the cell and the two icon guardrails on top of a real
 * `AppCell`, and its guides are wrong the moment they disagree with the cell by a padding value. L1's version worked
 * the arithmetic out a second time (`PREVIEW_CELL_PAD_DP = 4f` and `PREVIEW_LABEL_GAP_DP = 4f`, under a comment
 * reading "Keep in sync with CellPadH/CellPadV/LabelGap there").
 *
 * **What it does not buy is one copy of the padding.** The 4dp below is written out here, again in [IconLabelCell],
 * and a third time in `CellFit` — dp values are not named in this codebase, so the three are kept in step by hand and
 * nothing fails when they diverge. The *arithmetic* is shared, which is the larger half of the drift `CellFit` was
 * ported to remove; the numbers are not.
 *
 * **It must mirror [IconLabelCell] exactly, including where that is unkind.** Two details are load-bearing:
 * - the **no-label branch does not clamp** the icon to its area, so an icon whose lower guardrail exceeds the cell
 *   overflows it. That is what the cell does today, so it is what this reports — a preview that quietly clamped would
 *   hide the overflow rather than showing the user their guardrail is too large for their grid;
 * - the icon+label **group is centered** in the padded box rather than the icon being, so the icon's center sits above
 *   the cell's whenever a label is shown.
 *
 * @property iconSize the edge length [IconLabelCell] will draw the icon at.
 * @property iconBound the largest icon this cell could hold **without overflowing** — its inner box: the width less the
 *   horizontal padding, the height less the vertical padding and the label block, whichever is smaller. Not the same as
 *   [iconSize], and the gap between them is meaningful: an icon whose lower guardrail exceeds this is drawn *over* the
 *   cell's padding, which is a state a caller may want to show rather than hide.
 * @property labelHeight the label row's height, or zero when labels are off.
 * @property iconCenterY the icon's center, measured from the cell's **top** edge.
 */
data class CellIconLayout(
    val iconSize: Dp,
    val iconBound: Dp,
    val labelHeight: Dp,
    val iconCenterY: Dp,
)

/**
 * [CellIconLayout] for a cell of [cellWidth] × [cellHeight], with the label row's height supplied.
 *
 * The pure half, so the arithmetic is checkable without a `MaterialTheme` — the same split `CellFit` and
 * `appCollectionInnerSize` use, and for the same reason.
 *
 * @param labelHeight from [cellLabelHeight]; ignored entirely when [IconMetrics.showLabel] is false, exactly as the
 *   cell ignores it.
 */
fun cellIconLayout(
    cellWidth: Dp,
    cellHeight: Dp,
    metrics: IconMetrics,
    labelHeight: Dp,
): CellIconLayout {
    val availWidth = (cellWidth - 4.dp * 2).coerceAtLeast(0.dp)

    if (!metrics.showLabel) {
        val iconArea = (cellHeight - 4.dp * 2).coerceAtLeast(0.dp)
        // No clamp to the area, mirroring the cell's own no-label branch — see the note above.
        val iconSize = metrics.resolveIconSize(availWidth, iconArea)
        return CellIconLayout(
            iconSize = iconSize,
            iconBound = minOf(availWidth, iconArea),
            labelHeight = 0.dp,
            iconCenterY = cellHeight / 2,
        )
    }

    val iconArea = (cellHeight - 4.dp * 2 - 4.dp - labelHeight).coerceAtLeast(0.dp)
    val iconSize = metrics.resolveIconSize(availWidth, iconArea).coerceAtMost(iconArea)
    // The *group* is centered in the padded box, so the icon sits above the cell's center by half the label block.
    val groupHeight = iconSize + 4.dp + labelHeight
    val contentHeight = (cellHeight - 4.dp * 2).coerceAtLeast(0.dp)
    val iconTop = 4.dp + (contentHeight - groupHeight) / 2
    return CellIconLayout(
        iconSize = iconSize,
        iconBound = minOf(availWidth, iconArea),
        labelHeight = labelHeight,
        iconCenterY = iconTop + iconSize / 2,
    )
}

/** [cellIconLayout], with the label row's height read from the current type scale — the one a caller reaches for. */
@Composable
fun cellIconLayout(cellWidth: Dp, cellHeight: Dp, metrics: IconMetrics): CellIconLayout {
    val labelHeight = cellLabelHeight(metrics)
    return remember(cellWidth, cellHeight, metrics, labelHeight) {
        cellIconLayout(cellWidth, cellHeight, metrics, labelHeight)
    }
}
