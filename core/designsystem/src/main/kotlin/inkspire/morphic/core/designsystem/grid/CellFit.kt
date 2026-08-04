package inkspire.morphic.core.designsystem.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CellPadH
import inkspire.morphic.core.designsystem.cell.CellPadV
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LabelGap
import inkspire.morphic.core.designsystem.cell.cellLabelHeight
import inkspire.morphic.core.model.GridBlueprint
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridSizing
import kotlin.math.floor

/**
 * **How large a grid can be before its cells stop working** — the runtime half of grid configuration.
 *
 * `GridBlueprint` deliberately stores no maxima: how many rows or columns actually fit depends on the measured area and
 * on the icon size, neither of which a `core:model` constant can know. This is where that question is answered, which
 * is why it lives here rather than beside the blueprint.
 *
 * Ported from L1's `CellFit`. Two things changed:
 *
 * **One definition of "the smallest usable cell", not two — and neither of L1's.** L1's `gridMaxima` (behind its home
 * editor) used the raw `minIconDp` as the whole cell, forgetting that a cell insets its icon; `scrollingMaxColumns`
 * divided that guardrail by `iconPercent`, which is a different question entirely and answers it backwards — see
 * [minCellWidthDp], which is L1's home formula with the padding it was missing. Both axes are here as one
 * [minCellWidthDp] / [minCellHeightDp] pair.
 *
 * **The padding comes from the cell, not from a copy of it.** L1 declared `CELL_PADDING_DP = 8f` beside the fitting
 * maths, free to drift from what `IconLabelCell` actually inset. These read `CellPadH`/`CellPadV`/`LabelGap` directly,
 * so the inverse cannot disagree with the layout it is inverting.
 *
 * **The same answer serves two questions.** [editableRangeIn] bounds what a settings screen may *offer*;
 * [fitGridConfig] applies the identical bound to a value that was stored earlier, under conditions that may since
 * have changed ([fitCols] being that clamp for a grid whose rows scroll and so has only the one axis to fit). One
 * formula, so a grid can never be offered a size it would not then be drawn at — nor drawn at a size its own editor
 * would refuse.
 *
 * **Both directions of the same cell.** [minCellHeightDp] bounds a *count* (how many rows an area can hold);
 * [cellHeightDp] sizes a *cell* (how tall one of a known width must be). A grid whose rows are fixed needs the first,
 * a grid whose rows flow needs the second, and sharing one file is what keeps them describing the same cell.
 *
 * Pure arithmetic over `Float` dp, with a `@Composable` facade for the one input that needs a type scale (the label
 * row's height) — the same split `folderInnerSize` uses, and for the same reason: the interesting behaviour should be
 * checkable without an emulator.
 */

/** The space a grid has to fill, in dp. */
data class GridArea(val widthDp: Float, val heightDp: Float)

/**
 * The largest grid that fits an area.
 *
 * @property maxCols most columns that fit across the width; always ≥ 1.
 * @property maxRows most rows that fit down the height, or **null** for a scrolling grid, whose rows are however many
 *   its content reaches and so are not bounded by the area at all.
 */
data class GridBounds(val maxCols: Int, val maxRows: Int?)

/**
 * The narrowest cell whose icon still **fits**, in dp — the icon's own floor, plus the cell's horizontal padding.
 *
 * The inverse of `IconLabelCell` + `IconMetrics.resolveIconSize`, and the whole of it is which clamp actually binds:
 * `resolveIconSize` coerces the percent-derived size **up** to [IconMetrics.minIconDp], so an icon is never drawn
 * smaller than that guardrail whatever the percent says. A cell therefore overflows exactly when `minIconDp` exceeds
 * its inner width, which makes `minIconDp + padding` the floor. `minOf` on the guardrails mirrors `resolveIconSize`,
 * which is order-safe.
 *
 * **[IconMetrics.iconPercent] is deliberately absent, and that is the correction.** The percent scales the icon
 * *within* the guardrails; it cannot make a cell unusable. An earlier cut divided by it — asking instead "how wide must
 * a cell be for the percent to be honoured un-clamped" — which coupled the two the wrong way round: at 30% a 28dp
 * guardrail demanded a 101dp column, so *shrinking* the icons reported that **fewer** columns fit. Nothing was
 * overflowing there; the icon was simply clamped up to its floor and drew at a larger fraction of the cell than asked.
 *
 * **L1 had both formulas and neither was right**, which is why the port picked wrongly the first time.
 * `scrollingMaxColumns` divided by the percent as above; `gridMaxima` — the one behind its *home* editor, and the closer
 * of the two — used the raw `minIconDp` as the entire cell, correctly ignoring the percent but forgetting that a cell
 * insets its icon. This is the honest version of L1's home formula: its guardrail, plus the padding the cell really
 * applies.
 */
fun minCellWidthDp(metrics: IconMetrics): Float =
    minOf(metrics.minIconDp.value, metrics.maxIconDp.value) + CellPadH.value * 2

/**
 * The shortest cell whose icon still fits, in dp.
 *
 * Same inverse on the other axis, plus the label row when one is shown — `IconLabelCell` sizes the icon against the
 * cell *minus* the label and the gap above it, so both must be added back here. So the row axis is moved by the label
 * controls (shown at all, and at what scale) as well as by the guardrail, which is L1's home formula again
 * (`iconDp + labelRowDp * labelScale`).
 *
 * @param labelHeightDp the label row's height, or 0 when [IconMetrics.showLabel] is false. From `cellLabelHeight`,
 *   which needs a type scale — hence the parameter rather than a second constant.
 */
fun minCellHeightDp(metrics: IconMetrics, labelHeightDp: Float): Float {
    val minIcon = minOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val label = if (metrics.showLabel) labelHeightDp + LabelGap.value else 0f
    return minIcon + CellPadV.value * 2 + label
}

/**
 * **How tall a cell of width [cellWidthDp] needs to be**, in dp — the forward direction of [minCellHeightDp].
 *
 * The two are the same layout read from opposite ends. [minCellHeightDp] asks how short a cell may get before its icon
 * stops rendering at full size; this asks what height a *given* width implies, which is the question a scrolling grid
 * has: its columns fix the cell width, and the height is then whatever the icon and the label need. It is the exact
 * inverse arithmetic of `IconLabelCell` — vertical padding, the width-driven icon (`iconPercent` of the inner width,
 * clamped to the guardrails), and, when labels show, the gap and the label row.
 *
 * **Derived rather than stored, which is L1's answer and the right one.** A cell height is not an independent
 * preference: it is what the icon size the user *did* choose implies. Storing it as well would make two settings able
 * to disagree — and a user who enlarges the icons would get bigger icons in cells that stayed the same height. L1
 * derived it (`gridCellHeightDp`) and its grids track their icon sliders because of it; the only thing changed in the
 * port is that the padding is read from the cell rather than copied beside the maths.
 *
 * @param cellWidthDp one cell's width — the grid's usable width divided by its column count.
 * @param labelHeightDp the label row's height, or ignored entirely when [IconMetrics.showLabel] is false. From
 *   `cellLabelHeight`, as in [minCellHeightDp].
 */
fun cellHeightDp(cellWidthDp: Float, metrics: IconMetrics, labelHeightDp: Float): Float {
    val innerWidth = (cellWidthDp - CellPadH.value * 2).coerceAtLeast(0f)
    val minIcon = minOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val maxIcon = maxOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val iconDp = (innerWidth * metrics.iconPercent).coerceIn(minIcon, maxIcon)
    val label = if (metrics.showLabel) LabelGap.value + labelHeightDp else 0f
    return CellPadV.value * 2 + iconDp + label
}

/** The most whole cells of [minCellDp] that fit in [availableDp]. Always ≥ 1 — a grid with no cells is not a grid. */
fun maxCells(availableDp: Float, minCellDp: Float): Int {
    if (minCellDp <= 0f || availableDp <= 0f) return 1
    return floor(availableDp / minCellDp).toInt().coerceAtLeast(1)
}

/**
 * The largest this blueprint's grid can be in [area] while every cell still renders its icon at full size.
 *
 * **A fixed cell size, not one derived from the current grid.** That is L1's insight and worth keeping: measuring
 * against the *current* cell would make the answer move with the setting being changed, so a wider area could report
 * no more room. Measuring against the smallest usable cell makes the cap track the area alone.
 *
 * Rows come back null for a [GridSizing.SCROLL_GRID], which has no row count to bound.
 */
fun GridBlueprint.boundsIn(area: GridArea, metrics: IconMetrics, labelHeightDp: Float): GridBounds = GridBounds(
    maxCols = maxCells(area.widthDp, minCellWidthDp(metrics)),
    maxRows = when (sizing) {
        GridSizing.FIXED_PAGER -> maxCells(area.heightDp, minCellHeightDp(metrics, labelHeightDp))
        GridSizing.SCROLL_GRID -> null
    },
)

/**
 * The range a user may set each axis to in [area] — the blueprint's floor up to what actually fits.
 *
 * Null when the grid has no editor at all (a folder's, a list's), which is the same question
 * `SettingsRepository.updateGrid` refuses a write for.
 *
 * **The ceiling is never below the floor.** On an area too small to honour `minCols`, the floor wins: a blueprint's
 * minimum is its own promise that the grid is usable at that size, and offering an empty range would leave the editor
 * with nothing to show. The clamp is here, once, rather than at each call site as L1 left it ("callers apply their own
 * minimum").
 */
fun GridBlueprint.editableRangeIn(
    area: GridArea,
    metrics: IconMetrics,
    labelHeightDp: Float,
): GridEditableRange? {
    val range = editRange ?: return null
    val bounds = boundsIn(area, metrics, labelHeightDp)
    return GridEditableRange(
        cols = colRangeIn(area.widthDp, metrics),
        rows = range.minRows?.let { min -> bounds.maxRows?.let { max -> min..maxOf(max, min) } },
    )
}

/**
 * The column counts this grid may legally have in an area [areaWidthDp] wide — its blueprint's floor, up to what fits.
 *
 * Private because it is not a second answer, it is the *one* answer both public column questions are asked through:
 * [editableRangeIn] offers it to an editor and [fitCols] coerces a stored value into it. Keeping "the ceiling is never
 * below the floor" in one place is the whole reason it exists — that clamp read the same in two functions is one
 * refactor away from reading differently.
 *
 * A blueprint with no editor has no stated minimum, so the floor is one column: a grid with no cells is not a grid.
 */
private fun GridBlueprint.colRangeIn(areaWidthDp: Float, metrics: IconMetrics): IntRange {
    val minCols = editRange?.minCols ?: 1
    return minCols..maxOf(maxCells(areaWidthDp, minCellWidthDp(metrics)), minCols)
}

/**
 * **The column count a stored size actually produces across [areaWidthDp]** — [fitGridConfig] for a grid that scrolls.
 *
 * The same read-side clamp, on the one axis a [GridSizing.SCROLL_GRID] has: its rows are however many its content
 * reaches, so there is nothing to bound there, and a `GridConfig` (which requires a row count) is the wrong shape to
 * answer in. Hence a bare `Int` and, unlike its sibling, **no label height** — a label sits under an icon and so cannot
 * change how many columns fit. That absence is also why this needs no `@Composable` facade.
 *
 * **Clamped, never written back**, exactly as [fitGridConfig] is: a count too wide for today's icons draws narrower and
 * returns when the icons shrink. The consumers are a scrolling surface deciding what to draw and the settings section
 * showing what it drew — one formula, so the editor cannot claim a column the grid does not have.
 */
fun GridBlueprint.fitCols(areaWidthDp: Float, cols: Int, metrics: IconMetrics): Int =
    cols.coerceIn(colRangeIn(areaWidthDp, metrics))

/**
 * What an editor may offer per axis.
 *
 * @property rows null when the axis is not the user's to set — either the grid scrolls, or its blueprint edits columns
 *   only.
 */
data class GridEditableRange(val cols: IntRange, val rows: IntRange?)

/**
 * **The grid a stored size actually produces in [area]** — both counts clamped to what fits.
 *
 * A settings screen bounds what a user can *choose*, but the choice outlives the conditions it was made under: the
 * icon size grows, the dock is shortened, the window changes. This is the read that keeps what is drawn honest, and
 * it is the same [editableRangeIn] the editor offers, applied to a value instead of to a control.
 *
 * **Clamped here, not written back** — with one exception the caller owns. A count too large for today's icons is
 * *displayed* smaller and returns when the icons shrink, because nothing overwrote it. L1 had no read-side clamp at
 * all, so it had to reconcile the other way: a `LaunchedEffect` in its settings screen wrote clamped counts into
 * storage, which destroyed the preference **and** only ran while that screen happened to be open. The exception is
 * the dock's rows against its own height, where the reduction *is* written — see `DockGrid`, and note that it is a
 * deliberate write by that screen rather than something this function does behind a caller's back.
 *
 * Throws for a [GridSizing.SCROLL_GRID], whose rows are however many its content reaches: a config needs a row count
 * and a scrolling grid has none, the same precondition `toGridConfig` states in `core:model`.
 *
 * @param area the space the grid occupies — for the dock, its measured width and its height *setting*.
 * @param cols the stored visual column count.
 * @param rows the stored visual row count.
 * @param metrics the resolved icon sizing of the cells it will draw; bigger icons mean fewer, larger cells.
 */
fun GridBlueprint.fitGridConfig(
    area: GridArea,
    cols: Int,
    rows: Int,
    metrics: IconMetrics,
    labelHeightDp: Float,
): GridConfig {
    require(sizing == GridSizing.FIXED_PAGER) {
        "$slot scrolls, so its rows come from its content rather than from the area it is given"
    }
    // Null only for a grid with no editor at all, whose stored counts are the blueprint's own and already legal.
    val range = editableRangeIn(area, metrics, labelHeightDp)
    val visualCols = range?.cols?.let(cols::coerceIn) ?: cols
    val visualRows = range?.rows?.let(rows::coerceIn) ?: rows
    // Visual counts scaled into logical ones, exactly as `toGridConfig` does — which is also what keeps both axes
    // divisible by the multiplier, the invariant `GridConfig` requires.
    return GridConfig(
        rows = visualRows * cellMultiplier,
        cols = visualCols * cellMultiplier,
        cellMultiplier = cellMultiplier,
    )
}

/**
 * [boundsIn], with the label row's height read from the current type scale.
 *
 * The `@Composable` facade over the pure arithmetic above: `cellLabelHeight` needs a `MaterialTheme` and a `Density`,
 * and pushing that requirement into the maths would make all of it untestable without an emulator.
 */
@Composable
fun GridBlueprint.boundsIn(area: GridArea, metrics: IconMetrics): GridBounds {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(this, area, metrics, labelHeightDp) { boundsIn(area, metrics, labelHeightDp) }
}

/**
 * [minCellHeightDp], with the label row's height read from the current type scale.
 *
 * The floor an **extent** control needs: a dock shorter than one usable cell has nothing to show. Exposed as a facade
 * rather than by publishing `cellLabelHeight`, which stays internal to the cell package — a caller wanting a bound
 * should ask this file for it, not assemble one out of the cell's own parts.
 */
@Composable
fun minCellHeightDp(metrics: IconMetrics): Float {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(metrics, labelHeightDp) { minCellHeightDp(metrics, labelHeightDp) }
}

/**
 * **A cell whose height was derived from its width** — how tall it is, and the metrics to draw it with.
 *
 * The two travel together because using one without the other is the bug this type exists to prevent. See
 * [derivedCell].
 *
 * @property height what `Modifier.height` (or `LauncherGrid`'s `cellHeight`) is given.
 * @property metrics what the `AppCell` inside it is given — **not** the metrics passed in.
 */
data class DerivedCell(val height: Dp, val metrics: IconMetrics)

/**
 * [cellHeightDp], with the label row's height read from the current type scale — **the one a surface calls**.
 *
 * **It returns the metrics too, and that is the whole point.** The height is derived by spending
 * [IconMetrics.iconPercent] on it: the icon is `iconPercent` of the inner width, and the height is that plus the
 * chrome. A cell handed the *original* metrics then applies the fraction a **second** time — `IconLabelCell` resolves
 * the icon as `iconPercent × min(innerWidth, iconArea)`, and `iconArea` is already the multiplied value — so the icon
 * comes out `iconPercent²` of the width and lands on its lower guardrail, inside a row that was sized for something
 * larger. At 50% on a 4-column phone grid that was a 24dp icon in a row built for 41dp.
 *
 * So the fraction is spent once, here, and the cell is given `iconPercent = 1f`: **the icon fills exactly the area the
 * height bought it**. What the user's fraction now does on such a grid is choose the cell height — which is the
 * derive-vs-store rule working as written, and is why the two values must be taken as a pair.
 *
 * L1 had the same double-application (`gridCellHeightDp` × `resolveIconSize`) and the port inherited it; nothing showed
 * until the defaults moved to 100%, where the two agree.
 *
 * @param cellWidth one cell's width — for a lazy grid, the usable width divided by the column count.
 */
@Composable
fun derivedCell(cellWidth: Dp, metrics: IconMetrics): DerivedCell {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(cellWidth, metrics, labelHeightDp) {
        DerivedCell(
            height = cellHeightDp(cellWidth.value, metrics, labelHeightDp).dp,
            metrics = metrics.copy(iconPercent = 1f),
        )
    }
}

/** [editableRangeIn], with the label row's height read from the current type scale. */
@Composable
fun GridBlueprint.editableRangeIn(area: GridArea, metrics: IconMetrics): GridEditableRange? {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(this, area, metrics, labelHeightDp) { editableRangeIn(area, metrics, labelHeightDp) }
}

/** [fitGridConfig], with the label row's height read from the current type scale. */
@Composable
fun GridBlueprint.fitGridConfig(area: GridArea, cols: Int, rows: Int, metrics: IconMetrics): GridConfig {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(this, area, cols, rows, metrics, labelHeightDp) {
        fitGridConfig(area, cols, rows, metrics, labelHeightDp)
    }
}
