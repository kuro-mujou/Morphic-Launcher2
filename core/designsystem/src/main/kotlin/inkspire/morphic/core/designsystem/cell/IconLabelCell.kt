package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.layout.Arrangement
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
 * A grid-cell label: single line, ellipsised, sized by [IconMetrics.labelScale].
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
 * Shared cell body for grid items (apps, folders). Without a label the icon is centred in the whole cell.
 *
 * With a label the icon is *sized* from the leftover area (cell minus the label row + gap, as if the label sat
 * at the bottom) so a tall cell never inflates it; the icon + gap + label are then *placed* as one packed
 * group centred in the cell, so the label stays close under the icon instead of drifting to the bottom edge.
 * Since the group's height is ≤ the cell by construction (icon capped at the leftover), centring can't push
 * the label past the cell bound. [icon] receives the resolved size.
 */
@Composable
fun IconLabelCell(
    label: String,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    icon: @Composable (iconSize: Dp) -> Unit,
) {
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.labelSmall
    val fontSize = baseStyle.fontSize * metrics.labelScale
    val lineHeight = if (baseStyle.lineHeight.isSpecified) {
        baseStyle.lineHeight * metrics.labelScale
    } else {
        fontSize * 1.2f
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availW = maxWidth - CellPadH * 2

        if (!metrics.showLabel) {
            val iconDp = metrics.resolveIconSize(availW, maxHeight - CellPadV * 2)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CellPadH, vertical = CellPadV),
                contentAlignment = Alignment.Center,
            ) {
                icon(iconDp)
            }
            return@BoxWithConstraints
        }

        val labelHeight = with(density) { lineHeight.toDp() }
        val iconArea = (maxHeight - CellPadV * 2 - LabelGap - labelHeight).coerceAtLeast(0.dp)
        val iconDp = metrics.resolveIconSize(availW, iconArea).coerceAtMost(iconArea)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CellPadH, vertical = CellPadV),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon(iconDp)
            Spacer(Modifier.height(LabelGap))
            CellLabel(label = label, metrics = metrics)
        }
    }
}
