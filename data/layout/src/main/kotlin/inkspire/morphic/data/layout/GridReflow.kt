package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement

/**
 * Re-homes free-placement items that no longer fit the grid — the classic case being the user shrinking the
 * home grid in the editor, which can leave items hanging past the new right/bottom edge. Each stray item is
 * moved to the nearest free cell (scanning forward across pages, appending a page when the rest are full);
 * items that still fit keep their exact positions, because home is free-placement and gaps are allowed.
 *
 * Ported from L1's `GridReflow`, with its biggest smell removed: L1's `Result` carried **five parallel typed
 * maps** (apps / folders / widgets / containers / iconContainers) and ran `place()` five times against a
 * shared occupancy. Because occupancy has to be shared across every item type anyway (a reflowed app must not
 * land on a widget), the honest shape is **one** map keyed by whatever identity the caller uses — typically
 * `GridItem`, the sealed type that already unifies those five. The caller merges its stores into one
 * `Map<GridItem, GridPlacement>`, reflows once, and demerges by `when (item)`.
 *
 * Idempotent: when everything already fits, [Result.changed] is `false` and the input map passes straight
 * through untouched.
 */
object GridReflow {

    /**
     * @param placements the reflowed positions (same keys as the input).
     * @param changed `false` when nothing needed moving — lets the caller skip a redundant persist.
     */
    data class Result<K>(val placements: Map<K, GridPlacement>, val changed: Boolean)

    fun <K> reflow(placements: Map<K, GridPlacement>, config: GridConfig): Result<K> {
        if (placements.values.all { it.fitsIn(config) }) return Result(placements, changed = false)

        // Seed occupancy only with items that still fit; the strays are the ones we re-place around them.
        val occupancy = GridOccupancy(config, placements.values.filter { it.fitsIn(config) })
        val result = LinkedHashMap<K, GridPlacement>(placements.size)
        val strays = ArrayList<Map.Entry<K, GridPlacement>>()
        for (entry in placements) {
            if (entry.value.fitsIn(config)) result[entry.key] = entry.value else strays.add(entry)
        }

        // Re-place in reading order so the result is stable and predictable, not hash-order dependent.
        strays.sortWith(compareBy({ it.value.page }, { it.value.row }, { it.value.col }))
        for (entry in strays) {
            val p = entry.value
            val rect = occupancy.findFreeRect(
                page = p.page,
                row = p.row,
                col = p.col,
                rowSpan = p.rowSpan.coerceAtMost(config.rows),
                colSpan = p.colSpan.coerceAtMost(config.cols),
            )
            if (rect == null) {
                // Item is larger than the grid itself; leave it where it was rather than dropping it.
                result[entry.key] = p
                continue
            }
            occupancy.occupy(rect)
            result[entry.key] = rect
        }
        return Result(result, changed = true)
    }
}
