package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A few low harmonics at seeded phases, summed — the smooth, unrepeating deformation a design bends a shape with.
 *
 * **Low harmonics rather than a nudge per sample point.** A per-point nudge reads as noise on a finely sampled curve
 * and as a hand-drawn wobble on a coarse one, because its frequency is the sampling rather than anything about the
 * shape; a low harmonic bends a circle into a lumpy blob and a line into a lazy wave, which is the same gesture at
 * any sample count. [PolygonCascadeGenerator] deforms a radius with it and [FlowLinesGenerator] a lateral offset.
 *
 * **The phases are drawn from [random] on construction, whatever the amplitude the caller will scale by.** That is
 * the part worth sharing rather than restating: a design that drew them lazily, or only when its amplitude knob was
 * off zero, would shift the seeded stream underneath the knob — so moving *how far* the shape bends would also change
 * *which way*, and every render past that point in the stream would move too. It is invisible until someone drags the
 * knob and the picture reshuffles.
 *
 * **A scrub turns each phase the short way into another seed's** ([turnedTo]): a phase is an angle, so the bend
 * travels along the shape rather than fading out in place, and the weights — the knob-free part — hold still.
 *
 * @property weights each harmonic's share of the sum, in order — descending, so the first bends and the rest detail.
 * @property harmonics each harmonic's frequency, as a multiple of the input.
 * @property phases each harmonic's phase, in radians — the seed's.
 */
internal class SeededHarmonics private constructor(
    private val weights: FloatArray,
    private val harmonics: FloatArray,
    private val phases: FloatArray,
) {

    constructor(weights: FloatArray, harmonics: FloatArray, random: Random) :
        this(weights, harmonics, FloatArray(weights.size) { random.nextFloat() * TwoPi })

    /** These harmonics [t] of the way to [other]'s phases — each the short way round, the weights unchanged. */
    fun turnedTo(other: SeededHarmonics, t: Float): SeededHarmonics =
        SeededHarmonics(weights, harmonics, FloatArray(phases.size) { lerpAngle(phases[it], other.phases[it], t) })

    /** The sum at [x], roughly `-1..1` — the caller scales it by whatever its own amplitude knob asks for. */
    fun at(x: Float): Float {
        var sum = 0f
        for (h in weights.indices) {
            sum += weights[h] * sin(phases[h] + harmonics[h] * x)
        }
        return sum
    }

    private companion object {
        const val TwoPi = 2f * PI.toFloat()
    }
}
