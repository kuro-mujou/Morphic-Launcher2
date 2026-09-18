package inkspire.morphic.feature.home.widgetpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.ShrinkToFit
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.data.layout.CellSpan
import inkspire.morphic.data.widgets.WidgetTemplate
import inkspire.morphic.feature.home.WidgetCell

/**
 * The **Widgets** section: every design drawn live, packed into shelves **two grid rows wide**, each preview laid out
 * at the size it will land and then scaled by **one factor shared by the whole section** — so a 1 × 1 reads as a
 * quarter of a 4 × 1 here exactly as it will on HOME. Two rows rather than one because one row's width previews each
 * design at nearly full size, and the section is for seeing them all at once; a tap opens the design's own page,
 * where it is shown at full size.
 *
 * @param cellWidthPx the grid's measured logical cell, as [TemplateDetailPane] takes it. Zero before the grid is
 *   measured, when a visual cell is drawn square: the shared scale cancels its size, so only its shape is a guess.
 */
@Composable
internal fun TemplateShelves(
    templates: List<WidgetTemplate>,
    grid: GridConfig?,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onOpen: (WidgetTemplate) -> Unit,
) {
    val density = LocalDensity.current
    val cols = grid?.visualCols ?: FallbackCols
    val multiplier = grid?.cellMultiplier?.coerceAtLeast(1) ?: 1
    val cell = with(density) {
        if (cellWidthPx > 0f && cellHeightPx > 0f) {
            DpSize((cellWidthPx * multiplier).toDp(), (cellHeightPx * multiplier).toDp())
        } else {
            DpSize(100.dp, 100.dp)
        }
    }
    val spanOf: (WidgetTemplate) -> Int = { template -> visualCols(template, grid) }
    val shelfCols = cols * 2
    val shelves = templateShelves(templates, shelfCols, spanOf)
    // The scale leaves room for the gaps of the most crowded shelf, so every shelf fits at the one shared scale.
    val gaps = (shelves.maxOfOrNull { it.size } ?: 1) - 1
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val scale = (maxWidth - gap * gaps) / (cell.width * shelfCols)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            shelves.forEach { shelf ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    shelf.forEach { template ->
                        val span = grid?.let { CellSpan.forWidget(template.recipe.span, it) }
                        val size = DpSize(
                            cell.width * spanOf(template),
                            cell.height * (span?.rowSpan?.div(multiplier) ?: template.recipe.span.rows),
                        )
                        TemplatePreview(template, size, scale) { onOpen(template) }
                    }
                }
            }
        }
    }
}

/** One design at [size], shown at [scale], with its name under it. */
@Composable
private fun TemplatePreview(template: WidgetTemplate, size: DpSize, scale: Float, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier
            .width(size.width * scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShrinkToFit(size = size, modifier = Modifier.size(size * scale)) { WidgetCell(recipe = template.recipe) }
        Text(
            text = template.name,
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** How many visual columns [template] takes on [grid] — its declared span, clamped as placing it would clamp it. */
private fun visualCols(template: WidgetTemplate, grid: GridConfig?): Int = when (grid) {
    null -> template.recipe.span.cols.coerceIn(1, FallbackCols)
    else -> CellSpan.forWidget(template.recipe.span, grid).colSpan / grid.cellMultiplier.coerceAtLeast(1)
}

/** A phone's default grid, for a picker with no grid of its own to measure the shelves against. */
private const val FallbackCols = 4

/**
 * [templates] in their own order, cut into shelves no wider than [cols]: a design that does not fit beside the ones
 * before it starts the next shelf. Never reordered — the library's order is its author's, and a picker that shuffled
 * designs to fill gaps would move them as the grid changed.
 */
internal fun <T> templateShelves(templates: List<T>, cols: Int, spanOf: (T) -> Int): List<List<T>> {
    val shelves = mutableListOf<MutableList<T>>()
    var used = cols
    templates.forEach { template ->
        val span = spanOf(template).coerceIn(1, cols.coerceAtLeast(1))
        if (used + span > cols) {
            shelves += mutableListOf<T>()
            used = 0
        }
        shelves.last() += template
        used += span
    }
    return shelves
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
            ShrinkToFit(
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
