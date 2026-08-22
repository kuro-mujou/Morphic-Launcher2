package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridEditorEdge
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
 * **Three entry points, for the three ways a grid's size can change under its contents.** [reflow] settles a grid
 * against a new size it was simply *given* (the dock, after its height setting changed); [edit] applies a **named
 * edge**'s ± one row/column, shifting items when the change lands on the top or left; and [admit] finds room on
 * *another* grid for what the first could not keep. A dock shrunk past its contents needs the first and the last —
 * its bottom row's occupants have nowhere to go within a single-page strip, and home's main area is where they land.
 * None of them knows about zones: the caller composes them, which is what keeps this arithmetic reusable by any pair
 * of grids rather than encoding "the dock spills into main" here.
 *
 * Idempotent: when everything already fits, [Result.changed] is `false` and the input map passes straight
 * through untouched.
 */
object GridReflow {

    /**
     * What becomes of an item the grid cannot fit.
     *
     * @property NEXT_PAGE push it forward, appending a page when every existing one is full — a paged surface
     *   (home's main area) always has room somewhere.
     * @property EVICT report it in [Result.evicted] and leave it out of the result, because this grid has no
     *   later page to push it onto. The caller then owes it a home; see [admit].
     */
    enum class Overflow { NEXT_PAGE, EVICT }

    /**
     * @param placements the reflowed positions.
     * @param evicted the items this grid could not take at all — always empty under [Overflow.NEXT_PAGE], and
     *   deliberately **absent from [placements]** so a caller cannot persist a position for an item that has
     *   none.
     * @param changed `false` when nothing needed moving — lets the caller skip a redundant persist.
     */
    data class Result<K>(
        val placements: Map<K, GridPlacement>,
        val evicted: Set<K> = emptySet(),
        val changed: Boolean,
    )

    /**
     * Settles [placements] against [config], re-homing anything that no longer fits.
     *
     * @param overflow what to do with an item that finds no room; defaults to the paged behavior, since only a
     *   single-page grid has the problem [Overflow.EVICT] answers.
     */
    fun <K> reflow(
        placements: Map<K, GridPlacement>,
        config: GridConfig,
        overflow: Overflow = Overflow.NEXT_PAGE,
    ): Result<K> {
        if (placements.values.all { it.fitsIn(config) }) return Result(placements, changed = false)

        // Seed occupancy only with items that still fit; the strays are the ones we re-place around them.
        val kept = LinkedHashMap<K, GridPlacement>(placements.size)
        val strays = ArrayList<Pair<K, GridPlacement>>()
        for ((key, placement) in placements) {
            if (placement.fitsIn(config)) kept[key] = placement else strays += key to placement
        }

        val rehomed = rehome(strays, kept.values, config, overflow)
        return Result(kept + rehomed.placements, rehomed.evicted, changed = true)
    }

    /**
     * Finds room on this grid for [arrivals] — items that belong to no grid right now — around the [occupants]
     * already on it.
     *
     * The receiving half of a shrink, and it exists because [reflow] cannot express it: an arrival carries the
     * coordinate it held on the grid it came *from*, which is very often a perfectly valid coordinate here too
     * (dock row 0, column 2 is also a home cell). [reflow] would therefore judge it as "still fits" and leave it
     * sitting on top of whatever already occupies that cell. Telling this function which items are homeless is
     * the fact that cannot be recovered from the coordinates alone.
     *
     * Each arrival's old coordinate is still used, as a *hint*: it is tried first, so items keep their relative
     * order where the grid allows.
     *
     * @param arrivals the homeless items, mapped to the coordinate they last held.
     * @param occupants what is already placed here; these never move.
     * @return placements for the arrivals **only** — the occupants are not repeated, since the caller already
     *   has them and nothing about them changed.
     */
    fun <K> admit(
        arrivals: Map<K, GridPlacement>,
        occupants: Map<K, GridPlacement>,
        config: GridConfig,
    ): Result<K> = rehome(arrivals.toList(), occupants.values, config, Overflow.NEXT_PAGE)

    /**
     * Adds or removes one **visual** row or column at [edge], moving every item to match.
     *
     * The op behind a grid editor's ± buttons, and the reason an editor names an *edge* rather than a count: which
     * side changes decides what happens to the items. Adding or removing at [GridEditorEdge.TOP] or
     * [GridEditorEdge.LEFT] shifts everything by one visual cell so the change lands on that side; `BOTTOM` and
     * `RIGHT` need no shift, because the far edge is where the grid already ends. Anything the new grid cannot hold
     * — the top row's occupants after a TOP removal, or the bottom row's after a BOTTOM one — is re-homed exactly as
     * [reflow] re-homes a stray, [overflow] and all.
     *
     * **One function where L1 had two files.** Its `GridEdit` and `DockGridEdit` were ~90% identical: the same shift,
     * the same reflow, five parallel typed maps each, differing only in what happened when nothing fit (append a page
     * / drop the item). That difference is [Overflow] here, so the duplication has nowhere to live.
     *
     * @param placements the items **before** the edit.
     * @param config the grid **after** it — the caller has already decided the new size, and this places items into
     *   it. Growing therefore passes the larger grid, which is why a shifted item always fits one.
     * @param add true to gain a row/column at [edge], false to lose one.
     */
    fun <K> edit(
        placements: Map<K, GridPlacement>,
        edge: GridEditorEdge,
        add: Boolean,
        config: GridConfig,
        overflow: Overflow = Overflow.NEXT_PAGE,
    ): Result<K> {
        // One *visual* cell, which on a sub-cell grid is `cellMultiplier` logical ones — an edit adds a row a user
        // can see, not a half-row they cannot put anything in.
        val step = config.cellMultiplier
        val dRow = if (edge == GridEditorEdge.TOP) (if (add) step else -step) else 0
        val dCol = if (edge == GridEditorEdge.LEFT) (if (add) step else -step) else 0

        val kept = LinkedHashMap<K, GridPlacement>(placements.size)
        val strays = ArrayList<Pair<K, GridPlacement>>()
        for ((key, at) in placements) {
            // Computed as plain Ints because a shift can go negative, which `GridPlacement` rejects outright — so
            // the moved placement is only built once it is known to land. A stray keeps its *original* coordinate,
            // which is the hint `rehome` re-places it from.
            val row = at.row + dRow
            val col = at.col + dCol
            val lands = row >= 0 && col >= 0 &&
                row + at.rowSpan <= config.rows && col + at.colSpan <= config.cols
            if (lands) kept[key] = at.copy(row = row, col = col) else strays += key to at
        }

        val rehomed = rehome(strays, kept.values, config, overflow)
        val settled = kept + rehomed.placements
        return Result(
            placements = settled,
            evicted = rehomed.evicted,
            // Compared against the input rather than inferred from the shift: a `BOTTOM`/`RIGHT` edit moves nothing,
            // and an edit on an empty grid changes nothing at all, so neither should ask for a write.
            changed = rehomed.evicted.isNotEmpty() || placements.any { (key, at) -> settled[key] != at },
        )
    }

    /**
     * Places each of [strays] around [occupied], in reading order so the result is stable and predictable
     * rather than hash-order dependent.
     *
     * @return placements for the strays alone; whatever found no room is [Result.evicted] under
     *   [Overflow.EVICT], or left at its original coordinate under [Overflow.NEXT_PAGE].
     */
    private fun <K> rehome(
        strays: List<Pair<K, GridPlacement>>,
        occupied: Collection<GridPlacement>,
        config: GridConfig,
        overflow: Overflow,
    ): Result<K> {
        val occupancy = GridOccupancy(config, occupied)
        val placed = LinkedHashMap<K, GridPlacement>(strays.size)
        val evicted = LinkedHashSet<K>()

        for ((key, placement) in strays.sortedWith(compareBy({ it.second.page }, { it.second.row }, { it.second.col }))) {
            val rowSpan = placement.rowSpan.coerceAtMost(config.rows)
            val colSpan = placement.colSpan.coerceAtMost(config.cols)
            val rect = when (overflow) {
                Overflow.NEXT_PAGE ->
                    occupancy.findFreeRect(placement.page, placement.row, placement.col, rowSpan, colSpan)

                Overflow.EVICT ->
                    occupancy.findFreeRectOnPage(placement.page, placement.row, placement.col, rowSpan, colSpan)
            }
            when {
                rect != null -> {
                    occupancy.occupy(rect)
                    placed[key] = rect
                }
                // Null under NEXT_PAGE means the item is larger than the grid itself, which no page will fix:
                // leave it where it was rather than dropping it. Under EVICT it may simply be a full grid, and
                // either way the answer is the same — this grid cannot hold it, so say so.
                overflow == Overflow.NEXT_PAGE -> placed[key] = placement
                else -> evicted += key
            }
        }
        return Result(placed, evicted, changed = strays.isNotEmpty())
    }
}
