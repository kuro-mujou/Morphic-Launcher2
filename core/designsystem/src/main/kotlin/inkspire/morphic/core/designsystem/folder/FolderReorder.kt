package inkspire.morphic.core.designsystem.folder

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan
import kotlin.math.floor

/**
 * The open folder's drag hooks, published to the shared `DragCoordinator`'s owner (the home) while the folder
 * is open. The home runs one coordinator over both surfaces; its zone-dispatching planner and drop route the
 * folder zone here, so the folder's MovingGap logic stays inside the overlay (its order/gap/geometry aren't
 * hoisted). This is what lets a drag started in the folder continue as the *same* session onto home.
 */
interface FolderDragDelegate {
    /** Plan a hover over the folder zone: migrate the reorder gap toward the finger (the cell reflow is the
     *  preview, so there is no drop-shadow footprint — a placeholder placement is returned). */
    fun plan(item: GridItem, fingerInRoot: Offset): PlacementPlan?

    /** Commit the current reorder — the drop landed on the folder zone with [item] as the dragged app. */
    fun commitReorder(item: GridItem)
}

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
