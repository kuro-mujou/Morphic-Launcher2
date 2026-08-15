package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.icon.PreviewBackground
import inkspire.morphic.data.settings.IconStudioWorkspace
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** One square of the transparency checkerboard, at canvas scale. */
private val CheckerSquare = 12.dp

/** The checkerboard's two grays — mid-toned, so they read against both a black and a white surround. */
internal val CheckerLight = Color(0xFFBDBDBD)
internal val CheckerDark = Color(0xFF8A8A8A)

/**
 * The studio's canvas: a backdrop, and a **square bound** the icon is drawn in — resting hard against the chrome at
 * the top, and panned and zoomed from there by the user.
 *
 * The bound is square and it **clips**, both because the real renderer works that way — an icon is composited into a
 * square bitmap — so a layer pushed past the edge here disappears exactly as it would on the home screen. An editor
 * that let artwork spill past the bound would be showing the user something the launcher can never draw.
 *
 * The whole canvas is the Haze source, so a floating surface blurs the backdrop *and* the icon it overlaps, which is
 * what makes the panels read as glass over the work rather than as opaque cards parked on it.
 *
 * ### Three things it now does that it did not
 *
 * **The bound is derived once and handed to both readers.** The layout places a `Box` at it and [drawBackdrop] fills
 * the same rectangle with the checkerboard; those were two independent derivations from the same two constants, which
 * agreed only because neither had any input beyond the canvas's size. A user-controlled viewport ends that, so
 * [studioIconBound] is the single derivation and this passes its result to the draw rather than re-deriving there.
 *
 * **It pans and zooms.** One `detectTransformGestures` over the whole canvas, so a drag anywhere moves the icon and a
 * pinch anywhere scales it — see [IconStudioWorkspace.pinched] for the centroid anchoring and for why the rotation the
 * gesture also reports is thrown away. The gesture edits live and commits when the fingers lift, which is the same
 * shape every slider in the studio takes and is what keeps one pinch one write rather than sixty.
 *
 * **A tap on it puts the chrome away.** The canvas is the only part of this screen that is not a control, so it is the
 * one place a tap can mean "nothing, thanks" — which is what [onTap] is wired to close: the open tool panel, the layer
 * rail's quick menu, and the color picker. A tap is unambiguous against the pan gesture because a pan has to cross
 * touch slop before it claims anything.
 *
 * @param topInset how much of the canvas's top edge the chrome above occupies. The resting bound begins immediately
 *   below it; the canvas itself still paints edge to edge, because the backdrop is the *window's* and the insets here
 *   are content padding, as everywhere else in this launcher.
 * @param onWorkspaceChange the arrangement as it is being dragged — live, uncommitted, every frame.
 * @param onWorkspaceCommit the fingers have lifted. One undo-free write of what they settled on.
 * @param content the icon, drawn to fill the square bound.
 */
@Composable
fun StudioCanvas(
    background: PreviewBackground,
    workspace: IconStudioWorkspace,
    topInset: Dp,
    onWorkspaceChange: (IconStudioWorkspace) -> Unit,
    onWorkspaceCommit: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val checkerPx = with(density) { CheckerSquare.toPx() }
    val topInsetPx = with(density) { topInset.toPx() }

    // **Read from inside the gesture rather than keyed on**, which is the same trap `StudioStepperButton` documents:
    // keying `pointerInput` on the workspace would restart the gesture on its own first frame, so a pinch would be
    // cancelled the instant it moved anything and would never reach the commit that persists it.
    val currentWorkspace by rememberUpdatedState(workspace)
    val currentChange by rememberUpdatedState(onWorkspaceChange)
    val currentCommit by rememberUpdatedState(onWorkspaceCommit)
    val currentTap by rememberUpdatedState(onTap)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentAlignment = Alignment.TopStart,
    ) {
        // A fraction of **this node**, not of the screen. The two matter differently here: the canvas is what the
        // icon is placed in, and it is not always the window — the rails inset it, and a tablet gives it a different
        // share again.
        val canvasWidth = with(density) { maxWidth.toPx() }
        val canvasHeight = with(density) { maxHeight.toPx() }
        val bound = studioIconBound(canvasWidth, canvasHeight, topInsetPx, workspace)

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The bound is passed in rather than re-derived: with a viewport there is no longer any way for a
                // second derivation to be certain of agreeing with the first.
                .drawBehind { drawBackdrop(background, bound, checkerPx) }
                // **Two detectors, not one.** They live in separate `pointerInput` nodes so neither has to implement
                // the other: a transform gesture must cross touch slop before it consumes, so a tap reaches its own
                // detector untouched, and a drag is never mistaken for a dismissal.
                .pointerInput(Unit) {
                    detectTapGestures { currentTap() }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, drag, zoomBy, _ ->
                        currentChange(
                            currentWorkspace.pinched(
                                canvasWidth = size.width.toFloat(),
                                canvasHeight = size.height.toFloat(),
                                topInset = topInsetPx,
                                centroidX = centroid.x,
                                centroidY = centroid.y,
                                dragX = drag.x,
                                dragY = drag.y,
                                zoomBy = zoomBy,
                            ),
                        )
                    }
                    // Reached when the gesture ends, `detectTransformGestures` being a suspending loop per gesture.
                    // One write per pinch rather than one per frame — the rule every slider here follows.
                    currentCommit()
                },
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(bound.left.roundToInt(), bound.top.roundToInt()) }
                .size(with(density) { bound.side.toDp() })
                // The bound's whole job beyond holding the icon: overflow vanishes here as it would in the bake.
                .clipToBounds(),
        ) {
            content()
        }
    }
}

/**
 * Paints the backdrop: a flat color, a checkerboard, or a flat color with the checkerboard confined to the icon's
 * bound.
 *
 * The mixed modes are the useful ones and the reason this is not just two colors — they show an icon's own
 * transparency *and* how its silhouette reads against a dark or light surround at the same time, which is the pair
 * of questions actually being asked while shaping a layer.
 *
 * **[bound] is given rather than derived.** It used to be recomputed here from the same constants the layout used, on
 * the argument that two derivations from one node's size are certain to agree. That stopped being true the moment the
 * bound depended on a viewport the user could move: the checkerboard would have gone on marking where the icon *used*
 * to be. One derivation, two readers.
 */
private fun DrawScope.drawBackdrop(
    background: PreviewBackground,
    bound: StudioIconBound,
    checkerPx: Float,
) {
    val base = when (background) {
        PreviewBackground.WHITE, PreviewBackground.WHITE_WITH_CHECKER -> Color.White
        else -> Color.Black
    }
    if (!background.checkersOutsideBound) drawRect(base)

    if (background.checkersOutsideBound) {
        drawCheckerboard(Offset.Zero, size, checkerPx)
        return
    }
    if (!background.checkersInsideBound) return

    drawCheckerboard(Offset(bound.left, bound.top), Size(bound.side, bound.side), checkerPx)
}

/**
 * The transparency checkerboard, drawn over [area] starting at [topLeft].
 *
 * Internal rather than private so the background swatch on the cycle button draws the *same* checkerboard, at its own
 * [squarePx] — a swatch claiming to show what a backdrop looks like has to be made of the backdrop's own parts.
 *
 * **Clipped to the canvas as well as to [area]**, which the viewport made necessary: a zoomed bound reaches past the
 * canvas on every side, and without the intersection the loop would walk thousands of squares that are not on screen.
 */
internal fun DrawScope.drawCheckerboard(topLeft: Offset, area: Size, squarePx: Float) {
    if (squarePx <= 0f || area.width <= 0f || area.height <= 0f) return

    drawRect(CheckerLight, topLeft = topLeft, size = area)

    val columns = ceil(area.width / squarePx).toInt()
    val rows = ceil(area.height / squarePx).toInt()
    // **The loop is bounded by what is on screen, and the indices are kept** — which is what makes this a clip rather
    // than a re-tiling: skipping the off-screen squares by narrowing the *range* leaves every remaining square on the
    // index it always had, so the light/dark phase cannot shift as the bound is dragged. Recomputing from the visible
    // corner instead would flip the pattern whenever the first visible column happened to be odd.
    val firstColumn = visibleStart(topLeft.x, squarePx)
    val lastColumn = visibleEnd(topLeft.x, size.width, squarePx, columns)
    val firstRow = visibleStart(topLeft.y, squarePx)
    val lastRow = visibleEnd(topLeft.y, size.height, squarePx, rows)

    for (row in firstRow until lastRow) {
        for (column in firstColumn until lastColumn) {
            if ((row + column) % 2 == 0) continue
            val x = topLeft.x + column * squarePx
            val y = topLeft.y + row * squarePx
            // Clipped to the area so a partial square at the edge does not bleed past the bound.
            val w = min(squarePx, topLeft.x + area.width - x)
            val h = min(squarePx, topLeft.y + area.height - y)
            drawRect(CheckerDark, topLeft = Offset(x, y), size = Size(w, h))
        }
    }
}

/** The first square index on this axis that is not entirely off the near edge of the canvas. */
private fun visibleStart(origin: Float, squarePx: Float): Int =
    ((-origin) / squarePx).toInt().coerceAtLeast(0)

/** One past the last square index that starts before the canvas's far edge, never past the tiling's own end. */
private fun visibleEnd(origin: Float, extent: Float, squarePx: Float, count: Int): Int =
    ceil((extent - origin) / squarePx).toInt().coerceIn(0, count)
