package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
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
 * Single-value slider with a plain `value`/[onValueChange] API — the M3 [SliderState] is created and bridged
 * **inside**, so call sites don't need a `rememberSliderState`. Internally it uses the modern state-hoisted M3
 * `Slider` (not the value-based overload) and our custom thin thumb over M3's track.
 *
 * **The state is `remember`ed here rather than by `rememberSliderState`, and that is a bug fix rather than a
 * preference.** M3's factory is `rememberSaveable(saver = …) { SliderState(…) }` — an init block that never re-runs —
 * so two of its arguments are frozen at first composition, and both bite a facade like this one:
 *
 * - **[onValueChangeFinished] would be captured forever.** `SliderState` exposes it as a `var` but the factory never
 *   re-assigns it, so a call site whose commit lambda closes over changing state gets the *first* composition's
 *   closure for the lifetime of the slider. A caller whose commit reads an in-flight value held in a *fresh* object
 *   per commit — which `remember(value)` produces, and which is why [MorphicSliderRow] deliberately does not — then
 *   commits its first drag and re-commits that same value for every later one. Pushed in via [SideEffect] instead,
 *   which is what a `var` on a remembered holder is for.
 * - **[valueRange] and [steps] are `val`s**, so a slider whose range legitimately moves — the APPS list's row height is
 *   bounded by the icon guardrails, which the user can change on the same screen — would go on mapping finger position
 *   through the range it was born with. Keying the `remember` on them re-creates the state instead, seeded from the
 *   current [value].
 *
 * What is given up is `rememberSaveable`'s restore across a configuration change, and it is not load-bearing: [value]
 * is the caller's, so on recreation the state is constructed from it and the caller remains the single source of truth.
 * The M3 rule this codebase follows — sit on the **state-hoisted** `Slider(state = …)` rather than the deprecated
 * value-based overload — is untouched; only the factory changes.
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
    val state = remember(steps, valueRange) {
        SliderState(value = value, steps = steps, valueRange = valueRange)
    }
    // The one field M3 lets us keep current, kept current — see the note above.
    SideEffect { state.onValueChangeFinished = onValueChangeFinished }

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
                modifier = Modifier.height(6.dp),
                sliderState = sliderState,
                enabled = enabled,
                thumbTrackGapSize = 2.dp,
            )
        },
    )
}
