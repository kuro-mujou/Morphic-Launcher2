package inkspire.morphic.core.model

/**
 * What a drop would do, once the partition strategy has decided the target cell and (on free grids) which way
 * to push. Drives how the drop shadow reads: PLACE/PUSH/MERGE/REORDER are all droppable, INVALID paints the
 * error shadow. (PUSH is visually distinct only for debugging; it drops the same as PLACE.)
 *
 * [REORDER] is the odd one and the reason it exists: an **ordered** surface previews a drop by reflowing its own
 * cells around a migrating gap, so there is no target cell for a shadow to name. Without it such a surface has to
 * return a plan whose footprint is a deliberate lie — a token meaning "handled", which any surface that naively
 * paints will render as a shadow at cell (0,0). Now "reflow, don't paint" is representable, and the cost is one
 * paint-nothing branch in `DropFootprint`, which is honest rather than hidden.
 *
 * [REMOVE] is the second value with no cell behind it, added for the same reason and on the same terms: the
 * top-action band takes an item **off** the launcher rather than putting it somewhere, so its plan names no
 * destination at all. Its affordance is the band itself lighting up, which is why it too paints no shadow. The
 * alternative was a `PLACE` plan whose footprint was a token — exactly the lie [REORDER] exists to have stopped
 * telling.
 */
enum class DropIntent { PLACE, PUSH, MERGE, REORDER, REMOVE, INVALID }

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
 * A plan always has a [footprint] — the target cell under the finger — even when [intent] is
 * [DropIntent.INVALID], so the shadow can be painted red *there*. "Finger over no target at all" is a
 * different thing: it is a `null` plan (no shadow), not an INVALID plan.
 *
 * @property footprint the target cell the dragged item would occupy.
 * @property intent what the drop does — and therefore how the shadow reads.
 * @property moves occupants a push would displace, at their new placements; empty unless [intent] is
 *   [DropIntent.PUSH].
 */
data class PlacementPlan(
    val footprint: GridPlacement,
    val intent: DropIntent,
    val moves: Map<GridItem, GridPlacement> = emptyMap(),
)
