package inkspire.morphic.feature.settings.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.cellIconLayout
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import kotlin.math.min
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else in this module. */
private val PreviewPadding = 12.dp
private val CaptionGap = 6.dp

/**
 * How much of the box a cell may fill before it is scaled down — L1's `CELL_PREVIEW_FIT`, verbatim, because the number
 * is doing the same job: leave a little air so the cell's own outline is not flush against the panel.
 */
private const val CELL_FIT = 0.94f

/** Dash and dot patterns for the two guardrail outlines, in px. Greyscale needs *shape* to tell them apart. */
private val DashPattern = floatArrayOf(9f, 7f)
private val DotPattern = floatArrayOf(2f, 6f)

/**
 * **The live icon preview** — a real cell at its real dp size, with the cell and the two icon guardrails outlined on
 * top, updating as the sliders below it move.
 *
 * The port of L1's `IconPreviewBox` + `ClassicCellIconPreview`, and it is worth saying what it is *for*, because a
 * smaller mock would not do it: the icon controls set a fraction and two dp bounds, and nothing about those three
 * numbers tells you what you will get in **this** grid's cell. So the preview renders the actual [AppCell] at the
 * actual cell size the section computed, and draws the guardrails as squares around the icon — you can see which of
 * the three is binding, which is the whole question a user has while dragging.
 *
 * **It is the body only.** The heading and L1's dice belong to the section's pinned icon header (`SurfaceDetail`), so
 * that the two travel together when the controls scroll — which is what L1 does as well, and the reason its preview sits
 * inside the sticky header rather than under it.
 *
 * **Three deliberate departures from L1.**
 * - **The geometry is asked for, not copied.** L1 restated the cell's padding and label gap as `PREVIEW_CELL_PAD_DP` /
 *   `PREVIEW_LABEL_GAP_DP` under a "keep in sync" comment; this asks `cellIconLayout` where the icon is, so the guides
 *   cannot drift from the cell they are drawn over.
 * - **Greyscale, distinguished by stroke.** L1 colored the guardrails green (upper) and red (lower). This palette is
 *   monochrome and reserves red for `error`, so the cell is a **solid** outline, the upper guardrail **dashed** and the
 *   lower **dotted**, with the caption naming them. The same rule that made the grid editor's add/remove buttons sit on
 *   the edge they affect rather than being told apart by color.
 * - **The wallpaper behind it is L1's own trick, and it is back.** The cell box composites with `BlendMode.Src`, so
 *   wherever it draws nothing it *clears* the pane instead of covering it; the pane is an offscreen layer over a window
 *   that shows the wallpaper (`PunchThroughPane` in `SettingsScreen`, and `windowShowWallpaper` in `app`'s theme), so
 *   what is revealed is the real wallpaper. It is worth the machinery for the reason L1 gave: an icon is judged against
 *   what it will sit on, and a grey panel is not that.
 *
 * @param app the sample to draw, or null while the app cache is still loading — the preview then shows its outlines
 *   alone, which is still the useful half (the sizes are what is being edited).
 * @param metrics the sizing to draw at, as the cells' own type: the **dragged** value while a slider is held, so this
 *   tracks live. `IconMetrics` rather than `IconSizing` because a **derived-height** grid must be previewed with the
 *   metrics its cell is really drawn with — `CellFit.derivedCell` has already spent `iconPercent` on the height there,
 *   and a preview that spent it twice would show the same wrong icon the surfaces used to draw.
 * @param cellWidth the real width of one cell in the grid being configured — the section computes it, since only the
 *   section knows its own area and column count.
 * @param cellHeight likewise; for the vertical list this is the row height and [asRow] is set instead.
 * @param asRow draws an [AppRowCell] across the width rather than a grid cell, for the one layout that is a list. L1
 *   took a `listMode` flag at every call site for this; here the caller passes it once per section, from the slot.
 */
@Composable
internal fun IconSizingPreview(
    app: AppInfo?,
    metrics: IconMetrics,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: Modifier = Modifier,
    asRow: Boolean = false,
) {
    val colors = LocalMorphicColors.current

    Column(modifier.fillMaxWidth()) {
        // **The punch.** `BlendMode.Src` makes this layer *replace* the pixels under it rather than blend with them,
        // so everywhere the cell and its outlines do not draw, the pane's own background is cleared to transparent —
        // and since the pane is an offscreen layer over a window that shows the wallpaper, what shows through is the
        // wallpaper. The title row and the caption sit outside this box, so they are unaffected.
        //
        // Only the cell is inside it, which is also why the box is the right level: a punch around the whole preview
        // would clear the label and the legend too.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (asRow) cellHeight + PreviewPadding * 2 else previewBoxHeight(cellHeight))
                .graphicsLayer { blendMode = BlendMode.Src }
                .padding(vertical = PreviewPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (asRow) {
                RowPreview(app, metrics, cellHeight)
            } else {
                CellPreview(app, metrics, cellWidth, cellHeight)
            }
        }

        // The numbers behind the picture, and the legend for the two dashes. Both belong here rather than in the
        // outlines: the palette cannot color-code them, and a user reading "24–48" beside a 48dp square can see at a
        // glance that the upper bound is what is binding.
        val layout = cellIconLayout(cellWidth, cellHeight, metrics)
        Text(
            text = buildString {
                if (asRow) {
                    append("Row ${cellHeight.value.roundToInt()} dp")
                } else {
                    append("Cell ${cellWidth.value.roundToInt()} × ${cellHeight.value.roundToInt()} dp")
                    append(" · icon ${layout.iconSize.value.roundToInt()} dp")
                }
                append(" · limits ${metrics.minIconDp.value.roundToInt()}–${metrics.maxIconDp.value.roundToInt()} dp")
                if (!asRow) append("\nSolid outline: the cell · dashed: upper limit · dotted: lower limit")
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = CaptionGap),
        )
    }
}

/**
 * The cell, at **true dp size**, scaled *down* only if it would not fit the box — L1's rule, and the right one: a cell
 * drawn larger than life would misrepresent the very thing being measured, while a cell too large for the panel (a
 * tablet's) still has to be shown somehow.
 */
@Composable
private fun CellPreview(app: AppInfo?, metrics: IconMetrics, cellWidth: Dp, cellHeight: Dp) {
    val colors = LocalMorphicColors.current
    val layout = cellIconLayout(cellWidth, cellHeight, metrics)

    BoxWithConstraints(contentAlignment = Alignment.Center) {
        if (cellWidth <= 0.dp || cellHeight <= 0.dp) return@BoxWithConstraints
        val fit = min(
            1f,
            min(
                maxWidth.value * CELL_FIT / cellWidth.value,
                maxHeight.value * CELL_FIT / cellHeight.value,
            ),
        )
        Box(
            modifier = Modifier
                .requiredSize(cellWidth, cellHeight)
                .graphicsLayer { scaleX = fit; scaleY = fit },
        ) {
            if (app != null) AppCell(app = app, metrics = metrics)
            Canvas(Modifier.fillMaxSize()) {
                val centre = Offset(size.width / 2f, layout.iconCenterY.toPx())
                fun square(edge: Dp, effect: PathEffect?) {
                    val px = edge.toPx()
                    if (px <= 0f) return
                    drawRect(
                        color = colors.accent,
                        topLeft = Offset(centre.x - px / 2f, centre.y - px / 2f),
                        size = Size(px, px),
                        style = Stroke(width = GuideStrokeDp.toPx(), pathEffect = effect),
                    )
                }
                // The cell first, so the guardrails read as being *inside* it.
                drawRect(
                    color = colors.content,
                    style = Stroke(width = GuideStrokeDp.toPx()),
                )
                // **Clamped to the cell's *inner* box, not to the cell** — `layout.iconBound` is the width less its
                // horizontal padding and the height less its vertical padding and label row, which is the largest icon
                // the cell can actually hold. Clamping to the cell instead (as this first did) drew a too-large guardrail
                // flush against the outer ring, hiding the 4dp the cell insets its icon by and implying an icon could
                // fill the cell edge to edge. It is also what L1 clamped to (`minOf(availWdp, iconAreaDp)`).
                val bound = layout.iconBound
                square(minOf(maxOf(metrics.minIconDp, metrics.maxIconDp), bound), PathEffect.dashPathEffect(DashPattern))
                square(minOf(minOf(metrics.minIconDp, metrics.maxIconDp), bound), PathEffect.dashPathEffect(DotPattern))
            }
        }
    }
}

/** The list's row, at its real height and the full width it really has. */
@Composable
private fun RowPreview(app: AppInfo?, metrics: IconMetrics, rowHeight: Dp) {
    val colors = LocalMorphicColors.current
    Box(Modifier.fillMaxWidth().height(rowHeight)) {
        if (app != null) {
            AppRowCell(app = app, modifier = Modifier.fillMaxSize(), metrics = metrics)
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = colors.content, style = Stroke(width = GuideStrokeDp.toPx()))
        }
    }
}

/**
 * How tall the box holding a grid-cell preview is.
 *
 * L1 sized it to hold the *largest possible* cell (the fewest rows), so that the cell never scaled and the box never
 * resized as the grid changed. Here it follows the cell it is actually drawing, capped: the section's editor is right
 * above, so a preview that jumped in height as you added a row would be the more distracting of the two.
 */
private fun previewBoxHeight(cellHeight: Dp): Dp = cellHeight.coerceIn(96.dp, 220.dp) + PreviewPadding * 2

/** Stroke width for every outline. Thin enough not to swamp a small cell, thick enough to read over an icon. */
private val GuideStrokeDp = 1.5.dp

