package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * One open, waving curve drawn again and again between two placements, turning a little further each time, until the
 * copies weave an envelope — *Flow Lines*.
 *
 * **There is no flow field here, and the name is the reference's rather than a description.** What this replaced
 * combed a [PerlinNoise2d] field with up to two thousand streamlines seeded on a jittered lattice and stroked at one
 * hairline width — a brushed grain over the whole frame. Driving the reference refuted it at the first knob: its
 * *Iterations* `1` draws a **single curve**, and its `100` draws an envelope pixel-identical in extent to its `40`.
 * That is a **tween between two fully specified states**, which is [PolygonCascadeGenerator]'s construction exactly —
 * the two are siblings, and the only difference is that the cascade repeats a closed shape and this repeats an open
 * curve. Ours was written in W8a from a one-line note and never checked.
 *
 * **So [DesignParams.density] only subdivides.** Copy `i` is the base curve centred at `t` of the way along a run and
 * turned `t` of the way through the total, so the first and last copies are pinned by the other knobs and the count
 * chooses how densely the space between them is filled. Raising it never re-composes the picture, which is the
 * property that makes the knob safe to drag.
 *
 * **The run is [TweenRun] and the curve's own axis is square to it**, which is what stops the copies piling onto one
 * another: laid along the run they would overlap end to end, and laid across it they fan out into a rank the turn
 * then twists. The reference's *Start* and *End* nudge pads are that run's two ends, seeded here for the reason
 * [TweenRun] carries.
 *
 * **The curve is a straight span bent by [SeededHarmonics], and [DesignParams.irregularity] is how many waves it
 * carries — not how far they swing.** The reference's *Complexity* `0` is a **dead straight line**, which is the
 * rigid end the field's contract asks for and is only reachable if the knob is a frequency: at zero waves every
 * harmonic is a constant, so the curve is straight and merely offset. Driving it to `20` adds waves at much the same
 * swing rather than deepening the ones already there.
 *
 * **The palette runs along each curve's own length, the same ramp on every copy.** Measured twice on the reference:
 * three copies crossing one band of the frame carried three different stops at the same height, so it is not a
 * frame-space gradient; and at a hundred copies the fan shows colour bands sweeping across it that follow *arc*
 * position rather than screen position. So the ink cannot be a shader — a `LinearGradient` is a projection onto a
 * straight axis and this ramp follows a bending path — and the curve is drawn segment by segment instead, each with
 * its own colour. The ground is the palette's dark end and the ramp is everything above it ([RampTones.belowGround]),
 * as [FlowFieldGenerator]'s lit marks are.
 *
 * **Their *Blur section* is not built.** It offers None / Start / End / **Both**, and driven None ↔ Both at two very
 * different configurations it produced **byte-identical** renders both times. The name and the studio's separate
 * Filters stage read as *which part of the run a blur applies to*, with nothing to place while no blur is on; a knob
 * that is only meaningful through another stage's pass is not one to copy on a guess.
 *
 * Deterministic in [seed]. [copyCount], [waveCount], [strokeWidthPx] and [turnRadians] are the pure mappings, and
 * each is silently wrong when it is wrong — a fan at the wrong pitch, weight or turn is still a plausible fan.
 */
object FlowLinesGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — the copies between the two ends, and the *Iterations* slider's range.
     *
     * Its own range rather than the reference's `1..100`, as the cascade takes `2..24` against the same knob: their
     * floor draws a single hairline on a bare frame, which proves the construction and does not make a wallpaper.
     */
    private val Amount = AmountKnob.Count("Iterations", 8..80)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
        irregularity = "Waviness",
        rotation = "Turn",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // the dark end is the ground the lit curves are drawn on
        val tones = RampTones.belowGround(palette)
        if (tones.isEmpty()) return bitmap // a single-stop palette is all ground, with nothing to draw on it
        val ramp = Palette(tones.toList())

        val random = Random(seed)
        // Drawn before the run, so moving the waviness knob cannot shift the seeded stream underneath the placement.
        val wave = SeededHarmonics(WaveWeights, WaveHarmonics, random)
        val march = tweenRun(width, height, random)
        // Which way the fan twists is the seed's, as the cascade's is: the knob asks how far, not which way.
        val turn = (if (random.nextBoolean()) 1f else -1f) * turnRadians(params.rotation)

        val copies = copyCount(params.density)
        val waves = waveCount(params.irregularity)
        // The curve runs square to the march, so the copies fan across it rather than stacking end to end.
        val axisX = -march.headingY
        val axisY = march.headingX
        val span = hypot(width.toFloat(), height.toFloat()) * SpanOfDiagonal
        val swing = span * WaveSwing

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeWidthPx(params.scale, width)
        }

        for (i in 0 until copies) {
            val t = if (copies <= 1) 0f else i.toFloat() / (copies - 1)
            val cx = march.firstX + (march.lastX - march.firstX) * t
            val cy = march.firstY + (march.lastY - march.firstY) * t
            // The copy's own axes: the curve's span turned by this copy's share of the total.
            val angle = turn * t
            val cosA = cos(angle)
            val sinA = sin(angle)
            val alongX = axisX * cosA - axisY * sinA
            val alongY = axisX * sinA + axisY * cosA
            val sideX = -alongY
            val sideY = alongX

            var prevX = 0f
            var prevY = 0f
            for (s in 0..Samples) {
                val u = s.toFloat() / Samples
                val along = (u - Half) * span
                val side = swing * wave.at(u * waves * TwoPi)
                val x = cx + alongX * along + sideX * side
                val y = cy + alongY * along + sideY * side
                if (s > 0) {
                    // One colour per segment: the ramp follows the curve's own length, which no shader can do.
                    paint.color = LinearGradientGenerator.colorAt(u, ramp)
                    canvas.drawLine(prevX, prevY, x, y, paint)
                }
                prevX = x
                prevY = y
            }
        }
        return bitmap
    }

    /** How many copies [density] asks for — a loose rank up to a woven envelope. */
    internal fun copyCount(density: Float): Int = Amount.at(density)

    /**
     * How many waves the base curve carries at [irregularity] — `0` is dead straight, which is the rigid end.
     *
     * A fraction rather than a whole number, so the knob moves the curve continuously; a wave count that stepped
     * would redraw the whole design on every step of a slider whose other neighbours are smooth.
     */
    internal fun waveCount(irregularity: Float): Float = irregularity.coerceIn(0f, 1f) * MaxWaves

    /**
     * The stroke width in pixels at [scale], on a frame of this [width].
     *
     * **Measured against the reference's own ruler**, which runs `1..10` and defaults to `1.8`: its strokes came back
     * at `2.56px` per unit on a 1080-wide frame, so its top is [MaxStrokeFraction] of the width and its default is
     * about `0.0043`. The exponent is what lands the field's `0.5` on that default and leaves the fine end — where
     * the design's weave lives — most of the travel. Floored at a pixel, since a sub-pixel stroke antialiases to a
     * grey smear rather than to a fine line.
     */
    internal fun strokeWidthPx(scale: Float, width: Int): Float =
        max(1f, MaxStrokeFraction * width * scale.coerceIn(0f, 1f).pow(StrokeCurve))

    /**
     * The whole fan's turn in radians at [rotation] — `0` leaves every copy aligned.
     *
     * The **total**, first copy to last, as the reference's *Delta rotation* is: it reaches `500°` there and defaults
     * to `150°`, and the square is what puts that default on the field's `0.5` while leaving the far end past a full
     * revolution, where the fan closes on itself.
     */
    internal fun turnRadians(rotation: Float): Float =
        MaxTurn * rotation.coerceIn(0f, 1f).pow(TurnCurve)

    /** How many points a curve is walked with — enough that its waves read as curves and its ramp as a gradient. */
    private const val Samples = 160

    /** The curve's length as a share of the frame's diagonal — over one, so its ends run off the frame as theirs do. */
    private const val SpanOfDiagonal = 1.15f

    /** How far the waves swing, as a share of the curve's own length — measured off the reference's own curve. */
    private const val WaveSwing = 0.09f

    /** Which harmonics bend the curve and how much each contributes — low ones, so the waves are waves. */
    private val WaveHarmonics = floatArrayOf(1f, 2f, 3.7f)
    private val WaveWeights = floatArrayOf(0.6f, 0.28f, 0.12f)

    /**
     * Waves along the whole curve at [DesignParams.irregularity] `1`.
     *
     * **Four, not the twenty their *Complexity* ruler counts to.** Their number is not a wave count: at `10` the base
     * curve carries about two waves over its length and at `20` about four, so the ruler is roughly five clicks to
     * the wave. Taking it at face value drew a tight zigzag where theirs draws a long, lazy S — the design's whole
     * character — which is the sort of thing only a render beside a render shows.
     */
    private const val MaxWaves = 4f

    /** The stroke at [DesignParams.scale] `1`, as a share of the frame's width, and the exponent centring it. */
    private const val MaxStrokeFraction = 0.024f
    private const val StrokeCurve = 2.5f

    /** The fan's turn at [DesignParams.rotation] `1`, and the exponent that centres the reference's default. */
    private const val MaxTurn = 600f * PI.toFloat() / 180f
    private const val TurnCurve = 2f

    private const val TwoPi = 2f * PI.toFloat()
    private const val Half = 0.5f
}
