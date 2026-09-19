package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One grip on a resize frame, named by the edges it moves.
 *
 * **Two, on opposite corners** — between them they move every edge, so the item can grow or shrink in any
 * direction, and a drag along one axis changes only that axis because each edge snaps to its nearest cell boundary
 * (the finger starts on the other axis's boundary and stays there). Growing toward a corner without a grip — up and
 * to the right — takes one drag on each. Encoding a grip as *which edges move* rather than as a position is what
 * lets [resizedPlacement] be one expression.
 */
enum class ResizeHandle(
    val movesLeft: Boolean,
    val movesTop: Boolean,
    val movesRight: Boolean,
    val movesBottom: Boolean,
) {
    TOP_LEFT(movesLeft = true, movesTop = true, movesRight = false, movesBottom = false),
    BOTTOM_RIGHT(movesLeft = false, movesTop = false, movesRight = true, movesBottom = true),
}

/**
 * What a resize is allowed to change — **the caller's policy**, not something the geometry could work out.
 *
 * Which axes may move and how small the item may get are decisions above this layer, so they are passed in. The
 * launcher's current answer for widgets is "both axes, always": a provider's `resizeMode` is deliberately not
 * honored, because providers under-declare it constantly (see `AppWidgetResizeRules` in `data:appwidgets`). The
 * single-axis case is still expressible — both grips are offered and [resizedPlacement] moves only the permitted
 * axis — so the policy can change without this changing.
 *
 * @property minColSpan the smallest width in logical cells, at least 1.
 */
data class ResizeBounds(
    val horizontal: Boolean,
    val vertical: Boolean,
    val minColSpan: Int,
    val minRowSpan: Int,
)

/** The handles worth drawing: both, unless [bounds] permits no axis at all — a frame with nothing to grab. */
fun handlesFor(bounds: ResizeBounds): List<ResizeHandle> =
    if (bounds.horizontal || bounds.vertical) ResizeHandle.entries else emptyList()

/**
 * Where a handle's center sits within the frame `[left, top, right, bottom]`: [inset] px in from the corner on
 * each axis. For a grip drawn along a rounded corner of radius `r`, the midpoint of that arc is `r × (1 − 1/√2)` in
 * — see [cornerArcInset].
 */
fun handleCenter(
    handle: ResizeHandle,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    inset: Float,
): Offset = Offset(
    x = if (handle.movesLeft) left + inset else right - inset,
    y = if (handle.movesTop) top + inset else bottom - inset,
)

/** How far the midpoint of a rounded corner of [radius] sits in from the rectangle's own corner, on each axis. */
fun cornerArcInset(radius: Float): Float = radius * (1f - 1f / sqrt(2f))

/**
 * The placement [handle] would give [base] with the finger at [localFinger] (grid-local pixels).
 *
 * Only the edges the handle moves are recomputed, each snapped to the nearest cell boundary; the opposite edges
 * stay exactly where they were, which is what makes a resize feel like dragging *an edge* rather than moving the
 * item. Spans are held at or above the minimum so an edge cannot be dragged through its opposite.
 *
 * **The growing edge is deliberately not clamped to the grid.** The result may overshoot, and the caller wants
 * that: [clampToGrid] gives the placement that would actually be committed, and the difference between the two is
 * how the overlay knows to draw the frame as refused. Clamping here would make an over-drag indistinguishable
 * from a legal one that happens to end at the edge.
 */
fun resizedPlacement(
    base: GridPlacement,
    handle: ResizeHandle,
    localFinger: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float,
    bounds: ResizeBounds,
): GridPlacement {
    var left = base.col
    var top = base.row
    var right = base.colEndExclusive
    var bottom = base.rowEndExclusive

    if (bounds.horizontal && cellWidthPx > 0f) {
        val atCell = (localFinger.x / cellWidthPx).roundToInt()
        if (handle.movesRight) right = atCell.coerceAtLeast(left + bounds.minColSpan)
        if (handle.movesLeft) left = atCell.coerceIn(0, right - bounds.minColSpan)
    }
    if (bounds.vertical && cellHeightPx > 0f) {
        val atCell = (localFinger.y / cellHeightPx).roundToInt()
        if (handle.movesBottom) bottom = atCell.coerceAtLeast(top + bounds.minRowSpan)
        if (handle.movesTop) top = atCell.coerceIn(0, bottom - bounds.minRowSpan)
    }

    return GridPlacement(base.page, row = top, col = left, rowSpan = bottom - top, colSpan = right - left)
}

/** [rect] pulled back inside [config] — the largest part of it the grid can actually hold. */
fun clampToGrid(rect: GridPlacement, config: GridConfig): GridPlacement {
    val left = rect.col.coerceIn(0, (config.cols - 1).coerceAtLeast(0))
    val top = rect.row.coerceIn(0, (config.rows - 1).coerceAtLeast(0))
    return GridPlacement(
        page = rect.page,
        row = top,
        col = left,
        rowSpan = rect.rowEndExclusive.coerceIn(top + 1, config.rows) - top,
        colSpan = rect.colEndExclusive.coerceIn(left + 1, config.cols) - left,
    )
}
