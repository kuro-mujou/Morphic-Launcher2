package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.abs

/**
 * The outcome of a [FreePush.push] attempt.
 *
 * L1's `SpreadPush.push` returned a nullable `Map<Any, GridRect>?`, overloading `null` to mean "no
 * arrangement works" while an empty map meant "nothing needed to move". Callers had to remember that a
 * non-null-but-empty result was still success — the kind of untyped sentinel the rewrite mandate replaces
 * with an honest sum type. [Moved] (possibly with an empty [Moved.moves]) is success; [Blocked] is failure.
 *
 * @param K the identity type the caller uses to key its occupants (e.g. `GridItem`, a stable id). The engine
 *   never inspects it — it only echoes keys back — so the geometry stays domain-agnostic.
 */
sealed interface PushResult<out K> {
    /**
     * Room was made for the footprint. [moves] are the occupants that shifted, at their new placements; an
     * occupant absent from the map did not move. An empty map means the footprint was already clear.
     */
    data class Moved<K>(val moves: Map<K, GridPlacement>) : PushResult<K>

    /**
     * No arrangement makes room: some occupant would have to leave the grid to clear the footprint (or the
     * footprint itself does not fit). The drop is invalid — the caller paints the error footprint.
     */
    data object Blocked : PushResult<Nothing>
}

/**
 * Makes room on a **free-placement** grid (home, dock) for a dragged item that has landed on a target
 * [GridPlacement], by shoving whatever it overlaps out of the way. This is the live push run on every hover
 * while dragging: the caller supplies the current occupants and where the drag would land, and gets back the
 * occupant moves needed (or [PushResult.Blocked] if the drop can't be honoured).
 *
 * Ported from L1's `SpreadPush` (which was sound — the rot was in the UI layer that called it three times).
 * Refactors applied: `GridRect`/`AppPosition` collapsed to [GridPlacement]; `Pair<Int, Int>` deltas replaced
 * by [PushDirection]; the nullable-map return replaced by [PushResult]; the unused `PushPath` type dropped.
 *
 * Behaviour (locked in [docs/DRAG_AND_DROP_DESIGN.md] §6b, "Cascade, error at edge"):
 * - Each overlapped occupant is shoved off the footprint. Preference order is [preferred] first (the direction
 *   derived from *which sub-zone* of the target cell the finger entered — "hover the top → push down"), then
 *   the remaining directions ordered by nearest exit edge.
 * - A shove **cascades**: if the shoved occupant lands on another, that one is shoved the same way, and so on.
 * - If a cascade would push any occupant off the grid, that direction fails; if no direction works for some
 *   occupant, the whole push is [PushResult.Blocked] — unless `relocate` is on, below. The push never spills
 *   onto another page.
 *
 * The engine is pure and side-effect-free: it copies the occupants, never mutates the input, and returns only
 * the deltas. The dragged item's own placement is *not* included — the caller places it at the footprint.
 */
object FreePush {

    /**
     * @param footprint where the dragged item would land, in the target grid's logical coordinates.
     * @param occupants the current items on that grid (excluding the dragged item), keyed by caller identity.
     * @param config the target grid's dimensions.
     * @param preferred the direction to try first, if the partition strategy computed one; otherwise the
     *   nearest-exit order alone decides.
     * @param relocate last resort for an occupant no direction can clear: give it the **nearest free space that
     *   fits** instead of failing the whole push (see [relocation]).
     *
     *   **Off by default, and which gesture asks for it is the point.** In a *drag* the push direction is part of
     *   what the user said — it comes from the sub-zone of the cell the finger entered — so an item flying across
     *   the screen would answer a question nobody asked, and a refusal is undone by moving the finger a cell
     *   over. A *resize* says nothing about direction at all: the user is claiming an area, and the items in it
     *   simply need to be somewhere else, so refusing an expansion because the neighbour cannot slide *sideways*
     *   while half the screen is empty is the wrong answer. Hence: drags cascade, resizes relocate.
     */
    fun <K> push(
        footprint: GridPlacement,
        occupants: Map<K, GridPlacement>,
        config: GridConfig,
        preferred: PushDirection? = null,
        relocate: Boolean = false,
    ): PushResult<K> {
        if (!footprint.fitsIn(config)) return PushResult.Blocked

        // `working` tracks live positions as cascades shift things; `moved` accumulates only what changed.
        val working = LinkedHashMap(occupants)
        val moved = LinkedHashMap<K, GridPlacement>()

        for ((id, original) in occupants) {
            if (!original.overlaps(footprint)) continue
            // An earlier cascade may have already shoved this occupant clear.
            val current = working[id] ?: continue
            if (!current.overlaps(footprint)) continue

            val order = buildList {
                preferred?.let { add(it) }
                nearestExitOrder(current, footprint).forEach { if (it != preferred) add(it) }
            }

            val shifts = order.firstNotNullOfOrNull { cascade(id, footprint, it, working, config) }
                ?: (if (relocate) relocation(id, current, footprint, working, config) else null)
                ?: return PushResult.Blocked
            for ((key, placement) in shifts) {
                working[key] = placement
                moved[key] = placement
            }
        }

        // Safety net: the footprint must be clear and the result overlap-free before we commit to it.
        if (working.values.any { it.overlaps(footprint) }) return PushResult.Blocked
        if (hasOverlap(working.values.toList())) return PushResult.Blocked
        return PushResult.Moved(moved)
    }

    /**
     * The four directions ordered by how little the occupant has to travel to clear the footprint — its
     * shallowest overlap edge first. Each value is the overlap depth on that side (how far past the footprint
     * edge the occupant reaches), so the smallest means the nearest way out.
     */
    private fun nearestExitOrder(rect: GridPlacement, footprint: GridPlacement): List<PushDirection> =
        listOf(
            PushDirection.UP to rect.rowEndExclusive - footprint.row,
            PushDirection.DOWN to footprint.rowEndExclusive - rect.row,
            PushDirection.LEFT to rect.colEndExclusive - footprint.col,
            PushDirection.RIGHT to footprint.colEndExclusive - rect.col,
        ).sortedBy { it.second }.map { it.first }

    /**
     * Shoves [seedId] past the [footprint] in [dir], then chases the eviction: anything the seed now overlaps
     * is shoved the same way, breadth-first, until nothing overlaps or something is forced off the grid.
     *
     * @return the shifted placements (seed included), or `null` if the cascade can't fit on the grid — in
     *   which case the caller tries the next direction.
     */
    private fun <K> cascade(
        seedId: K,
        footprint: GridPlacement,
        dir: PushDirection,
        working: Map<K, GridPlacement>,
        config: GridConfig,
    ): Map<K, GridPlacement>? {
        val rects = LinkedHashMap(working)
        val result = LinkedHashMap<K, GridPlacement>()

        val seedShift = shiftPast(rects[seedId] ?: return null, footprint, dir) ?: return null
        if (!seedShift.fitsIn(config)) return null
        rects[seedId] = seedShift
        result[seedId] = seedShift

        val queue = ArrayDeque<Pair<K, GridPlacement>>()
        queue.add(seedId to seedShift)
        // Guard against a pathological non-terminating chase; a real cascade can't touch more cells than this.
        var guard = 0
        val limit = config.rows * config.cols * (working.size + 2) + 8
        while (queue.isNotEmpty()) {
            if (guard++ > limit) return null
            val (obstacleId, obstacle) = queue.removeFirst()
            // Replacing an existing key's value doesn't structurally modify the map, so iterating is safe.
            for ((id, rect) in rects) {
                if (id == obstacleId) continue
                if (!rect.overlaps(obstacle)) continue
                val shifted = shiftPast(rect, obstacle, dir) ?: return null
                if (!shifted.fitsIn(config)) return null
                rects[id] = shifted
                result[id] = shifted
                queue.add(id to shifted)
            }
        }
        return result
    }

    /**
     * A new home for [rect] when no direction could clear it: the **nearest free space on the grid** that its
     * spans fit in, or null when the grid genuinely has nowhere left.
     *
     * "Nearest" is Manhattan distance between top-left corners — the same spans, so corner distance and centre
     * distance agree — which keeps the item as close to where the user last saw it as the grid allows. Ties are
     * broken by the row-major scan: topmost, then leftmost, which reads as "it went up out of the way" rather
     * than as an arbitrary jump.
     *
     * It relocates **one** occupant into space that is already free, and never cascades — so it cannot loop, and
     * it cannot displace anything that the push had not already decided to move. Positions are scanned at
     * *logical* granularity, so an item may come to rest straddling the visual lattice exactly as a dropped one
     * may; that is what `cellMultiplier` means (see the note in `CLAUDE.md`).
     */
    private fun <K> relocation(
        id: K,
        rect: GridPlacement,
        footprint: GridPlacement,
        working: Map<K, GridPlacement>,
        config: GridConfig,
    ): Map<K, GridPlacement>? {
        val others = working.filterKeys { it != id }.values
        var best: GridPlacement? = null
        var bestCost = Int.MAX_VALUE
        for (row in 0..config.rows - rect.rowSpan) {
            for (col in 0..config.cols - rect.colSpan) {
                val cost = abs(row - rect.row) + abs(col - rect.col)
                if (cost >= bestCost) continue
                val candidate = rect.copy(row = row, col = col)
                if (candidate.overlaps(footprint)) continue
                if (others.any { it.overlaps(candidate) }) continue
                bestCost = cost
                best = candidate
            }
        }
        return best?.let { mapOf(id to it) }
    }

    /** Moves [rect] to just past [obstacle]'s far edge in [dir]; `null` if that would cross the grid origin. */
    private fun shiftPast(rect: GridPlacement, obstacle: GridPlacement, dir: PushDirection): GridPlacement? {
        val newRow = when {
            dir.deltaRow > 0 -> obstacle.rowEndExclusive
            dir.deltaRow < 0 -> obstacle.row - rect.rowSpan
            else -> rect.row
        }
        val newCol = when {
            dir.deltaCol > 0 -> obstacle.colEndExclusive
            dir.deltaCol < 0 -> obstacle.col - rect.colSpan
            else -> rect.col
        }
        if (newRow < 0 || newCol < 0) return null
        return rect.copy(row = newRow, col = newCol)
    }

    private fun hasOverlap(rects: List<GridPlacement>): Boolean {
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                if (rects[i].overlaps(rects[j])) return true
            }
        }
        return false
    }
}
