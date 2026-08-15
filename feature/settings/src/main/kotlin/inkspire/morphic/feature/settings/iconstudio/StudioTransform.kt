package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.runtime.Composable
import inkspire.morphic.core.model.icon.IconLayerSpec

/**
 * Position, zoom, rotation and tilt.
 *
 * **Tilt is here rather than in Effects, and that is the model's call rather than this panel's.** Leaning a layer out
 * of the plane says *where the layer sits*, which is what every other control on this panel does — so it is two more
 * `IconLayerSpec` fields resolved through `LayerTransform`, not a `LayerEffect`. As an effect its position in the list
 * would be orderable against a color matrix while the in-plane rotation's was not, which is one rotation being two
 * kinds of thing.
 *
 * **Every control has buttons beside it, because a drag cannot be exact and these values have exact answers people
 * want.** Centered, 1.00×, 0°, 90° — a finger on a 140dp pad or a 250dp slider lands on 0.037 and 87°, and no amount
 * of care fixes that: the control's resolution is its length in pixels. The pad and the sliders stay the way you
 * *find* a value; the buttons are how you land on one. [SliderControl] carries that for the sliders and states
 * the whole argument, including why a press snaps to the grid instead of adding to the value; [PositionPad] is the
 * same idea in two dimensions, and is shared with the bloom's own position now that it has one.
 *
 * **The four sliders are [SliderControl]s, which is the form every effect already uses** — the name on the left, the
 * value in a readout of its own beside the control, and a **reset** disabled at the default, so the row doubles as the
 * answer to "have I changed this?". They were `LabeledControl("Zoom  1.00")` before, which baked the number into the
 * name: a label that changes as you drag is not a label, the number sat on the far left where the eye does not return
 * to it, and there was no way back to the resting value but to find it again by hand. Nothing was extracted for this —
 * the component was already there, and Transform was the one section not using it.
 *
 * **One press is the smallest move each value has, and only the angles say so here.** Zoom takes `SliderControl`'s
 * derived step, which is the finest its readout can report; rotation and tilt pass [AngleStep], degrees not being
 * fractions. Both were coarse — 0.05 and 5° — picked so a press felt "worth pressing", which is the slider's job.
 * See `finestStep` for the whole argument and for why the step and the readout are derived together.
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

    SliderControl(
        label = "Zoom",
        value = spec.zoom,
        valueRange = ZoomRange,
        default = ZoomDefault,
        onValueChange = { value -> onUpdate { it.copy(zoom = value) } },
        onValueChangeFinished = onCommit,
        format = { "%.2f×".format(it) },
    )

    SliderControl(
        label = "Rotation",
        value = spec.rotation,
        valueRange = RotationRange,
        step = AngleStep,
        default = AngleDefault,
        onValueChange = { value -> onUpdate { it.copy(rotation = value) } },
        onValueChangeFinished = onCommit,
        format = { "%.0f°".format(it) },
    )

    // **Two sliders and no 2D pad, unlike Position** — the two tilts are not a point. A pad's knob says "the thing is
    // *here*", which is true of an offset and meaningless of a pair of angles; and unlike the offsets these two are
    // usually wanted one at a time, since a lean about both axes at once reads as a mistake rather than as depth.
    //
    // Named for the axis each turns *around*, which is `Camera.rotateX`'s convention and Compose's — so Tilt X leans
    // the top away and Tilt Y leans the left away. See `IconLayerSpec.tiltX`.
    SliderControl(
        label = "Tilt X",
        value = spec.tiltX,
        valueRange = TiltRange,
        step = AngleStep,
        default = AngleDefault,
        onValueChange = { value -> onUpdate { it.copy(tiltX = value) } },
        onValueChangeFinished = onCommit,
        format = { "%.0f°".format(it) },
    )

    SliderControl(
        label = "Tilt Y",
        value = spec.tiltY,
        valueRange = TiltRange,
        step = AngleStep,
        default = AngleDefault,
        onValueChange = { value -> onUpdate { it.copy(tiltY = value) } },
        onValueChangeFinished = onCommit,
        format = { "%.0f°".format(it) },
    )
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

/**
 * Where a reset lands. Per call site rather than `valueRange.start`, which is [SliderControl]'s own rule and is why
 * these are two constants and not one: a zoom rests at 1 in the middle of its range, an angle at 0 — which is the
 * *start* of [RotationRange] and the *middle* of [TiltRange].
 */
private const val ZoomDefault = 1f
private const val AngleDefault = 0f
