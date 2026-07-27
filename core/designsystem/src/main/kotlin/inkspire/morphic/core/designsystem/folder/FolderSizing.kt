package inkspire.morphic.core.designsystem.folder

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CellPadH
import inkspire.morphic.core.designsystem.cell.CellPadV
import inkspire.morphic.core.designsystem.cell.LabelGap
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig

/**
 * The inner-zone size (the bounded card holding the folder's app grid) for a [FolderOverlay]. Pure arithmetic
 * over the device, the folder [grid] (from the FolderGrid blueprint), and the cell's [cellLabelHeight] — so it
 * is deterministic and needs **no persisted precompute**: identical inputs always yield the same size, which is
 * what keeps every folder (home, APPS category card, …) the same size on a given device.
 *
 * Two modes by orientation:
 * - **width-driven** (portrait): inner width is a fraction of the window width; each cell's width follows from
 *   the column count, its icon area is the square inside that width, and the cell height is that square + the
 *   label row. Inner height = cell height × rows. The folder title sits *above* the inner zone (portrait has
 *   the vertical room), so it isn't part of this height.
 * - **height-driven** (landscape, where vertical space is scarce): inner height is a fraction of the window
 *   height minus [landscapeReserve] (the title row + its padding), and the width is derived back from it.
 *
 * The cell maths mirrors [inkspire.morphic.core.designsystem.cell.IconLabelCell] (same paddings, gap, and
 * square icon area), so an `AppCell` placed in a resulting cell renders at the size assumed here.
 */
fun folderInnerSize(
    window: DpSize,
    device: DeviceConfiguration,
    grid: GridConfig,
    cellLabelHeight: Dp,
    landscapeReserve: Dp,
): DpSize = when (device) {
    DeviceConfiguration.PHONE_PORTRAIT ->
        widthDriven(window.width, PHONE_PORTRAIT_WIDTH_FRACTION, grid, cellLabelHeight)
    DeviceConfiguration.TABLET_PORTRAIT ->
        widthDriven(window.width, TABLET_PORTRAIT_WIDTH_FRACTION, grid, cellLabelHeight)
    DeviceConfiguration.PHONE_LANDSCAPE ->
        heightDriven(window.height, PHONE_LANDSCAPE_HEIGHT_FRACTION, grid, cellLabelHeight, landscapeReserve)
    DeviceConfiguration.TABLET_LANDSCAPE ->
        heightDriven(window.height, TABLET_LANDSCAPE_HEIGHT_FRACTION, grid, cellLabelHeight, landscapeReserve)
}

private fun widthDriven(windowWidth: Dp, fraction: Float, grid: GridConfig, labelHeight: Dp): DpSize {
    val innerWidth = windowWidth * fraction
    val cellWidth = innerWidth / grid.cols.toFloat()
    val iconArea = (cellWidth - CellPadH * 2f).coerceAtLeast(0.dp) // the square icon bound inside the cell
    val cellHeight = CellPadV * 2f + iconArea + LabelGap + labelHeight
    return DpSize(innerWidth, cellHeight * grid.rows.toFloat())
}

private fun heightDriven(windowHeight: Dp, fraction: Float, grid: GridConfig, labelHeight: Dp, reserve: Dp): DpSize {
    val innerHeight = (windowHeight * fraction - reserve).coerceAtLeast(0.dp)
    val cellHeight = innerHeight / grid.rows.toFloat()
    val iconArea = (cellHeight - CellPadV * 2f - LabelGap - labelHeight).coerceAtLeast(0.dp)
    val cellWidth = iconArea + CellPadH * 2f
    return DpSize(cellWidth * grid.cols.toFloat(), innerHeight)
}

// Inner-zone fractions of the window. Portrait fixes width; landscape fixes height (vertical space is scarce).
// Tablets take proportionally less so folders don't sprawl on a large screen.
private const val PHONE_PORTRAIT_WIDTH_FRACTION = 0.82f
private const val TABLET_PORTRAIT_WIDTH_FRACTION = 0.68f
private const val PHONE_LANDSCAPE_HEIGHT_FRACTION = 0.95f
private const val TABLET_LANDSCAPE_HEIGHT_FRACTION = 0.80f
