package inkspire.morphic.core.designsystem.grid

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.roundToInt

/**
 * One grip on a resize frame, named by the edges it moves.
 *
 * Eight of them: four edges and four corners, a corner moving one edge on each axis. Encoding it as *which edges
 * move* rather than as a position is what lets [resizedPlacement] be one expression instead of eight — and what
 * makes [handlesFor] a filter rather than a table.
 */
enum class ResizeHandle(
    val movesLeft: Boolean,
    val movesTop: Boolean,
    val movesRight: Boolean,
    val movesBottom: Boolean,
) {
    LEFT(movesLeft = true, movesTop = false, movesRight = false, movesBottom = false),
    RIGHT(movesLeft = false, movesTop = false, movesRight = true, movesBottom = false),
    TOP(movesLeft = false, movesTop = true, movesRight = false, movesBottom = false),
    BOTTOM(movesLeft = false, movesTop = false, movesRight = false, movesBottom = true),
    TOP_LEFT(movesLeft = true, movesTop = true, movesRight = false, movesBottom = false),
    TOP_RIGHT(movesLeft = false, movesTop = true, movesRight = true, movesBottom = false),
    BOTTOM_LEFT(movesLeft = true, movesTop = false, movesRight = false, movesBottom = true),
    BOTTOM_RIGHT(movesLeft = false, movesTop = false, movesRight = true, movesBottom = true);

    /** True for the four that move an edge on each axis. They are drawn differently — a corner, not a pill. */
    val isCorner: Boolean get() = (movesLeft || movesRight) && (movesTop || movesBottom)
}

/**
 * What a resize is allowed to change — **the caller's policy**, not something the geometry could work out.
 *
 * Which axes may move and how small the item may get are decisions above this layer, so they are passed in. The
 * launcher's current answer for widgets is "both axes, always": a provider's `resizeMode` is deliberately not
 * honoured, because providers under-declare it constantly (see `WidgetResizeRules` in `data:widgets`). The
 * single-axis case is still expressible, and drawn correctly — a `horizontal = false` frame shows two pills and no
 * corners — so the policy can change without this changing.
 *
 * @property minColSpan the smallest width in logical cells, at least 1.
 */
data class ResizeBounds(
    val horizontal: Boolean,
    val vertical: Boolean,
    val minColSpan: Int,
    val minRowSpan: Int,
)

/** The handles worth drawing: every handle whose moved axes are all permitted by [bounds]. */
fun handlesFor(bounds: ResizeBounds): List<ResizeHandle> = ResizeHandle.entries.filter { handle ->
    val horizontalOk = !(handle.movesLeft || handle.movesRight) || bounds.horizontal
    val verticalOk = !(handle.movesTop || handle.movesBottom) || bounds.vertical
    horizontalOk && verticalOk
}

/**
 * Where a handle's centre sits within the frame `[left, top, right, bottom]`, pulled [inset] px *inward*.
 *
 * Inward rather than on the edge for two reasons L1 found the hard way: the frame is clipped, so a glyph centred
 * on the boundary loses half its touch target, and a handle on the screen's own edge competes with the system's
 * back gesture.
 */
fun handleCentre(
    handle: ResizeHandle,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    inset: Float,
): Offset = Offset(
    x = when {
        handle.movesLeft -> left + inset
        handle.movesRight -> right - inset
        else -> (left + right) / 2f
    },
    y = when {
        handle.movesTop -> top + inset
        handle.movesBottom -> bottom - inset
        else -> (top + bottom) / 2f
    },
)

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
