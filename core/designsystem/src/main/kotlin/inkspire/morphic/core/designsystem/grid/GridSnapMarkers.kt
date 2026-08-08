package inkspire.morphic.core.designsystem.grid

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Size of one marker. L1's 16dp. */
private val MarkerSize = 16.dp

/**
 * How far from the dragged item's edge a marker starts to appear, as a multiple of the larger visual cell. L1's
 * 1.8 — wide enough that the lattice reads as a field rather than as four dots stuck to the icon.
 */
private const val BufferCells = 1.8f

/** Below this the marker is invisible anyway, so it is not drawn. */
private const val MinVisibleAlpha = 0.05f

/**
 * **Shows the grid, near whatever is being dragged** — the affordance that makes a sub-divided grid legible.
 *
 * A marker at every **visual** cell corner, fading in as the dragged item approaches and out again as it leaves,
 * so the lattice appears under the finger and nowhere else. Ported from L1's `GridLinesCanvas`, which draws the
 * same concave diamonds at the same 16dp with the same 1.8-cell falloff.
 *
 * **Why the *visual* corners when the item snaps to the logical lattice.** `GridConfig.cellMultiplier` subdivides
 * the grid so an icon can come to rest straddling two visible cells; the corners are therefore *not* every place
 * the item can land, and they are not meant to be. They are the grid the user thinks in — the reference the drop
 * shadow is read against, so "half a cell across" is a thing you can see rather than a thing you discover. Marking
 * every logical intersection would double the dots and say nothing extra: on a `cellMultiplier = 2` grid every
 * other one falls in the middle of a cell.
 *
 * **A `drawBehind`, and the arguments are lambdas**, so a drag moving the finger re-runs the draw phase and
 * nothing else — no recomposition, no relayout, per frame. This is the same reason the surface pager reads its
 * `progress` as a function rather than a value.
 *
 * @param config the grid's **logical** dimensions; the markers are drawn every `cellMultiplier` cells.
 * @param localFinger the finger in this node's own coordinates, or null when nothing is being dragged over this
 *   grid — which draws nothing at all. Null rather than a separate `visible` flag because there is no meaningful
 *   position to fade around without a finger, and L1 needed both a provider and a `visibleProvider` to say it.
 * @param draggedSpan the dragged footprint in logical cells, used to measure distance from the item's *edge*
 *   rather than from the finger — so a wide item lights up the lattice along its whole width.
 */
@Composable
fun Modifier.gridSnapMarkers(
    config: GridConfig,
    localFinger: () -> Offset?,
    draggedSpan: () -> GridSpan,
    color: Color = LocalMorphicColors.current.content,
): Modifier {
    val markerPx = with(LocalDensity.current) { MarkerSize.toPx() }
    return drawBehind {
        val finger = localFinger() ?: return@drawBehind
        val span = draggedSpan()

        val cellW = size.width / config.cols
        val cellH = size.height / config.rows
        val visualCellW = cellW * config.cellMultiplier
        val visualCellH = cellH * config.cellMultiplier
        if (visualCellW <= 0f || visualCellH <= 0f) return@drawBehind

        // Half the dragged footprint, so the falloff below measures from its edge and not from its centre.
        val itemHalfW = span.colSpan * cellW / 2f
        val itemHalfH = span.rowSpan * cellH / 2f
        val buffer = max(visualCellW, visualCellH) * BufferCells

        for (col in 0..config.visualCols) {
            for (row in 0..config.visualRows) {
                val x = col * visualCellW
                val y = row * visualCellH
                // Distance from the marker to the nearest point on the item's bounding box: zero while the marker
                // is under the item, growing once it is outside — which is what makes the fade track the item's
                // shape rather than a circle around the finger.
                val dx = max(0f, abs(x - finger.x) - itemHalfW)
                val dy = max(0f, abs(y - finger.y) - itemHalfH)
                val distance = sqrt(dx * dx + dy * dy)
                if (distance >= buffer) continue

                // Eased rather than linear (L1's 1.5 power), so markers hold their brightness near the item and
                // drop away quickly at the edge of the field instead of leaving a wide grey haze.
                val alpha = (1f - distance / buffer).coerceIn(0f, 1f).pow(1.5f)
                if (alpha <= MinVisibleAlpha) continue
                drawSnapMarker(Offset(x, y), markerPx, color.copy(alpha = alpha))
            }
        }
    }
}

/**
 * A footprint's size in logical cells — what [gridSnapMarkers] measures its falloff against.
 *
 * Its own tiny type rather than two `Int` lambdas, so a caller cannot pass rows where columns go. `GridPlacement`
 * would do the job and carries a position as well, which at this call site would be a value nobody reads.
 */
data class GridSpan(val colSpan: Int, val rowSpan: Int)

/**
 * One marker: a diamond with **concave** sides, drawn as four quadratic curves pulled toward the centre.
 *
 * L1's shape exactly. A plain diamond or a dot reads as content — something placed on the grid — where the pinched
 * star reads as a registration mark, which is what it is.
 */
private fun DrawScope.drawSnapMarker(center: Offset, size: Float, color: Color) {
    val half = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - half)
        // Every curve's control point is the centre itself, which is what pulls the edges inward.
        quadraticTo(center.x, center.y, center.x + half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + half)
        quadraticTo(center.x, center.y, center.x - half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - half)
        close()
    }
    drawPath(path = path, color = color)
}
