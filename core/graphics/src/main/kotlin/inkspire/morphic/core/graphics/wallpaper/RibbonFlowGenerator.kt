package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A ruled rank of parallel lines, combed off their lanes by a noise field and inked by one gradient running along
 * them — *Ribbon Flow*.
 *
 * **The lines are a rank, not a scatter, and that is the whole design.** Every line spans the frame, none crosses
 * another, none ends inside it, and at [DesignParams.irregularity] `0` they are exactly straight and exactly evenly
 * spaced. The version this replaced traced [Streamlines] from *random* starts for a fixed number of steps and stroked
 * each at a twentieth of the short side, so it drew a scatter of short broad ribbons — a fatter [FlowLinesGenerator]
 * with a per-mark ramp — and could not reach a straight line at any setting. It was built from a one-line note; this
 * is built from the reference, driven.
 *
 * **A line is its lane displaced sideways, which is what makes crossing impossible by construction.** In the rank's
 * own frame — `u` along the lines, `v` across them — lane `i` sits at `v = vᵢ` and is drawn as
 * `v(u) = vᵢ + amplitude · noise(u, vᵢ)`. The noise is read at the lane's **own** `v`, not at the displaced one, so
 * two neighbouring lines stay in order for exactly as long as `amplitude · ∂noise/∂v > −1` — a bound on the
 * *amplitude* alone, independent of the spacing, which is why [amplitudeCeiling] can guarantee it rather than hope.
 * Tracing streamlines or walking level sets would draw the same family of curves; both cost far more and neither can
 * promise the ordering.
 *
 * **The field is isotropic, and the bound is what sets the amplitude's scale rather than taste.** Because the bound
 * is on the noise's slope *across* the rank, stretching the field along `v` would buy proportionally more amplitude —
 * and a first pass did stretch it four times, on a reading of the reference that turned out to be the noise's own
 * periodicity rather than any anisotropy. Measuring the reference's structure *down* a column gave a period within a
 * fifth of the one along a line, so its field is round; and at that frequency the ordering bound lands its maximum
 * deflection on about the deviation the reference actually draws at full *Distortion*. Stretching it was worth three
 * times too much wander at the default, which reads as a zigzag where the reference sweeps.
 *
 * **[DesignParams.density] is the rank's size, not the number of lines you can see.** The lanes are ruled across the
 * frame's *diagonal* plus a margin either side, so at any angle some of them start off-frame — which is what keeps
 * *Rotation* a turn rather than a second density knob, and what keeps an edge from going bare when the noise pushes
 * the outermost lane inward. See [spacingPx]. The reference does the same: its rank measured the same pitch at `0` and
 * at `90`, about 1.3× the frame's extent, over a spacing of exactly `extent / (count − 1)`.
 *
 * **[DesignParams.scale] is a *share of the lane*, not a width in pixels** — the reference's *Thickness* runs to
 * `100`, where the strokes exactly touch and no ground shows at all, and it means the same fraction at every count.
 * A cube maps the field's `0.5` onto the reference's shipped `12%` and leaves the fine end, where the design lives,
 * most of the travel.
 *
 * **The ink is one gradient across the whole frame and every line reads it**, so the palette climbs steadily along
 * the lines' direction while the lines themselves wander through it. It is a single shader on a single paint rather
 * than a colour per segment: the reference's lines are identical in colour wherever they cross the same point of the
 * axis, which a per-line ramp cannot do — that was this design's other borrowed mistake, and it is the finding W11q
 * made on Flow Field's *Pearls*. The ground is the palette's darkest stop and the ramp is everything above it, so a
 * line never fades into the back.
 *
 * **Two of the reference's six knobs are not built as it has them.** Its *Detail* is a noise frequency whose low end
 * is the smoothest picture, and [DesignParams.roundness]'s contract is that `0` is *sharp*, so it is carried inverted
 * and named for what our knob does: *Smoothness*, tight ripples to one long swell. Its *Gradient offset* slides the
 * ink ramp along its axis and clamps, so both of its ends flatten the frame to a single tone; there is no field in the
 * model whose meaning it fits and the picture it buys is worse than the one it costs, so it is left out.
 *
 * Deterministic in [seed]. [lineCount], [spacingPx], [detailFor], [thicknessFraction] and [amplitudeCeiling] are the
 * pure mappings, and every one of them is silently wrong when it is wrong — a rank at the wrong pitch is still a
 * plausible rank.
 */
object RibbonFlowGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Lines* slider's own range. */
    private val Amount = AmountKnob.Count("Lines", 10..50)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
        irregularity = "Distortion",
        roundness = "Smoothness",
        rotation = "Rotation",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground the rank is ruled on
        // The ramp is the lighter stops only, so a line's gradient never passes through the ground and vanishes.
        val ramp = if (palette.size > 1) palette.colors.dropLast(1) else palette.colors

        val degrees = params.rotation.coerceIn(0f, 1f) * Quarter
        val radians = Math.toRadians(degrees.toDouble())
        // The rank's own frame: `along` runs down a line, `across` runs from one lane to the next.
        val alongX = cos(radians).toFloat()
        val alongY = sin(radians).toFloat()
        val acrossX = -alongY
        val acrossY = alongX
        val alongAxis = frameAxis(degrees, width, height)
        // The rank is ruled across the frame's diagonal, whatever the angle — see [spacingPx].
        val diagonal = hypot((width - 1).toFloat(), (height - 1).toFloat())

        val count = lineCount(params.density)
        val spacing = spacingPx(diagonal, count)
        val strokeWidth = max(1f, spacing * thicknessFraction(params.scale))

        val reference = max(width, height).toFloat()
        val detail = detailFor(params.roundness)
        val frequency = detail / reference
        val amplitude = params.irregularity.coerceIn(0f, 1f) * amplitudeFor(frequency, diagonal)
        val noise = PerlinNoise2d(seed)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.strokeWidth = strokeWidth
            color = ramp.first()
            if (ramp.size > 1) {
                // The ramp climbs the way the lines travel, so its dark end sits at the axis' own origin.
                shader = LinearGradient(
                    alongAxis.startX, alongAxis.startY, alongAxis.endX, alongAxis.endY,
                    ramp.reversed().toIntArray(), null, Shader.TileMode.CLAMP,
                )
            }
        }

        // The frame's centre in the rank's frame: the rank is laid symmetrically about it, and each line is walked
        // from one end of the frame's extent along it to the other.
        val centerX = (width - 1) * Half
        val centerY = (height - 1) * Half
        val centerAlong = centerX * alongX + centerY * alongY
        val centerAcross = centerX * acrossX + centerY * acrossY
        // A round cap sits half a stroke past the last point, so starting a stroke's width out keeps the ends off-frame.
        val alongStart = centerAlong - alongAxis.lengthPx * Half - strokeWidth
        val alongEnd = centerAlong + alongAxis.lengthPx * Half + strokeWidth
        val step = (reference / detail / SamplesPerCycle).coerceIn(MinStepPx, MaxStepPx)

        val path = Path()
        repeat(count) { lane ->
            val across = centerAcross + (lane - (count - 1) * Half) * spacing
            val fieldAcross = across * frequency
            path.rewind()
            var along = alongStart
            var first = true
            while (along < alongEnd + step) {
                val at = min(along, alongEnd)
                val offset = across + amplitude * noise.at(at * frequency, fieldAcross)
                path.pointAt(at * alongX + offset * acrossX, at * alongY + offset * acrossY, first)
                first = false
                along += step
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    /** Starts the path or extends it — the two calls differ only in which one a point is the first of. */
    private fun Path.pointAt(x: Float, y: Float, first: Boolean) = if (first) moveTo(x, y) else lineTo(x, y)

    /** How many lanes the rank holds at [density] — a few broad bands up to a fine comb. */
    internal fun lineCount(density: Float): Int = Amount.at(density)

    /**
     * The distance between neighbouring lanes, for a frame whose [diagonal] the rank is ruled across and the [count]
     * of lanes it holds.
     *
     * **The diagonal rather than the frame's extent across the rank, so that turning the design only turns it.** The
     * extent across a rank shrinks and grows as it rotates — on a tall phone it is the height at `0°` and the width at
     * `90°` — so a spacing derived from it would treble the line density over that turn, and *Rotation* would be two
     * knobs. The diagonal is the largest extent the rank can ever need, so one spacing covers the frame at every
     * angle; the reference does the same, its rank measuring the same pitch at `0` and `90`.
     *
     * The rank is then ruled [MaxWander] wider on each side, which is exactly how far a line may be pushed off its
     * lane — without that margin the outermost lanes wander in and leave a bare strip along an edge. A single lane has
     * nothing to space and answers the whole extent.
     */
    internal fun spacingPx(diagonal: Float, count: Int): Float =
        if (count <= 1) diagonal else diagonal * (1f + 2f * MaxWander) / (count - 1)

    /**
     * The noise frequency at [roundness], in cycles across the frame's longer side — [MaxDetail] tight ripples at
     * `0`, one broad swell at `1`.
     *
     * Inverted and curved: the field's `0` must be the *sharp* end, and the exponent is what lands the default `0.5`
     * on the reference's shipped detail of about eight cycles.
     */
    internal fun detailFor(roundness: Float): Float =
        1f + (1f - roundness.coerceIn(0f, 1f)).pow(DetailCurve) * (MaxDetail - 1f)

    /**
     * A stroke's width as a share of its lane at [scale] — a hairline at `0`, exactly touching its neighbours at `1`.
     *
     * The cube is what puts the reference's shipped `12%` on the field's default `0.5`; see the class note.
     */
    internal fun thicknessFraction(scale: Float): Float = scale.coerceIn(0f, 1f).pow(ThicknessCurve)

    /**
     * The largest sideways displacement, in pixels, that still leaves every lane in order, for a field read at
     * [frequency] cycles per pixel.
     *
     * Lane `i` is drawn at `vᵢ + amplitude · noise(u, vᵢ)`, so lanes stay ordered while
     * `amplitude · ∂noise/∂v > −1`; `∂noise/∂v` is at most [frequency] × [PerlinMaxSlope], and
     * [NonCrossingMargin] keeps the maximum a little inside the bound rather than exactly on it. A degenerate
     * frequency of zero has no bound at all, and answers a floor so the amplitude stays finite.
     */
    internal fun amplitudeCeiling(frequency: Float): Float =
        NonCrossingMargin / (max(frequency, MinFrequency) * PerlinMaxSlope)

    /**
     * How far a line may leave its lane, in pixels, on a frame of this [diagonal] with the field read at [frequency].
     *
     * The ordering bound alone would allow a *sweep* at the smoothest setting — it scales as `1 / frequency`, so one
     * cycle across the frame buys a deflection near the frame's own size — which is both an ugly picture and more than
     * the rank's margin covers. [MaxWander] is that second ceiling, and it is the one [spacingPx] rules the extra
     * lanes for, so the two cannot disagree about how far a line can go.
     */
    internal fun amplitudeFor(frequency: Float, diagonal: Float): Float =
        min(amplitudeCeiling(frequency), diagonal * MaxWander)

    /** The share of the diagonal a line may wander off its lane — the rank is ruled this much wider on each side. */
    private const val MaxWander = 0.15f

    /** The steepest this Perlin field climbs per unit of its own domain — the divisor [amplitudeCeiling] is built on. */
    private const val PerlinMaxSlope = 2f

    /** How far inside the ordering bound the largest amplitude sits, so the extreme setting is safe rather than exact. */
    private const val NonCrossingMargin = 0.85f

    /** A frequency floor, so a degenerate detail of zero cycles does not divide the amplitude by nothing. */
    private const val MinFrequency = 1e-4f

    /** Cycles across the frame's longer side at the sharpest [detailFor], and the exponent that centres its default. */
    private const val MaxDetail = 20f
    private const val DetailCurve = 1.5f

    /** The exponent that maps [DesignParams.scale]'s default onto the reference's shipped stroke share. */
    private const val ThicknessCurve = 3f

    /** How many points a noise cycle is walked with, and the pixel bounds that keeps sane on any frame. */
    private const val SamplesPerCycle = 24f
    private const val MinStepPx = 2f
    private const val MaxStepPx = 24f

    private const val Quarter = 90f
    private const val Half = 0.5f
}
