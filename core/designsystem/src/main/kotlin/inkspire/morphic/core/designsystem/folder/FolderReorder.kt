package inkspire.morphic.core.designsystem.folder

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import kotlin.math.floor

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
 * The plan a folder zone reports for **every** hover it accepts: droppable, with a *meaningless* footprint.
 *
 * A folder previews a reorder by reflowing its own cells around the gap, so there is no target cell for a drop
 * shadow to name — but `PlacementPlan.footprint` is non-null by design (it always names the cell the shadow paints
 * at). This value is therefore a token: "the folder handled this hover", nothing more. The consequence for other
 * surfaces on the shared coordinator is that they must not paint from a plan whose active zone isn't their own.
 *
 * Rejected alternative: a `DropIntent.REORDER` value, which would make "no shadow" representable in the model.
 * `DropFootprint` switches exhaustively on the intent, so a new value would force a paint-nothing branch into a
 * component whose whole contract is to paint. Worth revisiting when a second reflow-preview zone exists (the APPS
 * pager, the dock) and there are two consumers to shape it — until then it is one file's concern.
 */
val FolderReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.PLACE)

/*
 * MovingGap reorder for the folder — the dense 1-D flow the folder uses (see the arrangement model). The folder
 * is one ordered list; dragging migrates a *gap* through it and the flow densifies on drop, re-chunked across
 * pages. Pure list/geometry maths (no data:layout), ported from the dev harness's OrderedSurface but with the
 * folder's simplification: no in-folder merge, so a cell splits left/right (insert before/after) with no centre
 * merge ring.
 */

/** The finger's horizontal position within its hovered cell, in `0f..1f` (`< 0.5` → left half). */
internal fun GridGeometry.cellFractionX(fingerInRoot: Offset): Float {
    val local = (fingerInRoot.x - originInRoot.x) / cellW
    return local - floor(local)
}

/**
 * The new gap — an insertion index into [order] *minus* the dragged item — for a finger over display slot
 * [flatSlot]. [currentGap] is the live gap (`< 0` seeds it from the dragged item's own position); [leftHalf]
 * picks the side of the hovered item to insert on.
 */
internal fun movingGap(
    order: List<ComponentKey>,
    dragged: ComponentKey,
    currentGap: Int,
    flatSlot: Int,
    leftHalf: Boolean,
): Int {
    val others = order.filter { it != dragged }
    var gap = (if (currentGap < 0) order.indexOf(dragged) else currentGap).coerceIn(0, others.size)
    when {
        flatSlot > others.size -> gap = others.size // past the last item → append
        flatSlot == gap -> Unit // already over the gap → no change
        else -> {
            val hovered = if (flatSlot < gap) flatSlot else flatSlot - 1 // others-index under the finger
            if (hovered in others.indices) gap = if (leftHalf) hovered else hovered + 1
        }
    }
    return gap
}

/**
 * [order] rendered with [dragged] lifted to gap index [gap], the others densified around it — this is both the
 * live display order (dragged drawn invisible in its slot) and, on drop, the committed new order. With no drag
 * ([dragged] null) it is just [order].
 */
internal fun movingGapDisplayOrder(order: List<ComponentKey>, dragged: ComponentKey?, gap: Int): List<ComponentKey> {
    if (dragged == null) return order
    val others = order.filter { it != dragged }
    val at = gap.coerceIn(0, others.size)
    return others.take(at) + dragged + others.drop(at)
}
