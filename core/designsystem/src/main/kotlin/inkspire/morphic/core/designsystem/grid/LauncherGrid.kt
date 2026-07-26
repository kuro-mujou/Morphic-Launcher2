package inkspire.morphic.core.designsystem.grid

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
 * **Two sizing modes, selected by [cellHeight].** Cell *width* is always a fraction of the (bounded) viewport
 * width — `viewport ÷ [GridConfig.cols]`. What differs is the height:
 * - **FIXED_PAGER** ([cellHeight] `null`): height is also measured — cell height = viewport height ÷
 *   [GridConfig.rows]. The grid fills its surface with (generally non-square) cells, so it needs **bounded**
 *   constraints — a pager page, a dock strip. [GridConfig.rows] sets the row count.
 * - **SCROLL_GRID** ([cellHeight] set): cell height is a **fixed dp** and the grid's total height **grows with
 *   its content** (the lowest occupied row × [cellHeight]), so it can exceed the viewport. Host it in a
 *   `verticalScroll` and the overflow scrolls — this is a per-category page of the APPS pager. Row count is
 *   whatever the placements reach; [GridConfig.rows] is unused.
 *
 * Either way, cell edges are rounded on a shared lattice (each child's right = `round((col+span)·cellW)`), so
 * tiles tile seamlessly with no cumulative 1px seams.
 *
 * [GridConfig] here is in **logical** cells (its `cellMultiplier` sub-cells are just more columns/rows to this
 * layout); a placement's `page` is ignored — a page is one grid, and the pager decides which page's items reach
 * a given grid.
 *
 * @param config the grid's logical dimensions; `rows` is used only in FIXED_PAGER.
 * @param cellHeight fixed row height that switches on SCROLL_GRID; `null` keeps FIXED_PAGER (measured height).
 * @param content the children, each tagged with [LauncherGridScope.gridPlacement].
 */
@Composable
fun LauncherGrid(
    config: GridConfig,
    modifier: Modifier = Modifier,
    cellHeight: Dp? = null,
    content: @Composable LauncherGridScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        content = { LauncherGridScopeImpl(config.cols).content() },
    ) { measurables, constraints ->
        val cols = config.cols
        // Cell width is always a fraction of the bounded viewport width. Height is either the same kind of
        // fraction of a bounded viewport (FIXED_PAGER) or a fixed dp that lets the grid grow (SCROLL_GRID).
        // Boundaries are rounded per child (below) so integer cell edges share one lattice and tiles meet cleanly.
        val cellW = constraints.maxWidth.toFloat() / cols
        val cellH = cellHeight?.toPx() ?: (constraints.maxHeight.toFloat() / config.rows)

        val placed = measurables.map { measurable ->
            val placement = measurable.parentData as? GridPlacement ?: DefaultPlacement
            val left = (placement.col * cellW).roundToInt()
            val top = (placement.row * cellH).roundToInt()
            val right = (placement.colEndExclusive * cellW).roundToInt()
            val bottom = (placement.rowEndExclusive * cellH).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(right - left, bottom - top))
            PlacedChild(placeable, left, top)
        }

        // SCROLL_GRID reports its content's own height (grows past the viewport, scrolled by a parent);
        // FIXED_PAGER fills the bounded viewport.
        val height = if (cellHeight != null) {
            placed.maxOfOrNull { it.y + it.placeable.height } ?: 0
        } else {
            constraints.maxHeight
        }

        layout(constraints.maxWidth, height) {
            placed.forEach { it.placeable.placeRelative(it.x, it.y) }
        }
    }
}

/** A measured child paired with the top-left px it goes at. */
private class PlacedChild(val placeable: Placeable, val x: Int, val y: Int)

/** Fallback when a child forgot [LauncherGridScope.gridPlacement]: a 1×1 tile at the origin. */
private val DefaultPlacement = GridPlacement(page = 0, row = 0, col = 0)

/**
 * Receiver scope for [LauncherGrid]'s content. Children are tagged with a placement one of three ways, and the
 * two `*Items` helpers **name the surface's placement strategy** so a call site tells you which it uses:
 * - [coordinateItems] — **coordinate** strategy: you give each item an explicit [GridPlacement]. For
 *   free-arranged surfaces (home, dock, widgets) whose positions are stored data, not derivable from order.
 * - [flowItems] — **flow** strategy: items are laid left→right, top→bottom in list order (like
 *   `LazyVerticalGrid`). For surfaces whose positions come from order (APPS grid, folders).
 * - [gridPlacement] — the raw per-child modifier the two helpers are built on; reach for it directly only when
 *   neither strategy fits.
 */
@Stable
interface LauncherGridScope {
    /** The grid's column count — the width [flowItems] wraps a row at. */
    val cols: Int

    /** Places this child at [placement]'s `(row, col)`, sized to its `rowSpan × colSpan`. */
    @Stable
    fun Modifier.gridPlacement(placement: GridPlacement): Modifier
}

/**
 * **Coordinate** placement strategy: render [items], giving each the explicit [placement] you compute. For
 * user-arranged surfaces (home, dock, widgets), where positions are data, not a function of order.
 *
 * [itemContent] receives the item and a `Modifier` already carrying its placement — apply it to your cell, then
 * layer whatever else the surface needs (gestures, [animatePlacement], alpha). Each item is wrapped in a
 * `key`, defaulting to the item itself, for stable identity across placement changes.
 */
@SuppressLint("ComposableNaming")
@Composable
fun <T> LauncherGridScope.coordinateItems(
    items: List<T>,
    itemKey: (T) -> Any? = { it },
    placement: (T) -> GridPlacement,
    itemContent: @Composable (item: T, Modifier) -> Unit,
) {
    items.forEach { item ->
        key(itemKey(item)) { itemContent(item, Modifier.gridPlacement(placement(item))) }
    }
}

/**
 * **Flow** placement strategy: render [items] laid out left→right, top→bottom in list order, wrapping every
 * [cols] cells (row-major, like `LazyVerticalGrid`). For surfaces whose positions come from order (APPS grid,
 * folders) — the caller feeds the list and the grid positions it, no index maths.
 *
 * [itemContent] receives the item and a `Modifier` already carrying its flowed placement. Pagination and any
 * reorder gap are handled *before* this by choosing the list you pass (e.g. one page's slice, in gap order).
 */
@SuppressLint("ComposableNaming")
@Composable
fun <T> LauncherGridScope.flowItems(
    items: List<T>,
    itemKey: (T) -> Any? = { it },
    itemContent: @Composable (item: T, Modifier) -> Unit,
) {
    items.forEachIndexed { index, item ->
        val placement = GridPlacement(page = 0, row = index / cols, col = index % cols)
        key(itemKey(item)) { itemContent(item, Modifier.gridPlacement(placement)) }
    }
}

/** The scope instance — carries the column count; placement itself travels as parent data. */
private class LauncherGridScopeImpl(override val cols: Int) : LauncherGridScope {
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
