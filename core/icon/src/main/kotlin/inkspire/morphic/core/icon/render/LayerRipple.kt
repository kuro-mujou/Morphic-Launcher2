package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.sin

/**
 * How far along its own radius a rippled pixel reads from.
 *
 * [LayerShadow]'s reason rather than the shared-derivation one: only the bake draws this, so nothing is competing
 * with the arithmetic — it is pulled out of `IconRenderer` because *that* class needs an emulator for every line,
 * and a displacement that is subtly wrong produces a plausible-looking ripple rather than an error.
 *
 * **The whole effect is one function of distance.** Everything else — where the center is, which direction a pixel
 * lies in, what to do at the edges — is geometry the renderer does around it, and none of it is the part that can be
 * quietly wrong.
 */
object LayerRipple {

    /**
     * Where a pixel [distancePx] from the center reads from, along the same radius.
     *
     * Positive is further out. A crest pulls pixels inward and a trough pushes them out, which is what makes the
     * layer appear to bulge — the displacement is *of the sampling*, so the picture moves the opposite way to the
     * number, and that inversion is the thing worth not flipping by accident.
     */
    fun sampleDistancePx(distancePx: Float, amplitudePx: Float, wavelengthPx: Float): Float =
        distancePx + amplitudePx * sin(TwoPi * distancePx / wavelengthPx)

    /** How far a crest pushes, in pixels — a fraction of the box, so one recipe ripples the same at every size. */
    fun amplitudePx(ripple: LayerEffect.Ripple, sizePx: Int): Float = ripple.amplitude * sizePx

    /**
     * The distance between crests, in pixels.
     *
     * `waves` counts crests **across the box**, which is why this is a division: asking for more waves has to make
     * them finer rather than the ripple bigger. Floored above zero because it divides, and
     * [LayerEffect.Ripple.isIdentity] already refuses a count of zero — this is the guard for a stored recipe that
     * never went through it.
     */
    fun wavelengthPx(ripple: LayerEffect.Ripple, sizePx: Int): Float =
        (sizePx / ripple.waves).coerceAtLeast(MinWavelengthPx)

    /** Where the waves start, in pixels from the box's own center. */
    fun centerPx(center: Float, sizePx: Int): Float = sizePx / 2f + center * sizePx

    /** Below a pixel a wave has no room to rise and fall, so it would alias into noise rather than ripple. */
    private const val MinWavelengthPx = 1f

    private const val TwoPi = 2f * Math.PI.toFloat()
}
