package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The **"Add" cell that trails a collection's apps** — an outlined square where an icon would be, with its own label.
 *
 * **It is an [IconLabelCell] like every other cell in the grid, and that is the point.** It sits in the flow after
 * the last app, so it has to size its mark and place its label exactly as the cells around it do — otherwise a user's
 * icon-size setting moves the apps and leaves this behind. Sharing the cell means the arithmetic is not repeated
 * here; only the mark differs.
 *
 * **Outlined rather than filled, and drawn rather than imported.** An outline reads as an empty slot waiting to be
 * filled, which is what it is, where a filled tile would read as another app. The `+` is a `Canvas` because this
 * module carries no material-icons dependency — `MorphicResetButton` and `TopActionZone` draw their marks by hand
 * for the same reason.
 *
 * **It takes its own `clickable` rather than the shared gesture contract**, which is the one place a cell may: the
 * contract exists so an *item* has exactly one gesture owner across tap, long-press and drag, and this is not an item.
 * There is nothing here to drag, nothing to open a menu on, and nothing for a drop to land on.
 */
@Composable
fun AddAppsCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
) {
    val colors = LocalMorphicColors.current
    IconLabelCell(
        label = label,
        modifier = modifier,
        metrics = metrics,
        itemGestures = Modifier.clickable(onClick = onClick),
    ) { iconSize ->
        Box(
            modifier = Modifier
                .size(iconSize)
                .border(1.dp, colors.outline, RoundedCornerShape(iconSize * 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(iconSize * 0.4f)) {
                val mid = size.minDimension / 2f
                val arm = size.minDimension * 0.4f
                val stroke = size.minDimension * 0.12f
                drawLine(
                    color = colors.content,
                    start = Offset(mid - arm, mid),
                    end = Offset(mid + arm, mid),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.content,
                    start = Offset(mid, mid - arm),
                    end = Offset(mid, mid + arm),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
