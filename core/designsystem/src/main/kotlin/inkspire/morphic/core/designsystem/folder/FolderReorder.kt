package inkspire.morphic.core.designsystem.folder

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.ordered.movingGapDisplayOrder
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan

/**
 * The open folder's drag hooks, published to the shared `DragCoordinator`'s owner (the home) while the folder
 * is open. The home runs one coordinator over both surfaces; its zone-dispatching planner and drop route the
 * folder zone here, so the folder's MovingGap logic stays inside the overlay (its order/gap/geometry aren't
 * hoisted). This is what lets a drag started in the folder continue as the *same* session onto home.
 */
interface FolderDragDelegate {
    /**
     * The drag moved to [fingerInRoot] over the folder zone: **advance the reorder gap** toward it and report
     * that the folder accepts a drop here ([FolderReorderPlan]), or null if it can't take [item] at all.
     *
     * Unlike the `DropPlanner` it is reached through, this is a **command, not a query** — calling it changes the
     * folder's gap, which is the reorder preview. Hence `onHover` rather than `plan`: it is safe only because the
     * coordinator calls it exactly once per finger move, and a name that promised purity would invite a
     * speculative second call that silently desynchronises the gap.
     */
    fun onHover(item: GridItem, fingerInRoot: Offset): PlacementPlan?

    /** Commit the current reorder — the drop landed on the folder zone with [item] as the dragged app. */
    fun commitReorder(item: GridItem)
}

/**
 * The plan a folder zone reports for **every** hover it accepts: droppable, and explicitly *painting nothing*.
 *
 * A folder previews a reorder by reflowing its own cells around the gap ([movingGapDisplayOrder]), so there is no
 * target cell for a drop shadow to name. [DropIntent.REORDER] says exactly that, and the footprint below is
 * therefore unread — `DropFootprint` returns early on this intent.
 *
 * It used to be a `PLACE` plan whose footprint was a deliberate lie, with a rule attached that other surfaces on
 * the shared coordinator must not paint from a foreign zone's plan (an unguarded one drew a shadow at cell (0,0)
 * behind the folder). The intent was parked until *"a second reflow-preview zone exists (the APPS pager, the
 * dock) and there are two consumers to shape it"* — the APPS pager is that consumer, so it now exists and the
 * lie is gone. Surfaces should still not paint another zone's plan; they just no longer *have* to for this.
 */
val FolderReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)
