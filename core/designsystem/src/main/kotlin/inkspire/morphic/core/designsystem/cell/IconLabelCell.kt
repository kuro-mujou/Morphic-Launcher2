package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

internal val CellPadH = 4.dp
internal val CellPadV = 4.dp
internal val LabelGap = 4.dp

/**
 * A grid-cell label: single line, ellipsized, sized by [IconMetrics.labelScale].
 *
 * TODO(launcher wallpaper-adaptive UI): [color] defaults to white + a drop shadow so it stays legible over a
 *  dark-ish wallpaper. When the wallpaper-brightness subsystem lands, feed the adaptive content tint (black on
 *  a bright wallpaper / white on a dark one) instead of a fixed white.
 */
@Composable
internal fun CellLabel(
    label: String,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    color: Color = Color.White,
) {
    val baseStyle = MaterialTheme.typography.labelSmall
    val fontSize = baseStyle.fontSize * metrics.labelScale
    val lineHeight = if (baseStyle.lineHeight.isSpecified) {
        baseStyle.lineHeight * metrics.labelScale
    } else {
        fontSize * 1.2f
    }
    Text(
        text = label,
        modifier = modifier,
        style = baseStyle.copy(
            fontSize = fontSize,
            lineHeight = lineHeight,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.6f),
                offset = Offset(0f, 2f),
                blurRadius = 4f,
            ),
        ),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Shared cell body for grid items (apps, folders). Without a label the icon is centered in the whole cell.
 *
 * With a label the icon is *sized* from the leftover area (cell minus the label row + gap, as if the label sat
 * at the bottom) so a tall cell never inflates it; the icon + gap + label are then *placed* as one packed
 * group centered in the cell, so the label stays close under the icon instead of drifting to the bottom edge.
 * Since the group's height is ≤ the cell by construction (icon capped at the leftover), centering can't push
 * the label past the cell bound. [icon] receives the resolved size.
 *
 * **Two extents, deliberately different.** The cell is what the icon is *measured against* — sizing reads the
 * whole cell, which is why the outer box fills it. [itemGestures] is applied to the packed group instead, so the
 * item's **touch target is what you can see**: the icon, the gap, and the label, and nothing else. A grid cell is
 * usually far larger than that (a home cell is a 2×2 visual slot), and a tap or long-press landing in that slack
 * is not aimed at the item — it is aimed at the surface. Since [inkspire.morphic.core.designsystem.drag.launcherItemGestures]
 * never consumes a down, leaving the slack uncovered lets those events fall through to whatever is beneath, which
 * is what keeps a press-and-hold on a *full* page of icons available for the home's own options menu.
 *
 * The group wraps its content in both axes, so its width is `max(icon, label)` — capped at the cell's inner width
 * because the label is single-line and ellipsized, which is exactly the visible bounding box.
 *
 * @param itemGestures the item-gesture modifier from the enclosing draggable cell (see
 *   [inkspire.morphic.core.designsystem.grid.LauncherDragCell]). Defaults to none, which is right for a
 *   non-interactive rendering such as a floating drag proxy.
 */
@Composable
fun IconLabelCell(
    label: String,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    itemGestures: Modifier = Modifier,
    icon: @Composable (iconSize: Dp) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availW = maxWidth - CellPadH * 2
        val padding = Modifier
            .fillMaxSize()
            .padding(horizontal = CellPadH, vertical = CellPadV)

        if (!metrics.showLabel) {
            val iconDp = metrics.resolveIconSize(availW, maxHeight - CellPadV * 2)
            Box(modifier = padding, contentAlignment = Alignment.Center) {
                Box(itemGestures) { icon(iconDp) }
            }
            return@BoxWithConstraints
        }

        val labelHeight = cellLabelHeight(metrics)
        val iconArea = (maxHeight - CellPadV * 2 - LabelGap - labelHeight).coerceAtLeast(0.dp)
        val iconDp = metrics.resolveIconSize(availW, iconArea).coerceAtMost(iconArea)
        // Outer box centers the group in the cell; the group itself wraps content and carries the gestures.
        Box(modifier = padding, contentAlignment = Alignment.Center) {
            Column(
                modifier = itemGestures,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                icon(iconDp)
                Spacer(Modifier.height(LabelGap))
                CellLabel(label = label, metrics = metrics)
            }
        }
    }
}

/**
 * The height of a cell's single-line label row, from the current type scale and [metrics] label scale — the
 * `lineHeight` of `labelSmall` (scaled) in dp. Shared so [IconLabelCell] and the folder sizer agree on how much
 * a label adds to a cell's height.
 */
@Composable
internal fun cellLabelHeight(metrics: IconMetrics): Dp {
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.labelSmall
    val fontSize = baseStyle.fontSize * metrics.labelScale
    val lineHeight = if (baseStyle.lineHeight.isSpecified) baseStyle.lineHeight * metrics.labelScale else fontSize * 1.2f
    return with(density) { lineHeight.toDp() }
}
