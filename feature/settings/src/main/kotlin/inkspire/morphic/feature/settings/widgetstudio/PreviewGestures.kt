package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * What the preview does with a finger: a tap selects the block under it (or the widget, off every block), and a drag or
 * a pinch moves or scales one.
 *
 * **The block a gesture acts on is decided when it starts**: the one under the first finger, or — when that lands on no
 * block — the one already selected, so a block too small to put two fingers on can still be pinched from beside it.
 * Everything is in the widget's own pixels, which is the space the renderer reports its layers in.
 *
 * @param target which block a gesture starting at a point acts on, or null for none.
 * @param onTap a tap at a point.
 * @param onStart a drag or pinch has begun on a block — which selects it.
 * @param onChange a step of it: the pan in pixels and the zoom factor since the last step.
 * @param onEnd the fingers have lifted.
 */
internal fun Modifier.previewGestures(
    target: (Offset) -> Int?,
    onTap: (Offset) -> Unit,
    onStart: (Int) -> Unit,
    onChange: (Int, Offset, Float) -> Unit,
    onEnd: (Int) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val part = target(down.position)
        val slop = viewConfiguration.touchSlop
        var pan = Offset.Zero
        var zoom = 1f
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            val panStep = event.calculatePan()
            val zoomStep = event.calculateZoom()
            if (!moved) {
                pan += panStep
                zoom *= zoomStep
                moved = pastSlop(pan, zoom, size.width, slop)
                if (moved && part != null) {
                    onStart(part)
                    // What was travelled inside the slop, so the block does not trail the finger by it.
                    onChange(part, pan, zoom)
                }
            } else if (part != null) {
                onChange(part, panStep, zoomStep)
            }
            if (moved && part != null) event.consumeMoves()
        }
        when {
            !moved -> onTap(down.position)
            part != null -> onEnd(part)
        }
    }
}

/** A drag past the slop, or a pinch that moved the fingers apart or together by as much across [width]. */
private fun pastSlop(pan: Offset, zoom: Float, width: Int, slop: Float): Boolean =
    pan.getDistance() > slop || abs(1f - zoom) * width > slop

/** Claims this event's movement, so nothing under the preview scrolls with the block. */
private fun PointerEvent.consumeMoves() = changes.forEach { if (it.positionChanged()) it.consume() }
