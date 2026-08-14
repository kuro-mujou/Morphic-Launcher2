package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.slider.Morphic2DPad
import inkspire.morphic.core.model.icon.IconLayerSpec

/**
 * Position, zoom and rotation.
 *
 * **Position is a 2D pad rather than two sliders**, because the value is a point: an icon is nudged diagonally as
 * often as along an axis, and two sliders make that two gestures and a mental transpose. `Morphic2DPad` was built
 * for exactly this and had no consumer until now.
 *
 * **Every control also has buttons, because a drag cannot be exact and these values have exact answers people want.**
 * Centered, 1.00×, 0°, 90° — a finger on a 140dp pad or a 250dp slider lands on 0.037 and 87°, and no amount of care
 * fixes that: the control's resolution is its length in pixels. The pad and the sliders stay the way you *find* a
 * value; the buttons are how you land on one. [SteppedSlider] carries that for the two sliders and states the whole
 * argument, including why a press snaps to the grid instead of adding to the value; the pad's cluster below is the
 * same idea in two dimensions.
 *
 * Steps are chosen so the values people ask for by name are on the grid: 5° puts 45, 90 and 180 on it, and 0.05 puts
 * 1.00 and 1.50 on it.
 *
 * A **disabled** button is one whose target is where the value already is or outside the range — the same "ask, do not
 * guess" rule the layer reorder buttons use, and what makes the pad's center button say whether the layer is centered
 * without a second readout.
 *
 * Every control edits live and calls [onCommit] when the gesture *ends* — so the preview follows the finger, while
 * undo steps over the whole drag rather than through a hundred frames of it. A button press is discrete, so it commits
 * at once and is one undo step.
 */
@Composable
internal fun TransformControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // Discrete edits record themselves, exactly as the source tiles do — there is no gesture here to punctuate.
    fun edit(transform: (IconLayerSpec) -> IconLayerSpec) {
        onUpdate(transform)
        onCommit()
    }

    LabeledControl("Position") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Morphic2DPad(
                x = spec.offsetX,
                y = spec.offsetY,
                onValueChange = { x, y -> onUpdate { it.copy(offsetX = x, offsetY = y) } },
                xRange = OffsetRange,
                yRange = OffsetRange,
                onValueChangeFinished = onCommit,
                modifier = Modifier.size(PadSide),
            )
            NudgePad(
                spec = spec,
                onStep = onUpdate,
                onStepsFinished = onCommit,
                onEdit = ::edit,
            )
        }
    }

    LabeledControl("Zoom  ${"%.2f".format(spec.zoom)}") {
        SteppedSlider(
            value = spec.zoom,
            valueRange = ZoomRange,
            step = ZoomStep,
            what = "zoom",
            onValueChange = { value -> onUpdate { it.copy(zoom = value) } },
            onValueChangeFinished = onCommit,
        )
    }

    LabeledControl("Rotation  ${"%.0f".format(spec.rotation)}°") {
        SteppedSlider(
            value = spec.rotation,
            valueRange = RotationRange,
            step = RotationStep,
            what = "rotation",
            onValueChange = { value -> onUpdate { it.copy(rotation = value) } },
            onValueChangeFinished = onCommit,
        )
    }
}

/**
 * The four directions and the way back to the middle, beside the pad rather than under it.
 *
 * **The center of a direction pad is where "back to the middle" belongs** — it is the one arrangement where the
 * control's shape states what it does, and it costs no row of its own. Disabled while the layer is already centered,
 * so the cluster doubles as the answer to "is it?", which the pad's knob only approximates.
 *
 * Arrows are disabled at the edge of the range for the same reason: a press that would do nothing says so first.
 */
@Composable
private fun NudgePad(
    spec: IconLayerSpec,
    onStep: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onStepsFinished: () -> Unit,
    onEdit: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
) {
    @Composable
    fun Arrow(icon: ImageVector, description: String, x: Int, y: Int) {
        StudioStepperButton(
            icon = icon,
            contentDescription = description,
            enabled = spec.nudged(x, y) != spec,
            onStep = { onStep { it.nudged(x, y) } },
            onStepsFinished = onStepsFinished,
            modifier = Modifier.size(NudgeSlot),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Arrow(Icons.Default.KeyboardArrowUp, "Nudge up", 0, -1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nudge left", -1, 0)
            StudioIconButton(
                icon = Icons.Default.CenterFocusStrong,
                contentDescription = "Center",
                enabled = spec.offsetX != 0f || spec.offsetY != 0f,
                onClick = { onEdit { it.copy(offsetX = 0f, offsetY = 0f) } },
                modifier = Modifier.size(NudgeSlot),
            )
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nudge right", 1, 0)
        }
        Arrow(Icons.Default.KeyboardArrowDown, "Nudge down", 0, 1)
    }
}

/**
 * This layer moved one nudge along each axis [x] and [y] name (`-1`, `0`, `+1`), clamped to the pad's own range so a
 * button can never leave the layer somewhere the pad cannot show.
 *
 * **Snapped like the sliders, and here it is load-bearing rather than tidy.** Adding `0.01` repeatedly accumulates
 * float error, so a layer nudged twenty steps out and twenty back would land near zero rather than on it — leaving the
 * Center button lit, and lit *forever*, over an offset too small to see. Stepping onto the grid means the way back is
 * exactly the way out.
 */
private fun IconLayerSpec.nudged(x: Int, y: Int): IconLayerSpec = copy(
    offsetX = offsetX.nudged(x),
    offsetY = offsetY.nudged(y),
)

private fun Float.nudged(direction: Int): Float =
    if (direction == 0) this else snappedStep(this, NudgeStep, up = direction > 0).coerceIn(OffsetRange)

/** Where the pad's own edges are; the nudge buttons clamp to the same range so the two agree. */
private val OffsetRange = -0.5f..0.5f
private val ZoomRange = 0.2f..2f
private val RotationRange = 0f..360f

/** A hundredth of the frame — fine enough that a press is a correction rather than a move. */
private const val NudgeStep = 0.01f

/** Coarse enough to be worth pressing, fine enough that 1.00 and 1.50 are both on the grid. */
private const val ZoomStep = 0.05f

/** Five degrees, so 45, 90 and 180 are all reachable by stepping rather than only by luck. */
private const val RotationStep = 5f

/** The pad, and one cell of the cluster beside it — equal to `StudioIconButton`'s own side. */
private val PadSide = 140.dp
private val NudgeSlot = 40.dp
