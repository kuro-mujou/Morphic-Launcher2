package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.slider.Morphic2DPad
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import kotlin.math.ceil
import kotlin.math.floor

/*
 * The vocabulary every studio section is written in — a labeled block, a chip, and the words a layer is named by.
 *
 * **The sections themselves are one file each** (`StudioLayers`, `StudioSource`, `StudioTransform`, `StudioShape`,
 * `StudioEffects`, `StudioPresets`), which is what this file is left over from: they were one `StudioSections.kt`
 * and it reached 1200 lines, at which point "which section is this?" was a scroll rather than a filename. What
 * stays shared is only what more than one section says, and it lives here so a second copy of a chip cannot appear
 * — the same reason `IconPreviewPlate` and `AppPicker` were extracted rather than repeated.
 *
 * A section emits controls and nothing else: no surface, no title, no scroll. Those belong to the host, which is the
 * only thing that knows a section is one of several — so a new section cannot arrive with its own idea of what a panel
 * looks like, and the host can rearrange them (a side rail in landscape) without touching one.
 *
 * There is no "this layer / whole icon" scope toggle, and that is a simplification the model earned rather than a
 * decision taken here. L1's editor mixed per-layer tools (transform, color, shadow) with whole-icon ones (icon shape,
 * background, theming, size, skin, pack) in one flat row, and its UI plan left the split as an open question. In L2
 * every one of those whole-icon tools has already gone somewhere else: the tile shape became a *per-layer* shape (there
 * is no stack-level mask), the background is the background layer's source, theming is `AppDefaultMonochrome` on the
 * foreground, sizing is `data:settings` and a different screen entirely, the skin is deferred, and an icon pack **is**
 * a per-layer source. So every section but Presets and More acts on one layer, and the question does not arise.
 */

@Composable
internal fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = StudioContentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        content()
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ChoiceChip(label = label, selected = selected, modifier = Modifier.fillMaxWidth(), onClick = onClick)
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        color = StudioContentColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** What a row calls the layer. The role, not the index — "layer 2" tells the user nothing. */
internal val LayerRole.label: String
    get() = when (this) {
        LayerRole.FOREGROUND -> "Foreground"
        LayerRole.BACKGROUND -> "Background"
        LayerRole.CUSTOM -> "Layer"
    }

/** The one-line summary of where a layer's pixels come from, shown beside its role. */
internal val LayerSource.label: String
    get() = when (this) {
        // What a freshly added layer says about itself, and it has to say *something*: the row is the only place an
        // empty layer is visible at all, since it draws nothing on the canvas.
        LayerSource.Empty -> "empty"
        LayerSource.AppDefault -> "app default"
        LayerSource.AppDefaultMonochrome -> "monochrome"
        is LayerSource.CustomImage -> "image"
        is LayerSource.SolidFill -> "solid color"
        is LayerSource.IconPack -> "icon pack"
    }

/**
 * The full form of a numeric control: a caption row carrying the name, the **value** and a **reset**, over a
 * [SteppedSlider].
 *
 * **The value is a readout of its own rather than part of the label**, which every one of these used to bake in
 * (`"Hue  180°"`). Two reasons: a name that changes as you drag is not a name, and a number wants to sit where the
 * eye returns to it — beside the control — not appended to prose on the far left.
 *
 * **Reset is a button because the alternative is remembering.** These values have a resting position that is easy
 * to leave and hard to find again: a slider dragged to 0.98 looks like 1.00 and is not, and the stepper buttons
 * only get you back if you can see that you are off. It is **disabled at [default]**, so the row doubles as the
 * answer to "have I changed this?" — the same "ask, do not guess" rule the layer reorder buttons and the transform
 * cluster are built on.
 *
 * **Which makes [default] "the value this arrives at", not "the value that does nothing".** The two are the same for
 * an adjustment, whose untouched state *is* its identity — and they came apart the moment effects began arriving
 * seeded at values chosen to be visible. Every `Strength` reset was pinned to zero on the old reading, so opening a
 * fresh effect lit every reset in the panel, claiming changes the user had not made, and pressing one took the
 * effect to invisible rather than back to what they had just been handed. See `BloomDefaults` for how the effect
 * panel now reads its targets from the model rather than restating them.
 *
 * A press is discrete, so it commits at once and is one undo step.
 *
 * @param default where reset goes — **the value this control has when untouched**. Deliberately per call site and
 *   not `valueRange.start`: a zoom rests at 1 in the middle of its range, a hue at the start, and a seeded effect's
 *   strength wherever that effect chose to arrive.
 * @param step how far one press of a stepper moves the value. Defaults to [finestStep], which is what almost every
 *   caller wants; an angle overrides it, degrees not being fractions.
 * @param format how the number reads. Defaults to [finestFormat], matched to [step] so a press always moves the
 *   digit the readout ends on. An angle overrides it, since printing 180.00 for one is as wrong as printing 1 for
 *   an opacity.
 * @param enabled false to show the control **spent rather than absent** — dimmed, unmoved and unpressable, with its
 *   value still legible.
 *
 *   This is a deliberate exception to the studio's own "a control that changes nothing is worse than a missing one",
 *   and it earns one where the gate is a *continuous control sitting directly above it*: the grain's angle means
 *   nothing until its directionality leaves zero, and hiding it made a row appear and disappear **under the finger
 *   that was dragging the slider above it**, moving everything below mid-gesture. A row that grays out states the
 *   dependency without ever moving the panel. Where the gate is a discrete choice made elsewhere — a shape picked, a
 *   tint set — absent is still right, because the layout settles before the finger arrives.
 */
@Composable
internal fun SliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    default: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    step: Float = finestStep(valueRange),
    format: (Float) -> String = { finestFormat(valueRange).format(it) },
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                // Dimmed together with everything else in the row, so "spent" reads as one state rather than as a
                // slider that happens not to respond.
                color = StudioContentColor.copy(alpha = if (enabled) 0.75f else 0.3f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = format(value),
                color = StudioContentColor.copy(alpha = if (enabled) 1f else 0.4f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            StudioIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Reset ${label.lowercase()}",
                enabled = enabled && value != default,
                onClick = {
                    onValueChange(default)
                    onValueChangeFinished()
                },
                modifier = Modifier.size(ResetSlot),
            )
        }
        SteppedSlider(
            value = value,
            valueRange = valueRange,
            step = step,
            what = label.lowercase(),
            enabled = enabled,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

/** Smaller than a stepper, because it sits in a caption row rather than beside the track. */
private val ResetSlot = 32.dp

/**
 * How far one press of a stepper moves a value on [range] — **the finest move its readout can report**, which is
 * the whole job of these buttons.
 *
 * **A stepper is for the last little bit the slider cannot reach, not for travelling.** A finger on a 250dp track
 * lands on 0.37 and the point of a press is to reach 0.38; a step chosen to feel "worth pressing" cannot express
 * that, so the control meant to make an edit exact was the one rounding it off. Holding is what pays for a fine
 * step — [SteppedSlider]'s buttons repeat, so crossing a range is a hold rather than a hundred taps, and a hold is
 * still **one** undo entry because `onValueChangeFinished` closes it. Coarse travel and fine correction out of the
 * same button.
 *
 * **It is paired with [finestFormat] and must stay so**: a step below what the number on screen can show is a press
 * that visibly does nothing, which is worse than a coarse one. That pairing is why both are derived from the range
 * here rather than chosen per slider — thirty call sites each picking a step *and* a matching format is thirty
 * chances for the two to disagree, and the symptom of disagreeing is a dead-looking button.
 *
 * **Narrow ranges get the extra digit**, which is where this earns its keep. Half the effect sliders run 0..0.1 or
 * 0..0.2 — a blur radius, a ripple's amplitude, a halo's spread — and against a two-decimal readout one press moved
 * five to ten percent of everything the control could express, on exactly the values where a small difference is
 * the point. The cut is at half a unit: wider than that and a hundredth is already a fine move, narrower and it is
 * a tenth of the whole range.
 */
internal fun finestStep(range: ClosedFloatingPointRange<Float>): Float =
    if (range.endInclusive - range.start >= FineRangeSpan) 0.01f else 0.001f

/** The readout [finestStep] is matched to — one more digit exactly where the step gains one. */
internal fun finestFormat(range: ClosedFloatingPointRange<Float>): String =
    if (range.endInclusive - range.start >= FineRangeSpan) "%.2f" else "%.3f"

/**
 * Where a range stops being "about a unit" and starts being a fine quantity.
 *
 * Half a unit rather than a whole one, because several sliders run `0.05..1` or `0.05..1.5` and are plainly the
 * same *kind* of value as the `0..1` ones beside them — a threshold of 1 would have given those an extra decimal
 * for the sake of the 0.05 missing from the bottom of their track.
 */
private const val FineRangeSpan = 0.5f

/**
 * One degree — the finest turn a `"%.0f°"` readout can report, and **the one step in the studio still stated rather
 * than derived**.
 *
 * [finestStep] answers for fractions, which is what every other slider here carries; an angle is not one, so it is
 * the single exception and it lives beside the rule rather than in one of the two sections that use it. Both do:
 * the transform panel's rotation and tilt, and the effects panel's gradient and pattern angles.
 *
 * It was five degrees in both places, chosen so 45, 90 and 180 sat on the grid. On a grid of one degree every whole
 * angle is reachable, those three included — so nothing was lost by making it the smallest move instead.
 */
internal const val AngleStep = 1f

/**
 * A slider between a pair of buttons that step it onto the nearest grid value.
 *
 * **A drag cannot be exact and these values have exact answers people want.** A finger on a 250dp slider lands on
 * 0.037 and 87°, and no amount of care fixes that — the control's resolution is its length in pixels. The slider
 * stays the way you *find* a value; the buttons are how you land on one.
 *
 * **The buttons snap to a grid rather than adding to the current value**, which is the detail that makes them worth
 * having: from 1.037 a plain `+0.05` gives 1.087 and every later press keeps the same debris, where snapping gives
 * 1.05 and one press the other way gives exactly 1.00. So the round numbers are always at most one press away, and
 * stepping from a dragged value cleans it up instead of preserving it. See [snappedStep].
 *
 * A **disabled** button is one whose target is where the value already is or outside the range — the "ask, do not
 * guess" rule the layer reorder buttons use, so a press that would do nothing says so first.
 *
 * **One [onValueChange] for both the drag and the press**, which is a simplification the second consumer paid for.
 * This carried a separate `onStepTo` while it served zoom and rotation alone, and both call sites passed exactly the
 * same lambda to the two — a parameter pair that has to agree is a parameter pair that will one day not, and the
 * only thing that distinguished them was a rule about committing that neither of them was doing.
 *
 * Neither of them commits, deliberately: a held button repeats, so [onValueChangeFinished] is what closes a drag
 * *and* a hold into one undo step. See `StudioStepperButton`.
 *
 * **Private to [SliderControl], which is what makes "every slider has a readout and a reset" structural.** It was
 * `internal`, and exactly one section reached past the wrapper for it — the bloom's linear position — which came out
 * as the one control in the studio with a track, two buttons, and no way to see what it was set to or put it back.
 * That is not a thing a caller should be able to choose by accident: this is the *mechanism*, and the caption row is
 * not decoration on top of it but the half that answers "what is this?". A section wanting a bare track now has to
 * make that argument by changing this line.
 *
 * @param what names the value for the buttons' content descriptions — the only per-caller text here, since both
 *   targets are computed from [step].
 */
@Composable
private fun SteppedSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    what: String,
    step: Float = finestStep(valueRange),
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val down = snappedStep(value, step, up = false).coerceIn(valueRange)
    val up = snappedStep(value, step, up = true).coerceIn(valueRange)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioStepperButton(
            icon = Icons.Default.Remove,
            contentDescription = "Decrease $what",
            enabled = enabled && down != value,
            onStep = { onValueChange(down) },
            onStepsFinished = onValueChangeFinished,
        )
        MorphicSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
        )
        StudioStepperButton(
            icon = Icons.Default.Add,
            contentDescription = "Increase $what",
            enabled = enabled && up != value,
            onStep = { onValueChange(up) },
            onStepsFinished = onValueChangeFinished,
        )
    }
}

/**
 * The next multiple of [step] beyond [value], in the direction [up] names.
 *
 * **A grid position, not an addition**, which is what lets one press clean up a dragged value: 1.037 steps down to
 * 1.00 rather than to 0.987, and every value on the way is a number somebody could have meant. A value already on the
 * grid moves a full step, so repeated presses walk it evenly.
 *
 * The epsilon is what stops a value that *is* on the grid — arrived at by an earlier press — being read as a hair
 * below it and stepping only to itself, which would present as a button that works every other press.
 */
internal fun snappedStep(value: Float, step: Float, up: Boolean): Float {
    val steps = value / step
    val target = if (up) floor(steps + SnapEpsilon) + 1f else ceil(steps - SnapEpsilon) - 1f
    return target * step
}

/** Small against any step here, large against the float error of adding them up. */
private const val SnapEpsilon = 1e-4f

/**
 * A point in the icon's frame: a [Morphic2DPad] to find it with, and a cluster of four arrows plus a center button
 * to land on one exactly.
 *
 * **A 2D pad rather than two sliders**, because the value is a point — something is nudged diagonally as often as
 * along an axis, and two sliders make that two gestures and a mental transpose.
 *
 * **And buttons beside it, because a drag cannot be exact.** A finger on a 140dp pad lands on 0.037, and no amount of
 * care fixes that: the control's resolution is its length in pixels. The pad stays the way you *find* a position; the
 * cluster is how you land on one. [SteppedSlider] carries the same argument in one dimension and states why a press
 * snaps to the grid rather than adding to the value.
 *
 * **The center of a direction pad is where "back to the middle" belongs** — the one arrangement where the control's
 * shape states what it does, and it costs no row of its own. It is disabled while the point is already centered, so
 * the cluster doubles as the answer to "is it?", which the pad's knob only approximates. Arrows disable at the edge of
 * the range for the same reason: a press that would do nothing says so first.
 *
 * **Extracted on its second consumer**, which is the rule `IconPreviewPlate` and `AppPicker` also arrived by. It was
 * the transform section's alone until a bloom needed somewhere to sit; two copies would have been two answers to
 * where the range ends and how far one press moves.
 *
 * Dragging edits live and commits when the gesture *ends*, so undo steps over the whole drag rather than through a
 * hundred frames of it. A button press is discrete, so it commits at once and is one undo step.
 */
@Composable
internal fun PositionPad(
    x: Float,
    y: Float,
    onValueChange: (x: Float, y: Float) -> Unit,
    onCommit: () -> Unit,
    range: ClosedFloatingPointRange<Float> = PositionRange,
) {
    // **The pad's own range decides the nudge, exactly as it decides the readout.** It was a flat hundredth of the
    // frame whatever the range — which is 1% of the travel on a full-frame offset and **10%** on a chromatic split's
    // ±0.05, so on the narrow pads the arrows were the jump they exist not to be. And since the readout beneath
    // already prints through `finestFormat`, those pads showed three decimals while a press moved ten units in the
    // last one: the step and the number disagreeing, which is the pairing `finestStep` was written to hold together.
    // The sliders were given that rule and the pad was not, because its constant predates it.
    val step = finestStep(range)

    @Composable
    fun Arrow(icon: ImageVector, description: String, dx: Int, dy: Int) {
        val target = x.nudged(dx, range, step) to y.nudged(dy, range, step)
        StudioStepperButton(
            icon = icon,
            contentDescription = description,
            enabled = target != (x to y),
            onStep = { onValueChange(target.first, target.second) },
            onStepsFinished = onCommit,
            modifier = Modifier.size(NudgeSlot),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Morphic2DPad(
                x = x,
                y = y,
                onValueChange = onValueChange,
                xRange = range,
                yRange = range,
                onValueChangeFinished = onCommit,
                modifier = Modifier.size(PadSide),
            )

            // **The pad says where, the readout says how far** — the same pairing every slider here has, and it was
            // missing for the same reason the bloom's linear position had no number: a pad is a picture, and a knob
            // three pixels off center is indistinguishable from one on it. A user cannot report a value they cannot
            // read, and cannot tell a nudged position from a resting one by looking.
            //
            // Both axes through [finestFormat] on the pad's own range, so a chromatic split's couple of percent
            // prints the digit it needs while an ordinary offset stays at two — the same rule the sliders follow,
            // and the reason it is derived from the range rather than fixed here.
            val format = finestFormat(range)
            Text(
                text = "${format.format(x)}, ${format.format(y)}",
                color = StudioContentColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Arrow(Icons.Default.KeyboardArrowUp, "Nudge up", 0, -1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nudge left", -1, 0)
                StudioIconButton(
                    icon = Icons.Default.CenterFocusStrong,
                    contentDescription = "Center",
                    enabled = x != 0f || y != 0f,
                    onClick = {
                        onValueChange(0f, 0f)
                        onCommit()
                    },
                    modifier = Modifier.size(NudgeSlot),
                )
                Arrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nudge right", 1, 0)
            }
            Arrow(Icons.Default.KeyboardArrowDown, "Nudge down", 0, 1)
        }
    }
}

/**
 * This coordinate moved one nudge in the direction [direction] names (`-1`, `0`, `+1`), clamped to the pad's own
 * range so a button can never leave the point somewhere the pad cannot show.
 *
 * **Snapped like the sliders, and here it is load-bearing rather than tidy.** Adding `0.01` repeatedly accumulates
 * float error, so a point nudged twenty steps out and twenty back would land near zero rather than on it — leaving
 * the Center button lit, and lit *forever*, over an offset too small to see. Stepping onto the grid means the way
 * back is exactly the way out.
 */
private fun Float.nudged(direction: Int, range: ClosedFloatingPointRange<Float>, step: Float): Float =
    if (direction == 0) this else snappedStep(this, step, up = direction > 0).coerceIn(range)

/**
 * Half a frame either way — which puts the point on an edge at the ends and off it nowhere.
 *
 * The default a [PositionPad] takes, and shared with any one-dimensional control over the same field so the pad, the
 * nudge buttons and a slider all agree about where the ends are.
 *
 * **A parameter rather than a constant everywhere, because not every point is an offset.** A chromatic split is a
 * displacement of a few percent, and a pad whose useful travel was the middle tenth of it would be a control you
 * could not hold still — so that one passes its own range and gets the same pad at a scale it can be dragged in.
 */
internal val PositionRange = -0.5f..0.5f

/** The pad, and one cell of the cluster beside it — equal to `StudioIconButton`'s own side. */
private val PadSide = 140.dp
private val NudgeSlot = 40.dp

/**
 * Which page of a pager is showing. Not a control — pressing one is not offered, since swiping is the gesture.
 *
 * Shared vocabulary since the effects grid grew a pager of its own; the two must not drift into different dots.
 *
 * **Centered under the pages it counts.** It filled the width and packed to the start, which left two or three dots
 * huddled in a corner of a panel they belonged to the whole of — reading as something left over rather than as the
 * position marker for what is above them. Centring is also what makes the count legible at a glance: dots either
 * side of a middle is a length the eye measures, where a left-packed row has to be counted.
 *
 * Both consumers get it, which is the reason this is one component: the effects grid and the shape chooser must not
 * end up with their pagers marked differently.
 */
@Composable
internal fun PagerDots(current: Int, count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StudioContentColor.copy(alpha = if (index == current) 1f else 0.3f)),
            )
        }
    }
}
