package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The point a radial design radiates from, placed **outside the frame** — and what the frame looks like from there.
 *
 * **A radial design whose origin is on screen draws a target, and a target is not a wallpaper.** Every ring closes
 * around one point and every ray converges on it, so the picture has a singularity sitting in the middle of the icon
 * grid that the eye cannot leave alone — a bullseye behind the apps, at full palette contrast. Moving the origin off
 * the frame is not a softening of that; it is a different design. What was a target becomes a **sweep**: arcs
 * crossing the screen, or a fan of light entering from one side, with no point of convergence anywhere in view. Both
 * [RingsGenerator] and [RaysGenerator] said as much in their own KDocs — "rings from a corner or an edge read as a
 * rising sun", "light through a gap" — and then kept the origin inside the frame with an inset, which is the one
 * placement that guarantees the thing they were arguing against.
 *
 * **The origin is placed the same way for both, which is what makes them a pair rather than two designs that rhyme.**
 * A seeded bearing from the frame's center, pushed out along it by [distance] — the knob both designs spend
 * [DesignParams.scale] on. `0` is just past the nearest corner, so the sweep is strongly curved and one part of the
 * frame is much nearer the origin than another; `1` is far enough out that the arcs are lazy and the rays nearly
 * parallel. It is never inside, at any setting.
 *
 * **Then everything a radial design *counts* has to be counted over what the frame can actually see, which is the
 * other half of the change.** With the origin on screen, the frame's diagonal was the natural extent and a full turn
 * was the natural sweep; from outside, the frame covers a slice of distance ([distanceSpan]) and a slice of angle
 * ([sectorTurns]), both of which shrink as the origin moves away. A count left against the old extents does not
 * merely look different — the knob starts lying: *Rings* `10` would draw four visible bands, and *Rays* `8` would
 * draw two. Both are read from here so the numbers on the panel stay the numbers on the screen.
 *
 * @property x where the origin sits across the frame, as a share of the **width** — `0` the left edge, `1` the right,
 *   and outside that range whenever the bearing points sideways.
 * @property y where it sits down the frame, as a share of the **height** — the metric a generator's own `ny` is in,
 *   so this drops straight into one as a center coordinate.
 */
internal class OffFrameOrigin(val x: Float, val y: Float, heightOverWidth: Float) {

    // Both measurements are taken on the screen rather than in the unit square, for the reason each generator's own
    // KDoc gives: a distance or a bearing read from two shares-of-their-own-side exists nowhere on the display.
    private val screenY = y * heightOverWidth

    /**
     * How much further the frame's far corner is than its nearest point, in width-shares — the span of distance the
     * frame actually covers, and what a ring count is a count *of*.
     *
     * The near end is the frame's nearest **point**, not its nearest corner: with the origin square-on to an edge
     * that point is in the middle of the edge, and using a corner there would overstate the span by the width of the
     * frame. Never zero, so a caller may divide by it — the origin is placed strictly outside, but a degenerate frame
     * should draw something rather than nothing.
     */
    val distanceSpan: Float

    /**
     * What share of a full turn the frame subtends, seen from here — `1` would be an origin inside it, and the
     * placement guarantees well under that.
     *
     * Taken from the four corners, which is exact because a rectangle is convex: seen from any point outside it, its
     * angular extent is spanned by two of its corners and the widest gap between consecutive corner bearings is the
     * part of the circle the frame does not cover.
     */
    val sectorTurns: Float

    init {
        val bottom = heightOverWidth
        val cornerX = floatArrayOf(0f, 1f, 0f, 1f)
        val cornerY = floatArrayOf(0f, 0f, bottom, bottom)

        val nearest = hypot(x - x.coerceIn(0f, 1f), screenY - screenY.coerceIn(0f, bottom))
        var farthest = 0f
        for (i in 0 until Corners) farthest = max(farthest, hypot(cornerX[i] - x, cornerY[i] - screenY))
        distanceSpan = max(farthest - nearest, Epsilon)

        val turns = FloatArray(Corners) { i ->
            val turn = atan2(cornerY[i] - screenY, cornerX[i] - x) / TwoPi
            turn - floor(turn)
        }
        turns.sort()
        var widest = turns[0] + 1f - turns[Corners - 1]
        for (i in 1 until Corners) widest = max(widest, turns[i] - turns[i - 1])
        sectorTurns = max(1f - widest, Epsilon)
    }

    private companion object {

        const val Corners = 4

        /** Keeps a degenerate frame from handing a caller a zero to divide by. */
        const val Epsilon = 1e-4f
    }
}

/**
 * An [OffFrameOrigin] on a bearing drawn from [random], pushed [distance] (`0..1`, the knob) out from the frame's
 * center over a `[heightOverWidth]`-shaped frame.
 *
 * **One draw, whatever the distance**, for [SeededHarmonics]' reason: a placement that consumed the seeded stream
 * differently at different knob settings would reshuffle everything drawn after it as the knob moved.
 *
 * The reach is in multiples of the frame's **half-diagonal**, which is the one distance that is outside the frame on
 * every bearing — the circumscribed circle touches the corners and clears every other point — so [NearestReach] just
 * over `1` is outside whichever way the bearing happens to point, with no per-bearing case to get wrong.
 */
internal fun offFrameOrigin(random: Random, distance: Float, heightOverWidth: Float): OffFrameOrigin {
    val bearing = random.nextFloat() * TwoPi
    val reach = NearestReach + distance.coerceIn(0f, 1f) * (FurthestReach - NearestReach)
    val out = reach * hypot(Half, Half * heightOverWidth)
    val screenY = Half * heightOverWidth + sin(bearing) * out
    return OffFrameOrigin(Half + cos(bearing) * out, screenY / heightOverWidth, heightOverWidth)
}

private const val TwoPi = 2f * PI.toFloat()
private const val Half = 0.5f

/**
 * The closest and furthest the origin is placed, as multiples of the frame's half-diagonal.
 *
 * The near end is just over `1` rather than `1` because exactly `1` puts the origin **on** a corner when the bearing
 * points at one — the single placement where the frame's nearest point is zero away and its angular extent jumps to
 * half a turn. The far end is where the curvature is still readable across a phone: past it the arcs straighten into
 * bands and the fan into stripes, which are designs this catalog already has.
 */
private const val NearestReach = 1.02f
private const val FurthestReach = 2.2f
