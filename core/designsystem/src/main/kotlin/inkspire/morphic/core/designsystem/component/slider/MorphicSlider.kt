package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicColors

/**
 * Slider on the M3 [Slider] but with a **custom thin thumb + track** supplied through the slider's `thumb`
 * and `track` slots — so we keep M3's state, gesture handling and interaction stream while fully owning the
 * monochrome, drawing-app appearance. The thumb grows on press/drag via the Expressive motion spring.
 *
 * Bipolar / centre-origin fill isn't wired: it would be a [MorphicTrack] variant that anchors the active fill
 * at an `origin` fraction instead of the left edge.
 */
@Composable
fun MorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = LocalMorphicColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        steps = steps,
        valueRange = valueRange,
        thumb = { MorphicThumb(interactionSource, colors, enabled) },
        track = { state -> MorphicTrack(state, valueRange, colors) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphicThumb(
    interactionSource: MutableInteractionSource,
    colors: MorphicColors,
    enabled: Boolean,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val diameter by animateDpAsState(
        targetValue = if ((pressed || dragged) && enabled) THUMB_ACTIVE else THUMB_DIAMETER,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "MorphicSliderThumb",
    )
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (enabled) colors.thumb else colors.contentDisabled),
    )
}

@Composable
private fun MorphicTrack(
    state: SliderState,
    valueRange: ClosedFloatingPointRange<Float>,
    colors: MorphicColors,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((state.value - valueRange.start) / span).coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(THUMB_DIAMETER),
    ) {
        val cy = size.height / 2f
        // Inset by the thumb radius so the active-fill end lines up with the thumb centre (matches how M3
        // travels the thumb across width − thumb). May need on-device tuning against the active thumb size.
        val inset = THUMB_DIAMETER.toPx() / 2f
        val left = inset
        val right = size.width - inset
        val activeEnd = left + (right - left) * fraction
        val stroke = TRACK_HEIGHT.toPx()
        drawLine(colors.trackInactive, Offset(left, cy), Offset(right, cy), stroke, cap = StrokeCap.Round)
        drawLine(colors.trackActive, Offset(left, cy), Offset(activeEnd, cy), stroke, cap = StrokeCap.Round)
    }
}

private val THUMB_DIAMETER = 18.dp
private val THUMB_ACTIVE = 22.dp
private val TRACK_HEIGHT = 6.dp
