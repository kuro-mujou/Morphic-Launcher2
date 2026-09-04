package inkspire.morphic.core.graphics.wallpaper

/**
 * A point pushed off itself by a pair of noise fields — how a rigid procedural field is made to look drawn by hand
 * rather than solved.
 *
 * **Two fields, not one, and that is the whole reason this is a type.** A single field pushing both coordinates by
 * the same amount moves every point along the **diagonal**, so the picture combs one way instead of swirling: the
 * distortion reads as a shear the user cannot name. The two are salted apart from each other and from the caller's
 * own use of [seed], which is three constants a second implementation would have to get right in the same way —
 * [MetaballsGenerator] had them inline until [PlasmaGenerator] needed the same push, and two of these drifting apart
 * is invisible, because a field warped slightly wrong is a plausible field.
 *
 * **[reach] is in the caller's own units, and the callers do not agree about what those are.** Metaballs pushes a
 * share of the *frame*, since its contours are frame-sized; the plasma pushes a share of a *wavelength*, since a
 * fixed frame distance is a gentle marble at its low frequency and total noise at its high one. Normalizing that here
 * would mean picking one of the two, so the scale stays the caller's and only the mechanism is shared.
 *
 * @property reach how far a coordinate may be pushed, in whatever units the caller measures its own space in.
 * @property frequency the noise's feature size — how many warp cells span one unit of the caller's space. Low numbers
 *   are broad swirls; past a handful the push stops being a distortion and becomes grain.
 */
internal class DomainWarp(seed: Long, private val reach: Float, private val frequency: Float) {

    private val alongX = PerlinNoise2d(seed xor SaltX)
    private val alongY = PerlinNoise2d(seed xor SaltY)

    /** Where ([x], [y]) is pushed to along the first axis. */
    fun x(x: Float, y: Float): Float = x + reach * alongX.at(x * frequency, y * frequency)

    /** Where ([x], [y]) is pushed to along the second axis. */
    fun y(x: Float, y: Float): Float = y + reach * alongY.at(x * frequency, y * frequency)

    private companion object {

        /** Keeps the two fields independent, so the push is not always along one diagonal. */
        const val SaltX = 0x2545F491L
        const val SaltY = 0x14057B7EL
    }
}
