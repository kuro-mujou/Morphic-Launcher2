package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particles carried through a wave field and left as a mist of translucent dots — the spray (gart's
 * `flowforce/spring`).
 *
 * **The fifth design on a flow field and the first that does not draw a line.** [FlowFieldGenerator] strokes short
 * packed marks, [FlowLinesGenerator] combs hairlines, [RibbonsGenerator] and [RibbonFlowGenerator] draw broad bands;
 * every one of them renders a particle's *path*. This renders the particle: a dot dropped at each step and left
 * there, so what accumulates is a **density** rather than a stroke.
 *
 * **[DesignParams.scale] is what decides whether that reads as spray at all, and both ends are the design.** A dot is
 * placed every [StepShare] of the short side, so a dot smaller than that leaves the trail as a *stipple* — separate
 * marks, drifting pollen, gart's own look — and a dot larger than it laps its neighbor and the trail closes into a
 * continuous plume. The knob crosses that threshold in the middle of its range rather than at an end, which is
 * unusual here and worth knowing before tuning either bound.
 *
 * **The field is an analytic wave rather than noise, which is the other half of what separates it.** Perlin noise
 * gives the wandering, unrepeating swirl the other four are built on; gart's `WaveFlow` is
 * `sin(x) + cos(y)` read as an angle, which turns through several whole revolutions across the frame and so folds the
 * particles into long smooth arcs that come back on themselves. [DesignParams.irregularity] is that amplitude, and
 * `0` is the rigid end the field's contract asks for: no turn at all, so every particle runs the same way and the
 * mist falls into parallel dotted lanes.
 *
 * **The color runs along each trail, not across the frame.** A dot's tone is how many steps its particle has taken,
 * read off the ramp below the ground — so a trail fades from one end of the palette to the other as it travels, and
 * because neighbouring particles are at different stages the frame comes out in soft drifts of color rather than in
 * bands. That is the whole of gart's own coloring, which indexes a nineteen-stop palette expanded to the trail's
 * length.
 *
 * **The dots are batched by tone into [ColorBands] draws, and that is a rendering decision with a bound behind it.**
 * A dot per call is up to a hundred and fifty thousand `drawCircle`s, which is seconds rather than milliseconds; one
 * `drawPoints` per band with a round cap is the same picture in a couple of dozen calls. It is affordable to
 * pre-size because step `i` always falls in band `i × bands / steps`, so a band can hold at most one point per trail
 * per step it covers — an exact bound, not an estimate that would have to grow.
 *
 * [flowAngle] and [toneAt] are pure and tested: a field whose metric is wrong draws smooth arcs that are simply the
 * wrong shape, and a ramp that reaches the ground paints dots in the color of the ground they sit on.
 */
object SprayGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — the particles released, and the *Trails* slider's own range.
     *
     * The top is gart's order of magnitude rather than a guess: it releases a thousand and keeps replacing the ones
     * that leave, which over its run puts a few hundred thousand dots on the frame. One pass of this reaches the same
     * place, and the dense end is the whole reason the design was worth building — a sparse drift of arcs is a
     * different and much quieter picture.
     */
    private val Amount = AmountKnob.Count("Trails", 100..1500)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Dot size",
        irregularity = "Swirl",
        roundness = "Trail length",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // the ground is the darkest stop, as gart's is
        if (RampTones.countFor(palette.size) <= 0) return bitmap // a single-stop palette is all ground

        val trails = trailCount(params.density)
        val steps = trailSteps(params.roundness)
        val shortSide = min(width, height)
        val dot = shortSide * (MinDot + (MaxDot - MinDot) * params.scale.coerceIn(0f, 1f))
        val swirl = params.irregularity.coerceIn(0f, 1f)
        val stride = shortSide * StepShare
        // The field is read in width-shares on both axes, so its arcs are the same shape across the frame as down it.
        val perWidth = 1f / width

        // One bucket per tone. Step `i` lands in band `i * ColorBands / steps`, so a band takes at most one point per
        // trail for each step it covers — which is what makes an exact size possible rather than a growing list.
        val perBand = (steps + ColorBands - 1) / ColorBands
        val buckets = Array(ColorBands) { FloatArray(trails * perBand * 2) }
        val counts = IntArray(ColorBands)

        for (t in 0 until trails) {
            // A stream per trail, so the length and swirl knobs cannot shift where the others start.
            val random = Random(seed + t * TrailStride)
            var x = random.nextFloat() * width
            var y = random.nextFloat() * height

            for (i in 0 until steps) {
                if (!inFrame(x, y, width, height)) break
                val band = i * ColorBands / steps
                val at = counts[band] * 2
                buckets[band][at] = x
                buckets[band][at + 1] = y
                counts[band]++

                val angle = flowAngle(x * perWidth, y * perWidth, swirl)
                x += cos(angle) * stride
                y += sin(angle) * stride
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dot
            alpha = DotAlpha
        }
        for (band in 0 until ColorBands) {
            if (counts[band] == 0) continue
            paint.color = toneAt((band + BandMiddle) / ColorBands, palette)
            paint.alpha = DotAlpha // setting the color carries its own alpha in, so this has to follow it
            canvas.drawPoints(buckets[band], 0, counts[band] * 2, paint)
        }

        return bitmap
    }

    /** Whether a particle is still on the frame — split out because the four bounds are one thought, not four. */
    private fun inFrame(x: Float, y: Float, width: Int, height: Int): Boolean {
        if (x < 0f || x >= width) return false
        return y >= 0f && y < height
    }

    /** How many particles [density] releases — a thin drift up to a dense mist. */
    internal fun trailCount(density: Float): Int = Amount.at(density)

    /** How many steps each particle takes before it stops, at this [length] — a short dash up to a long sweep. */
    internal fun trailSteps(length: Float): Int =
        MinSteps + ((MaxSteps - MinSteps) * length.coerceIn(0f, 1f)).toInt()

    /**
     * Which way the field carries a particle at ([nx], [ny]), in radians — gart's `WaveFlow`, an angle read straight
     * off `sin(x) + cos(y)`.
     *
     * **Both coordinates are shares of the frame's *width*, so [ny] runs past `1` on a taller frame** —
     * [PlasmaGenerator]'s metric, for its reason. Reading each as a share of its own side would stretch the field by
     * the aspect, and a stretched flow field does not look wrong, it looks like a *different* field: the arcs come
     * out elongated and the design reads as though it were composed for a square.
     *
     * **[swirl] `0` is a flat field**, so every particle runs the same way and the mist falls into parallel dotted
     * lanes — a real second picture, and the rigid end [DesignParams.irregularity]'s contract asks for. Climbing it
     * turns the angle through several whole revolutions across the frame, which is what folds the trails into arcs
     * that come back on themselves rather than merely bending them.
     */
    internal fun flowAngle(nx: Float, ny: Float, swirl: Float): Float =
        swirl * WaveAmplitude * (sin(nx * WaveFrequency) + cos(ny * WaveFrequency))

    /**
     * The color a dot [along] its trail takes — the ramp read **below the ground**, which is the palette's last stop.
     *
     * Scaled by [RampTones.spanBelowGround] so the far end of a trail never lands on the ground it is drawn over.
     * That is the mirror of the mosaic's own problem one design across — a mark painted in the color behind it does
     * not read as subtle, it reads as a mark that failed to draw.
     */
    internal fun toneAt(along: Float, palette: Palette): Int =
        LinearGradientGenerator.colorAt(along.coerceIn(0f, 1f) * RampTones.spanBelowGround(palette.size), palette)

    /** A dot's diameter as a share of the frame's short side — a fine mist up to a coarse spatter. */
    private const val MinDot = 0.004f
    private const val MaxDot = 0.022f

    /** How far a particle moves per step, as a share of the short side — gart's magnitude, in a frame-relative unit. */
    private const val StepShare = 0.006f

    /** The steps a particle takes at each end of *Trail length*. */
    private const val MinSteps = 24
    private const val MaxSteps = 260

    /**
     * How opaque one dot is.
     *
     * Low, for [ImpastoGenerator]'s reason: the picture is the *accumulation*, so a dot that covered the ground would
     * make a dense mist look exactly like a sparse one and the count knob would stop meaning anything past its first
     * few hundred.
     */
    private const val DotAlpha = 52

    /** The field's cycles across the frame's width, and how far it turns — gart's own two numbers. */
    private const val WaveFrequency = 10f
    private const val WaveAmplitude = 4f

    /**
     * How many tones the trail's ramp is drawn in.
     *
     * It is a *batching* number as much as a color one — see the class note — so it is large enough that the fade
     * along a trail reads as continuous and small enough that the frame is a couple of dozen draws. Beyond this the
     * steps are already under a level of the palette's own ramp.
     */
    private const val ColorBands = 24

    /** A band's own tone is read at its middle rather than its edge, so the ramp is centered on the dots it colors. */
    private const val BandMiddle = 0.5f

    /** Spaces the per-trail streams apart, so two particles never start in the same place. */
    private const val TrailStride = 0x27BB2EE687B0B0FDL
}
