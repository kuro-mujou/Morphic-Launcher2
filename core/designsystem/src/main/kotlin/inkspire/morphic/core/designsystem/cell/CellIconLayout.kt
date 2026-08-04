package inkspire.morphic.core.designsystem.cell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the icon lands inside a cell of a given size, and how big it comes out — [IconLabelCell]'s own layout,
 * published as numbers.
 *
 * **Why this exists at all: so a caller that draws *over* a cell can align with it without copying its constants.** The
 * settings icon preview needs exactly that — it outlines the cell and the two icon guardrails on top of a real
 * `AppCell`, and its guides are wrong the moment they disagree with the cell by a padding value. L1's version *did*
 * copy them (`PREVIEW_CELL_PAD_DP = 4f` and `PREVIEW_LABEL_GAP_DP = 4f`, under a comment reading "Keep in sync with
 * CellPadH/CellPadV/LabelGap there"), which is the same drift `CellFit` was ported to remove on the inverse direction.
 *
 * **It must mirror [IconLabelCell] exactly, including where that is unkind.** Two details are load-bearing:
 * - the **no-label branch does not clamp** the icon to its area, so an icon whose lower guardrail exceeds the cell
 *   overflows it. That is what the cell does today, so it is what this reports — a preview that quietly clamped would
 *   hide the overflow rather than showing the user their guardrail is too large for their grid;
 * - the icon+label **group is centred** in the padded box rather than the icon being, so the icon's centre sits above
 *   the cell's whenever a label is shown.
 *
 * @property iconSize the edge length [IconLabelCell] will draw the icon at.
 * @property labelHeight the label row's height, or zero when labels are off.
 * @property iconCenterY the icon's centre, measured from the cell's **top** edge.
 */
data class CellIconLayout(
    val iconSize: Dp,
    val labelHeight: Dp,
    val iconCenterY: Dp,
)

/**
 * [CellIconLayout] for a cell of [cellWidth] × [cellHeight], with the label row's height supplied.
 *
 * The pure half, so the arithmetic is checkable without a `MaterialTheme` — the same split `CellFit` and
 * `folderInnerSize` use, and for the same reason.
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
    val availWidth = (cellWidth - CellPadH * 2).coerceAtLeast(0.dp)

    if (!metrics.showLabel) {
        // No clamp to the area, mirroring the cell's own no-label branch — see the note above.
        val iconSize = metrics.resolveIconSize(availWidth, cellHeight - CellPadV * 2)
        return CellIconLayout(iconSize = iconSize, labelHeight = 0.dp, iconCenterY = cellHeight / 2)
    }

    val iconArea = (cellHeight - CellPadV * 2 - LabelGap - labelHeight).coerceAtLeast(0.dp)
    val iconSize = metrics.resolveIconSize(availWidth, iconArea).coerceAtMost(iconArea)
    // The *group* is centred in the padded box, so the icon sits above the cell's centre by half the label block.
    val groupHeight = iconSize + LabelGap + labelHeight
    val contentHeight = (cellHeight - CellPadV * 2).coerceAtLeast(0.dp)
    val iconTop = CellPadV + (contentHeight - groupHeight) / 2
    return CellIconLayout(
        iconSize = iconSize,
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
