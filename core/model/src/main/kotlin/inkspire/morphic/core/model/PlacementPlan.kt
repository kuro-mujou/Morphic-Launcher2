package inkspire.morphic.core.model

/**
 * What a drop would do, once the partition strategy has decided the target cell and (on free grids) which way
 * to push. Drives how the drop shadow reads: PLACE/PUSH/MERGE are all droppable, INVALID paints the error
 * shadow. (PUSH is visually distinct only for debugging; it drops the same as PLACE.)
 */
enum class DropIntent { PLACE, PUSH, MERGE, INVALID }

/**
 * The resolved outcome of a drag hovering over a drop zone — the **single value preview and commit both
 * read**, so the shadow can never disagree with what releasing will actually do. (L1's bug: it recomputed
 * placement independently in the render layer and the commit layer, so the previewed footprint could lie
 * about the result.)
 *
 * Purely geometric and toolkit-free, which is why it lives in `core:model` rather than the Compose drag
 * package: the coordinator paints [footprint] tinted by [intent] and animates [moves], and nothing here
 * touches Compose or persistence. Translating a committed plan into the repository's `LayoutChange`s is a
 * separate `data:layout` concern.
 *
 * @property footprint where the dragged item would land; null only when [intent] is [DropIntent.INVALID].
 * @property intent what the drop does — and therefore how the shadow reads.
 * @property moves occupants a push would displace, at their new placements; empty unless [intent] is
 *   [DropIntent.PUSH].
 */
data class PlacementPlan(
    val footprint: GridPlacement?,
    val intent: DropIntent,
    val moves: Map<GridItem, GridPlacement> = emptyMap(),
)
