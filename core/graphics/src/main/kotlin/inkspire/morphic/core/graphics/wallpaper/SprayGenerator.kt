package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
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
 * **The field is `sin(x) + cos(y)` read as an angle, and what makes it a mist is how fast it turns compared to how
 * far a particle steps.** [WaveFrequency]'s period is a *fraction of a step*, so two consecutive positions read the
 * field several periods apart, the angle a particle gets is uncorrelated with the last one, and it **random walks**:
 * a trail is not a curve at all but a compact cloud spreading as the square root of its length. A thousand of those
 * overlapping is the granular mist.
 *
 * **The first build of this design got that one number wrong, and the mistake is worth recording.** gart builds its
 * field with `FlowField.of(d) { x, y -> … }`, which hands the function **pixel** coordinates — so `sin(x * 10)` has a
 * period of `0.63` of a pixel against a step of seven. Read as though the coordinates were normalized, the same two
 * numbers describe a field that turns once or twice across the whole frame, whose particles trace long smooth arcs
 * and fill the frame with feathered plumes. Both draw something; only one of them is Spring, and nothing in the
 * source says which reading is meant except the picture. The plume was kept for a while as a second look and is gone
 * by the author's call — this design is the mist.
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
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val plan = plan(width, height, params, seed)
        draw(Canvas(bitmap), listOf(sorted(plan)), plan, palette, width, height)
        return bitmap
    }

    /**
     * A mist planned but not painted: every trail walked, each dot where the walk left it.
     *
     * **In pixels, like Confetti's plan**, since a trail stops where it first leaves the frame and that is decided in
     * pixels; [draw] reaches another size by scaling the canvas.
     *
     * @property steps the most dots a trail may leave — trail `t`'s are at `t × steps` onward, [lengths] of them.
     * @property xs every dot's `x`, trail-major.
     * @property ys every dot's `y`, likewise.
     * @property lengths how many dots each trail left before it walked off the frame — never fewer than one, since
     *   every trail starts on it.
     */
    @Suppress("LongParameterList") // The dots, how they are laid out, and the knobs and frame they were walked for.
    internal class Plan(
        val params: DesignParams,
        val steps: Int,
        val xs: FloatArray,
        val ys: FloatArray,
        val lengths: IntArray,
        val width: Int,
        val height: Int,
    )

    /** The mist [params] and [seed] describe in a `[width]` × `[height]` frame. */
    internal fun plan(width: Int, height: Int, params: DesignParams, seed: Long): Plan {
        val trails = trailCount(params.density)
        val steps = trailSteps(params.roundness)
        val shortSide = min(width, height)
        val turbulence = params.irregularity.coerceIn(0f, 1f)
        val stride = shortSide * StepShare
        // The field is read in width-shares on both axes, so its arcs are the same shape across the frame as down it.
        val perWidth = 1f / width

        val xs = FloatArray(trails * steps)
        val ys = FloatArray(trails * steps)
        val lengths = IntArray(trails)
        for (t in 0 until trails) {
            // A stream per trail, so the length and turbulence knobs cannot shift where the others start.
            val random = Random(seed + t * TrailStride)
            var x = random.nextFloat() * width
            var y = random.nextFloat() * height
            for (i in 0 until steps) {
                if (!inFrame(x, y, width, height)) break
                xs[t * steps + i] = x
                ys[t * steps + i] = y
                lengths[t]++
                val angle = flowAngle(x * perWidth, y * perWidth, turbulence)
                x += cos(angle) * stride
                y += sin(angle) * stride
            }
        }
        return Plan(params, steps, xs, ys, lengths, width, height)
    }

    /**
     * Dots sorted by tone, all drawn at one [opacity] — one array of `x, y` pairs per tone band, grown as dots arrive.
     *
     * **One bucket per tone, because a band is one draw call.** A dot's band is mostly its trail's start height, so
     * unlike a pure along-the-trail ramp there is no per-step bound on a band, and the buckets grow rather than being
     * sized.
     */
    internal class Dots(val opacity: Float = 1f) {
        val points = Array(ColorBands) { FloatArray(InitialBucket) }
        val counts = IntArray(ColorBands)

        /** Files the dot at ([x], [y]) under the tone a trail started [from] its frame's top and [along] it has. */
        fun add(from: Float, along: Float, x: Float, y: Float) {
            val band = (bandAt(from, along) * ColorBands).toInt().coerceIn(0, ColorBands - 1)
            if (counts[band] * 2 == points[band].size) points[band] = points[band].copyOf(points[band].size * 2)
            val at = counts[band] * 2
            points[band][at] = x
            points[band][at + 1] = y
            counts[band]++
        }
    }

    /** [plan]'s dots, sorted by tone in the order the walk laid them — the bake's. */
    internal fun sorted(plan: Plan): Dots {
        val dots = Dots()
        val perHeight = 1f / plan.height
        for (t in plan.lengths.indices) {
            val first = t * plan.steps
            val from = plan.ys[first] * perHeight
            for (i in 0 until plan.lengths[t]) dots.add(from, alongOf(i, plan.steps), plan.xs[first + i], plan.ys[first + i])
        }
        return dots
    }

    /** How far along a trail of [steps] its dot [i] is, `0..1`. */
    private fun alongOf(i: Int, steps: Int): Float = if (steps <= 1) 0f else i.toFloat() / (steps - 1)

    /**
     * A scrub between two mists: every cloud slides from where its trail started in one to where it started in the
     * other, and keeps its spread the whole way.
     *
     * **Interpolating the starts and walking again would be noise, not a morph.** The field turns faster than a
     * particle steps — that is what makes the mist — so a start nudged by a fraction of a pixel walks an unrelated
     * trail, and a scrub that re-walked would boil. So each dot keeps the place its own walk left it, as an offset from
     * its trail's start, and [Morph] carries those offsets from one mist to the other. A trail that walked off the
     * frame sooner at one end has dots the other lacks; those fade out, or in, rather than collapse.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val a = plan(width, height, params, from)
        val b = plan(width, height, params, to)
        val morph = Morph(a, b)
        return WallpaperMorph { canvas, t, w, h ->
            when {
                t <= 0f -> draw(canvas, listOf(sorted(a)), a, palette, w, h)
                t >= 1f -> draw(canvas, listOf(sorted(b)), b, palette, w, h)
                else -> draw(canvas, morph.at(t), a, palette, w, h)
            }
        }
    }

    /**
     * Two mists and every moment between them: each trail's start slides, and each dot's offset from it is carried as
     * the mist's **drift** plus the dot's own **deviation** from that drift.
     *
     * **The deviations turn ([turnNoise]) and the drift is interpolated, and the split is what keeps both honest.** The
     * field's angle is symmetric about zero, so its cosine averages above zero and every particle drifts the same way
     * — the parallel lanes at no *Turbulence* are that drift alone. So an offset is not a zero-mean sample: turned
     * whole, the drift both ends share would stretch by up to `√2` mid-scrub, and blended straight, the random spread
     * would pull in to about 0.7. The drift at each step is the mean offset over every trail that reached it, worked
     * out once per scrub; what is left of each offset is zero-mean, and turning that keeps a cloud's spread exactly.
     *
     * **A trail's partner is the nearest one, not the one at its own index.** Every other scatter here sits on a
     * jittered lattice, so a primitive's partner is the one in its own cell. This one does not: a trail starts anywhere,
     * and two unrelated uniform points averaged fall toward the middle, so pairing by index pulls the whole mist in
     * from the frame's edges halfway through a scrub. So both sets of starts are ranked along one Hilbert curve and
     * paired rank for rank ([partners]) — the curve keeps nearby points nearby, so a cloud moves only as far as its
     * nearest counterpart, and the midpoints stay spread over the frame.
     */
    internal class Morph(private val a: Plan, private val b: Plan) {
        private val driftA = drift(a)
        private val driftB = drift(b)

        /** Each of [a]'s trails' partner in [b] — see the class note. */
        val partner = partners(a, b)

        /**
         * The mist [t] of the way across, as three sets of dots: those both ends have, at full opacity, and those only
         * one end has, fading with the scrub.
         */
        fun at(t: Float): List<Dots> {
            val both = Dots()
            val leaving = Dots(opacity = 1f - t)
            val arriving = Dots(opacity = t)
            val turn = NoiseTurn(t)
            val perHeight = 1f / a.height
            val shared = FloatArray(2)
            for (trail in a.lengths.indices) {
                val first = trail * a.steps
                val other = partner[trail] * b.steps
                val x0 = a.xs[first] + (b.xs[other] - a.xs[first]) * t
                val y0 = a.ys[first] + (b.ys[other] - a.ys[first]) * t
                val from = y0 * perHeight
                val inA = a.lengths[trail]
                val inB = b.lengths[partner[trail]]
                for (i in 0 until maxOf(inA, inB)) {
                    val along = alongOf(i, a.steps)
                    when {
                        i < inA && i < inB -> {
                            shared(trail, i, t, turn, shared)
                            both.add(from, along, shared[0], shared[1])
                        }
                        i < inA -> leaving.add(
                            from,
                            along,
                            x0 + a.xs[first + i] - a.xs[first],
                            y0 + a.ys[first + i] - a.ys[first],
                        )
                        else -> arriving.add(
                            from,
                            along,
                            x0 + b.xs[other + i] - b.xs[other],
                            y0 + b.ys[other + i] - b.ys[other],
                        )
                    }
                }
            }
            return listOf(both, leaving, arriving)
        }

        /**
         * Where dot [i] of [trail], which both it and its [partner] have, stands [t] of the way across — written to
         * [out] as `x, y`. The start slides, the drift is interpolated and the deviation turns by [turn], which is [t]'s.
         */
        fun shared(trail: Int, i: Int, t: Float, turn: NoiseTurn, out: FloatArray) {
            val first = trail * a.steps
            val other = partner[trail] * b.steps
            for (axis in 0..1) {
                val pa = if (axis == 0) a.xs else a.ys
                val pb = if (axis == 0) b.xs else b.ys
                val da = driftA[i * 2 + axis]
                val db = driftB[i * 2 + axis]
                val start = pa[first] + (pb[other] - pa[first]) * t
                val deviation = turn.of(pa[first + i] - pa[first] - da, pb[other + i] - pb[other] - db)
                out[axis] = start + da + (db - da) * t + deviation
            }
        }

        /**
         * [a]'s trails' partners in [b]: both sets of starts ranked along one Hilbert curve over the frame, and paired
         * rank for rank.
         */
        private fun partners(a: Plan, b: Plan): IntArray {
            val span = maxOf(a.width, a.height).toFloat()
            fun ranked(plan: Plan) = plan.lengths.indices.sortedBy { trail ->
                val start = trail * plan.steps
                hilbert((plan.xs[start] / span * HilbertSide).toInt(), (plan.ys[start] / span * HilbertSide).toInt())
            }
            val fromA = ranked(a)
            val fromB = ranked(b)
            val partner = IntArray(a.lengths.size)
            for (rank in fromA.indices) partner[fromA[rank]] = fromB[rank]
            return partner
        }

        /** [plan]'s mean offset from a trail's start at each step, over every trail that reached it — `x, y` pairs. */
        private fun drift(plan: Plan): FloatArray {
            val sums = DoubleArray(plan.steps * 2)
            val counts = IntArray(plan.steps)
            for (trail in plan.lengths.indices) {
                val first = trail * plan.steps
                for (i in 0 until plan.lengths[trail]) {
                    sums[i * 2] += (plan.xs[first + i] - plan.xs[first]).toDouble()
                    sums[i * 2 + 1] += (plan.ys[first + i] - plan.ys[first]).toDouble()
                    counts[i]++
                }
            }
            return FloatArray(plan.steps * 2) { if (counts[it / 2] == 0) 0f else (sums[it] / counts[it / 2]).toFloat() }
        }
    }

    /**
     * Paints [layers] of dots, walked for [plan]'s frame, into [canvas] at `[width]` × `[height]`, in [palette] — one
     * `drawPoints` per tone band per layer.
     */
    @Suppress("LongParameterList") // The dots, the plan that sizes them, and a palette and frame to paint them in.
    internal fun draw(canvas: Canvas, layers: List<Dots>, plan: Plan, palette: Palette, width: Int, height: Int) {
        canvas.drawColor(palette.colorAt(palette.size - 1)) // the ground is the darkest stop, as gart's is
        if (RampTones.countFor(palette.size) <= 0) return // a single-stop palette is all ground
        val dot = min(plan.width, plan.height) * (MinDot + (MaxDot - MinDot) * plan.params.scale.coerceIn(0f, 1f))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dot
            alpha = DotAlpha
        }
        val resized = width != plan.width || height != plan.height
        if (resized) {
            canvas.save()
            canvas.scale(width.toFloat() / plan.width, height.toFloat() / plan.height)
        }
        for (dots in layers) {
            // The bake's own alpha exactly, at full opacity, rather than a product that rounds to it.
            val alpha = if (dots.opacity >= 1f) DotAlpha else (DotAlpha * dots.opacity).roundToInt()
            if (alpha <= 0) continue
            for (band in 0 until ColorBands) {
                if (dots.counts[band] == 0) continue
                paint.color = toneAt((band + BandMiddle) / ColorBands, palette)
                paint.alpha = alpha // setting the color carries its own alpha in, so this has to follow it
                canvas.drawPoints(dots.points[band], 0, dots.counts[band] * 2, paint)
            }
        }
        if (resized) canvas.restore()
    }

    /** Whether a particle is still on the frame — split out because the four bounds are one thought, not four. */
    private fun inFrame(x: Float, y: Float, width: Int, height: Int): Boolean {
        if (x < 0f || x >= width) return false
        return y >= 0f && y < height
    }

    /**
     * Where ([x], [y]) falls along a Hilbert curve filling a [HilbertSide] square — the textbook `xy2d`.
     *
     * A space-filling curve rather than a sort by one axis, because it is the ranking that keeps nearby points nearby
     * in *both* directions: sorted by `y`, two points a row apart are neighbors and two a pixel apart across a row
     * boundary are not.
     */
    internal fun hilbert(x: Int, y: Int): Int {
        var px = x.coerceIn(0, HilbertSide - 1)
        var py = y.coerceIn(0, HilbertSide - 1)
        var d = 0
        var s = HilbertSide / 2
        while (s > 0) {
            val rx = if (px and s > 0) 1 else 0
            val ry = if (py and s > 0) 1 else 0
            d += s * s * ((QuadrantOrder * rx) xor ry)
            if (ry == 0) {
                if (rx == 1) {
                    px = HilbertSide - 1 - px
                    py = HilbertSide - 1 - py
                }
                val swap = px
                px = py
                py = swap
            }
            s /= 2
        }
        return d
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
    internal fun flowAngle(nx: Float, ny: Float, turbulence: Float): Float =
        turbulence * WaveAmplitude * (sin(nx * WaveFrequency) + cos(ny * WaveFrequency))

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
     * How fast the field turns — the one number that makes this a mist rather than a set of arcs.
     *
     * Its period is a fraction of one [StepShare], so consecutive steps read the field several periods apart and the
     * particle random walks. That is gart's own regime expressed in a **frame-relative** unit rather than its pixel
     * one: reading `sin(x * 10)` off pixel coordinates ties the grain to the render's resolution, where this keeps
     * the same picture at any size.
     */
    private const val WaveFrequency = 3000f

    /** How far the field turns at full *Turbulence*, per axis — gart's own, and over a whole revolution across the two. */
    private const val WaveAmplitude = 4f

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

    /** The Hilbert curve's side, in cells — a power of two, fine enough that no two starts on a frame share a cell. */
    private const val HilbertSide = 1024

    /** `xy2d`'s quadrant weight: `3 × rx xor ry` numbers a cell's four quadrants in the order the curve visits them. */
    private const val QuadrantOrder = 3

    /** Spaces the per-trail streams apart, so two particles never start in the same place. */
    private const val TrailStride = 0x27BB2EE687B0B0FDL
}
