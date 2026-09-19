package inkspire.morphic.core.designsystem.grid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.surface.LockSurfaceGesture
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** The outline is quieter than its grips, so the grips read as the things to take hold of. */
private const val OutlineAlpha = 0.6f

/**
 * **The resize frame** — an outline with two grips around one placed item, dragged to change its span.
 *
 * A full-screen `Canvas` above the surface, drawn at the item's own cells: one rounded outline, and a thick grip
 * wrapping its top-left and bottom-right corners ([handlesFor]). Dragging a grip re-reports a candidate placement
 * every frame; releasing commits it. The geometry lives in [ResizeHandles] so it can be unit-tested apart from the
 * drawing.
 *
 * **The outline traces the cells, not the item's own shape.** Items draw inside their cells with insets and corners
 * of their own, and a widget's outline need not be a rectangle at all, so hugging one would have to be told a shape
 * and would be wrong for any that has none. The cell rectangle is what a resize actually changes, and one fixed
 * corner radius for every item follows from that.
 *
 * **The grips sit on the corners, so they are excluded from the system's gestures.** A top-left grip on an item at
 * the grid's edge is inside the back-gesture band, where the platform would take the drag; exclusion rectangles are
 * the platform's own answer, and they cover the grips' touch targets and nothing more.
 *
 * **A commit does not end the frame — only a press outside it does.** Resizing is an adjustment made in several
 * drags (widen, then shorten, then nudge the other edge), and dismissing on the first release makes every one of
 * them cost another long-press and another menu. So the frame lives until the user says they are finished, which
 * is a press on anything that is not the item: a press *inside* the frame that misses a grip is swallowed and
 * changes nothing, since the item under it is exactly what the user is still working on.
 *
 * **Everything is drawn from [placement], which the caller owns.** The overlay reports and does not remember:
 * the host clamps each candidate to its grid, asks its planner whether the occupants in the way can be pushed
 * clear, and hands back only a rectangle it would commit — so the frame the user sees is always the outcome,
 * never a hopeful preview of one, and the surface beneath can show that outcome at the same time (home previews
 * the push live under the frame).
 *
 * **It is modal in two different ways, because two different things would otherwise take the gesture.**
 * - It **consumes every event it sees**, including the press that dismisses it, so the cell beneath a grip
 *   cannot also launch or lift while the frame is up.
 * - It holds [inkspire.morphic.core.designsystem.surface.SurfaceGestureLock] for as long as it is composed, which
 *   is what stops the **surface swipe** taking a sideways grip drag. Consumption alone cannot: that pan runs on
 *   `PointerEventPass.Initial` and so sees the raw delta *before* this node does, claiming at the platform slop
 *   while a resize is still a few pixels in. The lock is the launcher's one answer to "something on screen owns
 *   this finger", and the widget picker and the open folder hold it for the same reason.
 *
 * **Back dismisses it, and that is not a convenience.** Its only other way out is a press *strictly outside* the
 * item's own rect. That margin shrinks as the item grows, and a press inside is swallowed by design, so a large
 * item can leave the whole surface reading as frozen. Every other modal in this launcher answers back (the open
 * collection, the bottom sheets, the context menu); this one did not, and that made it the one modal a user could
 * get stuck behind.
 *
 * @param placement the frame's current cells, in the grid [geometry] describes — **the last thing the grid
 *   accepted**, which is why a drag past what is possible leaves the frame still and merely turns it red.
 * @param refused draws the frame in the error color: the last thing [onResize] asked for could not be given.
 *   The host decides, because it is the only thing that knows whether the request left the grid or collided with
 *   occupants that cannot be pushed clear — this overlay knows nothing about either.
 * @param onResize a grip moved: here is the candidate placement, in logical cells.
 * @param onCommit the finger lifted off a grip. What to commit is whatever [onResize] last reported. The frame
 *   stays up afterwards, so the host must re-seed [placement] from what the grid actually holds now — a drag the
 *   grid refused would otherwise leave the frame describing a size nothing took.
 * @param onDismiss a press outside the frame, or back: the user is done with this item.
 */
@Composable
fun ResizeOverlay(
    placement: GridPlacement,
    geometry: GridGeometry,
    bounds: ResizeBounds,
    onResize: (GridPlacement) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    refused: Boolean = false,
) {
    // The surface swipe is not this overlay's to share — see the KDoc. Declarative, because the reason is simply
    // that the frame is on screen.
    LockSurfaceGesture(locked = true)

    // The escape that does not depend on finding a gap beside the item — see the KDoc.
    BackHandler(onBack = onDismiss)

    val colors = LocalMorphicColors.current
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { 28.dp.toPx() }
    val cornerPx = with(density) { 20.dp.toPx() }

    // Read live inside the gesture loop: a resize runs for as long as the finger is down, and the placement it
    // starts from must be the one on screen when it started rather than the one this composition captured.
    val live by rememberUpdatedState(placement)
    val liveBounds by rememberUpdatedState(bounds)
    val liveGeometry by rememberUpdatedState(geometry)
    val liveOnResize by rememberUpdatedState(onResize)
    val liveOnCommit by rememberUpdatedState(onCommit)
    val liveOnDismiss by rememberUpdatedState(onDismiss)

    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()

            val frame = liveGeometry.frameOf(live)
            val handle = handlesFor(liveBounds)
                .minByOrNull { frame.distanceTo(it, down.position, cornerPx) }
                ?.takeIf { frame.distanceTo(it, down.position, cornerPx) <= hitRadiusPx }

            if (handle == null) {
                // Missed every grip. Outside the frame that means "done"; inside it means nothing at all, because
                // the item under the finger is the one still being resized. Waiting for the release rather than
                // acting on the press is what stops a dismiss also being read as a tap on whatever is underneath.
                waitForUpOrCancellation()
                if (!frame.contains(down.position)) liveOnDismiss()
                return@awaitEachGesture
            }

            // The placement the drag is measured *from* stays fixed for the whole gesture: recomputing against the
            // live one would compound each frame's change into a runaway edge.
            val base = live
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) {
                    liveOnCommit()
                    break
                }
                liveOnResize(
                    resizedPlacement(
                        base = base,
                        handle = handle,
                        localFinger = change.position - liveGeometry.originInRoot,
                        cellWidthPx = liveGeometry.cellW,
                        cellHeightPx = liveGeometry.cellH,
                        bounds = liveBounds,
                    ),
                )
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        val frame = geometry.frameOf(placement)
        val handles = handlesFor(bounds)
        GripExclusions(frame, handles, cornerPx, hitRadiusPx)
        val color = if (refused) colors.error else colors.content
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(gestures),
        ) {
            val radius = frame.cornerRadius(cornerPx)
            drawRoundRect(
                color = color.copy(alpha = if (refused) 1f else OutlineAlpha),
                topLeft = Offset(frame.left, frame.top),
                size = Size(frame.width, frame.height),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = 3.dp.toPx()),
            )
            handles.forEach { drawCornerGrip(it, frame, radius, color) }
        }
    }
}

/**
 * A grip: the outline's own rounded corner, traced again thick with round ends, so it reads as the corner of the
 * frame made graspable rather than as a glyph set down on it.
 */
private fun DrawScope.drawCornerGrip(handle: ResizeHandle, frame: ResizeFrame, radius: Float, color: Color) {
    val arcLeft = if (handle.movesLeft) frame.left else frame.right - radius * 2f
    val arcTop = if (handle.movesTop) frame.top else frame.bottom - radius * 2f
    drawArc(
        color = color,
        startAngle = if (handle.movesLeft) 180f else 0f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(arcLeft, arcTop),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
    )
}

/**
 * Keeps the system's edge gestures off the grips' touch targets. Each is an empty box laid over a grip, so it
 * follows the frame through layout — which is when the platform re-reads exclusion rectangles. It takes no pointer
 * input, so presses still reach the canvas beneath.
 */
@Composable
private fun GripExclusions(frame: ResizeFrame, handles: List<ResizeHandle>, cornerPx: Float, hitRadiusPx: Float) {
    val side = with(LocalDensity.current) { (hitRadiusPx * 2f).toDp() }
    handles.forEach { handle ->
        val center = frame.gripCenter(handle, cornerPx)
        Box(
            Modifier
                .offset { IntOffset((center.x - hitRadiusPx).roundToInt(), (center.y - hitRadiusPx).roundToInt()) }
                .size(side)
                .systemGestureExclusion(),
        )
    }
}

/** A placement's pixel rectangle in root coordinates — the frame the overlay draws and hit-tests. */
private data class ResizeFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** [cornerPx], reduced on a frame too small to round that much — a rounded rectangle cannot exceed a pill. */
    fun cornerRadius(cornerPx: Float): Float = min(cornerPx, min(width, height) / 2f)

    /** The midpoint of [handle]'s rounded corner, where its grip is drawn and hit-tested. */
    fun gripCenter(handle: ResizeHandle, cornerPx: Float): Offset =
        handleCenter(handle, left, top, right, bottom, cornerArcInset(cornerRadius(cornerPx)))

    /** Whether [point] is on the item itself — which is what tells a dismissing press from an idle one. */
    fun contains(point: Offset): Boolean =
        point.x in left..right && point.y in top..bottom

    fun distanceTo(handle: ResizeHandle, point: Offset, cornerPx: Float): Float {
        val center = gripCenter(handle, cornerPx)
        // Manhattan distance, as L1 used: it is only ever compared against itself and against a square hit
        // radius, so the square root would buy nothing.
        return abs(center.x - point.x) + abs(center.y - point.y)
    }
}

/** [placement]'s frame, rounded to whole pixels so the border does not shimmer as it is dragged. */
private fun GridGeometry.frameOf(placement: GridPlacement) = ResizeFrame(
    left = originInRoot.x + (placement.col * cellW).roundToInt(),
    top = originInRoot.y + (placement.row * cellH).roundToInt(),
    right = originInRoot.x + (placement.colEndExclusive * cellW).roundToInt(),
    bottom = originInRoot.y + (placement.rowEndExclusive * cellH).roundToInt(),
)
