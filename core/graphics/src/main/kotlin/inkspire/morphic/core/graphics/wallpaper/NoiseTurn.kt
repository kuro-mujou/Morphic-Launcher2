package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Noise sample [a] turned [t] of the way into [b], both centered on zero — how a scrub carries one seed's noise field
 * into another's, turned rather than blended straight.
 *
 * **Two seeds' fields are unrelated, and the average of two unrelated fields swings only about 0.7 as far from zero as
 * either does.** Blended straight, the middle of every scrub would be a flatter picture passing for the same one — a
 * halftone's bare paper filling in, a dot grid's seams ruling straight. Weighted by the cosine and sine of a quarter
 * turn, the squares of the two weights sum to one at every moment, so the swing holds from end to end; and a smooth
 * field turned into another moves its features rather than fading them in place.
 *
 * **Shared by every design whose seed is a noise field** — Halftone's dot sizes, Dot Grid's seam drifts, Modern
 * Mosaic's corner skew, Ribbon Flow's combing and Triangular Facets' relief — because the failure is silent: a
 * straight blend still scrubs smoothly, and only a measurement says the middle went flat.
 *
 * **What it costs is slope**: where both fields climb together the turn climbs up to `√2` times as steeply as either,
 * which matters to a design that bounds its amplitude by the field's slope. Ribbon Flow is that design, and says what
 * it measured.
 */
internal fun turnNoise(a: Float, b: Float, t: Float): Float {
    val angle = t * QuarterTurn
    return a * cos(angle) + b * sin(angle)
}

/** A quarter turn, in radians — [turnNoise]'s whole travel. */
private const val QuarterTurn = (PI / 2).toFloat()
