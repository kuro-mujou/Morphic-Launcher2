package inkspire.morphic.feature.home.widgetpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.data.layout.CellSpan
import inkspire.morphic.data.widgets.WidgetTemplate
import inkspire.morphic.feature.home.WidgetCell

/** One of the launcher's own widget designs, as a row in the picker's list: its name and the cells it takes. */
@Composable
internal fun TemplateRow(template: WidgetTemplate, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Dashboard, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = template.name, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(
                text = "${template.recipe.span.cols} × ${template.recipe.span.rows}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
            )
        }
    }
}

/**
 * A design's detail page: the widget itself, live, at the size it will land — and **Add to home**, absent when the
 * grid has no room for it.
 *
 * @param cellWidthPx the grid's measured cell, which is what makes the preview the widget's real size rather than a
 *   likeness. Zero before the grid is measured, when the page shows a square of the design instead.
 */
@Composable
internal fun TemplateDetailPane(
    template: WidgetTemplate,
    grid: GridConfig?,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onBack: () -> Unit,
    onAdd: ((WidgetTemplate) -> Unit)?,
    hasRoomFor: (CellSpan) -> Boolean,
) {
    val colors = LocalMorphicColors.current
    val span = grid?.let { CellSpan.forWidget(template.recipe.span, it) }
    val fits = span == null || hasRoomFor(span)
    DetailFrame(title = template.name, onBack = onBack, onAdd = onAdd?.let { add -> { add(template) } }?.takeIf { fits }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val density = LocalDensity.current
            val size = with(density) {
                if (span != null && cellWidthPx > 0f && cellHeightPx > 0f) {
                    DpSize((span.colSpan * cellWidthPx).toDp(), (span.rowSpan * cellHeightPx).toDp())
                } else {
                    DpSize(160.dp, 160.dp)
                }
            }
            TrueSizePreview(
                size = size,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { WidgetCell(recipe = template.recipe) }
            Spacer(Modifier.height(12.dp))
            if (grid != null && span != null) {
                Text(text = span.visualLabel(grid), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            }
            if (!fits) RoomlessNotice()
        }
    }
}

/**
 * [content] laid out at exactly [size] and shrunk, never grown, to fit what the page has — so a widget that re-lays
 * rather than scales is seen as it will land, only smaller when the sheet is narrower than the grid.
 */
@Composable
private fun TrueSizePreview(size: DpSize, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(1f, maxWidth / size.width, maxHeight / size.height)
        Box(Modifier.size(size.width * scale, size.height * scale), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) { content() }
        }
    }
}
