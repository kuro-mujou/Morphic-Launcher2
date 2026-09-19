package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan

/**
 * A zone's only window onto placement logic: "if the drag landed here, what would happen?" It is a **port** —
 * supplied, not imported — so the drag UI stays testable with a fake and carries no dependency on `data:layout`
 * (the inversion in docs/DRAG_AND_DROP_DESIGN.md §3). The drag UI defines this interface; the engine-backed
 * implementation is supplied up in `feature:home`.
 *
 * The real implementation maps `itemCenterInRoot` to a cell using its zone's geometry, reads that zone's current
 * occupants, and calls the placement engine (e.g. `FreeGridPlanner`). **A planner about cells reads the item's
 * centre; one about a pointed-at target (a card, a tab, a band) reads the finger** — see [DragSession] for why the
 * two differ. The proxy is drawn around the same centre, so a cell planner that read the finger instead would put
 * the drop shadow as far from the carried item as the press was from its middle. Returning `null` means the point
 * is inside the zone but not over any droppable target — for example an empty gap left mid-reflow — which reads
 * as "no shadow" rather than an invalid drop.
 *
 * **It hangs off [DropZone], not off the coordinator.** It used to take the zone as a parameter, because one
 * coordinator held one planner and that planner had to dispatch on the zone id — a `when` every multi-zone surface
 * repeated, and one that could not span surfaces at all. Now that a single coordinator is rooted above *every*
 * surface (the design doc's §2 architecture, finally taken literally), a per-coordinator planner would have to know
 * about home's grids, the APPS pager and an open folder at once. So the planner travels with the zone that answers
 * it, and the zone parameter goes: a planner is only ever asked about its own zone.
 */
fun interface DropPlanner {
    fun plan(item: GridItem, fingerInRoot: Offset, itemCenterInRoot: Offset): PlacementPlan?
}
