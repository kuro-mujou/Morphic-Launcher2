package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.surface.LockSurfaceGesture
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** How far from a handle's centre a press still grabs it. Generous, because the glyphs are small. L1's 28dp. */
private val HandleHitRadius = 28.dp

/** How far inside the frame a handle sits, and the least frame it leaves either side of one. L1's pair. */
private val HandleInset = 18.dp
private val HandleInsetMargin = 10.dp

private val FrameStroke = 1.dp
private val CornerArm = 10.dp
private val CornerRounding = 3.5.dp
private val PillLong = 16.dp
private val PillShort = 3.dp

/** How strongly the frame's interior is washed, legal and refused. */
private const val FillAlpha = 0.08f
private const val RefusedFillAlpha = 0.22f
private const val FrameAlpha = 0.5f

/**
 * **The resize frame** — a border with grips around one placed item, dragged to change its span.
 *
 * A full-screen `Canvas` above the surface, drawn at the item's own cells: a frame, four edge pills and four
 * corner arrows, each offered only on an axis the item may actually be resized on ([handlesFor]). Dragging a
 * handle re-reports a candidate placement every frame; releasing commits it. Ported from L1's `ResizeOverlay`,
 * with the geometry lifted out into [ResizeHandles] so it can be unit-tested apart from the drawing.
 *
 * **A commit does not end the frame — only a press outside it does.** Resizing is an adjustment made in several
 * drags (widen, then shorten, then nudge the other edge), and dismissing on the first release makes every one of
 * them cost another long-press and another menu. So the frame lives until the user says they are finished, which
 * is a press on anything that is not the item: a press *inside* the frame that misses a handle is swallowed and
 * changes nothing, since the item under it is exactly what the user is still working on.
 *
 * **Everything is drawn from [placement], which the caller owns.** The overlay reports and does not remember:
 * the host clamps each candidate to its grid, asks its planner whether the occupants in the way can be pushed
 * clear, and hands back only a rectangle it would commit — so the frame the user sees is always the outcome,
 * never a hopeful preview of one, and the surface beneath can show that outcome at the same time (home previews
 * the push live under the frame).
 *
 * **It is modal in two different ways, because two different things would otherwise take the gesture.**
 * - It **consumes every event it sees**, including the press that dismisses it, so the cell beneath a handle
 *   cannot also launch or lift while the frame is up.
 * - It holds [inkspire.morphic.core.designsystem.surface.SurfaceGestureLock] for as long as it is composed, which
 *   is what stops the **surface swipe** taking a sideways handle drag. Consumption alone cannot: that pan runs on
 *   `PointerEventPass.Initial` and so sees the raw delta *before* this node does, claiming at the platform slop
 *   while a resize is still a few pixels in. The lock is the launcher's one answer to "something on screen owns
 *   this finger", and the widget picker and the open folder hold it for the same reason.
 *
 * @param placement the frame's current cells, in the grid [geometry] describes — **the last thing the grid
 *   accepted**, which is why a drag past what is possible leaves the frame still and merely turns it red.
 * @param refused draws the frame in the error color: the last thing [onResize] asked for could not be given.
 *   The host decides, because it is the only thing that knows whether the request left the grid or collided with
 *   occupants that cannot be pushed clear — this overlay knows nothing about either.
 * @param onResize a handle moved: here is the candidate placement, in logical cells.
 * @param onCommit the finger lifted off a handle. What to commit is whatever [onResize] last reported. The frame
 *   stays up afterwards, so the host must re-seed [placement] from what the grid actually holds now — a drag the
 *   grid refused would otherwise leave the frame describing a size nothing took.
 * @param onDismiss a press outside the frame: the user is done with this item.
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

    val colors = LocalMorphicColors.current
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { HandleHitRadius.toPx() }
    val insetPx = with(density) { HandleInset.toPx() }
    val insetMarginPx = with(density) { HandleInsetMargin.toPx() }

    // Read live inside the gesture loop: a resize runs for as long as the finger is down, and the placement it
    // starts from must be the one on screen when it started rather than the one this composition captured.
    val live by rememberUpdatedState(placement)
    val liveBounds by rememberUpdatedState(bounds)
    val liveGeometry by rememberUpdatedState(geometry)
    val liveOnResize by rememberUpdatedState(onResize)
    val liveOnCommit by rememberUpdatedState(onCommit)
    val liveOnDismiss by rememberUpdatedState(onDismiss)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val frame = liveGeometry.frameOf(live)
                    val handle = handlesFor(liveBounds)
                        .minByOrNull { frame.distanceTo(it, down.position, insetPx, insetMarginPx) }
                        ?.takeIf {
                            frame.distanceTo(it, down.position, insetPx, insetMarginPx) <= hitRadiusPx
                        }

                    if (handle == null) {
                        // Missed every handle. Outside the frame that means "done"; inside it means nothing at
                        // all, because the item under the finger is the one still being resized. Waiting for the
                        // release rather than acting on the press is what stops a dismiss also being read as a tap
                        // on whatever is underneath.
                        waitForUpOrCancellation()
                        if (!frame.contains(down.position)) liveOnDismiss()
                        return@awaitEachGesture
                    }

                    // The placement the drag is measured *from* stays fixed for the whole gesture: recomputing
                    // against the live one would compound each frame's change into a runaway edge.
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
            },
    ) {
        val frame = geometry.frameOf(placement)
        val inset = frame.handleInset(insetPx, insetMarginPx)
        val accent = if (refused) colors.error else colors.content

        drawRect(
            color = accent.copy(alpha = if (refused) RefusedFillAlpha else FillAlpha),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.width, frame.height),
        )
        drawRect(
            color = accent.copy(alpha = FrameAlpha),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.width, frame.height),
            style = Stroke(width = FrameStroke.toPx()),
        )

        handlesFor(bounds).forEach { handle ->
            val centre = handleCentre(handle, frame.left, frame.top, frame.right, frame.bottom, inset)
            if (handle.isCorner) drawCornerArrow(handle, centre, accent) else drawEdgePill(handle, centre, accent)
        }
    }
}

/** A corner grip: a rounded triangle pointing out of the corner it drags. */
private fun DrawScope.drawCornerArrow(handle: ResizeHandle, centre: Offset, color: Color) {
    val half = CornerArm.toPx() / 2f
    val sx = if (handle.movesLeft) -1f else 1f
    val sy = if (handle.movesTop) -1f else 1f
    val path = Path().apply {
        moveTo(centre.x + sx * half, centre.y + sy * half)
        lineTo(centre.x - sx * half, centre.y + sy * half)
        lineTo(centre.x + sx * half, centre.y - sy * half)
        close()
    }
    drawPath(path, color)
}

/** An edge grip: a pill lying along the edge it drags — wide on a horizontal edge, tall on a vertical one. */
private fun DrawScope.drawEdgePill(handle: ResizeHandle, centre: Offset, color: Color) {
    val horizontal = handle.movesTop || handle.movesBottom
    val width = if (horizontal) PillLong.toPx() else PillShort.toPx()
    val height = if (horizontal) PillShort.toPx() else PillLong.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(centre.x - width / 2f, centre.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(PillShort.toPx() / 2f),
    )
}

/** A placement's pixel rectangle in root coordinates — the frame the overlay draws and hit-tests. */
private data class ResizeFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /**
     * How far inside the frame the handles sit: [insetPx], reduced on a frame too small to hold it so the two
     * handles on an axis cannot cross over each other on a one-cell item.
     */
    fun handleInset(insetPx: Float, marginPx: Float): Float =
        min(insetPx, min(width, height) / 2f - marginPx).coerceAtLeast(0f)

    /** Whether [point] is on the item itself — which is what tells a dismissing press from an idle one. */
    fun contains(point: Offset): Boolean =
        point.x in left..right && point.y in top..bottom

    fun distanceTo(handle: ResizeHandle, point: Offset, insetPx: Float, marginPx: Float): Float {
        val centre = handleCentre(handle, left, top, right, bottom, handleInset(insetPx, marginPx))
        // Manhattan distance, as L1 used: it is only ever compared against itself and against a square hit
        // radius, so the square root would buy nothing.
        return abs(centre.x - point.x) + abs(centre.y - point.y)
    }
}

/** [placement]'s frame, rounded to whole pixels so the border does not shimmer as it is dragged. */
private fun GridGeometry.frameOf(placement: GridPlacement) = ResizeFrame(
    left = originInRoot.x + (placement.col * cellW).roundToInt(),
    top = originInRoot.y + (placement.row * cellH).roundToInt(),
    right = originInRoot.x + (placement.colEndExclusive * cellW).roundToInt(),
    bottom = originInRoot.y + (placement.rowEndExclusive * cellH).roundToInt(),
)
