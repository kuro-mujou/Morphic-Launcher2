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
 * Ported from L1's `CellFit`, whose arithmetic is sound. Two things changed:
 *
 * **One definition of "the smallest usable cell", not two.** L1 had `scrollingMaxColumns` compute it as
 * `minIcon / iconPercent + cellPadding` — correct — while `gridMaxima` used the raw `minIconDp` as the whole cell,
 * which treats the cell as if the icon filled it and so over-counts how many columns fit. Both are here as one
 * [minCellWidthDp] / [minCellHeightDp] pair.
 *
 * **The padding comes from the cell, not from a copy of it.** L1 declared `CELL_PADDING_DP = 8f` beside the fitting
 * maths, free to drift from what `IconLabelCell` actually inset. These read `CellPadH`/`CellPadV`/`LabelGap` directly,
 * so the inverse cannot disagree with the layout it is inverting.
 *
 * **The same answer serves two questions.** [editableRangeIn] bounds what a settings screen may *offer*;
 * [fitGridConfig] applies the identical bound to a value that was stored earlier, under conditions that may since
 * have changed. One formula, so a grid can never be offered a size it would not then be drawn at.
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
 * The narrowest cell that still renders its icon at full size, in dp.
 *
 * The inverse of `IconLabelCell` + `IconMetrics.resolveIconSize`: the icon is [IconMetrics.iconPercent] of the smaller
 * *inner* bound, so a cell narrower than `minIcon / iconPercent` plus its horizontal padding would have the icon
 * clamped up to its minimum and overflow. `minOf` on the guardrails mirrors `resolveIconSize`, which is order-safe.
 */
fun minCellWidthDp(metrics: IconMetrics): Float {
    val percent = metrics.iconPercent.coerceAtLeast(MIN_ICON_PERCENT)
    val minIcon = minOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    return minIcon / percent + CellPadH.value * 2
}

/**
 * The shortest cell that still renders its icon at full size, in dp.
 *
 * Same inverse on the other axis, plus the label row when one is shown — `IconLabelCell` sizes the icon against the
 * cell *minus* the label and the gap above it, so both must be added back here.
 *
 * @param labelHeightDp the label row's height, or 0 when [IconMetrics.showLabel] is false. From `cellLabelHeight`,
 *   which needs a type scale — hence the parameter rather than a second constant.
 */
fun minCellHeightDp(metrics: IconMetrics, labelHeightDp: Float): Float {
    val percent = metrics.iconPercent.coerceAtLeast(MIN_ICON_PERCENT)
    val minIcon = minOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val label = if (metrics.showLabel) labelHeightDp + LabelGap.value else 0f
    return minIcon / percent + CellPadV.value * 2 + label
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
        cols = range.minCols..maxOf(bounds.maxCols, range.minCols),
        rows = range.minRows?.let { min -> bounds.maxRows?.let { max -> min..maxOf(max, min) } },
    )
}

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
 * [cellHeightDp], with the label row's height read from the current type scale — **the one a surface calls**.
 *
 * Named apart from the pure function rather than overloading it, because it answers in the type its callers lay out
 * with: a `Dp` for `Modifier.height`, `LauncherGrid`'s `cellHeight`, and the floating drag proxy's size. The pure
 * arithmetic stays in `Float` dp so it can be tested without a `MaterialTheme`, as everything else in this file is.
 *
 * @param cellWidth one cell's width — for a lazy grid, the usable width divided by the column count.
 */
@Composable
fun cellHeight(cellWidth: Dp, metrics: IconMetrics): Dp {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(cellWidth, metrics, labelHeightDp) { cellHeightDp(cellWidth.value, metrics, labelHeightDp).dp }
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

/**
 * Floor on the icon fraction used when inverting a cell.
 *
 * Guards the division only. `IconSizingRanges.IconPercent` already floors what a user can choose well above this; this
 * is for a value arriving from anywhere else, where dividing by zero would report an unbounded grid.
 */
private const val MIN_ICON_PERCENT = 0.05f
