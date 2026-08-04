package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import kotlinx.coroutines.flow.drop

/**
 * Two-thumb range slider with a plain `value: ClosedFloatingPointRange<Float>` / [onValueChange] API — the M3
 * [androidx.compose.material3.RangeSliderState] is created and bridged **inside**, so call sites don't need a
 * `rememberRangeSliderState`. Both thumbs are our [MorphicSliderThumb] over M3's track and can't cross.
 *
 * Intended consumer: the per-layout **icon-size rail** — [value] is the min..max icon size a layout scales
 * between (`IconMetrics.minIconDp`/`maxIconDp`).
 *
 * The state is `remember`ed here rather than by `rememberRangeSliderState` for the two reasons spelled out on
 * [MorphicSlider]: that factory's `rememberSaveable` init block never re-runs, so it would freeze
 * [onValueChangeFinished] at first composition (one working commit, then every later drag re-committing the first one)
 * and freeze [valueRange]/[steps] as the `val`s they are. Same bug, same shape, same fix — kept in step deliberately,
 * since two sliders that disagree about when they commit is worse than either being wrong.
 */
@Composable
fun MorphicRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = LocalMorphicColors.current
    val startSource = remember { MutableInteractionSource() }
    val endSource = remember { MutableInteractionSource() }
    val state = remember(steps, valueRange) {
        RangeSliderState(
            activeRangeStart = value.start,
            activeRangeEnd = value.endInclusive,
            steps = steps,
            valueRange = valueRange,
        )
    }
    // The one field M3 lets us keep current, kept current — see [MorphicSlider].
    SideEffect { state.onValueChangeFinished = onValueChangeFinished }

    LaunchedEffect(value) {
        if (value.start != state.activeRangeStart || value.endInclusive != state.activeRangeEnd) {
            state.activeRangeStart = value.start
            state.activeRangeEnd = value.endInclusive
        }
    }
    val currentOnChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.activeRangeStart to state.activeRangeEnd }
            .drop(1)
            .collect { (start, end) -> currentOnChange(start..end) }
    }

    RangeSlider(
        state = state,
        modifier = modifier,
        enabled = enabled,
        startInteractionSource = startSource,
        endInteractionSource = endSource,
        startThumb = { MorphicSliderThumb(startSource, colors, enabled) },
        endThumb = { MorphicSliderThumb(endSource, colors, enabled) },
        track = { rangeState ->
            SliderDefaults.Track(
                modifier = Modifier.height(MorphicTrackHeight),
                rangeSliderState = rangeState,
                enabled = enabled,
                thumbTrackGapSize = 2.dp,
            )
        },
    )
}
