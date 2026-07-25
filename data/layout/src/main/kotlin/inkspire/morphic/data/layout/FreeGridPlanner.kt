package inkspire.morphic.data.layout

import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan

/**
 * Packages the free-grid engine ([FreePush]) into a [PlacementPlan] the drag coordinator can render directly —
 * the "planner face" of home/dock placement. Given where a drag is hovering (a target [footprint]) and what
 * the partition strategy already decided (a [preferred] push direction, and whether the finger sits in a
 * mergeable target's inner ring — [merge]), it returns the one plan that both the live shadow and the eventual
 * drop read.
 *
 * The partition/hover geometry that produces [footprint]/[preferred]/[merge] lives in the UI layer; this
 * planner is pure and deterministic, so running it for the preview and again at drop yields the same result.
 * Sharing the *logic* (not caching a result) is what keeps the shadow honest — the fix for L1's three-way
 * `SpreadPush` divergence.
 */
object FreeGridPlanner {

    /**
     * @param footprint the target cell the dragged item would occupy.
     * @param occupants the grid's current items (excluding the dragged one), keyed by their [GridItem] id.
     * @param config the target grid's dimensions.
     * @param preferred the push direction from the entered sub-zone, or null to let nearest-edge decide.
     * @param merge true when the finger is in the merge ring of a target already known to accept a merge.
     */
    fun plan(
        footprint: GridPlacement,
        occupants: Map<GridItem, GridPlacement>,
        config: GridConfig,
        preferred: PushDirection? = null,
        merge: Boolean = false,
    ): PlacementPlan {
        // Merge is decided upstream (finger in a mergeable target's inner ring); no cells shift.
        if (merge) return PlacementPlan(footprint, DropIntent.MERGE)

        return when (val result = FreePush.push(footprint, occupants, config, preferred)) {
            is PushResult.Moved -> PlacementPlan(
                footprint = footprint,
                intent = if (result.moves.isEmpty()) DropIntent.PLACE else DropIntent.PUSH,
                moves = result.moves,
            )
            // Still report the hovered footprint so the shadow can paint red there, not vanish.
            PushResult.Blocked -> PlacementPlan(footprint = footprint, intent = DropIntent.INVALID)
        }
    }
}
