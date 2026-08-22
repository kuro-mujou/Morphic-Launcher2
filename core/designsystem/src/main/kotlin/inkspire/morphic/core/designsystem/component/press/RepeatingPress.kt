package inkspire.morphic.core.designsystem.component.press

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * **A press that keeps firing while it is held** — for a button whose job is to move a value by a little.
 *
 * Tapping one is a correction; wanting twenty of them is wanting to hold it, not to tap twenty times. That is the whole
 * of what this adds over `clickable`, and every part of it is a detail that is wrong in an obvious implementation:
 *
 * - **It fires on the *press*, not the release**, unlike every other button. A repeating control that waited for the
 *   release would show nothing at all for the whole first tap. That also removes the double-fire a naive "clickable plus
 *   a repeat timer" produces, where the release adds one more step after the value already looked right.
 * - **The first repeat waits the platform's own long-press timeout**, so an ordinary tap can never become two.
 * - **The gesture is keyed on `Unit` and [enabled] is read from inside it.** Keying on `enabled` would restart the whole
 *   `pointerInput` the moment a hold reached the end of its range — cancelling the gesture mid-press, so the finger
 *   comes up with the edit made and [onStepsFinished] never called, leaving a change outside undo history.
 * - **Two callbacks, not one.** [onStep] runs per fire and must not commit; [onStepsFinished] runs once when the finger
 *   lifts. That is deliberately the shape a slider takes (`onValueChange` / `onValueChangeFinished`) and it is what
 *   keeps a hold **one** edit rather than thirty — one undo step in the icon studio, one store write in settings, and
 *   for anything that re-renders on commit, one render.
 * - **An accessibility service gets a plain click**, which fires both callbacks once: there is no gesture to hold when a
 *   button is activated by name, and a stepper that only worked under a real finger would be unreachable.
 *
 * **Extracted from the icon studio's `StudioStepperButton` when the settings sliders wanted the same behavior**, and
 * what is shared is exactly the part that would drift: the two thresholds, the fire-on-press, and the callback pairing.
 * The *look* is not here — the studio's stepper is a glass face on a fixed-white panel and the settings one is an
 * ordinary tinted glyph — which is why this is a `Modifier` over whatever a caller draws rather than a button.
 *
 * @param interactionSource the caller's, so the ripple and any other indication it drives stay the caller's business.
 *   Passed in rather than remembered here because a caller usually already has one.
 */
@Composable
fun Modifier.repeatingPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onStep: () -> Unit,
    onStepsFinished: () -> Unit,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentStep by rememberUpdatedState(onStep)
    val currentFinished by rememberUpdatedState(onStepsFinished)
    val indication = LocalIndication.current

    return this
        .indication(interactionSource, indication)
        .semantics {
            role = Role.Button
            onClick(label = null) {
                if (currentEnabled) {
                    currentStep()
                    currentFinished()
                }
                true
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!currentEnabled) return@awaitEachGesture

                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                currentStep()

                var wait = viewConfiguration.longPressTimeoutMillis
                while (true) {
                    // Three outcomes, and the timeout is the interesting one: null means the finger is still down, so
                    // this is a repeat. Anything else ended the gesture.
                    val ended = withTimeoutOrNull(wait) { waitForUpOrCancellation() != null }
                    if (ended != null) {
                        interactionSource.tryEmit(
                            if (ended) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                        break
                    }
                    if (currentEnabled) currentStep()
                    wait = StepRepeatIntervalMs
                }
                currentFinished()
            }
        }
}

/**
 * Convenience for the common case: a caller that has no other use for the interaction source.
 *
 * Kept separate rather than defaulting the parameter, because a `remember` in a default expression is easy to read as
 * free and is not — it is one more slot per call site.
 */
@Composable
fun Modifier.repeatingPress(enabled: Boolean, onStep: () -> Unit, onStepsFinished: () -> Unit): Modifier =
    repeatingPress(remember { MutableInteractionSource() }, enabled, onStep, onStepsFinished)

/**
 * How fast a held stepper repeats.
 *
 * Fast enough that holding it feels like dragging the value, slow enough that a value with few steps in it does not
 * shoot past what the finger meant. Public because the two callers that repeat should not be able to disagree about it.
 */
const val StepRepeatIntervalMs = 60L
