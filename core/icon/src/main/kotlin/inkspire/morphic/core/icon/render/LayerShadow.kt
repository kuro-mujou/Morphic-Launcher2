package inkspire.morphic.core.icon.render

import kotlin.math.abs
import kotlin.math.ceil
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

    /**
     * How much room an **inner** shadow's complement needs beyond the icon's box, in pixels.
     *
     * **The complement has to exist before it can be blurred, and inside the box it may not.** An inner shadow is
     * cast by everything *outside* the artwork; for a layer whose artwork reaches the box — a background plate, which
     * is the commonest thing anyone recesses — there is no outside within the bitmap at all. Built at box size, the
     * shadow would fade in from nothing along exactly those edges, so a full-bleed plate would come out recessed on
     * the sides its artwork happened not to reach and flat everywhere else. Built in a padded buffer, the region
     * beyond the box is genuinely filled and the blur has something to gather from.
     *
     * The three terms are the three ways the complement's edge reaches inward: the blur spreads it, the choke grows
     * it, and the throw slides it. [BlurReach] is generous rather than exact — `BlurMaskFilter`'s radius is not a
     * hard cutoff, and a margin a few pixels too large costs a fringe of a buffer that is thrown away.
     *
     * At least one pixel, so the padded buffer is never the same bitmap by another name.
     */
    fun innerMarginPx(radiusPx: Float?, spreadPx: Float, dxPx: Float, dyPx: Float): Int {
        val blur = (radiusPx ?: 0f) * BlurReach
        val thrown = maxOf(abs(dxPx), abs(dyPx))
        return ceil(blur + spreadPx + thrown).toInt().coerceAtLeast(1)
    }

    /** How far past its stated radius a `BlurMaskFilter` still puts visible coverage, as a multiple of it. */
    private const val BlurReach = 2f

    /** Below this `BlurMaskFilter` has nothing to soften, and it refuses zero outright. */
    private const val MinBlurPx = 0.5f

    /** Enough copies that the ring reads as a circle rather than as a polygon. */
    private const val MinSteps = 12
    private const val MaxSteps = 48
    private const val StepsPerPx = 1.5f
}
