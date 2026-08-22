package inkspire.morphic.core.designsystem.collection

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.cellLabelHeight
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig

/**
 * The inner-zone size (the bounded card holding the collection's app grid) for an [AppCollectionOverlay].
 *
 * Deterministic: identical inputs always yield the same size, so it needs **no persisted precompute** — which is
 * what keeps every collection (a home folder, an APPS category card, …) the same size on a given device. The inputs
 * it doesn't take as parameters it reads from composition — the cell's label height (via [metrics]) and its chrome
 * — because both are ambient facts about the current theme and surface, and every caller would otherwise have to
 * recompute the same two values to ask a simple question.
 *
 * Two modes by orientation:
 * - **width-driven** (portrait): inner width is a fraction of the window width; each cell's width follows from
 *   the column count, its icon area is the square inside that width, and the cell height is that square + the
 *   label row. Inner height = cell height × rows. The title sits *above* the inner zone (portrait has
 *   the vertical room), so it isn't part of this height.
 * - **height-driven** (landscape, where vertical space is scarce): inner height is a fraction of the window
 *   height minus the chrome the card puts above and below the grid, and the width is derived back from it.
 *
 * The cell maths mirrors [inkspire.morphic.core.designsystem.cell.IconLabelCell] (same paddings, gap, and
 * square icon area), so an `AppCell` placed in a resulting cell renders at the size assumed here.
 */
@Composable
fun appCollectionInnerSize(
    window: DpSize,
    device: DeviceConfiguration,
    grid: GridConfig,
    metrics: IconMetrics = LocalIconMetrics.current,
): DpSize = appCollectionInnerSize(window, device, grid, cellLabelHeight(metrics), collectionChromeReserve())

/**
 * The pure core of [appCollectionInnerSize] — the same maths with its two composition-read inputs supplied explicitly:
 * [cellLabelHeight] (how much a cell's label row adds to its height) and [chromeReserve] (the title row + the dots
 * row, which only the height-driven mode has to subtract). Kept separate from the `@Composable` facade so the
 * arithmetic stays callable and testable without a composition.
 */
internal fun appCollectionInnerSize(
    window: DpSize,
    device: DeviceConfiguration,
    grid: GridConfig,
    cellLabelHeight: Dp,
    chromeReserve: Dp,
): DpSize = when (device) {
    DeviceConfiguration.PHONE_PORTRAIT ->
        widthDriven(window.width, PHONE_PORTRAIT_WIDTH_FRACTION, grid, cellLabelHeight)

    DeviceConfiguration.TABLET_PORTRAIT ->
        widthDriven(window.width, TABLET_PORTRAIT_WIDTH_FRACTION, grid, cellLabelHeight)

    DeviceConfiguration.PHONE_LANDSCAPE ->
        heightDriven(window.height, PHONE_LANDSCAPE_HEIGHT_FRACTION, grid, cellLabelHeight, chromeReserve)

    DeviceConfiguration.TABLET_LANDSCAPE ->
        heightDriven(window.height, TABLET_LANDSCAPE_HEIGHT_FRACTION, grid, cellLabelHeight, chromeReserve)
}

/**
 * The vertical space the card spends on chrome rather than on the grid: the title row (its `lineHeight` at the
 * current type scale) plus its bottom padding, plus the fixed page-dots row. Landscape sizing must leave room for
 * it; portrait has the height to spare and ignores it.
 */
@Composable
private fun collectionChromeReserve(): Dp {
    val titleStyle = MaterialTheme.typography.titleMedium
    val titleHeight = with(LocalDensity.current) {
        (if (titleStyle.lineHeight.isSpecified) titleStyle.lineHeight else titleStyle.fontSize * 1.2f).toDp()
    }
    return titleHeight + 12.dp + 24.dp
}

private fun widthDriven(windowWidth: Dp, fraction: Float, grid: GridConfig, labelHeight: Dp): DpSize {
    val innerWidth = windowWidth * fraction
    val cellWidth = innerWidth / grid.cols.toFloat()
    val iconArea = (cellWidth - 4.dp * 2f).coerceAtLeast(0.dp) // the square icon bound inside the cell
    val cellHeight = 4.dp * 2f + iconArea + 4.dp + labelHeight
    return DpSize(innerWidth, cellHeight * grid.rows.toFloat())
}

private fun heightDriven(windowHeight: Dp, fraction: Float, grid: GridConfig, labelHeight: Dp, reserve: Dp): DpSize {
    val innerHeight = (windowHeight * fraction - reserve).coerceAtLeast(0.dp)
    val cellHeight = innerHeight / grid.rows.toFloat()
    val iconArea = (cellHeight - 4.dp * 2f - 4.dp - labelHeight).coerceAtLeast(0.dp)
    val cellWidth = iconArea + 4.dp * 2f
    return DpSize(cellWidth * grid.cols.toFloat(), innerHeight)
}

// Inner-zone fractions of the window. Portrait fixes width; landscape fixes height (vertical space is scarce).
// Tablets take proportionally less so a collection doesn't sprawl on a large screen.
private const val PHONE_PORTRAIT_WIDTH_FRACTION = 0.82f
private const val TABLET_PORTRAIT_WIDTH_FRACTION = 0.68f
private const val PHONE_LANDSCAPE_HEIGHT_FRACTION = 0.95f
private const val TABLET_LANDSCAPE_HEIGHT_FRACTION = 0.80f
