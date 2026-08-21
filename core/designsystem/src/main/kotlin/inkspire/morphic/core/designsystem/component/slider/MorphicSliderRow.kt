package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.press.repeatingPress
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * The launcher's numeric control: a caption row carrying the name, the **value** and a **reset**, over a track flanked
 * by a **minus** and a **plus**.
 *
 * One component for every surface that sets a number — the icon studio's panels, the effects section, and each layout
 * section's metrics — because these are the same control doing the same job, and the parts that keep them honest are
 * the parts that fail *silently* when they are written twice. [snappedStep] and [quantizedTo] are one such part; the
 * three rules below are the rest.
 *
 * **The value is a readout of its own rather than part of the label.** A name that changes as you drag is not a name,
 * and a number wants to sit where the eye returns to it — beside the control — not appended to prose on the far left.
 *
 * **Reset is a button because the alternative is remembering.** These values have a resting position that is easy to
 * leave and hard to find again: a slider dragged to 0.98 looks like 1.00 and is not. It is **disabled at [default]**,
 * so the row doubles as the answer to "have I changed this?".
 *
 * **A drag finds a value; the buttons land on one.** A finger on a 250dp track cannot be exact — the control's
 * resolution is its length in pixels — so the steppers walk the value along the grid [step] names, and they *snap*
 * onto it rather than adding to it: from 1.037 a press gives 1.00 or 1.05, never 0.987 or 1.087. The drag lands on the
 * same grid ([quantizedTo]), so the readout, the thumb and the store all hold one number rather than three.
 *
 * ## The value in flight
 *
 * A commit is asynchronous wherever it is a store write, so this holds the value the gesture is *claiming* and shows
 * that in preference to [value]. Three details, each of which has been a bug on this screen family:
 *
 * - **Every callback reads the claim from state; none of them captures it.** A held stepper repeats faster than
 *   composition, so a fire that stepped from a captured value would step from wherever the value was when the frame
 *   was built — the value moves once and then sits still under a finger that is still holding.
 * - **The claim lives in one stable object, cleared by a write — never `remember(value)`.** Keying the `remember` on
 *   the incoming value *recreates* the holder, so a callback Compose had already memoised goes on writing to the dead
 *   one: a stepper's fire and its release then hold different instances, and every press silently fails to commit.
 * - **A claim expires** ([EchoWindowMs]). Clearing it when a new [value] arrives is the normal path, and it is not
 *   enough on its own: a store that quantizes, clamps or rejects the write can answer with the value it already had,
 *   and there is no arrival to observe. Without an expiry the row would go on showing a number nothing holds.
 *
 * @param default where reset goes — **the value this control has when untouched**, which is not always the value that
 *   does nothing: a zoom rests at 1 in the middle of its range, and a seeded effect wherever that effect chose to
 *   arrive. Read it from the model that owns the default rather than typing it here, or the reset will one day light
 *   up on a control nobody has touched.
 * @param what names the value for the buttons' content descriptions, and is what a nameless row announces itself as.
 * @param label the control's name. **Null draws no name**, which is right where the thing directly above the row
 *   already is the label — an amount under the swatches it applies to. The value and reset then sit at the end, where
 *   the eye already goes for them.
 * @param step how far one press moves the value, and the grid a drag lands on. Defaults to [finestStep], which is
 *   right for any fraction; **anything stored in coarser units must say so** — whole dp, whole degrees — because a
 *   step finer than the store's resolution is a press that writes the value it already had, which reads as a dead
 *   button.
 * @param onPreview fires per frame of a drag and per fire of a held stepper. It must not write: it is what lets a
 *   caller move a live preview without a store transaction per frame.
 * @param onCommit fires once, when the gesture ends — and at once for a reset, which is one discrete act.
 * @param enabled false shows the control **spent rather than absent** — dimmed, unmoved and unpressable, with its
 *   value still legible. A deliberate exception to "a control that changes nothing is worse than a missing one", and
 *   it earns one only where the gate is a *continuous* control sitting directly above: hiding the row would move
 *   everything below it under the finger that is dragging that control.
 */
@Composable
fun MorphicSliderRow(
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
    enabled: Boolean = true,
    style: SliderRowStyle = SliderRowDefaults.style(),
) {
    val flight = rememberValueInFlight(value)
    val current by rememberUpdatedState(value)
    val shown = flight.claim ?: value

    // Reads the claim rather than `shown`, for the reason in this component's KDoc: a repeat can outrun composition.
    fun move(next: Float) {
        val bounded = next.coerceIn(valueRange)
        if (bounded == (flight.claim ?: current)) return
        flight.claim = bounded
        flight.unsent = bounded
        onPreview(bounded)
    }

    fun stepBy(up: Boolean) = move(snappedStep(flight.claim ?: current, step, up))

    fun release() = flight.release(onCommit)

    // A reset is discrete, so it previews and commits in one go — there is no gesture to coalesce.
    fun resetTo(target: Float) = flight.commit(target, onPreview, onCommit)

    val down = snappedStep(shown, step, up = false).coerceIn(valueRange)
    val up = snappedStep(shown, step, up = true).coerceIn(valueRange)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        SliderCaption(
            label = label,
            valueText = valueLabel(shown),
            what = what,
            enabled = enabled,
            // **"Off its default" to within half a step, not `!=`.** A value walked onto the grid is `n × step` in
            // float arithmetic, which lands a hair either side of a default written as a literal — 40 hundredths is
            // 0.39999999 and the default is 0.40000001. Compared exactly, a reset stayed lit on a control reading
            // exactly its default value, which is the one thing this button must never do: it is the row's answer to
            // "have I changed this?". Half a step is the honest tolerance, being the point below which no readout
            // paired with this step can show a difference at all.
            canReset = abs(shown - default) > step / 2f,
            onReset = { resetTo(default) },
            style = style,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StepGap),
        ) {
            StepperButton(
                glyph = SliderRowGlyph.MINUS,
                description = "Decrease $what",
                enabled = enabled && down != shown,
                onStep = { stepBy(up = false) },
                onStepsFinished = ::release,
                style = style,
            )
            MorphicSlider(
                value = shown,
                onValueChange = { move(quantizedTo(it, step)) },
                modifier = Modifier.weight(1f),
                valueRange = valueRange,
                enabled = enabled,
                onValueChangeFinished = ::release,
            )
            StepperButton(
                glyph = SliderRowGlyph.PLUS,
                description = "Increase $what",
                enabled = enabled && up != shown,
                onStep = { stepBy(up = true) },
                onStepsFinished = ::release,
                style = style,
            )
        }
    }
}

/**
 * [MorphicSliderRow] over a **whole-number** value — a dp metric, a count.
 *
 * **Its own overload rather than a step and a rounding lambda at each call site**, because those two have to agree and
 * nothing checks that they do: a slider stepping by a hundredth under a readout printing whole dp is a control whose
 * buttons appear dead, and the store then receives a hundred writes of the number it already had. Here the step *is*
 * the unit, and the value the caller gets is the value the readout printed.
 */
@Composable
fun MorphicSliderRow(
    value: Int,
    valueRange: IntRange,
    default: Int,
    what: String,
    valueLabel: (Int) -> String,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    onPreview: (Int) -> Unit = {},
    enabled: Boolean = true,
    style: SliderRowStyle = SliderRowDefaults.style(),
) {
    MorphicSliderRow(
        value = value.toFloat(),
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        default = default.toFloat(),
        what = what,
        valueLabel = { valueLabel(it.roundToInt()) },
        onCommit = { onCommit(it.roundToInt()) },
        modifier = modifier,
        label = label,
        onPreview = { onPreview(it.roundToInt()) },
        step = 1f,
        enabled = enabled,
        style = style,
    )
}

/**
 * The two-thumb form: a caption row over a **range** whose ends cannot cross.
 *
 * **No steppers, and that is the one difference from [MorphicSliderRow] rather than an omission** — a press would have
 * to pick a thumb, and a control with two of them has no answer to "which one did you mean". Reset covers what the
 * steppers cover elsewhere: it is the exact value that is hard to get back to by dragging.
 *
 * Whole numbers, because both consumers of a pair of bounds so far are dp. The thumbs are **rounded rather than
 * stepped**: a discrete M3 track draws a tick per step, which over a hundred dp reads as a ruler.
 */
@Composable
fun MorphicRangeSliderRow(
    value: IntRange,
    bounds: IntRange,
    default: IntRange,
    what: String,
    valueLabel: (IntRange) -> String,
    onCommit: (IntRange) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    onPreview: (IntRange) -> Unit = {},
    style: SliderRowStyle = SliderRowDefaults.style(),
) {
    val flight = rememberValueInFlight(value)
    val current by rememberUpdatedState(value)
    val shown = flight.claim ?: value

    fun move(next: IntRange) {
        if (next == (flight.claim ?: current)) return
        flight.claim = next
        flight.unsent = next
        onPreview(next)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        SliderCaption(
            label = label,
            valueText = valueLabel(shown),
            what = what,
            enabled = true,
            canReset = shown != default,
            onReset = { flight.commit(default, onPreview, onCommit) },
            style = style,
        )
        MorphicRangeSlider(
            value = shown.first.toFloat()..shown.last.toFloat(),
            onValueChange = { move(it.start.roundToInt()..it.endInclusive.roundToInt()) },
            valueRange = bounds.first.toFloat()..bounds.last.toFloat(),
            onValueChangeFinished = { flight.release(onCommit) },
        )
    }
}

/**
 * The name, the number and the reset — one line, shared by every form above so a second one cannot arrive with the
 * readout on the other side.
 */
@Composable
private fun SliderCaption(
    label: String?,
    valueText: String,
    what: String,
    enabled: Boolean,
    canReset: Boolean,
    onReset: () -> Unit,
    style: SliderRowStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CaptionGap),
    ) {
        if (label != null) {
            Text(
                text = label,
                // Dimmed with the rest of the row, so "spent" reads as one state rather than as a slider that
                // happens not to respond.
                color = style.labelColor.dimmedUnless(enabled),
                style = style.labelStyle,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Pushes the value and reset to the end, which is where they sit in the named form too.
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = valueText,
            color = style.valueColor.dimmedUnless(enabled),
            style = style.valueStyle,
            modifier = Modifier
                .clip(RoundedCornerShape(ReadoutCorner))
                .background(style.readoutBackground)
                .padding(horizontal = ReadoutPadH, vertical = ReadoutPadV),
        )
        GlyphButton(
            glyph = SliderRowGlyph.RESET,
            description = "Reset $what",
            enabled = enabled && canReset,
            slot = ResetSlot,
            style = style,
            // A tap, not a repeat: there is one place to go and pressing again would not move it.
            interaction = Modifier.clickable(enabled = enabled && canReset, onClick = onReset),
        )
    }
}

/** A stepper: **held, it keeps stepping** — see `Modifier.repeatingPress` for the four details that makes it behave. */
@Composable
private fun StepperButton(
    glyph: SliderRowGlyph,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    onStepsFinished: () -> Unit,
    style: SliderRowStyle,
) {
    val interactionSource = remember { MutableInteractionSource() }
    GlyphButton(
        glyph = glyph,
        description = description,
        enabled = enabled,
        slot = StepperSlot,
        style = style,
        interaction = Modifier.repeatingPress(
            interactionSource = interactionSource,
            enabled = enabled,
            onStep = onStep,
            onStepsFinished = onStepsFinished,
        ),
    )
}

/**
 * One round press target with a glyph in it.
 *
 * **Disabled rather than hidden** wherever a press would do nothing, so the row never changes width as a value reaches
 * a bound — which on this row would move the readout out from under the finger that is stepping towards it.
 */
@Composable
private fun GlyphButton(
    glyph: SliderRowGlyph,
    description: String,
    enabled: Boolean,
    slot: Dp,
    style: SliderRowStyle,
    interaction: Modifier,
) {
    Box(
        modifier = Modifier
            .size(slot)
            .clip(CircleShape)
            .then(interaction)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(GlyphSide)) {
            drawSliderRowGlyph(glyph, style.glyphColor.dimmedUnless(enabled))
        }
    }
}

/** The three marks this row needs. */
private enum class SliderRowGlyph { MINUS, PLUS, RESET }

/**
 * The glyphs, drawn rather than imported: `core:designsystem` carries no material-icons dependency, and a minus, a
 * plus and a turning arrow are not a reason to take one on — `TopActionZone` draws its three marks by hand for the
 * same reason.
 */
private fun DrawScope.drawSliderRowGlyph(glyph: SliderRowGlyph, tint: Color) {
    val side = size.minDimension
    val mid = side / 2f
    val stroke = side * 0.11f
    fun line(a: Offset, b: Offset) = drawLine(tint, a, b, stroke, StrokeCap.Round)

    when (glyph) {
        SliderRowGlyph.MINUS -> {
            val arm = mid * 0.62f
            line(Offset(mid - arm, mid), Offset(mid + arm, mid))
        }
        SliderRowGlyph.PLUS -> {
            val arm = mid * 0.62f
            line(Offset(mid - arm, mid), Offset(mid + arm, mid))
            line(Offset(mid, mid - arm), Offset(mid, mid + arm))
        }
        // A ring open at one point, with a head on the leading end: the least that still reads as "turn it back".
        // The radius leaves room for that head, which sticks out past the arc it grows from.
        SliderRowGlyph.RESET -> {
            val radius = mid * 0.62f
            drawArc(
                color = tint,
                startAngle = ResetArcStart,
                sweepAngle = ResetArcSweep,
                useCenter = false,
                topLeft = Offset(mid - radius, mid - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val end = ((ResetArcStart + ResetArcSweep) * PI / 180f).toFloat()
            val tip = Offset(mid + radius * cos(end), mid + radius * sin(end))
            // Backwards along the tangent, splayed either side of it — a chevron rather than a filled triangle, which
            // at this size would read as a blob.
            val back = Offset(sin(end), -cos(end))
            val arm = radius * 0.7f
            listOf(HeadSplay, -HeadSplay).forEach { angle ->
                val a = (angle * PI / 180f).toFloat()
                val dir = Offset(back.x * cos(a) - back.y * sin(a), back.x * sin(a) + back.y * cos(a))
                line(tip, Offset(tip.x + dir.x * arm, tip.y + dir.y * arm))
            }
        }
    }
}

/** Where the ring's gap sits and how far the arrow travels — a gap at the top right, read as "not quite closed". */
private const val ResetArcStart = -60f
private const val ResetArcSweep = 285f

/** How far each arm of the head splays off the tangent. Wide enough to read, narrow enough not to look like a V. */
private const val HeadSplay = 38f

/** Dimmed exactly as M3 dims disabled content, stated once so the label, the number and the glyphs agree. */
private fun Color.dimmedUnless(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = alpha * DisabledAlpha)

/**
 * The chrome a [MorphicSliderRow] draws itself in — everything about it that is *not* the same on every surface.
 *
 * A bundle rather than a dozen parameters, and a parameter rather than a theme read, because the icon studio is a
 * genuine second answer: its panels are glass over a canvas the **user** sets to black or white, so its content is
 * fixed white where a settings pane follows the theme. Reading `LocalMorphicColors` unconditionally would have put
 * white text on a white pane.
 *
 * Only the *enabled* colors are carried; the disabled ones are derived, so a style cannot express a row whose label
 * and glyphs disagree about being spent.
 */
@Immutable
data class SliderRowStyle(
    val labelColor: Color,
    val labelStyle: TextStyle,
    val valueColor: Color,
    val valueStyle: TextStyle,
    val readoutBackground: Color,
    val glyphColor: Color,
)

/** The row's default dress: the theme's, which is what every surface but the icon studio wants. */
object SliderRowDefaults {

    @Composable
    fun style(): SliderRowStyle {
        val colors = LocalMorphicColors.current
        return SliderRowStyle(
            labelColor = colors.content,
            labelStyle = MaterialTheme.typography.bodyLarge,
            // The number reads by contrast rather than by hue — the palette is grayscale by design, and `accent` is
            // its emphasis.
            valueColor = colors.accent,
            valueStyle = MaterialTheme.typography.labelLarge,
            readoutBackground = colors.surfaceElevated,
            glyphColor = colors.content,
        )
    }
}

/**
 * The value a gesture is claiming, until the store answers.
 *
 * A class rather than two `remember`s at each call site so that the *stability* rule cannot be broken by accident:
 * one object per row, never re-created, so every closure that ever ran writes to the one the release reads.
 */
private class ValueInFlight<T : Any> {

    /** What to show in preference to the incoming value, or null when nothing is in flight. */
    var claim: T? by mutableStateOf<T?>(null)

    /**
     * What the gesture in progress has moved to and not yet written — **the value a release commits**.
     *
     * Separate from [claim], and deliberately not snapshot state. Nothing draws from it, and it has to outlive the
     * echo that clears [claim]: where a caller's preview writes through to the value it feeds back (the icon studio
     * does), that echo lands *during* the gesture, so a release reading [claim] would find nothing to commit and the
     * edit would never be closed. Null means this gesture moved nothing, which is what keeps a tap that changed no
     * number from issuing a store write.
     */
    var unsent: T? = null

    /** Bumped by each commit, which is what starts the expiry. */
    var commits: Int by mutableIntStateOf(0)
        private set

    /** The end of a gesture: commit what it moved, if it moved anything. */
    fun release(onCommit: (T) -> Unit) {
        val moved = unsent ?: return
        unsent = null
        commits++
        onCommit(moved)
    }

    /** A discrete edit — a reset. Claims the value so the readout holds still, and writes it at once. */
    fun commit(target: T, onPreview: (T) -> Unit, onCommit: (T) -> Unit) {
        claim = target
        unsent = null
        commits++
        onPreview(target)
        onCommit(target)
    }
}

/**
 * A [ValueInFlight] wired to expire.
 *
 * Two ways a claim ends, and both are needed: a **new value arriving** is the store answering, and an **expiry** is
 * what covers a store answering with the value it already had — a write it quantized to the same number, clamped, or
 * refused. Without the second the row would show a number nothing holds, indefinitely, and a stepper would go on
 * stepping from it.
 */
@Composable
private fun <T : Any> rememberValueInFlight(value: T): ValueInFlight<T> {
    val flight = remember { ValueInFlight<T>() }
    LaunchedEffect(value) { flight.claim = null }
    LaunchedEffect(flight.commits) {
        if (flight.commits == 0) return@LaunchedEffect
        delay(EchoWindowMs.milliseconds)
        flight.claim = null
    }
    return flight
}

/**
 * How long a committed value may stand before the row falls back to what is stored.
 *
 * Long enough that a DataStore round trip lands first — so the ordinary case never flickers — and short enough that a
 * write nothing accepted corrects itself while the user is still looking at the control they pressed.
 */
private const val EchoWindowMs = 600L

/** M3's own disabled content alpha: plainly spent, without disappearing. */
private const val DisabledAlpha = 0.38f

/** Between the caption and the track it captions — they are one control, and read as one. */
private val RowGap = 4.dp
private val CaptionGap = 8.dp
private val StepGap = 4.dp
private val ReadoutCorner = 6.dp
private val ReadoutPadH = 8.dp
private val ReadoutPadV = 2.dp

/** Large enough to be a comfortable target, small enough that the track keeps most of the width. */
private val StepperSlot = 40.dp

/** Smaller, because it sits in a caption row rather than beside the track. */
private val ResetSlot = 32.dp

/** Short of either slot, so the press target is larger than the mark it shows. */
private val GlyphSide = 20.dp
