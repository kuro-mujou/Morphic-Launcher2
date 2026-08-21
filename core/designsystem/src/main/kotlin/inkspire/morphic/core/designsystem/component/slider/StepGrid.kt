package inkspire.morphic.core.designsystem.component.slider

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * The grid a stepper button moves a value along, and the readout that grid is matched to.
 *
 * Arithmetic rather than UI, and separate from [MorphicSliderRow] for that reason: it is the half that is *silently*
 * wrong when it is written twice. A stepper that adds rather than snapping takes 1.037 to 0.987 instead of to 1.00,
 * and nobody reads a number that plausible as a bug.
 */

/**
 * How far one press of a stepper moves a value on [range] — **the finest move its readout can report**, which is the
 * whole job of those buttons.
 *
 * **A stepper is for the last little bit the slider cannot reach, not for travelling.** A finger on a 250dp track
 * lands on 0.37 and the point of a press is to reach 0.38; a step chosen to feel "worth pressing" cannot express
 * that, so the control meant to make an edit exact would be the one rounding it off. Holding is what pays for a fine
 * step — the buttons repeat, so crossing a range is a hold rather than a hundred taps, and a hold is still **one**
 * commit because the gesture's end is reported separately.
 *
 * **It is paired with [finestFormat] and must stay so**: a step below what the number on screen can show is a press
 * that visibly does nothing, which is worse than a coarse one. That pairing is why both are derived from the range
 * here rather than chosen per slider — a hundred call sites each picking a step *and* a matching format is a hundred
 * chances for the two to disagree, and the symptom of disagreeing is a dead-looking button.
 *
 * **Narrow ranges get the extra digit**, which is where this earns its keep. Half the icon-effect sliders run 0..0.1
 * or 0..0.2 — a blur radius, a ripple's amplitude, a halo's spread — and against a two-decimal readout one press
 * moved five to ten percent of everything the control could express, on exactly the values where a small difference
 * is the point. The cut is at half a unit: wider than that and a hundredth is already a fine move, narrower and it is
 * a tenth of the whole range.
 *
 * **It answers for *fractions* only.** A quantity whose stored unit is larger than a hundredth must say so — whole dp
 * and whole degrees both do — because a step finer than the store's own resolution is a press that writes the value
 * it already had. That is the one failure this derivation cannot see, so the callers that quantize pass their own step.
 */
fun finestStep(range: ClosedFloatingPointRange<Float>): Float =
    if (range.endInclusive - range.start >= FineRangeSpan) UnitStep else FineStep

/** The readout [finestStep] is matched to — one more digit exactly where the step gains one. */
fun finestFormat(range: ClosedFloatingPointRange<Float>): String =
    if (range.endInclusive - range.start >= FineRangeSpan) UnitFormat else FineFormat

/**
 * The two steps and the two readouts, named because they come in **pairs** — a step must move the last digit its
 * format prints, and that is a fact about the four of them together rather than about any one.
 */
private const val UnitStep = 0.01f
private const val FineStep = 0.001f
private const val UnitFormat = "%.2f"
private const val FineFormat = "%.3f"

/**
 * Where a range stops being "about a unit" and starts being a fine quantity.
 *
 * Half a unit rather than a whole one, because several sliders run `0.05..1` or `0.05..1.5` and are plainly the same
 * *kind* of value as the `0..1` ones beside them — a threshold of 1 would have given those an extra decimal for the
 * sake of the 0.05 missing from the bottom of their track.
 */
private const val FineRangeSpan = 0.5f

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
fun snappedStep(value: Float, step: Float, up: Boolean): Float {
    val steps = value / step
    val target = if (up) floor(steps + SnapEpsilon) + 1f else ceil(steps - SnapEpsilon) - 1f
    return target * step
}

/**
 * The nearest multiple of [step] to [value] — the drag's counterpart to [snappedStep]'s press.
 *
 * **A drag has to land on the same grid the buttons walk, or the two controls disagree about what the value is.** A
 * finger reports 41.6 on a slider whose store holds whole dp: the readout then says "42 dp" while the value under it
 * is 41.6, a press from there lands on 42.6, and the number on screen moves by one for a press that moved the value
 * by a fraction. Quantizing the drag is what makes "what it says" and "what is stored" the same number, and it is
 * what lets a reset light up exactly when the value is off its default rather than a hundredth away from it.
 */
fun quantizedTo(value: Float, step: Float): Float = (value / step).roundToInt() * step

/** Small against any step here, large against the float error of adding them up. */
private const val SnapEpsilon = 1e-4f
