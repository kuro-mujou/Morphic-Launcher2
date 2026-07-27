package inkspire.morphic.core.designsystem.folder

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.model.ComponentKey
import kotlin.math.floor

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
