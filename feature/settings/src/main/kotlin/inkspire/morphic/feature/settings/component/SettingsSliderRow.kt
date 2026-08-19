package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.feature.settings.iconstudio.finestStep
import inkspire.morphic.feature.settings.iconstudio.snappedStep

/**
 * A numeric control in the **icon studio's** shape: a caption row carrying the name, the value and a reset, over a
 * track flanked by a minus and a plus.
 *
 * **The studio's form, not the studio's component**, and the split is deliberate. What is genuinely shared is the
 * *arithmetic* — [snappedStep] and [finestStep], which decide where a press lands and how the readout is formatted, and
 * which would be silently wrong if written twice: a stepper that adds rather than snapping to the grid takes 1.037 to
 * 0.987 instead of to 1.00, and nobody reads a number that plausible as a bug. What is not shared is the **chrome**: the
 * studio fixes its content color to white on purpose (its canvas is a checkerboard the user switches between black and
 * white), where a settings pane follows the system's light/dark theme, so reusing `SliderControl` verbatim would put
 * white text on a white pane. This reads `LocalMorphicColors` instead. Both live in `feature:settings`, which is what
 * makes sharing the arithmetic a plain import rather than an extraction.
 *
 * **Preview and commit are separate, as everywhere else in settings.** [onPreview] fires per frame so a live preview can
 * follow the drag, [onCommit] fires on release so the store is written once — see [SettingsCommitSlider], whose contract
 * this keeps. The steppers and reset are *discrete*, so they preview and commit in one go: there is no drag to coalesce.
 *
 * **Reset is disabled at [default]**, which is what makes the row double as the answer to "have I changed this?" — the
 * studio's reasoning, kept. And [default] is *the value this control arrives at* rather than the value that does
 * nothing: they are the same for most things here and come apart for anything seeded to be visible.
 *
 * @param label the control's name, shown at the head of the caption row. **Null draws no name**, which is right where
 *   the thing above the row already is the label — the effects section's tint amount sits directly under a row of tint
 *   swatches, and calling it "Tint" a second time would be repeating the heading in the row it belongs to. The value and
 *   reset then sit at the end, where the eye already goes for them.
 * @param what names the value for accessibility and for the reset's description. Separate from [label] because a
 *   nameless row still has to announce what its buttons do.
 * @param step how far one press of a stepper moves the value. Defaults to the finest move the readout can report, which
 *   is what makes a press always change the digit the number ends on.
 */
@Composable
internal fun SettingsSliderRow(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    default: Float,
    what: String,
    valueLabel: (Float) -> String,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    onPreview: (Float) -> Unit = {},
    step: Float = finestStep(valueRange),
) {
    val colors = LocalMorphicColors.current

    // The dragged value, keyed on the incoming one so a commit — or a change from anywhere else — re-seeds it. The same
    // in-and-out shape `SettingsCommitSlider` uses, and the reason the readout can track a finger that has written
    // nothing yet.
    var dragged by remember(value) { mutableStateOf<Float?>(null) }
    val shown = dragged ?: value

    // A press commits at once, so it also clears the drag: leaving one in place would make the readout keep showing the
    // value the finger left rather than the one the button just chose.
    fun commit(next: Float) {
        dragged = null
        onPreview(next)
        onCommit(next)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CaptionGap),
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.content,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Pushes the value and reset to the end, which is where they sit in the named form too.
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = valueLabel(shown),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(ReadoutCorner))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = ReadoutPadH, vertical = ReadoutPadV),
            )
            StepButton(
                icon = Icons.Default.Refresh,
                description = "Reset $what",
                enabled = shown != default,
                onClick = { commit(default) },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StepGap),
        ) {
            val down = snappedStep(shown, step, up = false).coerceIn(valueRange)
            val up = snappedStep(shown, step, up = true).coerceIn(valueRange)
            StepButton(
                icon = Icons.Default.Remove,
                description = "Decrease $what",
                enabled = down != shown,
                onClick = { commit(down) },
            )
            MorphicSlider(
                value = shown,
                onValueChange = {
                    dragged = it
                    onPreview(it)
                },
                modifier = Modifier.weight(1f),
                valueRange = valueRange,
                onValueChangeFinished = { dragged?.let(onCommit) },
            )
            StepButton(
                icon = Icons.Default.Add,
                description = "Increase $what",
                enabled = up != shown,
                onClick = { commit(up) },
            )
        }
    }
}

/**
 * One of the row's three small buttons — the two steppers and the reset.
 *
 * Plain M3 `IconButton`, unlike the studio's glass-faced one: a settings pane is an ordinary surface, and the disabled
 * treatment M3 already applies is the one the rest of this screen uses. **Disabled rather than hidden**, so the row
 * never changes width as a value reaches a bound — which on the caption row would move the readout under the finger.
 *
 * No press-and-hold repeat, which the studio's stepper has. A settings value has a coarse enough range that a tap is a
 * correction rather than a way to travel, and a repeat would need the two-callback shape a hold requires to stay one
 * undo step. It is the thing to add if a range ever wants it.
 */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(ButtonSlot)) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(GlyphSize))
    }
}

private val RowGap = 4.dp
private val CaptionGap = 8.dp
private val StepGap = 4.dp
private val ReadoutCorner = 6.dp
private val ReadoutPadH = 8.dp
private val ReadoutPadV = 2.dp

/** Small enough that the track keeps most of the width, large enough to stay a comfortable target. */
private val ButtonSlot = 36.dp
private val GlyphSize = 18.dp
