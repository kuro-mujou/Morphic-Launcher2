package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.designsystem.component.slider.SliderRowStyle
import inkspire.morphic.core.designsystem.component.slider.finestFormat
import inkspire.morphic.core.designsystem.component.slider.finestStep
import inkspire.morphic.core.designsystem.component.slider.snappedStep
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource

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
 * The studio's numeric control — [MorphicSliderRow] in the studio's own dress.
 *
 * **A wrapper rather than a call site, because the studio's edit model is not the settings one.** A settings row
 * previews and then *writes*; here the preview **is** the write — every frame edits the workspace live, and what the
 * gesture's end contributes is the undo boundary, not a value. So [onValueChangeFinished] takes no value, and the
 * committed one is deliberately dropped.
 *
 * The other half is the dress: [studioSliderRowStyle] states why the studio cannot read the theme like every other
 * surface.
 *
 * @param default where reset goes — **the value this control has when untouched**, which is not "the value that does
 *   nothing": an effect seeded to be visible arrives somewhere other than zero. See `BloomDefaults` for how the effect
 *   panels read their targets from the model rather than restating them.
 * @param step how far one press of a stepper moves the value, and the grid a drag lands on. An angle overrides the
 *   derived default with [AngleStep], degrees not being fractions.
 * @param format how the number reads. Matched to [step] by default so a press always moves the digit the readout ends
 *   on; an angle overrides it, since printing 180.00 for one is as wrong as printing 1 for an opacity.
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
    MorphicSliderRow(
        value = value,
        valueRange = valueRange,
        default = default,
        what = label.lowercase(),
        valueLabel = format,
        label = label,
        step = step,
        enabled = enabled,
        onPreview = onValueChange,
        onCommit = { onValueChangeFinished() },
        style = studioSliderRowStyle(),
    )
}

/**
 * The studio's dress for [MorphicSliderRow]: fixed white on glass, at the panel's own scale.
 *
 * **Fixed rather than themed, which is the one place the studio departs from the palette** — the thing behind its
 * panels is a canvas the *user* sets to black or white at will, so a theme-derived color would be unreadable half the
 * time. `StudioSurface` carries the whole argument.
 *
 * The type scale is a step down from a settings row's, because these labels sit in a rail a third of the screen wide
 * and there are six of them stacked.
 */
@Composable
private fun studioSliderRowStyle(): SliderRowStyle = SliderRowStyle(
    labelColor = StudioContentColor.copy(alpha = 0.75f),
    labelStyle = MaterialTheme.typography.labelMedium,
    valueColor = StudioContentColor,
    valueStyle = MaterialTheme.typography.labelMedium,
    readoutBackground = Color.White.copy(alpha = 0.06f),
    glyphColor = StudioContentColor,
)

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
 * A point in the icon's frame: a [Morphic2DPad] to find it with, and a cluster of four arrows plus a center button
 * to land on one exactly.
 *
 * **A 2D pad rather than two sliders**, because the value is a point — something is nudged diagonally as often as
 * along an axis, and two sliders make that two gestures and a mental transpose.
 *
 * **And buttons beside it, because a drag cannot be exact.** A finger on a 140dp pad lands on 0.037, and no amount of
 * care fixes that: the control's resolution is its length in pixels. The pad stays the way you *find* a position; the
 * cluster is how you land on one. [MorphicSliderRow] carries the same argument in one dimension and states why a press
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
            modifier = Modifier.size(40.dp),
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
                modifier = Modifier.size(140.dp),
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
                    modifier = Modifier.size(40.dp),
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
