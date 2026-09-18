package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.widget.WidgetSpan
import kotlin.math.ceil

/**
 * How many grid cells an item larger than an icon needs — an app widget, a widget of the launcher's own, a
 * container — as the lattice everything on HOME is placed in counts them.
 *
 * **In logical cells, like every other span in the placement engine.** A home grid is sub-divided
 * (`GridConfig.cellMultiplier`), so an app occupies a 2×2 logical footprint rather than one cell; a widget is
 * measured against the same lattice, which is what lets [FreeGridPlanner] treat it as an ordinary occupant with no
 * special case. Turning it back into the "3 × 2" a *user* would recognize is a division by the multiplier, and
 * [visualLabel] is the one place that happens.
 */
data class CellSpan(val colSpan: Int, val rowSpan: Int) {

    /**
     * The span as the user reads it — whole visual cells, "3 × 2".
     *
     * Floored at one cell each way, because a widget smaller than a cell still occupies one and a label saying
     * "0 × 1" would describe nothing.
     */
    fun visualLabel(config: GridConfig): String {
        val multiplier = config.cellMultiplier.coerceAtLeast(1)
        val cols = (colSpan / multiplier).coerceAtLeast(1)
        val rows = (rowSpan / multiplier).coerceAtLeast(1)
        return "$cols × $rows"
    }

    companion object {

        /**
         * The footprint a provider is placed at — **the size it declares, if it declares one, and a size derived
         * from its minimums otherwise.**
         *
         * Android 12 let a provider state its default size directly, in cells: [targetCols] × [targetRows]
         * (`targetCellWidth`/`targetCellHeight`). That is the widget saying "I am 2 × 2", and it is what a user
         * reads in the picker — so when it is present it wins outright, turned into logical cells by the multiplier
         * and clamped to the grid. Deriving a span from the minimum pixels instead is what made the picker's "2 × 2"
         * land as a taller footprint on home: `ceil(minHeight / cellHeight)` answers a different question (the
         * fewest cells the widget *fits* in) than the one the provider already answered (the size it *wants*), and
         * on a grid whose cells are not the provider's the two disagree by a cell.
         *
         * [forMinSize] is the fallback, for a provider too old to state a target or one that leaves it at zero. Both
         * clamp **down** to the grid, which is the "resize it to fit" case: a widget larger than the screen lands at
         * the largest footprint that could be placed, and the view is then told that size and re-lays itself out.
         *
         * The same function feeds the picker and every home surface, so the number the label prints and the number
         * the placement searches for cannot drift — the drift this fixes.
         */
        fun forAppWidget(
            targetCols: Int,
            targetRows: Int,
            minWidthPx: Int,
            minHeightPx: Int,
            cellWidthPx: Float,
            cellHeightPx: Float,
            config: GridConfig,
        ): CellSpan? {
            if (targetCols > 0 && targetRows > 0) return forVisualCells(targetCols, targetRows, config)
            return forMinSize(minWidthPx, minHeightPx, cellWidthPx, cellHeightPx, config)
        }

        /**
         * The footprint of a widget of the launcher's own: its recipe's size, in the visual cells a user counts. The
         * same conversion an app widget's declared target gets, so a 2 × 2 of either kind is the same footprint.
         */
        fun forWidget(span: WidgetSpan, config: GridConfig): CellSpan = forVisualCells(span.cols, span.rows, config)

        /** [cols] × [rows] visual cells in logical ones, at least one visual cell and at most the whole grid. */
        private fun forVisualCells(cols: Int, rows: Int, config: GridConfig): CellSpan {
            val multiplier = config.cellMultiplier.coerceAtLeast(1)
            return CellSpan(
                colSpan = (cols * multiplier).coerceIn(multiplier, config.cols),
                rowSpan = (rows * multiplier).coerceIn(multiplier, config.rows),
            )
        }

        /**
         * The smallest span that holds a widget whose provider states [minWidthPx] × [minHeightPx], on a grid of
         * [config] whose cells measure [cellWidthPx] × [cellHeightPx].
         *
         * `ceil`, because a widget that needs a fraction of a further cell needs the whole cell. Clamped **up** to
         * one visual cell (the multiplier) so a tiny widget is still a real footprint, and **down** to the grid,
         * so a widget larger than the screen is offered at the largest size that could actually be placed rather
         * than at one that could not.
         *
         * Null when the grid has not been measured yet — a caller with no cell size cannot be told anything true,
         * and a zero would divide into an infinite span.
         */
        fun forMinSize(
            minWidthPx: Int,
            minHeightPx: Int,
            cellWidthPx: Float,
            cellHeightPx: Float,
            config: GridConfig,
        ): CellSpan? {
            if (cellWidthPx <= 0f || cellHeightPx <= 0f) return null
            val multiplier = config.cellMultiplier.coerceAtLeast(1)
            return CellSpan(
                colSpan = ceil(minWidthPx / cellWidthPx).toInt().coerceIn(multiplier, config.cols),
                rowSpan = ceil(minHeightPx / cellHeightPx).toInt().coerceIn(multiplier, config.rows),
            )
        }
    }
}
