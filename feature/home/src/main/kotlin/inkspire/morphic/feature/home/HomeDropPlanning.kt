package inkspire.morphic.feature.home

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.Cell
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.data.layout.PushDirection
import kotlin.math.abs

/*
 * Home drop-planning helpers: the cell-partition maths that turns a finger position over an occupant into a
 * merge or a directional push. They live in feature:home because they bridge GridGeometry (core:designsystem)
 * and PushDirection (data:layout) — neither of which depends on the other. The dev harness keeps its own copy;
 * a shared home-planner is a later extraction if a second surface needs it.
 */

/** Merge-ring radius as a fraction of the target's smaller side; inside it a drop combines rather than pushes. */
private const val MERGE_INNER_RADIUS = 0.3f

/**
 * Whether the dragged item may combine onto [target]: for now only an **app**, onto another app or a folder,
 * and never onto itself. Folder-on-app, widget, and container merges arrive with those item types.
 */
internal fun canMerge(dragged: GridItem, target: GridItem): Boolean =
    dragged is GridItem.App && dragged != target && (target is GridItem.App || target is GridItem.Folder)

/** True when [cell] falls inside this placement's rectangle. */
internal fun GridPlacement.covers(cell: Cell): Boolean =
    cell.row in row until rowEndExclusive && cell.col in col until colEndExclusive

/** Radius (px) of the merge ring at the centre of the item occupying [rect], scaled by its smaller side. */
private fun GridGeometry.mergeRadius(rect: GridPlacement): Float =
    MERGE_INNER_RADIUS * minOf(rect.colSpan * cellW, rect.rowSpan * cellH)

/** True when the finger sits in the inner merge ring of the item occupying [rect] — one circle at its centre. */
internal fun GridGeometry.inMergeRingOf(fingerInRoot: Offset, rect: GridPlacement): Boolean {
    val dx = fingerInRoot.x - (originInRoot.x + (rect.col + rect.colSpan / 2f) * cellW)
    val dy = fingerInRoot.y - (originInRoot.y + (rect.row + rect.rowSpan / 2f) * cellH)
    val radius = mergeRadius(rect)
    return dx * dx + dy * dy < radius * radius
}

/**
 * Which way to push the item occupying [rect], from where the finger sits within its rectangle: the rectangle
 * is split into four triangles by its diagonals and the occupant is shoved away from the nearest edge — finger
 * in the left triangle pushes it right, top pushes down, and so on.
 */
internal fun GridGeometry.pushDirectionInRect(fingerInRoot: Offset, rect: GridPlacement): PushDirection {
    val fx = (fingerInRoot.x - (originInRoot.x + rect.col * cellW)) / (rect.colSpan * cellW) - 0.5f
    val fy = (fingerInRoot.y - (originInRoot.y + rect.row * cellH)) / (rect.rowSpan * cellH) - 0.5f
    return if (abs(fx) > abs(fy)) {
        if (fx < 0f) PushDirection.RIGHT else PushDirection.LEFT
    } else {
        if (fy < 0f) PushDirection.DOWN else PushDirection.UP
    }
}
