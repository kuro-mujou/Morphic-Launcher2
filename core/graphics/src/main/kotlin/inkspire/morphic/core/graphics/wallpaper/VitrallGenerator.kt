package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The frame cut and re-cut into leaded panes of tinted glass — *Vitrall*, the cathedral window.
 *
 * **The cuts run edge to edge, which is what separates this from a mosaic.** A Voronoi breaks a frame into cells
 * *around points*, so every cell is a compact blob of roughly its neighbours' size; this cuts it *with lines*, so one
 * chord can run the whole diagonal. Drive the reference's own density to `1` and a single cut crosses the frame,
 * which no point-based diagram can do — that is the measurement this design is built on.
 *
 * **The model is gart's `arts/lines/vitrali`** (`Vitrall.kt` + `glasscut.kt`), and the parts of it that are not
 * obvious are the parts worth naming, because each one is a way a plausible re-invention looks wrong:
 *
 * - **It recurses to a target *area*, not to a count** — a region stops splitting when it is smaller than
 *   `1/panes` of the frame times a **[MinSpread]..[MaxSpread] log-uniform multiplier drawn per branch**. That one
 *   multiplier is what makes the pane sizes vary while keeping them all in a band. Picking the biggest pane to cut
 *   gives a suspiciously even honeycomb.
 * - **Every cut is drawn from a *grain* the whole window shares** — two perpendicular diagonals chosen once from the
 *   seed, plus the vertical and the horizontal, plus the occasional free angle. With the direction chosen per pane
 *   the picture is a scattershot; with a shared grain the edges line up across panes into long lines and the window
 *   reads as *designed*.
 * - **A pane is sometimes glazed into a run of parallel courses** afterwards — straight, or concentric arcs — which
 *   is how a real window is leaded and the detail a plain subdivision does not have.
 * - **The first cuts are remembered as *bones* and re-drawn in heavier lead.** A cathedral window has structural
 *   bars carrying the light panes, and without them a subdivision reads as flat crazing.
 *
 * **[DesignParams.irregularity] is *Curves*, and it is the chance a cut bows** — [GlassCut.bow] clips the pane
 * against a circle instead of a line, so at `0` every cut is straight and at `1` the window is all tracery. The
 * grain's own jitter is a fixed [GrainJitter], as the reference has it; a knob that only widened that jitter would be
 * *Randomness*, which is a different tab of theirs and one that moved the picture barely at all. How a bowed cut
 * leaves no hairline between its two panes is [GlassCut]'s business, and the argument is worth reading before
 * touching either.
 *
 * **The color is a field, not a ramp read at the pane's height.** A rise down the frame plus three octaves of noise,
 * sampled at the pane's centroid, then nudged per pane — and once in [IntrudeChance] a pane takes a tone from clean
 * across the ramp. That last one is what stops the window being smooth bands of near-identical panes: a scattering of
 * strongly contrasting panes is most of what the eye reads as hand-cut glass. **A tone that runs off the ramp is
 * reflected back, not clamped** — clamping piles every overshooting pane onto the same end stop, which shows up as
 * large flat runs of one color exactly where the field is most interesting.
 *
 * **[DesignParams.depth] is the glass**, and it does two things the reference spends two knobs on. Each pane is
 * filled with a gradient at an angle and a strength of its own rather than a flat color, and the glass darkens where
 * it meets the came. Both are most of why its panes read as material rather than as flat cells.
 *   - **The rim is a *wash*, not a stroke of the came.** Black, at [RimAlpha] of full at most and scaled by the knob
 *     — the reference's own value, and the thing that separates "the glass thickens toward the lead" from a blurred
 *     opaque band eating half of every small pane.
 *
 * **[DesignParams.scale] is the leading**, stroked over every fill after every fill is done. Filling and stroking
 * pane by pane would let each fill erase half of its neighbour's line.
 *
 * [panes] is pure and tested, as is the [GlassCut] toolkit under it — a cut that drops a crossing leaves a hairline
 * of ground between two panes, and that reads as a rendering artifact rather than as a bug.
 */
object VitrallGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — the pane count, and the slider's own range.
     *
     * It is a **target**: the subdivision recurses on area and then glazes some panes into courses, so the window
     * lands near this rather than on it. [Overshoot] divides it back down so "near" is centered on the number the
     * slider shows rather than half again above it.
     */
    private val Amount = AmountKnob.Count("Panes", 12..160)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Leading",
        irregularity = "Curves",
        depth = "Glass",
        variant = VariantKnob("Colors", Tint.entries.map { it.label }),
    )

    /**
     * How a pane's tone is chosen — the reference's *Color distribution*.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     */
    internal enum class Tint(val label: String) {
        /** The color field: a rise down the frame under three octaves of noise — the reference's default. */
        FIELD("Field"),

        /** A tone of its own per pane, for a palette that is a set of accents rather than a progression. */
        SCATTERED("Scattered"),
    }

    /**
     * A finished window.
     *
     * @property panes each pane's outline, interleaved `x, y` in a frame [aspect] wide and one tall. A bowed cut
     *   leaves the arc sampled into short segments, so a pane is a plain polygon however curved it looks.
     * @property bones the first few cuts, as full chords across whatever they cut — drawn in heavier lead. Two points
     *   for a straight cut, the sampled arc chain for a bowed one.
     * @property aspect how wide the cut frame was, in units of its own height. Everything above is in that frame.
     */
    internal class Window(val panes: List<FloatArray>, val bones: List<FloatArray>, val aspect: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        // Cut in a frame that is `aspect` wide and 1 tall, not in the unit square: in the unit square a 45° cut on
        // a 1080×2400 frame draws as a near-vertical one, so the grain collapses toward the long axis and the window
        // fills with needles. Aspect-true, one unit is one unit, and `height` is the scale for both axes.
        val window = panes(Amount.at(params.density), params.irregularity, seed, width.toFloat() / height)
        val glass = params.depth.coerceIn(0f, 1f)
        val tint = Tint.entries[params.variant.coerceIn(0, Tint.entries.lastIndex)]
        val lead = params.scale.coerceIn(0f, 1f) * MaxLeading * min(width, height)
        val came = palette.colorAt(palette.size - 1) // darkest stop by convention — the lead between panes

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(came)

        val random = Random(seed xor ToneSalt)
        val noise = PerlinNoise2d(seed xor FieldSalt)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // Black rather than the came, and translucent: the rim is the glass *thickening* toward the lead, so it has
        // to darken whatever tone it lands on. A wash of the palette's darkest stop would lighten a bright pane.
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb((glass * RimAlpha).toInt().coerceIn(0, Opaque), 0, 0, 0)
            strokeWidth = max(1f, lead) * RimWidth
            maskFilter = BlurMaskFilter(max(1f, lead) * RimBlur, BlurMaskFilter.Blur.NORMAL)
        }

        val scale = height.toFloat()
        val paths = window.panes.map { path(it, scale) }
        paths.forEachIndexed { i, path ->
            val pane = window.panes[i]
            val at = tone(pane, tint, noise, random, window.aspect)
            var base = LinearGradientGenerator.colorAt(at, palette)
            // The odd flashed pane, a stop-independent lift — a real window carries a few pieces of much paler glass,
            // and they are what the eye reads as light coming through rather than as color laid on.
            if (random.nextFloat() < FlashChance) base = TriangularFacetsGenerator.shade(base, FlashLift)
            fill.shader = glassShader(pane, base, glass, random, scale)
            canvas.drawPath(path, fill)
            if (glass > 0f) {
                // The rim is a blurred stroke clipped to the pane, so the glass darkens inward only.
                canvas.save()
                canvas.clipPath(path)
                canvas.drawPath(path, rim)
                canvas.restore()
            }
        }
        if (lead > 0f) {
            val came1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = came
                strokeWidth = lead
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            paths.forEach { canvas.drawPath(it, came1) }
            val heavy = Paint(came1).apply { strokeWidth = lead * BoneWidth }
            window.bones.forEach { canvas.drawPath(path(it, scale, close = false), heavy) }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(came1).apply {
                strokeWidth = lead * FrameWidth
            })
        }
        return bitmap
    }

    /**
     * A window of about [count] panes, of which [curves] bow into arcs, from [seed], in a frame [aspect] wide and
     * one tall.
     *
     * The recursion is depth-first over a stack rather than by call, so a fine window cannot run the frame out of
     * stack; [MaxTries] attempts per region and a hard cap on the pane count keep a pathological seed bounded.
     */
    internal fun panes(count: Int, curves: Float, seed: Long, aspect: Float = 1f): Window {
        val random = Random(seed)
        val bowing = curves.coerceIn(0f, 1f)
        val frame = aspect.coerceAtLeast(GlassCut.Tiny)
        val target = frame * Overshoot / count.coerceAtLeast(1)
        // The grain: two perpendicular diagonals for this window, joined by the architectural vertical and horizontal.
        val tilt = random.nextFloat() * (MaxGrain - MinGrain) + MinGrain
        val diagonal = if (random.nextBoolean()) tilt else -tilt
        val grain = floatArrayOf(diagonal, diagonal + GlassCut.Quarter, GlassCut.Quarter, 0f)

        val settled = ArrayList<FloatArray>()
        val bones = ArrayList<FloatArray>()
        val pending = ArrayDeque<Triple<FloatArray, Float, Int>>()
        pending.addLast(Triple(floatArrayOf(0f, 0f, frame, 0f, frame, 1f, 0f, 1f), 1f, 0))
        while (pending.isNotEmpty() && settled.size < count * PaneCap) {
            val (region, spread, depth) = pending.removeLast()
            // Small enough for this branch's own stopping area, or too awkward a shape to cut — either way, it stays.
            val cut = if (GlassCut.area(region) < target * spread) {
                null
            } else {
                cut(region, grain, bowing, target, depth, random)
            }
            if (cut == null) {
                settled.add(region)
            } else {
                if (depth <= BoneDepth) cut.arc?.let(bones::add)
                cut.panes.forEach { pending.addLast(Triple(it, nextSpread(random), depth + 1)) }
            }
        }
        settled.addAll(pending.map { it.first })
        return Window(settled.flatMap { glaze(it, target, bowing, random) }, bones, frame)
    }

    /**
     * One cut of [region] — straight or bowed — or null after [MaxTries] tries, which the caller reads as "leave
     * this pane whole".
     *
     * Retrying is the whole reason this can fail: [GlassCut.split] and [GlassCut.bow] both refuse a cut that would
     * not leave exactly two pieces, and both halves have to be worth keeping ([MinHalf] of the target area), so a
     * pane that has been bitten into an awkward shape needs several angles offered before one lands.
     */
    @Suppress("LongParameterList") // The window's settings plus the region; every one is read on the first line.
    private fun cut(
        region: FloatArray,
        grain: FloatArray,
        bowing: Float,
        target: Float,
        depth: Int,
        random: Random,
    ): GlassCut.Cut? {
        repeat(MaxTries) {
            val angle = cutAngle(grain, random)
            val box = GlassCut.bounds(region)
            val px = GlassCut.centroidX(region) + (random.nextFloat() * 2f - 1f) * PointDrift * (box[2] - box[0])
            val py = GlassCut.centroidY(region) + (random.nextFloat() * 2f - 1f) * PointDrift * (box[3] - box[1])
            // A bow is likelier on the first cuts, where it becomes the window's tracery rather than a wobble.
            val bows = random.nextFloat() < min(1f, bowing * (if (depth <= EarlyDepth) EarlyBowGain else 1f))
            val made = if (bows) {
                GlassCut.bow(region, angle, px, py, bowReach(box, depth, random))
            } else {
                GlassCut.Cut(
                    GlassCut.split(region, px, py, cos(angle), sin(angle)),
                    GlassCut.chord(region, px, py, cos(angle), sin(angle)),
                )
            }
            val clean = made.panes.size == 2 && made.panes.all { GlassCut.area(it) > target * MinHalf }
            if (clean) return made
        }
        return null
    }

    /**
     * A cut's direction: one of the window's four grain lines, or a free angle, jittered by a fixed [GrainJitter].
     *
     * The two diagonals carry more of the window than the two square directions, and one cut in eight ignores the
     * grain entirely — the shares are the reference's, and they are what keep the long lines reading as deliberate
     * without making the window a lattice.
     */
    private fun cutAngle(grain: FloatArray, random: Random): Float {
        val r = random.nextFloat()
        val base = when {
            r < FirstDiagonalShare -> grain[0]
            r < SecondDiagonalShare -> grain[1]
            r < VerticalShare -> grain[2]
            r < HorizontalShare -> grain[3]
            else -> random.nextFloat() * PI.toFloat()
        }
        return base + (random.nextFloat() * 2f - 1f) * GrainJitter
    }

    /**
     * How tight a bow across a region of [box] at [depth] is — a **signed** radius in the cut frame's own units,
     * the sign being which side of the straight cut it bows to.
     *
     * The early cuts bow on a radius near the region's own reach, which is a pronounced arc, and the later ones on
     * a much larger one. That split is what makes the curves read as *tracery* — a few sweeping ribs with gently
     * bowed glass hung off them — rather than as every edge being equally wobbly.
     */
    private fun bowReach(box: FloatArray, depth: Int, random: Random): Float {
        val reach = hypot(box[2] - box[0], box[3] - box[1])
        val lo = if (depth <= EarlyDepth) EarlyBowMin else LateBowMin
        val hi = if (depth <= EarlyDepth) EarlyBowMax else LateBowMax
        val side = if (random.nextBoolean()) 1f else -1f
        return reach * (lo + random.nextFloat() * (hi - lo)) * side
    }

    /** The next branch's area multiplier — log-uniform, which is what spreads the pane sizes without stretching them. */
    private fun nextSpread(random: Random): Float =
        exp(ln(MinSpread) + random.nextFloat() * (ln(MaxSpread) - ln(MinSpread)))

    /**
     * [region] as itself, or glazed into a run of two to four parallel courses.
     *
     * Straight courses run near-vertical or near-horizontal, or — once in [LongEdgeChance] — along the pane's own
     * longest diagonal, which is where the reference's runs of parallel strips come from; the rest are concentric
     * arcs. Only a pane with room is glazed at all: courses thinner than the target's own fraction are slivers.
     *
     * **The arc courses are gated on [bowing], where the reference model's are unconditional.** That is a departure
     * and it is the knob's fault, not the model's: *Curves* at `0` has to leave every cut straight, and a glazing
     * pass that keeps striking arcs there makes the rigid end of the knob a lie about a fifth of the window.
     */
    private fun glaze(region: FloatArray, target: Float, bowing: Float, random: Random): List<FloatArray> {
        if (random.nextFloat() >= GlazeChance || GlassCut.area(region) <= target * GlazeFloor) return listOf(region)
        val courses = min(MinStrips + random.nextInt(StripSpread), (GlassCut.area(region) / (target * StripFloor)).toInt())
        if (courses < MinStrips) return listOf(region)
        return if (random.nextFloat() < ArcCourseChance * bowing) {
            arcCourses(region, courses, random)
        } else {
            straightCourses(region, courses, random)
        }
    }

    /** [count] courses cut off [region] by parallel lines — the plain leaded band. */
    private fun straightCourses(region: FloatArray, count: Int, random: Random): List<FloatArray> {
        val angle = if (random.nextFloat() < LongEdgeChance) {
            GlassCut.longestDiagonal(region) + (random.nextFloat() * 2f - 1f) * LongEdgeJitter
        } else {
            (if (random.nextBoolean()) GlassCut.Quarter else 0f) + (random.nextFloat() * 2f - 1f) * GlazeJitter
        }
        val nx = cos(angle + GlassCut.Quarter)
        val ny = sin(angle + GlassCut.Quarter)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in region.indices step 2) {
            val t = region[i] * nx + region[i + 1] * ny
            lo = min(lo, t); hi = max(hi, t)
        }
        val out = ArrayList<FloatArray>(count)
        var rest = region
        for (s in 1 until count) {
            val at = lo + (hi - lo) * (s.toFloat() / count + (random.nextFloat() * 2f - 1f) * StripJitter)
            // (nx·at, ny·at) projects to `at` on the normal, so it sits on the cut line.
            val halves = GlassCut.split(rest, nx * at, ny * at, cos(angle), sin(angle))
            if (halves.size != 2) continue
            out.add(halves[0])
            rest = halves[1]
        }
        out.add(rest)
        return out
    }

    /**
     * [count] courses cut off [region] by concentric circles struck from a center outside it — the curved glazing a
     * rose window is leaded in.
     */
    private fun arcCourses(region: FloatArray, count: Int, random: Random): List<FloatArray> {
        val box = GlassCut.bounds(region)
        val reach = hypot(box[2] - box[0], box[3] - box[1])
        val away = random.nextFloat() * GlassCut.Turn
        val offset = CourseOffsetMin + random.nextFloat() * (CourseOffsetMax - CourseOffsetMin)
        val cx = GlassCut.centroidX(region) + cos(away) * reach * offset
        val cy = GlassCut.centroidY(region) + sin(away) * reach * offset
        var near = Float.MAX_VALUE
        var far = -Float.MAX_VALUE
        for (i in region.indices step 2) {
            val d = hypot(region[i] - cx, region[i + 1] - cy)
            near = min(near, d); far = max(far, d)
        }
        val courses = ArrayList<FloatArray>(count)
        var rest = region
        for (s in 1 until count) {
            val at = near + (far - near) * (s.toFloat() / count + (random.nextFloat() * 2f - 1f) * CourseJitter)
            val bowed = GlassCut.bowAbout(rest, cx, cy, at)
            if (bowed.panes.size != 2) continue
            courses.add(bowed.panes[0])
            rest = bowed.panes[1]
        }
        courses.add(rest)
        return courses
    }

    /**
     * Where on the ramp a pane's glass is cut from.
     *
     * *Field* is a rise down the frame plus three octaves of noise, so neighbouring panes are related without being
     * the same; the per-pane nudge separates them, and the [IntrudeChance] intruder — a pane taking its tone from
     * clean across the ramp — is what keeps a window of related tones from reading as a smooth wash.
     */
    private fun tone(pane: FloatArray, tint: Tint, noise: PerlinNoise2d, random: Random, aspect: Float): Float {
        if (tint == Tint.SCATTERED) return random.nextFloat()
        val x = GlassCut.centroidX(pane) / aspect.coerceAtLeast(GlassCut.Tiny)
        val y = GlassCut.centroidY(pane)
        val field = reflected(FieldFloor + FieldRise * y + FieldWarp * fbm(noise, x, y))
        val nudged = field + (random.nextFloat() * 2f - 1f) * ToneWander
        val intruded = if (random.nextFloat() < IntrudeChance) {
            (nudged + IntrudeFloor + random.nextFloat() * IntrudeSpread) % 1f
        } else {
            nudged
        }
        return intruded.coerceIn(0f, 1f)
    }

    /** Three octaves of the color field's noise, each [OctaveGain] the weight and [Lacunarity] the frequency of the last. */
    private fun fbm(noise: PerlinNoise2d, x: Float, y: Float): Float {
        var sum = 0f
        var weight = FirstOctave
        var fx = x * FieldFrequency
        var fy = y * FieldFrequency
        repeat(Octaves) {
            sum += weight * noise.at(fx, fy)
            fx *= Lacunarity
            fy *= Lacunarity
            weight *= OctaveGain
        }
        return sum
    }

    /**
     * A tone folded back into `0..1` rather than clamped to it, at [ReflectGain] of its overshoot.
     *
     * Clamping is what it looks like when the top and the bottom of a window are each one flat color over a large
     * area: every pane whose field ran past the end lands on the *same* end stop. Reflected, an overshoot becomes a
     * step back down the ramp, so the run keeps moving.
     */
    private fun reflected(tone: Float): Float = when {
        tone < 0f -> -tone * ReflectGain
        tone > 1f -> 1f - (tone - 1f) * ReflectGain
        else -> tone
    }.coerceIn(0f, 1f)

    /** [pane] as a device-space [Path] — one uniform [scale], since the cut frame already carries the aspect. */
    private fun path(pane: FloatArray, scale: Float, close: Boolean = true): Path {
        val path = Path()
        path.moveTo(pane[0] * scale, pane[1] * scale)
        for (i in 2 until pane.size step 2) path.lineTo(pane[i] * scale, pane[i + 1] * scale)
        if (close) path.close()
        return path
    }

    /**
     * The shader a pane is filled with: [base] lifted at one end of the pane and dropped at the other, along an angle
     * of the pane's own, by [glass].
     *
     * The angle *and the strength* are per pane rather than shared, which is the difference between a window of
     * hand-cut glass and a sheet with a gradient over it: a real window's pieces each catch the light their own way.
     * The sweep spans the pane's own extent along that angle, so a small pane gets the whole of it too, and the drop
     * is [DropBias] of the lift — glass reads as tinted rather than lit when its dark end goes further than its
     * bright one.
     */
    private fun glassShader(pane: FloatArray, base: Int, glass: Float, random: Random, scale: Float): Shader {
        val angle = random.nextFloat() * GlassCut.Turn
        val lx = cos(angle)
        val ly = sin(angle)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in pane.indices step 2) {
            val t = (pane[i] * lx + pane[i + 1] * ly) * scale
            lo = min(lo, t); hi = max(hi, t)
        }
        val reach = MinLift + glass * (MaxLift - MinLift)
        val lift = reach * (LiftFloor + random.nextFloat() * (1f - LiftFloor))
        return LinearGradient(
            hi * lx, hi * ly, lo * lx, lo * ly,
            TriangularFacetsGenerator.shade(base, 1f + lift),
            TriangularFacetsGenerator.shade(base, 1f - lift * DropBias),
            Shader.TileMode.CLAMP,
        )
    }

    /** Divides the asked-for count down, so the subdivision plus the glazing pass land near it rather than above it. */
    private const val Overshoot = 1.4f

    /** A hard ceiling on panes, as a multiple of the count — a guard against a pathological seed, not a budget. */
    private const val PaneCap = 3

    /** The band a branch's stopping area is drawn from, log-uniformly — what spreads the pane sizes. */
    private const val MinSpread = 0.45f
    private const val MaxSpread = 3f

    /** The smallest half a cut may leave, as a fraction of the target area. */
    private const val MinHalf = 0.13f

    /** Cut attempts per region before it is left whole. */
    private const val MaxTries = 7

    /** How deep a cut still counts as structure and is drawn in heavier lead. */
    private const val BoneDepth = 2

    /** How deep a cut is still the window's tracery: bowed more often, and on a tighter radius. */
    private const val EarlyDepth = 1
    private const val EarlyBowGain = 1.6f

    /** A bow's radius, as multiples of the pane's own reach — tighter on the early cuts, gentle on the late ones. */
    private const val EarlyBowMin = 0.55f
    private const val EarlyBowMax = 1.2f
    private const val LateBowMin = 0.85f
    private const val LateBowMax = 2.2f

    /** The window's diagonal grain, in radians — the second is this plus a right angle. */
    private const val MinGrain = 0.45f
    private const val MaxGrain = 1.1f

    /** Cumulative shares of the grain lines: two diagonals, the vertical, the horizontal, then a free angle. */
    private const val FirstDiagonalShare = 0.36f
    private const val SecondDiagonalShare = 0.56f
    private const val VerticalShare = 0.74f
    private const val HorizontalShare = 0.88f

    /** How far a cut wanders off the grain, in radians. */
    private const val GrainJitter = 0.09f

    /** How far a cut's point may drift from the centroid, as a fraction of the region's bounding box. */
    private const val PointDrift = 0.26f

    /** The glazing pass: how often a pane becomes a run of courses, and how much room it needs first. */
    private const val GlazeChance = 0.2f
    private const val GlazeFloor = 1.2f
    private const val GlazeJitter = 0.07f
    private const val MinStrips = 2
    private const val StripSpread = 3
    private const val StripFloor = 0.14f
    private const val StripJitter = 0.06f

    /** How the courses run: concentric arcs, or straight along the pane's longest diagonal rather than the square. */
    private const val ArcCourseChance = 0.4f
    private const val LongEdgeChance = 0.35f
    private const val LongEdgeJitter = 0.05f

    /** An arc course's center, as multiples of the pane's reach away from it, and the wobble in its radii. */
    private const val CourseOffsetMin = 0.6f
    private const val CourseOffsetMax = 1.4f
    private const val CourseJitter = 0.05f

    /** The color field: a floor, a rise down the frame, and how much noise folds into it. */
    private const val FieldFloor = 0.14f
    private const val FieldRise = 0.74f
    private const val FieldWarp = 0.432f
    private const val FieldFrequency = 3.15f

    /** The field's octaves: how many, the first's weight, and how frequency and weight move between them. */
    private const val Octaves = 3
    private const val FirstOctave = 0.55f
    private const val Lacunarity = 2.2f
    private const val OctaveGain = 0.5f

    /** How much of an overshoot past the ramp's ends is folded back in, rather than clamped away. */
    private const val ReflectGain = 0.7f

    /** How far a pane's tone wanders off the field, and the odd pane cut from clean across the ramp. */
    private const val ToneWander = 0.045f
    private const val IntrudeChance = 0.07f
    private const val IntrudeFloor = 0.3f
    private const val IntrudeSpread = 0.4f

    /** The rare pane of much paler glass, and how far it is lifted. */
    private const val FlashChance = 0.02f
    private const val FlashLift = 1.22f

    /** How far a pane's glass is lifted across itself at no glass and at full, and the share it never drops below. */
    private const val MinLift = 0.04f
    private const val MaxLift = 0.22f
    private const val LiftFloor = 0.29f

    /** How much further a pane's dark end goes than its bright one. */
    private const val DropBias = 1.15f

    /** The rim of darker glass where a pane meets its came: its alpha at full glass, and its size in leads. */
    private const val RimAlpha = 74f
    private const val RimWidth = 2.6f
    private const val RimBlur = 0.9f

    /** The heavier leads, as multiples of the pane lead: the structural bones, and the frame around the window. */
    private const val BoneWidth = 2.1f
    private const val FrameWidth = 2.2f

    /** The widest lead, as a fraction of the frame's short side. */
    private const val MaxLeading = 0.012f

    /** Fully opaque, the ceiling the rim's alpha is clamped to. */
    private const val Opaque = 255

    /** Keeps each seeded stream independent, so tuning one knob does not reshuffle what the others drew. */
    private const val FieldSalt = 0x1D872B41L
    private const val ToneSalt = 0x6C078965L
}
