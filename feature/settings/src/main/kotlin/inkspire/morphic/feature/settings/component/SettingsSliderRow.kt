package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import inkspire.morphic.core.designsystem.component.press.repeatingPress
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
 * this keeps.
 *
 * **The steppers hold to repeat, and a hold is one commit.** `Modifier.repeatingPress` — the icon studio's, shared —
 * fires on the press, waits the platform long-press timeout before the first repeat, and reports the end of the gesture
 * separately. So each fire previews and only the release writes, which matters more here than in the studio: a commit is
 * a store write, and for the blur it is a re-blur of the wallpaper. Thirty of those for one hold would be thirty of
 * each. **Reset stays a plain tap** and commits at once, being a single discrete act with nothing to coalesce.
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

    // **A step reads `dragged` rather than the captured `shown`.** A repeat can fire faster than a recomposition, so a
    // captured value would have every fire in a burst stepping from the same place — the value would move once and then
    // sit there while the finger held. Reading the state is always reading the current one.
    fun stepBy(up: Boolean) {
        val from = dragged ?: value
        val next = snappedStep(from, step, up).coerceIn(valueRange)
        if (next == from) return
        dragged = next
        onPreview(next)
    }

    // What the release does, and what a discrete press (reset) does in one go. Clearing the drag is what stops the
    // readout showing the value the finger left rather than the one now stored.
    fun finishSteps() {
        dragged?.let(onCommit)
    }

    fun commitAtOnce(next: Float) {
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
            TapButton(
                icon = Icons.Default.Refresh,
                description = "Reset $what",
                enabled = shown != default,
                onClick = { commitAtOnce(default) },
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
                onStep = { stepBy(up = false) },
                onStepsFinished = ::finishSteps,
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
                onStep = { stepBy(up = true) },
                onStepsFinished = ::finishSteps,
            )
        }
    }
}

/**
 * A stepper: **held, it keeps stepping.**
 *
 * Hand-built rather than an M3 `IconButton`, because that one owns its own click and this needs the gesture — see
 * `Modifier.repeatingPress` for the four details that make a repeat behave. The ripple still comes from the platform
 * indication the modifier drives, and the shape is clipped so it stays inside the slot.
 *
 * **Disabled rather than hidden** at the end of a range, so the row never changes width as a value reaches a bound —
 * which on the caption row would move the readout out from under the finger.
 */
@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    onStepsFinished: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ButtonSlot)
            .clip(CircleShape)
            .repeatingPress(enabled = enabled, onStep = onStep, onStepsFinished = onStepsFinished),
        contentAlignment = Alignment.Center,
    ) {
        StepGlyph(icon, description, enabled)
    }
}

/** The reset, which is one act with nothing to repeat — so it stays the plain M3 button and its plain click. */
@Composable
private fun TapButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(ButtonSlot)) {
        StepGlyph(icon, description, enabled)
    }
}

/** One glyph, dimmed when spent — the disabled treatment M3's own button applies, stated once for both forms. */
@Composable
private fun StepGlyph(icon: ImageVector, description: String, enabled: Boolean) {
    val colors = LocalMorphicColors.current
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (enabled) colors.content else colors.contentMuted.copy(alpha = DisabledGlyphAlpha),
        modifier = Modifier.size(GlyphSize),
    )
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

/** M3's own disabled content alpha, near enough — a glyph that is plainly spent without disappearing. */
private const val DisabledGlyphAlpha = 0.38f
