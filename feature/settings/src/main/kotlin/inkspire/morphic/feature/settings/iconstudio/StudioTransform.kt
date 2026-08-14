package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.runtime.Composable
import inkspire.morphic.core.model.icon.IconLayerSpec

/**
 * Position, zoom, rotation and tilt.
 *
 * **Tilt is here rather than in Effects, and that is the model's call rather than this panel's.** Leaning a layer out
 * of the plane says *where the layer sits*, which is what every other control on this panel does — so it is two more
 * `IconLayerSpec` fields resolved through `LayerTransform`, not a `LayerEffect`. As an effect its position in the list
 * would be orderable against a colour matrix while the in-plane rotation's was not, which is one rotation being two
 * kinds of thing.
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

    // **Two sliders and no 2D pad, unlike Position** — the two tilts are not a point. A pad's knob says "the thing is
    // *here*", which is true of an offset and meaningless of a pair of angles; and unlike the offsets these two are
    // usually wanted one at a time, since a lean about both axes at once reads as a mistake rather than as depth.
    //
    // Named for the axis each turns *around*, which is `Camera.rotateX`'s convention and Compose's — so Tilt X leans
    // the top away and Tilt Y leans the left away. See `IconLayerSpec.tiltX`.
    LabeledControl("Tilt X  ${"%.0f".format(spec.tiltX)}°") {
        SteppedSlider(
            value = spec.tiltX,
            valueRange = TiltRange,
            step = RotationStep,
            what = "tilt X",
            onValueChange = { value -> onUpdate { it.copy(tiltX = value) } },
            onValueChangeFinished = onCommit,
        )
    }

    LabeledControl("Tilt Y  ${"%.0f".format(spec.tiltY)}°") {
        SteppedSlider(
            value = spec.tiltY,
            valueRange = TiltRange,
            step = RotationStep,
            what = "tilt Y",
            onValueChange = { value -> onUpdate { it.copy(tiltY = value) } },
            onValueChangeFinished = onCommit,
        )
    }
}

private val ZoomRange = 0.2f..2f
private val RotationRange = 0f..360f

/**
 * How far a layer may lean, either way.
 *
 * **Signed and resting at zero**, unlike [RotationRange], because a tilt has a *neutral* — flat to the screen —
 * where an in-plane rotation is a full circle with no privileged point. Bounded well short of 90°, which is where a
 * layer turns edge-on and disappears: past about sixty the artwork is too foreshortened to recognize, and the
 * corners start reaching the camera at the depth `LayerTransform` places it.
 */
private val TiltRange = -60f..60f

/** Coarse enough to be worth pressing, fine enough that 1.00 and 1.50 are both on the grid. */
private const val ZoomStep = 0.05f

/** Five degrees, so 45, 90 and 180 are all reachable by stepping rather than only by luck. */
private const val RotationStep = 5f
