package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.runtime.Composable
import inkspire.morphic.core.model.icon.IconLayerSpec

/**
 * Position, zoom and rotation.
 *
 * **Every control has buttons beside it, because a drag cannot be exact and these values have exact answers people
 * want.** Centered, 1.00×, 0°, 90° — a finger on a 140dp pad or a 250dp slider lands on 0.037 and 87°, and no amount
 * of care fixes that: the control's resolution is its length in pixels. The pad and the sliders stay the way you
 * *find* a value; the buttons are how you land on one. [SteppedSlider] carries that for the two sliders and states
 * the whole argument, including why a press snaps to the grid instead of adding to the value; [PositionPad] is the
 * same idea in two dimensions, and is shared with the bloom's own position now that it has one.
 *
 * Steps are chosen so the values people ask for by name are on the grid: 5° puts 45, 90 and 180 on it, and 0.05 puts
 * 1.00 and 1.50 on it.
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
    LabeledControl("Position") {
        PositionPad(
            x = spec.offsetX,
            y = spec.offsetY,
            onValueChange = { x, y -> onUpdate { it.copy(offsetX = x, offsetY = y) } },
            onCommit = onCommit,
        )
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

private val ZoomRange = 0.2f..2f
private val RotationRange = 0f..360f

/** Coarse enough to be worth pressing, fine enough that 1.00 and 1.50 are both on the grid. */
private const val ZoomStep = 0.05f

/** Five degrees, so 45, 90 and 180 are all reachable by stepping rather than only by luck. */
private const val RotationStep = 5f
