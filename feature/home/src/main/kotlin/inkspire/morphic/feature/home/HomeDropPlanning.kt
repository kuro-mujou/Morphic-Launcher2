package inkspire.morphic.feature.home

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.Cell
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.GridSpan
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.data.layout.FreeGridPlanner
import inkspire.morphic.data.layout.PushDirection
import kotlin.math.abs

/*
 * Home drop-planning: the cell-partition maths that turns a finger position over an occupant into a merge or a
 * directional push, and the one planner ([planCoordinateDrop]) that every free-placement zone on HOME runs. They
 * live in feature:home because they bridge GridGeometry (core:designsystem) and PushDirection (data:layout) —
 * neither of which depends on the other. The dev harness keeps its own copy.
 */

/**
 * Plans a drop of [item] at [fingerInRoot] on **any** free-placement zone of HOME — the pager's main area, the
 * dock, and (later) the widget area all resolve a hover the same way, differing only in the arguments below.
 *
 * Having one function rather than one per zone is deliberate. L1 grew a `resolveDockDrop` that was a near-copy of
 * its home resolver, and the copies drifted; the whole point of the coordinate primitives is that a zone is
 * described by *data* (its geometry, its dimensions, its occupants) and not by its own algorithm.
 *
 * The rule, unchanged from the single-zone version:
 * - the footprint is [span] logical cells, snapped to the **logical** lattice;
 * - if the finger is over an occupant, that occupant's cell partitions into a center merge ring plus four push
 *   triangles — the ring merges into a folder, a triangle picks which way the occupant is shoved;
 * - otherwise the free-grid engine pushes whatever the footprint lands on.
 *
 * **The lattice it snaps to is the whole point of `cellMultiplier`, and this used to get it wrong.** A home grid is
 * declared sub-divided — 4×5 visual cells at `cellMultiplier = 2` really is a 8×10 logical grid, and an app is a
 * 2×2 logical footprint rather than a 1×1 one. The user is never shown that: they see 4×5 cells holding one icon
 * each. What the subdivision buys is that an icon can come to rest **straddling** two visual cells — its top-left
 * on any logical cell, so the offsets between the visible cells are reachable — which is the whole reason a
 * launcher subdivides at all. Passing `step = span` here rounded the top-left back onto the visual lattice, which
 * made a grid declared at `cellMultiplier = 2` behave in every observable way like one declared at 1: the
 * subdivision cost twice the occupancy bookkeeping and bought nothing. `step = 1` is what L1 does — it resolves
 * the hovered cell at logical granularity and centers the footprint on it.
 *
 * @param geo the zone's measured geometry, as published by its grid — the same cells the user can see.
 * @param config the zone's logical dimensions; `cellMultiplier` is the visual-cell span.
 * @param page the page the drop lands on — the pager's current page, or 0 for a single (non-paged) zone.
 * @param span the dragged item's footprint. **Not derivable here**, and assuming one visual cell is what broke
 *   widgets: an app and a folder are always one, but a widget is whatever size its provider asked for, so the
 *   plan drew a 1×1 shadow and the `Move` it produced resized the widget to match on drop. The caller reads it
 *   from the item's own placement, which is the only place that knows.
 * @param occupants the zone's items *excluding* [item], already restricted to [page].
 * @return the plan the live drop shadow and the eventual commit both read, or null when there is nothing to plan.
 */
internal fun planCoordinateDrop(
    geo: GridGeometry,
    config: GridConfig,
    page: Int,
    occupants: Map<GridItem, GridPlacement>,
    item: GridItem,
    span: GridSpan,
    fingerInRoot: Offset,
): PlacementPlan {
    // Whatever size the item is, free to land on any logical cell in *position* — see
    // [GridGeometry.snapTopLeftCell] for why the lattice is the logical one rather than the visual one.
    val topLeft = geo.snapTopLeftCell(fingerInRoot, colSpan = span.colSpan, rowSpan = span.rowSpan)
    val footprint = GridPlacement(page, topLeft.row, topLeft.col, rowSpan = span.rowSpan, colSpan = span.colSpan)

    val target = geo.cellAt(fingerInRoot)?.let { cell -> occupants.entries.firstOrNull { it.value.covers(cell) } }
        ?: return FreeGridPlanner.plan(footprint, occupants, config)

    if (canMerge(item, target.key) && geo.inMergeRingOf(fingerInRoot, target.value)) {
        return FreeGridPlanner.plan(target.value, occupants, config, merge = true)
    }
    return FreeGridPlanner.plan(footprint, occupants, config, geo.pushDirectionInRect(fingerInRoot, target.value))
}

/** Merge-ring radius as a fraction of the target's smaller side; inside it a drop combines rather than pushes. */
private const val MERGE_INNER_RADIUS = 0.3f

/**
 * Whether the dragged item may combine onto [target] — never onto itself, and otherwise decided by what the
 * target can *hold*.
 *
 * A `when` over the target rather than a boolean expression, so each holder states its own rule and a new
 * [GridItem] kind fails to compile until it says whether anything may be dropped into it.
 *
 * - An **app or a folder** takes only an app: dropping one app on another makes a folder, and dropping one into a
 *   folder adds it. Folder-on-app is still refused, because folders do not nest.
 * - An **icon container** takes either, which is the whole of what makes it fillable by drag — it is the one
 *   holder whose contents are `IconItem`, i.e. exactly "app or folder".
 * - Neither kind of **widget** takes anything from this surface. A widget container holds widgets, and merging a
 *   widget into one is the drag that is still to come; nothing an icon drag is carrying can go there. Returning
 *   false is what makes the drop fall through to an ordinary push, which is the honest outcome — the finger is
 *   over something that cannot receive it.
 */
internal fun canMerge(dragged: GridItem, target: GridItem): Boolean {
    if (dragged == target) return false
    return when (target) {
        is GridItem.App, is GridItem.Folder -> dragged is GridItem.App
        is GridItem.IconContainer -> dragged is GridItem.App || dragged is GridItem.Folder
        is GridItem.Widget, is GridItem.WidgetContainer -> false
    }
}

/** True when [cell] falls inside this placement's rectangle. */
internal fun GridPlacement.covers(cell: Cell): Boolean =
    cell.row in row until rowEndExclusive && cell.col in col until colEndExclusive

/** Radius (px) of the merge ring at the center of the item occupying [rect], scaled by its smaller side. */
private fun GridGeometry.mergeRadius(rect: GridPlacement): Float =
    MERGE_INNER_RADIUS * minOf(rect.colSpan * cellW, rect.rowSpan * cellH)

/** True when the finger sits in the inner merge ring of the item occupying [rect] — one circle at its center. */
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
