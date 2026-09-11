package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Where a pixel falls along one direction across the frame, `0` at the first corner that direction meets and `1` at
 * the last — the shared projection behind the designs built on an angled axis ([DiagonalBandsGenerator]'s bands,
 * [LouversGenerator]'s strips and the gradient running along them).
 *
 * **Spanning exactly `0..1` corner to corner is what the knobs above it are fractions *of*.** A coverage, a strip
 * index, a ramp position: each is a share of this axis, so an axis that fell short at some angles would read as the
 * design being uneven rather than as arithmetic being wrong — and it is invisible in any one render. Extracted here on
 * the second consumer so the two cannot drift.
 *
 * **Projected in pixels, not in the unit square.** A `20°` axis has to run at `20°` on the *screen*; in the unit
 * square it would run at whatever the frame's aspect turns `20°` into, which on a phone is near-flat.
 */
internal class FrameAxis(
    private val dx: Float,
    private val dy: Float,
    private val lowest: Float,
    private val span: Float,
) {

    /**
     * How long this axis is, **in pixels**, corner to corner.
     *
     * For the designs that set a dimension in pixels rather than as a share of the axis —
     * [WaveDividersGenerator]'s wavelength, which is measured against the frame's *height* so that turning the stack
     * does not rescale the wave. Without it a turned design silently redraws at a different scale, since the axis a
     * `0..1` reading spans is the frame's width at one angle and its diagonal at another.
     */
    val lengthPx: Float get() = span

    /**
     * The two ends of this axis in pixels — where [at] reads `0` and where it reads `1`.
     *
     * For a design that hands the axis to a **shader** rather than sampling it: a `LinearGradient` is given two
     * points and works out the projection itself, so a caller that computed them from the angle again would be
     * running this class' arithmetic a second time — and a gradient that spans slightly the wrong pixels is a
     * wallpaper that is merely a little flatter than intended, which nothing reports.
     */
    val startX: Float get() = dx * lowest
    val startY: Float get() = dy * lowest
    val endX: Float get() = dx * (lowest + span)
    val endY: Float get() = dy * (lowest + span)

    /**
     * Where ([x], [y]) falls along this axis, `0..1`.
     *
     * A frame with no extent along the axis — a one-pixel strip — answers the middle, so a degenerate size draws the
     * center of whatever sits on the axis rather than dividing by nothing.
     */
    fun at(x: Float, y: Float): Float =
        if (span <= 0f) Center else ((dx * x + dy * y - lowest) / span).coerceIn(0f, 1f)

    /**
     * The point on this axis that [at] reads as [share] — any share, so a caller can reach past either end — in
     * pixels. For a shader's anchor points, which have to sit where the axis says a boundary is.
     */
    fun xAt(share: Float): Float = startX + (endX - startX) * share

    /** The other coordinate of [xAt]. */
    fun yAt(share: Float): Float = startY + (endY - startY) * share

    /**
     * The quad covering this axis from share [from] to share [to], reaching [reach] pixels either side of it —
     * interleaved `x, y`, corners `0` and `3` on the [from] edge and `1` and `2` on the [to] edge; empty on an axis
     * with no length.
     *
     * **What a band across the frame is, drawn**: [DiagonalBandsGenerator]'s slab and [GradientColumnsGenerator]'s
     * columns. Built from the axis' own ends, so a drawn edge lies exactly where [at] reads the boundary — a quad
     * worked out from the degrees again could land a quarter turn or a pixel off and still look like bands.
     */
    fun slab(from: Float, to: Float, reach: Float): FloatArray {
        val ax = endX - startX
        val ay = endY - startY
        val length = hypot(ax, ay)
        if (length <= 0f) return FloatArray(0)
        val px = -ay / length * reach
        val py = ax / length * reach
        val fx = xAt(from)
        val fy = yAt(from)
        val tx = xAt(to)
        val ty = yAt(to)
        return floatArrayOf(fx + px, fy + py, tx + px, ty + py, tx - px, ty - py, fx - px, fy - py)
    }

    /** [slab] as a closed path, for filling. */
    fun slabPath(from: Float, to: Float, reach: Float): Path = Path().apply {
        val quad = slab(from, to, reach)
        if (quad.isEmpty()) return@apply
        moveTo(quad[0], quad[1])
        for (i in 2 until quad.size step 2) lineTo(quad[i], quad[i + 1])
        close()
    }
}

/**
 * The [FrameAxis] pointing [degrees] from the horizontal over a `[width] × [height]` frame, measured clockwise —
 * screen coordinates, so `0°` runs left to right and `90°` runs top to bottom.
 */
internal fun frameAxis(degrees: Float, width: Int, height: Int): FrameAxis {
    val radians = Math.toRadians(degrees.toDouble())
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val right = (width - 1).toFloat()
    val bottom = (height - 1).toFloat()
    // The axis runs corner to corner: its lowest reading is whichever corner it points away from.
    return FrameAxis(dx, dy, min(0f, dx * right) + min(0f, dy * bottom), abs(dx) * right + abs(dy) * bottom)
}

/** The middle of an axis — what a frame with no extent along it reads as. */
private const val Center = 0.5f
