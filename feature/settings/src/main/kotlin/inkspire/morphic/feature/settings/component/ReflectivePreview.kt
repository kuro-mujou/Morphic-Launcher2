package inkspire.morphic.feature.settings.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.grid.derivedCell
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridEditorEdge
import kotlin.math.ceil

/**
 * A **scrolling** grid's mockup: cells at their *real* aspect, filling downward and clipped where they run past the
 * fold.
 *
 * The port of L1's `ReflectiveGridPreview`, and the difference from [GridPreview] is the whole point. That one divides
 * the box evenly by rows × columns, which is what a **fixed pager** actually looks like. A scrolling grid has no row
 * count: its columns fix the cell *width*, and the height is whatever the icon and its label need — so an even
 * division would show a shape the surface never draws, and adding a column would appear to change nothing but the
 * count. Here the same `derivedCell` the surface lays out with decides the aspect, so a column press visibly narrows
 * the cells and **gains rows**, which is the actual consequence of the press.
 *
 * The last row being clipped rather than fitted is deliberate for the same reason: a scrolling grid's content runs past
 * the bottom, and a mockup whose final row lands flush implies a page that ends.
 *
 * @param areaWidthDp the width the grid is really given, **margin already subtracted** — the aspect comes from a dp
 *   cell width, so it cannot be derived from the mockup's own pixels.
 * @param insetFraction the margin again, as a fraction, to inset the drawn lattice. Both are needed and they are not
 *   redundant: one sets the shape of a cell, the other where the lattice starts.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReflectivePreview(
    cols: Int,
    metrics: IconMetrics,
    areaWidthDp: Float,
    insetFraction: Float,
    edit: PreviewEdit?,
) {
    val colors = LocalMorphicColors.current
    val currentEdit by rememberUpdatedState(edit)
    var shownCols by remember { mutableIntStateOf(cols) }
    var flashIndex by remember { mutableIntStateOf(-1) }
    var flashAdd by remember { mutableStateOf(true) }
    // Consumed once, as in `GridPreview`: a column count can change without a press (a larger minimum icon size
    // re-fits the grid), and replaying the pending edit would flash an edge nobody touched.
    var flashedNonce by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    val flashSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(cols) {
        val pending = currentEdit
        if (pending == null || pending.nonce == flashedNonce || shownCols == cols) {
            shownCols = cols
            return@LaunchedEffect
        }
        flashedNonce = pending.nonce
        flashAdd = pending.add
        flashIndex = if (pending.add) cols - 1 else shownCols - 1
        shownCols = cols
        flash.snapTo(1f)
        flash.animateTo(0f, flashSpec)
    }

    val animCols by animateFloatAsState(
        shownCols.toFloat(),
        MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "reflectiveCols",
    )

    val inset = insetFraction.coerceIn(0f, MAX_REFLECTIVE_INSET)
    // The cell's real dp width at the *drawn* column count, and the height the icon and label need in it. Computed
    // outside the draw block because `derivedCell` is a composable read (it remembers against the type scale).
    val drawnCols = animCols.coerceAtLeast(1f)
    val realCellWidthDp = (areaWidthDp * (1f - inset * 2) / drawnCols).coerceAtLeast(1f)
    val derived = derivedCell(cellWidth = realCellWidthDp.dp, metrics = metrics)
    val aspect = (derived.height.value / realCellWidthDp).coerceIn(MIN_CELL_ASPECT, MAX_CELL_ASPECT)

    val cellColor = colors.contentMuted.copy(alpha = 0.45f)
    val flashColor = if (flashAdd) colors.accent else Color.Transparent
    val flashValue = flash.value
    // Only LEFT mirrors: rows are not edited on a scrolling grid, so there is no TOP case to mirror for.
    val mirrorCols = edit?.edge == GridEditorEdge.LEFT

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(1f - inset * 2)
                .fillMaxHeight()
                .clipToBounds()
        ) {
            val gap = 3.dp.toPx()
            val cellW = ((size.width - gap * (drawnCols - 1f)) / drawnCols).coerceAtLeast(1f)
            val cellH = (cellW * aspect).coerceAtLeast(1f)
            val corner = CornerRadius(3.dp.toPx())
            // One more row than fits, so the bottom one is cut off by the clip rather than stopping short of it.
            val rowCount = ceil((size.height + gap) / (cellH + gap)).toInt().coerceAtLeast(1)
            for (r in 0 until rowCount) {
                for (c in 0 until ceil(drawnCols).toInt()) {
                    val flashed = flashValue > 0f && c == flashIndex
                    val color = when {
                        !flashed -> cellColor
                        flashAdd -> lerp(cellColor, flashColor, flashValue)
                        else -> lerp(Color.Transparent, cellColor, flashValue)
                    }
                    val x = c * (cellW + gap)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(if (mirrorCols) size.width - x - cellW else x, r * (cellH + gap)),
                        size = Size(cellW, cellH),
                        cornerRadius = corner,
                    )
                }
            }
        }
    }
}

/** As [GridEditor]'s own cap, and for the same reason: a wide margin on a narrow screen must still draw something. */
private const val MAX_REFLECTIVE_INSET = 0.4f

/**
 * Bounds on the drawn cell shape.
 *
 * A guard on arithmetic rather than a design choice: at extreme icon settings the derived height can dwarf the width
 * (or the reverse), and a cell 30× taller than it is wide would draw one row of slivers. L1 clamps the same ratio.
 */
private const val MIN_CELL_ASPECT = 0.1f
private const val MAX_CELL_ASPECT = 10f
