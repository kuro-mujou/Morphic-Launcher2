package inkspire.morphic.core.icon.render

import kotlin.math.roundToInt

/**
 * The pixel quantities a glow or a drop shadow is built from.
 *
 * **The first derivation here that is not about two renderers agreeing**, and the difference is worth stating: glow
 * and shadow are the first effects that *cannot* be drawn live, so there is only one implementation and nothing to
 * disagree with it. What this exists for instead is the other half of why the rest of the package is shaped this way
 * — the arithmetic is the part that can be silently wrong, and pulled out of `IconRenderer` it is unit-testable on
 * the JVM, where every line of that class needs an emulator.
 *
 * Both effects go through the same numbers because they are the same effect twice: a blurred copy of the finished
 * silhouette placed behind. A glow spreads and does not move; a shadow moves and does not spread.
 */
object LayerShadow {

    /**
     * The blur radius in pixels, or `null` when there is no blur to apply.
     *
     * Null rather than zero because `BlurMaskFilter` **rejects** a non-positive radius, so a slider at its floor
     * would throw rather than draw a hard edge — and a hard edge is a legitimate thing to ask a shadow for. The
     * caller reads null as "skip the blur", which is the picture the user asked for.
     */
    fun radiusPxOrNull(radius: Float, sizePx: Int): Float? =
        (radius * sizePx).takeIf { it >= MinBlurPx }

    /** How far the silhouette is grown before blurring, in pixels — zero meaning it is not grown at all. */
    fun spreadPx(spread: Float, sizePx: Int): Float = (spread * sizePx).coerceAtLeast(0f)

    /**
     * How many copies a spread is drawn as, for a ring of [spreadPx].
     *
     * **A spread is a dilation, and a dilation is the silhouette swept around a circle** — the same "no primitive
     * draws this" problem [LayerExtrude] has, one dimension over. N copies evenly around the ring approximate it,
     * and the error is the scallop between adjacent copies: `1 - cos(π/N)` of the spread, which at sixteen is under
     * two percent and invisible. Larger spreads get more copies so it stays that way.
     *
     * Cheap in a way [LayerExtrude]'s cap could not be, because this effect never draws live: the copies are blits
     * of a bitmap the bake already holds, run once, off the main thread, behind a cache.
     */
    fun spreadSteps(spreadPx: Float): Int =
        (spreadPx * StepsPerPx).roundToInt().coerceIn(MinSteps, MaxSteps)

    /** A displacement in pixels — the shadow's throw, from a fraction of the box. */
    fun offsetPx(offset: Float, sizePx: Int): Float = offset * sizePx

    /** Below this `BlurMaskFilter` has nothing to soften, and it refuses zero outright. */
    private const val MinBlurPx = 0.5f

    /** Enough copies that the ring reads as a circle rather than as a polygon. */
    private const val MinSteps = 12
    private const val MaxSteps = 48
    private const val StepsPerPx = 1.5f
}
