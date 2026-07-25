package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan

/**
 * The coordinator's only window onto placement logic: "if the drag landed here, what would happen?" It is a
 * **port** — injected, not imported — so the drag UI stays testable with a fake and carries no dependency on
 * `data:layout` (the inversion in docs/DRAG_AND_DROP_DESIGN.md §3). The drag UI defines this interface; the
 * engine-backed implementation is supplied up in `feature:home`.
 *
 * The real implementation maps [fingerInRoot] to a cell using [zone]'s geometry, reads that zone's current
 * occupants, and calls the placement engine (e.g. `FreeGridPlanner`). Returning `null` means the finger is
 * inside the zone but not over any droppable target — for example an empty gap left mid-reflow — which reads
 * as "no shadow" rather than an invalid drop.
 */
fun interface DropPlanner {
    fun plan(zone: DropZone, item: GridItem, fingerInRoot: Offset): PlacementPlan?
}
