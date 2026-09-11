package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The segment a design's copies march along — where the first one is centred and where the last one is — seeded
 * rather than chosen.
 *
 * **This is the reference's pair of four-arrow nudge pads.** Two of its designs are a *tween between two fully
 * specified states* ([PolygonCascadeGenerator]'s shapes and [FlowLinesGenerator]'s open curve), and each exposes the
 * first and last copy's **centre** as a nudge pad rather than a slider. It re-rolls both on every pick, so a seed is
 * the honest port of them; a pad needs a control the studio does not have, and a run that never moves is the one
 * thing the construction cannot survive — every copy stacks on one centre and the march becomes a rosette.
 *
 * **The heading is drawn uniformly in the frame's own space, not in the plane.** A uniform angle sends a run across a
 * phone-shaped wallpaper as often as down it, and a run across leaves two thirds of the frame empty; stretching the
 * draw by the frame's own proportions makes a tall frame mostly draw tall runs without ever forbidding the others.
 * The length is then a share of the frame's extent **along whichever heading came up**, so the long way round gets
 * the long travel — which is the part that is silently wrong if it is restated: a run measured against the width on a
 * vertical heading is a design that never leaves the middle of the frame, and it still looks like a design.
 *
 * Consumes exactly two values from [random] — the bearing and the length — so a caller's seeded stream is
 * predictable.
 *
 * @property firstX where the first copy is centred, in pixels.
 * @property firstY where the first copy is centred, in pixels.
 * @property lastX where the last copy is centred, in pixels.
 * @property lastY where the last copy is centred, in pixels.
 */
internal class TweenRun(
    val firstX: Float,
    val firstY: Float,
    val lastX: Float,
    val lastY: Float,
) {

    /** The heading's `x` component, unit length — for a design that orients its copies against the run. */
    val headingX: Float = if (length == 0f) 1f else (lastX - firstX) / length

    /** The heading's `y` component, unit length — for a design that orients its copies against the run. */
    val headingY: Float = if (length == 0f) 0f else (lastY - firstY) / length

    private val length: Float get() = hypot(lastX - firstX, lastY - firstY)
}

/**
 * What a [TweenRun] is drawn from, before any frame: the heading's bearing and how much of the frame the run takes —
 * kept by a plan so it can be laid across a frame of any size, or turned into another seed's.
 *
 * **Turned as a bearing and a length, never as two endpoints** ([turnedTo]). Two runs pointing opposite ways have
 * their endpoints swapped, and interpolating those pulls both to the frame's middle halfway — every copy stacked on one
 * centre, the rosette the class note says the construction cannot survive. Turned, the run swings about the frame's
 * centre and keeps its length the whole way.
 *
 * @property bearing the heading, in radians, in the frame's own stretched space — see [TweenRun].
 * @property share where the run's length sits between [MinRun] and [MaxRun], `0..1`.
 */
internal class RunDraw(val bearing: Float, val share: Float) {

    /**
     * The run across a `[width] × [height]` frame, centred on it.
     *
     * The share of the frame the run takes is [MinRun]..[MaxRun] — enough that the copies cross the frame rather than
     * huddling, short enough that the first and last are both on it.
     */
    fun at(width: Int, height: Int): TweenRun {
        val across = cos(bearing) * width
        val down = sin(bearing) * height
        val span = hypot(across, down)
        val headingX = across / span
        val headingY = down / span
        val run = (abs(headingX) * width + abs(headingY) * height) * (MinRun + share * (MaxRun - MinRun))
        val firstX = width / 2f - headingX * run / 2f
        val firstY = height / 2f - headingY * run / 2f
        return TweenRun(firstX, firstY, firstX + headingX * run, firstY + headingY * run)
    }

    /** This run [t] of the way to [other] — the bearing the short way round, the length straight. */
    fun turnedTo(other: RunDraw, t: Float): RunDraw =
        RunDraw(lerpAngle(bearing, other.bearing, t), share + (other.share - share) * t)
}

/** A [RunDraw] from [random] — the bearing, then the length. */
internal fun runDraw(random: Random): RunDraw = RunDraw(random.nextFloat() * TwoPi, random.nextFloat())

private const val TwoPi = 2f * PI.toFloat()

/** The share of the frame's extent along the heading that the run takes, at its shortest and longest. */
private const val MinRun = 0.55f
private const val MaxRun = 0.80f
