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
 * **[DesignParams.scale] is the grain.** A dot is placed every [StepShare] of the short side and gart's is very
 * slightly wider than its own step, so the marks of a *Plume* just touch and close into a line while the marks of a
 * *Spray*, going a different way each time, stay legible as grain however much they overlap.
 *
 * **The field is `sin(x) + cos(y)` read as an angle, and the only thing that decides what the design looks like is
 * how fast it turns compared to how far a particle steps.** That is [DesignParams.variant], and it is a difference of
 * one number:
 *
 * - **Spray** reads the field at [SprayFrequency], whose period is a *fraction of a step*. Two consecutive positions
 *   are then several periods apart, so the angle a particle gets is uncorrelated with the last one and it **random
 *   walks**: a trail is not a curve at all but a compact cloud that spreads as the square root of its length. A
 *   thousand of those overlapping is the granular mist gart draws, and it is the default.
 * - **Plume** reads it at [PlumeFrequency], slow enough that neighbouring steps agree, so the same particles trace
 *   long smooth arcs that fold back on themselves and the frame fills with feathered plumes.
 *
 * **The first build of this design had only the second, by mistake, and the mistake is worth recording.** gart builds
 * its field with `FlowField.of(d) { x, y -> … }`, which hands the function **pixel** coordinates — so `sin(x * 10)`
 * has a period of `0.63` of a pixel against a step of seven. Read as though the coordinates were normalized, the same
 * two numbers describe a field that turns once or twice across the whole frame. Both draw something; only one of them
 * is Spring, and nothing in the source says which reading is meant except the picture.
 *
 * **[DesignParams.irregularity] is how far the field turns**, gart's own two amplitudes. `0` is the rigid end the
 * field's contract asks for — no turn at all, so every particle runs one way and the mist falls into parallel dotted
 * lanes — and anything past about a third already spans every direction, which is why the chaos does not need the top
 * of the knob to arrive.
 *
 * **The color is mostly where a trail *started* and partly how far along it is** — see [toneAt]. gart colors purely by
 * the second, indexing a nineteen-stop palette expanded to the trail's length, and gets the broad warm-top,
 * cool-bottom drift of its own picture from somewhere else entirely: it runs six hundred frames, kills trails that
 * leave the frame and reseeds the replacements in the **bottom half**, so the population itself sorts by age and
 * therefore by color. A generator that renders one pass has no history to sort, and reproducing that would mean
 * simulating six hundred frames to throw away five hundred and ninety-nine. Reading part of the tone off the start
 * height is the same picture from a mechanism a single pass has: the drift arrives, and the walk still mixes the ramp
 * inside each cloud so it never bands.
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
     * The top is gart's own: it releases a thousand and keeps replacing the ones that leave. Paired with a trail of
     * [MaxSteps] that is around half a million dots on the frame, which is what a mist is made of — a few hundred
     * clouds is a different and much quieter picture.
     */
    private val Amount = AmountKnob.Count("Trails", 100..1200)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Dot size",
        irregularity = "Turbulence",
        roundness = "Trail length",
        variant = VariantKnob("Look", listOf("Spray", "Plume")),
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
        val turbulence = params.irregularity.coerceIn(0f, 1f)
        val frequency = if (params.variant.coerceIn(0, 1) == LookPlume) PlumeFrequency else SprayFrequency
        val stride = shortSide * StepShare
        // The field is read in width-shares on both axes, so its arcs are the same shape across the frame as down it.
        val perWidth = 1f / width
        val perHeight = 1f / height

        // One bucket per tone. A dot's band is its tone, and a tone is mostly the trail's start height — so unlike a
        // pure along-the-trail ramp there is no per-step bound on a band, and the buckets are grown rather than sized.
        val buckets = Array(ColorBands) { FloatArray(InitialBucket) }
        val counts = IntArray(ColorBands)

        for (t in 0 until trails) {
            // A stream per trail, so the length and turbulence knobs cannot shift where the others start.
            val random = Random(seed + t * TrailStride)
            var x = random.nextFloat() * width
            var y = random.nextFloat() * height
            val from = y * perHeight

            for (i in 0 until steps) {
                if (!inFrame(x, y, width, height)) break
                val along = if (steps <= 1) 0f else i.toFloat() / (steps - 1)
                val band = (bandAt(from, along) * ColorBands).toInt().coerceIn(0, ColorBands - 1)
                if (counts[band] * 2 == buckets[band].size) {
                    buckets[band] = buckets[band].copyOf(buckets[band].size * 2)
                }
                val at = counts[band] * 2
                buckets[band][at] = x
                buckets[band][at + 1] = y
                counts[band]++

                val angle = flowAngle(x * perWidth, y * perWidth, turbulence, frequency)
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
    internal fun flowAngle(nx: Float, ny: Float, turbulence: Float, frequency: Float): Float =
        turbulence * WaveAmplitude * (sin(nx * frequency) + cos(ny * frequency))

    /**
     * Where on the ramp a dot sits: mostly the height its trail started [from], partly how far [along] the trail it
     * is — `0..1`, before the ground is kept clear of it.
     *
     * **[DriftShare] of the answer is the start height and the rest is the walk**, which is the port's own arithmetic
     * and not gart's — see the class note for why a single pass cannot get the drift the way gart does. The split
     * matters in both directions: all height and the frame bands into flat horizontal stripes with no mixing, all
     * walk and every cloud holds the whole palette so the frame averages to one muddy tone.
     */
    internal fun bandAt(from: Float, along: Float): Float =
        (from.coerceIn(0f, 1f) * DriftShare + along.coerceIn(0f, 1f) * (1f - DriftShare)).coerceIn(0f, 1f)

    /**
     * The color at [position] on the ramp — read **below the ground**, which is the palette's last stop.
     *
     * Scaled by [RampTones.spanBelowGround] so a dot never lands on the ground it is drawn over. That is the mirror
     * of the mosaic's own problem one design across — a mark painted in the color behind it does not read as subtle,
     * it reads as a mark that failed to draw.
     */
    internal fun toneAt(position: Float, palette: Palette): Int =
        LinearGradientGenerator.colorAt(position.coerceIn(0f, 1f) * RampTones.spanBelowGround(palette.size), palette)

    /**
     * A dot's diameter as a share of the frame's short side — a fine mist up to a coarse spatter.
     *
     * gart's is `8` pixels on a `1024` frame, a shade under `0.008`, which lands just above the middle here.
     */
    private const val MinDot = 0.003f
    private const val MaxDot = 0.014f

    /** How far a particle moves per step, as a share of the short side — gart's magnitude, in a frame-relative unit. */
    private const val StepShare = 0.006f

    /**
     * The steps a particle takes at each end of *Trail length* — gart's trail holds five hundred.
     *
     * On a *Spray* this is not a length but a **density**: a random walk of `n` steps spreads as `√n`, so four times
     * the steps is only twice the cloud and the rest of them pile into it.
     */
    private const val MinSteps = 40
    private const val MaxSteps = 500

    /**
     * How opaque one dot is.
     *
     * Low, for [ImpastoGenerator]'s reason: the picture is the *accumulation*, so a dot that covered the ground would
     * make a dense mist look exactly like a sparse one and the count knob would stop meaning anything past its first
     * few hundred.
     */
    private const val DotAlpha = 52

    /**
     * How fast the field turns, for each look — the one number that separates a mist from a plume.
     *
     * [SprayFrequency] puts the field's period at a fraction of one [StepShare], so consecutive steps read it several
     * periods apart and the particle random walks. That is gart's own regime expressed in a **frame-relative** unit
     * rather than its pixel one: reading `sin(x * 10)` off pixel coordinates ties the grain to the render's
     * resolution, where this keeps the same picture at any size. [PlumeFrequency] is slow enough that a step barely
     * moves the angle, so the walk straightens into an arc.
     */
    private const val SprayFrequency = 3000f
    private const val PlumeFrequency = 10f

    /** How far the field turns at full *Turbulence*, per axis — gart's own, and more than a whole revolution over the two. */
    private const val WaveAmplitude = 4f

    /** [DesignParams.variant] selecting the smooth field, whose particles trace arcs instead of walking. */
    private const val LookPlume = 1

    /** How much of a dot's tone is the height its trail started at rather than its distance along it — see [bandAt]. */
    private const val DriftShare = 0.7f

    /** Room for a band's first dots; it doubles from here, since a band's share of them is not known in advance. */
    private const val InitialBucket = 4096

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
