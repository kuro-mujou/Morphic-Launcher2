package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 * @param what names the value for the buttons' content descriptions — the only per-caller text here, since both
 *   targets are computed from [step].
 */
@Composable
internal fun SteppedSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    what: String,
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
            enabled = down != value,
            onStep = { onValueChange(down) },
            onStepsFinished = onValueChangeFinished,
        )
        MorphicSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
        StudioStepperButton(
            icon = Icons.Default.Add,
            contentDescription = "Increase $what",
            enabled = up != value,
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
