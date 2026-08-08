package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import kotlin.math.ceil

/**
 * How many grid cells a widget needs — the bridge between the size a provider states in pixels and the lattice
 * everything on HOME is placed in.
 *
 * **In logical cells, like every other span in the placement engine.** A home grid is sub-divided
 * (`GridConfig.cellMultiplier`), so an app occupies a 2×2 logical footprint rather than one cell; a widget is
 * measured against the same lattice, which is what lets [FreeGridPlanner] treat it as an ordinary occupant with no
 * special case. Turning it back into the "3 × 2" a *user* would recognise is a division by the multiplier, and
 * [visualLabel] is the one place that happens.
 *
 * Ported from L1's `WidgetSpan`, with the arguments folded into the [GridConfig] the caller already has rather
 * than passed as three loose ints beside it.
 */
data class WidgetSpan(val colSpan: Int, val rowSpan: Int) {

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
        ): WidgetSpan? {
            if (cellWidthPx <= 0f || cellHeightPx <= 0f) return null
            val multiplier = config.cellMultiplier.coerceAtLeast(1)
            return WidgetSpan(
                colSpan = ceil(minWidthPx / cellWidthPx).toInt().coerceIn(multiplier, config.cols),
                rowSpan = ceil(minHeightPx / cellHeightPx).toInt().coerceIn(multiplier, config.rows),
            )
        }
    }
}
