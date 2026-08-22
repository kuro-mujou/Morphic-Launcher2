package inkspire.morphic.core.designsystem.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CategoryPreviewCols
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.cellLabelHeight
import inkspire.morphic.core.model.CardChrome
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
 * Two things are worth knowing:
 *
 * **One definition of "the smallest usable cell", on both axes** — [minCellWidthDp] / [minCellHeightDp]. It is the
 * icon guardrail *plus the padding a cell really applies*; the two ways to get this wrong are to use the raw
 * `minIconDp` as the whole cell, and to divide the guardrail by `iconPercent`, which answers a different question
 * backwards.
 *
 * **The padding here is a copy of what `IconLabelCell` insets, and nothing checks that the two agree.** The 4dp
 * used below is the same number that cell applies on each axis, plus the same 4dp gap above its label — written
 * out in both places rather than shared. Change one and this inverse silently stops inverting the layout it is
 * built from, which shows up as cells that fit their own maths and not the icon drawn in them.
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
 * row's height) — the same split `appCollectionInnerSize` uses, and for the same reason: the interesting behavior
 * should be checkable without an emulator.
 */

/** The space a grid has to fill, in dp. */
data class GridArea(val widthDp: Float, val heightDp: Float)

/**
 * **The smallest cell a grid may be divided into**, in dp — the one input every fit in this file is really made of.
 *
 * Extracted as a type once a grid arrived whose cells are not icons. Everything here divides an area by a floor, and
 * for seven of the eight grids that floor comes from the icon guardrails ([minCellFor]); the **widget area** has no
 * icon sizing at all (`WidgetAreaGrid.icon` is null — a widget is not an icon in a cell), so it supplies
 * [WidgetMinCell] instead. Naming the input rather than the *source* of it is what lets one set of functions serve
 * both, where an `IconMetrics` parameter would have forced a parallel copy for the one grid that cannot produce one.
 *
 * Two dimensions rather than one because an icon cell's floors genuinely differ per axis: the row axis has to carry a
 * label as well. A widget's do not, which is why [WidgetMinCell] is square.
 */
data class MinCell(val widthDp: Float, val heightDp: Float)

/**
 * The floor for a grid of **widgets**, square on both axes.
 *
 * 48dp is the platform's own minimum touch target and the size one `App Widget` cell has historically been quoted at,
 * so it is a widget's floor in the same sense `minIconDp + padding` is an icon cell's: below it there is nothing to
 * place. It is a constant rather than a setting because, unlike an icon, nothing about a widget's size is the user's
 * to choose per grid — a widget declares its own minimum span and the area either holds it or does not.
 */
val WidgetMinCell = MinCell(widthDp = 48f, heightDp = 48f)

/**
 * The floor for a grid of **tiles** — the narrowest APPS category card that can still draw its preview.
 *
 * **Derived, where this was a flat number picked by eye.** A card's preview is [CategoryPreviewCols]² slots, so the
 * narrowest card that honors the user's own guardrail is `CategoryPreviewCols × minIconDp`, plus the gap between the
 * slots and the padding around them. That is exactly the inversion [minCellWidthDp] performs for an icon cell —
 * `minIconDp + padding` — applied to a tile holding four icons instead of one, and it became available the moment the
 * card grid started declaring icon sizing.
 *
 * The constant it replaced was wrong both times it was chosen: 96dp let a 393dp phone draw four lanes of unreadable
 * dots, and 120dp only looked right because it happened to absorb chrome it could not see. A ceiling that follows the
 * guardrails cannot drift like that — ask for larger icons and the lane count comes down on its own, which is what
 * the same expression already does on every other grid.
 *
 * Square because a column fit reads [MinCell.widthDp] and nothing else. A card is its square preview *plus* a title,
 * so its drawn height is larger; nothing asks this type for that height (the card grid scrolls, so it has no row
 * count to bound), and carrying a number nobody reads is how it would go stale.
 */
fun cardMinCell(metrics: IconMetrics, chrome: CardChrome): MinCell {
    val cardDp = CategoryPreviewCols * metrics.minIconDp.value +
        chrome.innerPaddingDp + 2 * chrome.outerPaddingDp
    // Plus a lane's share of the grid's own chrome, because a column fit divides the grid's *raw* width. Without it
    // this floor describes a card while the division describes a lane, and the gap between the two is one extra lane
    // of cards too narrow to fill — which is precisely how the constant this replaced went wrong.
    val laneDp = cardDp + 12.dp.value
    return MinCell(widthDp = laneDp, heightDp = laneDp)
}

/** The [MinCell] an icon grid's guardrails imply — [minCellWidthDp] and [minCellHeightDp] as one value. */
fun minCellFor(metrics: IconMetrics, labelHeightDp: Float): MinCell =
    MinCell(minCellWidthDp(metrics), minCellHeightDp(metrics, labelHeightDp))

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
 * a cell be for the percent to be honored un-clamped" — which coupled the two the wrong way round: at 30% a 28dp
 * guardrail demanded a 101dp column, so *shrinking* the icons reported that **fewer** columns fit. Nothing was
 * overflowing there; the icon was simply clamped up to its floor and drew at a larger fraction of the cell than asked.
 *
 * **Two ways to get this wrong, and both have been written here.** Dividing the guardrail by the percent answers a
 * different question and inverts this one. Using the raw `minIconDp` as the entire cell correctly ignores the percent
 * but forgets that a cell insets its icon. What is right is the guardrail plus the padding the cell really applies.
 */
fun minCellWidthDp(metrics: IconMetrics): Float =
    minOf(metrics.minIconDp.value, metrics.maxIconDp.value) + 4.dp.value * 2

/**
 * The shortest cell whose icon still fits, in dp.
 *
 * Same inverse on the other axis, plus the label row when one is shown — `IconLabelCell` sizes the icon against the
 * cell *minus* the label and the gap above it, so both must be added back here. So the row axis is moved by the label
 * controls (shown at all, and at what scale) as well as by the guardrail: `iconDp + labelRowDp * labelScale`.
 *
 * @param labelHeightDp the label row's height, or 0 when [IconMetrics.showLabel] is false. From `cellLabelHeight`,
 *   which needs a type scale — hence the parameter rather than a second constant.
 */
fun minCellHeightDp(metrics: IconMetrics, labelHeightDp: Float): Float {
    val minIcon = minOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val label = if (metrics.showLabel) labelHeightDp + 4.dp.value else 0f
    return minIcon + 4.dp.value * 2 + label
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
 * **Derived rather than stored.** A cell height is not an independent preference: it is what the icon size the user
 * *did* choose implies. Storing it as well would make two settings able to disagree — a user who enlarges the icons
 * would get bigger icons in cells that stayed the same height. Deriving it is what makes the grids track their icon
 * sliders.
 *
 * @param cellWidthDp one cell's width — the grid's usable width divided by its column count.
 * @param labelHeightDp the label row's height, or ignored entirely when [IconMetrics.showLabel] is false. From
 *   `cellLabelHeight`, as in [minCellHeightDp].
 */
fun cellHeightDp(cellWidthDp: Float, metrics: IconMetrics, labelHeightDp: Float): Float {
    val innerWidth = (cellWidthDp - 4.dp.value * 2).coerceAtLeast(0f)
    val minIcon = minOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val maxIcon = maxOf(metrics.minIconDp.value, metrics.maxIconDp.value)
    val iconDp = (innerWidth * metrics.iconPercent).coerceIn(minIcon, maxIcon)
    val label = if (metrics.showLabel) 4.dp.value + labelHeightDp else 0f
    return 4.dp.value * 2 + iconDp + label
}

/** The most whole cells of [minCellDp] that fit in [availableDp]. Always ≥ 1 — a grid with no cells is not a grid. */
fun maxCells(availableDp: Float, minCellDp: Float): Int {
    if (minCellDp <= 0f || availableDp <= 0f) return 1
    return floor(availableDp / minCellDp).toInt().coerceAtLeast(1)
}

/**
 * The largest this blueprint's grid can be in [area] while every cell still renders its icon at full size.
 *
 * **A fixed cell size, not one derived from the current grid.** Measuring against the *current* cell would make the
 * answer move with the setting being changed, so a wider area could report
 * no more room. Measuring against the smallest usable cell makes the cap track the area alone.
 *
 * Rows come back null for a [GridSizing.SCROLL_GRID], which has no row count to bound.
 */
fun GridBlueprint.boundsIn(area: GridArea, min: MinCell): GridBounds = GridBounds(
    maxCols = maxCells(area.widthDp, min.widthDp),
    maxRows = when (sizing) {
        GridSizing.FIXED_PAGER -> maxCells(area.heightDp, min.heightDp)
        GridSizing.SCROLL_GRID -> null
    },
)

/** [boundsIn] for an icon grid, whose floor is its guardrails. */
fun GridBlueprint.boundsIn(area: GridArea, metrics: IconMetrics, labelHeightDp: Float): GridBounds =
    boundsIn(area, minCellFor(metrics, labelHeightDp))

/**
 * The range a user may set each axis to in [area] — the blueprint's floor up to what actually fits.
 *
 * Null when the grid has no editor at all (a folder's, a list's), which is the same question
 * `SettingsRepository.updateGrid` refuses a write for.
 *
 * **The ceiling is never below the floor.** On an area too small to honor `minCols`, the floor wins: a blueprint's
 * minimum is its own promise that the grid is usable at that size, and offering an empty range would leave the editor
 * with nothing to show. The clamp is here, once, rather than left to each call site.
 */
fun GridBlueprint.editableRangeIn(area: GridArea, min: MinCell): GridEditableRange? {
    val range = editRange ?: return null
    val bounds = boundsIn(area, min)
    return GridEditableRange(
        cols = colRangeIn(area.widthDp, min.widthDp),
        rows = range.minRows?.let { floor -> bounds.maxRows?.let { max -> floor..maxOf(max, floor) } },
    )
}

/** [editableRangeIn] for an icon grid, whose floor is its guardrails. */
fun GridBlueprint.editableRangeIn(
    area: GridArea,
    metrics: IconMetrics,
    labelHeightDp: Float,
): GridEditableRange? = editableRangeIn(area, minCellFor(metrics, labelHeightDp))

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
private fun GridBlueprint.colRangeIn(areaWidthDp: Float, minCellWidthDp: Float): IntRange {
    val minCols = editRange?.minCols ?: 1
    return minCols..maxOf(maxCells(areaWidthDp, minCellWidthDp), minCols)
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
    cols.coerceIn(colRangeIn(areaWidthDp, minCellWidthDp(metrics)))

/**
 * [fitCols] for a grid whose floor is not an icon guardrail — the category cards' [CardMinCell].
 *
 * Only [MinCell.widthDp] is read, for the reason stated above: a column count is a question about width alone. The
 * pairing with the icon overload mirrors [boundsIn] and [editableRangeIn], which each take a [MinCell] so that the
 * one grid unable to produce an [IconMetrics] needs no parallel copy of the fit.
 */
fun GridBlueprint.fitCols(areaWidthDp: Float, cols: Int, min: MinCell): Int =
    cols.coerceIn(colRangeIn(areaWidthDp, min.widthDp))

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
 * *displayed* smaller and returns when the icons shrink, because nothing overwrote it. Without a read-side clamp the
 * reconciliation has to go the other way — writing clamped counts back from a `LaunchedEffect` in the settings screen,
 * which destroys the preference **and** only runs while that screen is open. The exception is
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
fun GridBlueprint.fitGridConfig(area: GridArea, cols: Int, rows: Int, min: MinCell): GridConfig {
    require(sizing == GridSizing.FIXED_PAGER) {
        "$slot scrolls, so its rows come from its content rather than from the area it is given"
    }
    // Null only for a grid with no editor at all, whose stored counts are the blueprint's own and already legal.
    val range = editableRangeIn(area, min)
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

/** [fitGridConfig] for an icon grid, whose floor is its guardrails. */
fun GridBlueprint.fitGridConfig(
    area: GridArea,
    cols: Int,
    rows: Int,
    metrics: IconMetrics,
    labelHeightDp: Float,
): GridConfig = fitGridConfig(area, cols, rows, minCellFor(metrics, labelHeightDp))

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

/**
 * [minCellFor], with the label row's height read from the current type scale — **the one a settings screen calls**.
 *
 * The facade exists because `cellLabelHeight` stays internal to the cell package: a caller wanting a floor should ask
 * this file for it rather than assembling one out of the cell's own parts, which is the same rule the
 * [minCellHeightDp] facade above states.
 */
@Composable
fun minCellFor(metrics: IconMetrics): MinCell {
    val labelHeightDp = cellLabelHeight(metrics).value
    return remember(metrics, labelHeightDp) { minCellFor(metrics, labelHeightDp) }
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
