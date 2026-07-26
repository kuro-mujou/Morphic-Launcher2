package inkspire.morphic.core.designsystem.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.roundToInt

/**
 * A responsive, free-placement grid: it places each child at the exact `(row, col)` it declares via
 * [LauncherGridScope.gridPlacement], sized to its span, in a cell area derived from the *measured* viewport.
 *
 * **What it is — and deliberately isn't.** This is the one dumb coordinate layout every grid surface reuses.
 * It knows nothing about lists, ordering, reflow, or paging: it takes spans at coordinates and places them.
 * The differences between surfaces (home keeps stored coordinates; a folder or the APPS pager reflow an ordered
 * list into coordinates, per page) live in the caller that produces the [GridPlacement]s — not here. That
 * separation is what lets home, dock, folders and the pager share this single component.
 *
 * **Free placement, not packing.** Unlike a first-fit/auto-packing grid (the `MosaicGrid` reference), placements
 * are honoured verbatim — coordinates are exact and **gaps are allowed** (an empty cell stays empty). The caller
 * owns collision-freedom; the grid trusts its input.
 *
 * **Sizing (FIXED_PAGER).** Both axes are bounded: cell width = viewport width ÷ [GridConfig.cols], cell height
 * = viewport height ÷ [GridConfig.rows] — so the grid fills its surface and cells are (generally non-square)
 * fractions of it, correct in any orientation because the size is measured, not fixed in dp. Cell edges are
 * rounded on a shared lattice (each child's right = `round((col+span)·cellW)`), so tiles tile seamlessly with no
 * cumulative 1px seams. This requires **bounded** constraints — a `FIXED_PAGER` grid lives inside a sized
 * viewport (a pager page, a dock strip). The scrolling `SCROLL_GRID` mode is a later addition.
 *
 * [GridConfig] here is in **logical** cells (its `cellMultiplier` sub-cells are just more columns/rows to this
 * layout); a placement's `page` is ignored — a page is one grid, and the pager decides which page's items reach
 * a given grid.
 *
 * @param config the grid's logical dimensions (rows × cols).
 * @param content the children, each tagged with [LauncherGridScope.gridPlacement].
 */
@Composable
fun LauncherGrid(
    config: GridConfig,
    modifier: Modifier = Modifier,
    content: @Composable LauncherGridScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        content = { LauncherGridScopeInstance.content() },
    ) { measurables, constraints ->
        val cols = config.cols
        val rows = config.rows
        // Fractional cell size from the measured viewport; boundaries are rounded per child (below) so the
        // integer cell edges share one lattice and tiles meet without gaps.
        val cellW = constraints.maxWidth.toFloat() / cols
        val cellH = constraints.maxHeight.toFloat() / rows

        val placed = measurables.map { measurable ->
            val placement = measurable.parentData as? GridPlacement ?: DefaultPlacement
            val left = (placement.col * cellW).roundToInt()
            val top = (placement.row * cellH).roundToInt()
            val right = (placement.colEndExclusive * cellW).roundToInt()
            val bottom = (placement.rowEndExclusive * cellH).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(right - left, bottom - top))
            PlacedChild(placeable, left, top)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { it.placeable.placeRelative(it.x, it.y) }
        }
    }
}

/** A measured child paired with the top-left px it goes at. */
private class PlacedChild(val placeable: androidx.compose.ui.layout.Placeable, val x: Int, val y: Int)

/** Fallback when a child forgot [LauncherGridScope.gridPlacement]: a 1×1 tile at the origin. */
private val DefaultPlacement = GridPlacement(page = 0, row = 0, col = 0)

/**
 * Receiver scope for [LauncherGrid]'s content — the only way to tag a child with its [GridPlacement], so a
 * placement can't be attached to anything but a grid child.
 */
@Stable
interface LauncherGridScope {
    /** Places this child at [placement]'s `(row, col)`, sized to its `rowSpan × colSpan`. */
    @Stable
    fun Modifier.gridPlacement(placement: GridPlacement): Modifier
}

/** The single stateless scope instance — placement travels as parent data, so the scope holds nothing. */
private object LauncherGridScopeInstance : LauncherGridScope {
    override fun Modifier.gridPlacement(placement: GridPlacement): Modifier =
        this.then(GridPlacementElement(placement))
}

/** Attaches a [GridPlacement] as parent data, read back by [LauncherGrid]'s measure policy. */
private class GridPlacementElement(
    private val placement: GridPlacement,
) : ModifierNodeElement<GridPlacementNode>() {
    override fun create() = GridPlacementNode(placement)

    override fun update(node: GridPlacementNode) {
        node.placement = placement
    }

    override fun equals(other: Any?): Boolean =
        other is GridPlacementElement && other.placement == placement

    override fun hashCode(): Int = placement.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "gridPlacement"
        properties["placement"] = placement
    }
}

private class GridPlacementNode(var placement: GridPlacement) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = placement
}
