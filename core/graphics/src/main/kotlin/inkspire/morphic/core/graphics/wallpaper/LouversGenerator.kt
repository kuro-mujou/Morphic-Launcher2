package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.max

/**
 * One palette gradient seen through a set of parallel strips, each showing it from a slightly different place —
 * *Louvers*, the reference studio's *Gradient Columns*.
 *
 * **The gradient runs *along* each strip, not across the set — which is the whole difference from
 * [GradientColumnsGenerator], and why both designs exist.** That one steps the palette sideways and fills each column
 * with one flat color; this fills every strip with the *same* ramp and slides it a little from strip to strip, so the
 * frame reads as a single soft gradient that staircases as it crosses them. Driving theirs proved it: wind their
 * *Columns* down to `1` and the design is a mathematically plain gradient with no seams at all, which is a rigid end
 * no sideways stepping can reach. Ours was built in W9 from a one-line note and is a different picture under the same
 * name, so it keeps its name and this is built beside it — the fourth of these splits, after Mondrian, Halftone and
 * the Voronoi.
 *
 * **[DesignParams.scale] is *Spread*, and it moves the palette's inner stops only.** The first and last stops are
 * pinned to the ends of the axis and the ones between are placed in a cluster, `spread` wide, about the middle: wound
 * down, the middle of the palette collapses into a single hard edge with a long gentle wash either side of it; wound
 * up, the stops spread out into an even ramp. That is theirs, measured — inverting their pixels back to a position in
 * the palette gives a straight line through the outer stops at every setting and a step in the middle whose *width* is
 * the knob. Clamping the ramp instead (the obvious alternative) flattens those washes into two dead blocks of flat
 * color, which is most of what makes theirs read as material rather than as a poster.
 *
 * **[DesignParams.irregularity] is *Drift*, and it is a departure from that field's usual meaning.** Everywhere else
 * it is organic noise; here it is a systematic slide — how far the ramp moves along the axis between the first strip
 * and the last. What it keeps is the part of the contract that matters: `0` is the rigid end, and it is rigid in the
 * strongest sense — every strip identical, the design a plain gradient. The strips' widths are left perfectly even as
 * a result, where theirs has a separate *Irregularity* for them; the reference opens that knob at `0` anyway, and this
 * design has only so many knobs to spend.
 *
 * **[DesignParams.depth] is *Shadow*: a linear darken over the outer half of each strip.** Measured off theirs at
 * full — the fall starts at exactly the strip's midpoint, is dead straight, and reaches `×0.75` at the seam — and the
 * last strip carries none, having nothing beside it to stand under. The response is squared so the `0.5` every design
 * opens at lands near their own restrained default rather than halfway to the maximum.
 *
 * **[DesignParams.variant] is their *Rotation*, sampled** — the same call [DiagonalBandsGenerator] makes and for the
 * same reason: a continuous rotation wants an orientation field on [DesignParams] that would be shaped by one design.
 * Theirs turns the strips and the gradient *together*, which is what these three directions do.
 *
 * Their *Start column* (the strip the drift is anchored to, making a V rather than a run) and their *Progression
 * smoothness* (linear versus eased interpolation across the strips) are **not** ported: the first needs a knob this
 * design has not got left, and the second moves nothing at all below about twelve strips.
 *
 * Ignores [seed] — like the plain gradient, this design has no scatter to vary, and every knob it has is the user's.
 */
object LouversGenerator : Generator {

    /** What [DesignParams.density] resolves to — the strip count, and the slider's own range. Theirs exactly. */
    private val Amount = AmountKnob.Count("Columns", 1..30)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Spread",
        irregularity = "Drift",
        depth = "Shadow",
        variant = VariantKnob("Direction", Direction.entries.map { it.label }),
    )

    /**
     * Which way the strips are laid out — their *Rotation*, at the stops a segmented control can offer.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property acrossDegrees the direction the strip *index* increases in, from the horizontal, clockwise. The
     *   gradient runs along the strips, so its own axis is this plus a quarter turn — which is what ties the two
     *   together the way theirs does.
     */
    internal enum class Direction(val label: String, val acrossDegrees: Float) {
        /** Upright strips, the gradient running down them — theirs at rotation `0`, and so the one at index `0`. */
        VERTICAL("Vertical", acrossDegrees = 0f),

        /** The same, leaned over by the shallow angle the reference's own designs open on. */
        DIAGONAL("Diagonal", acrossDegrees = -20f),

        /** Strips stacked up the frame, the gradient running left to right along them. */
        HORIZONTAL("Horizontal", acrossDegrees = -90f),
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val direction = Direction.entries[params.variant.coerceIn(0, Direction.entries.lastIndex)]
        val count = stripCount(params.density)
        val spread = params.scale.coerceIn(0f, 1f)
        val drift = params.irregularity.coerceIn(0f, 1f)
        val shadow = shadowDepth(params.depth)

        val across = frameAxis(direction.acrossDegrees, width, height)
        val along = frameAxis(direction.acrossDegrees + QuarterTurn, width, height)
        // One ramp per strip, resolved once: every strip paints the same colors, from its own place along the axis.
        val ramps = Array(count) { rampOf(centerOf(it, count, drift), spread, palette) }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            val fy = y.toFloat()
            for (x in 0 until width) {
                val fx = x.toFloat()
                val place = across.at(fx, fy) * count
                val strip = place.toInt().coerceIn(0, count - 1)
                val ramp = ramps[strip]
                val color = ramp[(along.at(fx, fy) * (RampSteps - 1)).toInt().coerceIn(0, RampSteps - 1)]
                // The far half of a strip falls into the shadow of the one beyond it; the last strip has none.
                val within = place - strip
                pixels[row + x] = if (strip == count - 1 || within <= Midpoint) {
                    color
                } else {
                    Shades.scale(color, 1f - shadow * (within - Midpoint) / Midpoint)
                }
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many strips [density] asks for — one un-sliced gradient up to a fine set of slats. */
    internal fun stripCount(density: Float): Int = Amount.at(density)

    /**
     * Where strip [index] of [count] centers its ramp on the axis, at [drift].
     *
     * The slide is spread **about the middle strip** rather than run from the first, so winding the knob up opens the
     * design out symmetrically instead of walking the whole picture off one end — the reference anchors it at a strip
     * of the user's choosing, which is the knob this design has not got left.
     */
    internal fun centerOf(index: Int, count: Int, drift: Float): Float {
        val place = if (count <= 1) Midpoint else index.toFloat() / (count - 1)
        return Midpoint + drift.coerceIn(0f, 1f) * DriftSweep * (place - Midpoint)
    }

    /**
     * Where each rung of the ramp sits along the axis, for a ramp centered at [center] and [spread] wide — the first
     * pinned to `0`, the last to `1`, and the ones between clustered about the center.
     *
     * Kept as its own step because it is the design's whole color model and it is checkable without a bitmap: a
     * cluster that ran off the end, or positions that stopped ascending, would draw a plausible-looking wallpaper with
     * a stop silently missing from it.
     */
    internal fun rungPlaces(rungs: Int, center: Float, spread: Float): FloatArray {
        val places = FloatArray(rungs)
        places[rungs - 1] = 1f
        val inner = rungs - 2
        var previous = 0f
        for (i in 1..inner) {
            // The inner rungs share the cluster evenly, its width the spread; a single one would sit at the center.
            val offset = if (inner <= 1) 0f else (i - 1).toFloat() / (inner - 1) - Midpoint
            places[i] = max(previous, (center + spread * offset).coerceIn(0f, 1f))
            previous = places[i]
        }
        places[rungs - 1] = max(previous, 1f)
        return places
    }

    /**
     * The ramp a strip paints, as [RampSteps] colors along the axis — [palette] read at its rungs and laid out by
     * [rungPlaces].
     *
     * **At least [RungFloor] rungs, whatever the palette's length.** One rung per stop lands on the palette's own
     * colors exactly, but the default color mode reduces it to *two* — which leaves no rung between the ends for the
     * spread to move, and so a knob that does nothing at the setting most users will first see. It is the failure
     * [RampTones] exists for, one design further on; the floor is `4` rather than that one's `3` because a cluster
     * needs two rungs before it has a width at all.
     */
    private fun rampOf(center: Float, spread: Float, palette: Palette): IntArray {
        val rungs = max(palette.size, RungFloor)
        val ink = IntArray(rungs) { LinearGradientGenerator.colorAt(it.toFloat() / (rungs - 1), palette) }
        val places = rungPlaces(rungs, center, spread)

        val ramp = IntArray(RampSteps)
        var rung = 0
        for (step in 0 until RampSteps) {
            val at = step.toFloat() / (RampSteps - 1)
            while (rung < rungs - 2 && at > places[rung + 1]) rung++
            val low = places[rung]
            val high = places[rung + 1]
            val within = if (high > low) ((at - low) / (high - low)).coerceIn(0f, 1f) else 1f
            ramp[step] = LinearGradientGenerator.lerpArgb(ink[rung], ink[rung + 1], within)
        }
        return ramp
    }

    /**
     * How far a seam darkens at [depth].
     *
     * **Squared**, so the `0.5` every design opens at lands near the reference's own default (a twentieth) rather than
     * halfway to the maximum. Per-design defaults would make this unnecessary, and this is the second design to say so.
     */
    internal fun shadowDepth(depth: Float): Float {
        val amount = depth.coerceIn(0f, 1f)
        return MaxShadow * amount * amount
    }

    /** The fewest rungs the ramp is read at, so a two-stop palette still has an inside for the spread to move. */
    private const val RungFloor = 4

    /** How many colors a strip's ramp is resolved to — fine enough that a soft wash never steps visibly. */
    private const val RampSteps = 1024

    /** How far the ramp's center may slide between the outermost strips, as a share of the axis. */
    private const val DriftSweep = 0.4f

    /** The darkest a seam goes, as a share of the color it falls on — theirs, measured at its maximum. */
    private const val MaxShadow = 0.25f

    /** From the strips' own direction to the gradient running along them, in degrees. */
    private const val QuarterTurn = 90f

    /** The middle of a strip, of the axis, and of the run of strips — the one number all three are measured from. */
    private const val Midpoint = 0.5f
}
