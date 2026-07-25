package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import kotlinx.coroutines.flow.drop

/**
 * Single-value slider with a plain `value`/[onValueChange] API — the M3 [SliderState] is created and bridged
 * **inside**, so call sites don't need a `rememberSliderState`. Internally it uses the modern state-hoisted M3
 * `Slider` (not the value-based overload) and our custom thin thumb over M3's track.
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
    val state = rememberSliderState(
        value = value,
        steps = steps,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
    )

    // Reflect external value changes into the state (e.g. a programmatic reset)…
    LaunchedEffect(value) { if (value != state.value) state.value = value }
    // …and push the state's own (drag) changes back out. drop(1) skips the initial echo.
    val currentOnChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.value }.drop(1).collect { currentOnChange(it) }
    }

    Slider(
        state = state,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = { MorphicSliderThumb(interactionSource, colors, enabled) },
        track = { sliderState ->
            SliderDefaults.Track(
                modifier = Modifier.height(MorphicTrackHeight),
                sliderState = sliderState,
                enabled = enabled,
                thumbTrackGapSize = 2.dp,
            )
        },
    )
}
