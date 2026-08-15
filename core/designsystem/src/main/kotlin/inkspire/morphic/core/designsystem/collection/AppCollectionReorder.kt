package inkspire.morphic.core.designsystem.collection

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.ordered.movingGapDisplayOrder
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan

/**
 * The open collection's drag hooks — what its own `DropZone` binds its planner and its drop to, so the collection's
 * MovingGap logic stays inside the overlay (its order, gap and geometry are never hoisted). This is what lets a drag
 * started inside a collection continue as the *same* session onto the surface beneath.
 *
 * **It used to be published to the host surface**, which held it in a `mutableStateOf` and routed the collection's
 * zone to it from a `when (zone.id)` in its own planner and drop — three surfaces each carrying the same pair of
 * branches, plus a documented construction-order squeeze (the holder had to exist before the coordinator, which had to
 * exist before the collection host). All of that was one statement said in the wrong place: *this zone's behavior is
 * the collection's*. A zone carries its own behavior now, so the interface stays and the hand-off goes.
 */
interface AppCollectionDragDelegate {
    /**
     * The drag moved to [fingerInRoot] over the collection's zone: **advance the reorder gap** toward it and report
     * that the collection accepts a drop here ([AppCollectionReorderPlan]), or null if it can't take [item] at all.
     *
     * Unlike the `DropPlanner` it backs, this is a **command, not a query** — calling it changes the
     * collection's gap, which is the reorder preview. Hence `onHover` rather than `plan`: it is safe only because the
     * coordinator calls it exactly once per finger move, and a name that promised purity would invite a
     * speculative second call that silently desynchronizes the gap.
     */
    fun onHover(item: GridItem, fingerInRoot: Offset): PlacementPlan?

    /** Commit the current reorder — the drop landed on the collection's zone with [item] as the dragged app. */
    fun commitReorder(item: GridItem)
}

/**
 * The plan a collection's zone reports for **every** hover it accepts: droppable, and explicitly *painting nothing*.
 *
 * A collection previews a reorder by reflowing its own cells around the gap ([movingGapDisplayOrder]), so there is no
 * target cell for a drop shadow to name. [DropIntent.REORDER] says exactly that, and the footprint below is
 * therefore unread — `DropFootprint` returns early on this intent.
 *
 * It used to be a `PLACE` plan whose footprint was a deliberate lie, with a rule attached that other surfaces on
 * the shared coordinator must not paint from a foreign zone's plan (an unguarded one drew a shadow at cell (0,0)
 * behind the open collection). The intent was parked until *"a second reflow-preview zone exists (the APPS pager, the
 * dock) and there are two consumers to shape it"* — the APPS pager is that consumer, so it now exists and the
 * lie is gone. Surfaces should still not paint another zone's plan; they just no longer *have* to for this.
 */
val AppCollectionReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)
