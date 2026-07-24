package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * A 2-D control pad — edit X and Y at once by dragging the knob. Value increases right (X) and up (Y). The
 * caller sets the size via [modifier] (square recommended). Live [onValueChange] while dragging;
 * [onValueChangeFinished] on release. Built for the icon editor's offset (transform) tool.
 *
 * Consumes the Expressive motion engine: the knob springs larger while pressed (a size cue only; the knob
 * position tracks the finger 1:1). Colours come from [LocalMorphicColors] — the knob is drawn as an accent
 * disc with a contrasting core so it stays visible on the monochrome pad in both schemes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Morphic2DPad(
    x: Float,
    y: Float,
    onValueChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    xRange: ClosedFloatingPointRange<Float> = -1f..1f,
    yRange: ClosedFloatingPointRange<Float> = -1f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = LocalMorphicColors.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    var pressed by remember { mutableStateOf(false) }
    val knobRadius by animateDpAsState(
        targetValue = if (pressed && enabled) KNOB_RADIUS_ACTIVE else KNOB_RADIUS,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Morphic2DPadKnob",
    )

    fun spanOf(range: ClosedFloatingPointRange<Float>): Float =
        (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f

    fun fractionOf(v: Float, range: ClosedFloatingPointRange<Float>): Float =
        ((v - range.start) / spanOf(range)).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .then(
                if (enabled) {
                    Modifier.pointerInput(xRange, yRange) {
                        val kn = KNOB_RADIUS.toPx()
                        fun emit(px: Float, py: Float) {
                            val w = (size.width - 2 * kn).coerceAtLeast(1f)
                            val h = (size.height - 2 * kn).coerceAtLeast(1f)
                            val fx = ((px - kn) / w).coerceIn(0f, 1f)
                            val fy = 1f - ((py - kn) / h).coerceIn(0f, 1f)
                            currentOnChange(
                                xRange.start + spanOf(xRange) * fx,
                                yRange.start + spanOf(yRange) * fy,
                            )
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            pressed = true
                            emit(down.position.x, down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChanged()) {
                                    emit(change.position.x, change.position.y)
                                    change.consume()
                                }
                            }
                            pressed = false
                            currentOnFinished?.invoke()
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val kn = KNOB_RADIUS.toPx()
        val w = (size.width - 2 * kn).coerceAtLeast(0f)
        val h = (size.height - 2 * kn).coerceAtLeast(0f)
        val knobX = kn + w * fractionOf(x, xRange)
        val knobY = kn + h * (1f - fractionOf(y, yRange))
        val cx = size.width / 2f
        val cy = size.height / 2f
        val hairline = 1.dp.toPx()

        drawLine(color = colors.divider, start = Offset(0f, cy), end = Offset(size.width, cy), strokeWidth = hairline)
        drawLine(color = colors.divider, start = Offset(cx, 0f), end = Offset(cx, size.height), strokeWidth = hairline)

        val knobPx = knobRadius.toPx()
        drawCircle(color = colors.accent, radius = knobPx, center = Offset(knobX, knobY))
        drawCircle(color = colors.onAccent, radius = knobPx * 0.4f, center = Offset(knobX, knobY))
    }
}

private val KNOB_RADIUS = 12.dp
private val KNOB_RADIUS_ACTIVE = 14.dp
