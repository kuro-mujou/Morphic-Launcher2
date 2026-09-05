package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A planet: a sphere of poured, stirred pigment on a dark ground (gart's `flowforce/Orb1`, `Orb2`, `Orb3`).
 *
 * **The catalog's only design that is an *object* rather than a surface.** Everything else fills the frame edge to
 * edge; this draws one thing on a ground, with air around it.
 *
 * **The pigment is a solid field, not a pile of strokes — this is the thing the first two builds got wrong.** gart
 * advects a pool of forty thousand points through a flow field for two hundred frames: eight million marks over a
 * half-million-pixel disc, two hundred deep, so every mark is buried and what survives is *flat opaque color with an
 * organic boundary*. Drawing a few hundred thousand translucent strokes instead does not approach that from below —
 * it is a different picture, a scratchy coin of visible hairs, and no amount of density or opacity reaches the
 * reference before the render takes seconds.
 *
 * So the field is resolved **per pixel, backwards** ([render]'s inner loop): for each point on the sphere, walk the
 * flow *upstream* until the walk leaves the disc or runs out of stir, and take the color of the slab it came from.
 * That is the same picture gart's forward pool converges to — a mark at a pixel carries the color of wherever its
 * particle was born — with no gaps, no stroke edges, and a cost that does not depend on how thickly it is covered.
 * Stopping the walk at the rim is not a shortcut either: gart clips its particles to the disc, so a pixel near the
 * downstream rim really is painted by pigment that entered at the upstream one.
 *
 * **The pigment is laid in *slabs of one palette stop*, never sampled off a smooth ramp.** gart's color is
 * `palette.safe(x * 0.01 + y * 0.01)` — an **integer index**, so neighbouring slabs are neighbouring *stops* and the
 * flow that stirs them past each other is legible. Sampled as a gradient the whole disc collapses into one wash. See
 * [toneIndex].
 *
 * **[DesignParams.depth] is the sphere, and without it this is a disc.** gart's `drawGlassBall` re-samples the flat
 * picture through Snell's law before lighting it, which compresses the pattern toward the limb and magnifies the
 * middle — that warp, not the shading, is what makes a flat marbled field read as something round. Here the
 * refraction is folded into the *coordinate* the field is resolved at rather than applied as a second pass over
 * finished pixels, so it costs nothing and cannot resample its own artifacts. The Fresnel rim and the diffuse
 * highlight are gart's, scaled by the same knob, so `0` is the flat poured disc of `Orb2`/`Orb3` and `1` is `Orb1`'s
 * ball.
 *
 * **[fieldAngle] is gart's three fields, and they are smooth.** gart writes them against **pixel** coordinates on a
 * 1024 frame, so `sin(x * 0.01)` is about 1.6 cycles across it — read as a unit square the same constant would be a
 * sixth of a cycle, and read as [SprayGenerator]'s regime it would be noise. Every frequency here is therefore
 * expressed per **short side** (gart's constant times 1024), which draws gart's picture at any render size.
 *
 * **Everything is read from where the disc *is*, not where the frame is.** The slabs and the vortex centers are both
 * positioned against the disc, because this is the one design that does not fill the frame: on a 1080×2400 wallpaper
 * a ramp read across the frame's diagonal clamps over half the disc, and twelve vortices scattered over the frame
 * land almost entirely outside a disc that occupies a third of its height. Both were silent — each drew a coherent
 * picture that was not this one.
 *
 * [discRadius], [slabCount], [stirSteps], [fieldAngle], [toneIndex] and [refraction] are pure and tested.
 */
object PlanetGenerator : Generator {

    override val style = DesignStyle(
        // How finely the pigment is divided, which is this design's "how much of it is there": three broad poured
        // regions at one end, a fine marbling at the other.
        amount = AmountKnob.Fraction("Detail"),
        scale = "Size",
        irregularity = "Turbulence",
        depth = "Sphere",
        roundness = "Stir",
        variant = VariantKnob("Field", listOf("Bands", "Marbled", "Vortices")),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val ground = palette.colorAt(palette.size - 1) // the ground is the darkest stop
        canvas.drawColor(ground)

        val shortSide = min(width, height).toFloat()
        val radius = discRadius(params.scale, shortSide)
        val cx = width / 2f
        val cy = height / 2f
        val depth = params.depth.coerceIn(0f, 1f)

        // The pigment: every stop below the one the disc is painted in. See toneIndex for why the ground's own color
        // is among them rather than held back.
        val tones = RampTones.aboveGround(palette)
        if (tones.isEmpty()) { // a single-stop palette has no pigment to stir, and the honest picture is bare
            canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.colorAt(0) })
            drawRim(canvas, cx, cy, radius, shortSide, ground)
            return bitmap
        }

        val look = params.variant.coerceIn(0, LookVortices)
        val slabs = slabCount(params.density)
        val stride = shortSide * StepShare
        val steps = stirSteps(params.roundness, radius, stride)

        // The disc is resolved into a square of its own, at most WorkSide across, and stretched over the circle when
        // it is drawn. A solid field upsamples cleanly — there is no stroke or grain in it for the filter to smear —
        // so a large disc costs the same as a small one, and the whole design has one predictable price.
        val side = min((radius * 2f).toInt(), WorkSide).coerceAtLeast(MinWorkSide)
        val perCell = 2f * radius / side
        val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val flow = flowTable(side, bounds, shortSide, look, params.irregularity.coerceIn(0f, 1f), Random(seed))

        val reach = radius * radius
        val pixels = IntArray(side * side)
        var at = 0
        for (row in 0 until side) {
            val dy0 = (row + Center) * perCell - radius
            for (column in 0 until side) {
                val dx0 = (column + Center) * perCell - radius

                // Where on the flat pigment this point of the sphere is looking, then the walk upstream from there.
                val warp = refraction(sqrt(dx0 * dx0 + dy0 * dy0) / radius, depth)
                var x = dx0 * warp
                var y = dy0 * warp
                // The walk runs while its steps land inside; the one that would leave stops *on* the rim, and the
                // fraction of it that fits is what ends the loop.
                var step = 0
                var fits = 1f
                while (step < steps && fits >= 1f && x * x + y * y < reach) {
                    val cell = ((y / perCell + side * Center).toInt().coerceIn(0, side - 1)) * side +
                        (x / perCell + side * Center).toInt().coerceIn(0, side - 1)
                    val stepX = -flow[cell * 2]
                    val stepY = -flow[cell * 2 + 1]
                    fits = rimCrossing(x, y, stepX, stepY, reach)
                    x += stepX * fits
                    y += stepY * fits
                    step++
                }
                pixels[at++] = tones[toneIndex(x / radius, y / radius, look, slabs, tones.size)]
            }
        }

        drawField(canvas, pixels, side, cx, cy, radius)
        if (depth > 0f) drawSphere(canvas, cx, cy, radius, depth, ground)
        drawRim(canvas, cx, cy, radius, shortSide, ground)
        return bitmap
    }

    /** The disc's radius — gart's is `0.39` of its frame, which is near the middle of this range. */
    internal fun discRadius(size: Float, shortSide: Float): Float =
        shortSide * (MinRadius + (MaxRadius - MinRadius) * size.coerceIn(0f, 1f))

    /** How many slabs of pigment the disc is divided into — gart's three orbs sit at `4`, `12` and `6`. */
    internal fun slabCount(detail: Float): Int =
        MinSlabs + ((MaxSlabs - MinSlabs) * detail.coerceIn(0f, 1f)).toInt()

    /**
     * How many steps of [stride] the walk takes upstream, for a disc of [radius] — [stir] read as a distance in
     * **radii** and then divided into steps, rather than as a step count of its own.
     *
     * **A step count is the wrong unit and it collapsed the design once.** The walk stops at the rim, so a drag
     * longer than the disc traces almost every pixel out through the upstream edge: the whole face then takes its
     * color from the one-dimensional slab function along that arc, and the planet draws two or three enormous flat
     * lobes. gart's `Orb3` drags a hundred pixels across an eight-hundred-pixel disc — a quarter of a radius — which
     * is the scale the boundaries stretch into filaments at instead of running off the edge. Expressed in radii it is
     * also the same picture whatever [discRadius] the size knob asks for, where a fixed step count would stir a small
     * planet to mush and barely touch a large one.
     */
    internal fun stirSteps(stir: Float, radius: Float, stride: Float): Int =
        (radius * (MinDrag + (MaxDrag - MinDrag) * stir.coerceIn(0f, 1f)) / stride).toInt().coerceAtLeast(1)

    /**
     * How far in toward the middle a point at [nd] of the way to the rim looks, `0..1`, at sphere [depth] — gart's
     * `drawGlassBall` refraction, which is the single thing that makes a flat field read as round.
     *
     * Snell's law through a ball of [Eta]: the surface height is `√(1 - nd²)`, and the displacement it refracts by
     * runs from a mild magnification at the middle to a hard squeeze at the limb, where a whole ring of the pattern
     * is compressed into the last few pixels. Shading alone does not do this — a lit flat disc is a coin — and it is
     * cheaper here than in gart, which re-samples finished pixels: this warps the *coordinate* the field is resolved
     * at, so there is nothing to resample and no second pass.
     *
     * `0` depth returns `1`, an untouched flat disc, which is `Orb2` and `Orb3`.
     */
    internal fun refraction(nd: Float, depth: Float): Float {
        val edge = nd.coerceIn(0f, 1f)
        val inside = 1f - Eta * Eta * edge * edge
        if (inside <= 0f) return 1f - Thickness * depth // total internal reflection, at the last ring of the limb
        return 1f + (Eta * sqrt(1f - edge * edge) - sqrt(inside)) * Thickness * depth
    }

    /**
     * Which of the [tones] the pigment at ([u], [v]) — an offset from the disc's center in radii, so `±1` is the rim
     * — was poured in, across [slabs] of them.
     *
     * **A slab index, not a ramp position, and that is the design's whole color idea.** Each slab is painted in
     * *one* stop, so the boundary between two of them survives being stirred as a boundary; sampling a continuous
     * ramp at the same positions makes neighbouring pigment differ by a percent of a gradient and the disc reads as
     * one wash.
     *
     * **The slabs run down the disc, except the marbled field's, which run across its diagonal** — gart's `y * 0.01`
     * for the first field and `(x + y) * 0.01` for the second, whose filigree wants the slabs cutting across the way
     * it flows.
     *
     * **The run turns back on itself rather than wrapping**, which is gart's `expandReversed()`: a plain cycle puts
     * the palette's two ends against each other at every seam, and a seam carrying the picture's highest contrast is
     * the one place the eye is not meant to be sent.
     *
     * The tones themselves are `RampTones.aboveGround`, whose exclusion lands on stop `0` — which here is the
     * **disc** rather than the ground. That is the inversion this design makes of the usual rule: pigment is poured
     * *onto* the disc, so the disc's color is the one it must not vanish into and the ground's is free to be pigment.
     * gart's `Orb3` pours its own clear color for exactly that reason, and on the default two-stop palette holding it
     * back would leave the disc almost nothing to be stirred with.
     */
    internal fun toneIndex(u: Float, v: Float, look: Int, slabs: Int, tones: Int): Int {
        val axis = if (look == LookMarbled) (u + v) * Center else v
        val across = ((axis + 1f) * Center).coerceIn(0f, 1f)
        val slab = (across * slabs).toInt()
        val period = tones * 2
        val place = slab % period
        return if (place < tones) place else period - 1 - place
    }

    /**
     * Which way the field carries pigment at ([nx], [ny]) — shares of the frame's **short side**, so the three fields
     * draw the same picture at any render size. See the class note for why they are not shares of their own side and
     * not pixels.
     *
     * **[turbulence] scales the field against a plain heading**, so `0` is the rigid end the field's contract asks
     * for: one direction everywhere, and the disc combed into straight bands. For [LookVortices] that plain heading
     * is a uniform drift the circulation is added to, since a swirl has no amplitude of its own to turn down.
     */
    internal fun fieldAngle(nx: Float, ny: Float, look: Int, turbulence: Float, vortices: FloatArray): Float =
        when (look) {
            LookMarbled -> turbulence * FullTurn * (
                sin(nx * MarbleAcross) * MarbleAcrossWeight + cos(ny * MarbleDown) * MarbleDownWeight +
                    sin((nx + ny) * MarbleSum) * MarbleSumWeight +
                    cos((nx - ny) * MarbleDiff) * MarbleDiffWeight +
                    sin(nx * ny * MarbleCross) * MarbleCrossWeight
                )

            LookVortices -> {
                // A uniform drift the circulation is added to — at turbulence 0 the drift is all that is left.
                var vx = 1f - turbulence
                var vy = 0f
                var i = 0
                while (i < vortices.size) {
                    val dx = nx - vortices[i]
                    val dy = ny - vortices[i + 1]
                    val falloff = dx * dx + dy * dy + VortexCore
                    vx -= turbulence * vortices[i + 2] * dy / falloff * VortexStrength
                    vy += turbulence * vortices[i + 2] * dx / falloff * VortexStrength
                    i += VortexStride
                }
                atan2(vy, vx) + QuarterTurn
            }

            else -> QuarterTurn + turbulence * (sin(nx * BandAcross) + cos(ny * BandDown)) * BandSwing
        }

    /**
     * How fast the field carries it there, as a multiple of the step.
     *
     * Only the marbled field varies it — gart gives that one a speed that rises and falls across the frame, which is
     * what leaves its filigree fine in some regions and open in others. The other two run at one speed, and a
     * generator reads only the inputs its look depends on.
     */
    internal fun fieldSpeed(nx: Float, ny: Float, look: Int): Float =
        if (look == LookMarbled) MarbleSlow + abs(sin((nx + ny) * MarbleSpeed)) * MarbleFast else 1f

    /**
     * The flow over the disc, resolved once as `dx, dy` pairs on a [side]×[side] grid of the disc's bounding square
     * — the step a point takes, in the frame's pixels.
     *
     * **The table is why the field can be resolved per pixel at all.** A backward walk is tens of millions of steps,
     * and [fieldAngle] costs up to twelve divisions or five trig calls each; against a table it is two array reads.
     * gart does the same thing for the same reason — `FlowField.of(d)` is evaluated once per pixel before a single
     * particle moves — so this is its structure and not a shortcut around it.
     */
    private fun flowTable(
        side: Int,
        bounds: RectF,
        shortSide: Float,
        look: Int,
        turbulence: Float,
        random: Random,
    ): FloatArray {
        val perShort = 1f / shortSide
        val radius = bounds.width() * Center
        val vortices = vortices(random, bounds.centerX() * perShort, bounds.centerY() * perShort, radius * perShort)
        val stride = shortSide * StepShare
        val perCell = bounds.width() / side
        val table = FloatArray(side * side * 2)
        var at = 0
        for (row in 0 until side) {
            val ny = (bounds.top + (row + Center) * perCell) * perShort
            for (column in 0 until side) {
                val nx = (bounds.left + (column + Center) * perCell) * perShort
                val angle = fieldAngle(nx, ny, look, turbulence, vortices)
                val speed = fieldSpeed(nx, ny, look) * stride
                table[at++] = cos(angle) * speed
                table[at++] = sin(angle) * speed
            }
        }
        return table
    }

    /**
     * How much of the step from ([x], [y]) by ([dx], [dy]) fits inside a rim of radius `√`[reach] — `1` for a step
     * that lands inside, and the fraction that reaches the rim for one that would leave.
     *
     * **The walk has to end *on* the rim rather than at the last point that was inside it.** A whole-step stop moves
     * the answer by a full step between one pixel and the next, and a step is several pixels of pigment: every
     * boundary the flow runs off the edge comes out as a sawtooth, which is the one artifact in this design that
     * reads as a bug rather than as weather.
     *
     * The forward root of `|p + t·d|² = reach`: the walk starts inside, so the quadratic has one root behind it and
     * one ahead, and the one ahead is the exit.
     */
    private fun rimCrossing(x: Float, y: Float, dx: Float, dy: Float, reach: Float): Float {
        val a = dx * dx + dy * dy
        val b = x * dx + y * dy
        val c = x * x + y * y - reach
        if (a <= 0f || c + b + b + a < 0f) return 1f
        return ((-b + sqrt(b * b - a * c)) / a).coerceIn(0f, 1f)
    }

    /** The resolved field, stretched over the disc — a shader rather than a clip, so the rim comes out smooth. */
    private fun drawField(canvas: Canvas, pixels: IntArray, side: Int, cx: Float, cy: Float, radius: Float) {
        val field = createBitmap(side, side)
        field.setPixels(pixels, 0, side, 0, 0, side, side)
        val scale = 2f * radius / side
        val stretch = Matrix().apply {
            setScale(scale, scale)
            postTranslate(cx - radius, cy - radius)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(field, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(stretch)
            }
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /**
     * What turns the poured disc into a ball: gart's Fresnel rim and its diffuse highlight, both at [depth].
     *
     * The rim is the ground's own color rising from nothing at the halfway mark to nearly opaque at the edge, which
     * is what seats the sphere in the page; the highlight is that color mixed almost to white, up and to the left,
     * which is where the light is. gart's third overlay — a small hard specular spot — is left out: it is off by
     * default in the source, and it reads as a rendered ball bearing rather than a planet.
     */
    private fun drawSphere(canvas: Canvas, cx: Float, cy: Float, radius: Float, depth: Float, ground: Int) {
        val fresnel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                    withAlpha(ground, 0f),
                    withAlpha(ground, 0f),
                    withAlpha(ground, RimInner * depth),
                    withAlpha(ground, RimEdge * depth),
                ),
                floatArrayOf(0f, 0.5f, 0.85f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, fresnel)

        val lit = LinearGradientGenerator.lerpArgb(ground, White, HighlightMix)
        val hx = cx - radius * HighlightOffsetX
        val hy = cy - radius * HighlightOffsetY
        val hr = radius * HighlightRadius
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                hx,
                hy,
                hr,
                intArrayOf(withAlpha(lit, HighlightCore * depth), withAlpha(lit, HighlightEdge * depth), withAlpha(lit, 0f)),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(hx, hy, hr, highlight)
    }

    /**
     * The ring and the shadow under it — a blurred halo **darker than the ground**, with a sharp ring of the
     * ground's own color over it.
     *
     * gart draws one stroke carrying a drop shadow; two passes are the same picture with the tools here, and the
     * order matters: the halo has to go under the ring or the blur washes the ring's own edge out.
     *
     * **The halo is darker than the ground rather than the ground's own color, and that is what keeps the planet a
     * shape.** The ground's stop is also *pigment* here (see [toneIndex]), so a region poured in it runs to the rim
     * and merges with the page — the disc reads as having a bite taken out of it. gart's shadow is `Color.BLACK`
     * against a ground that is not black for exactly this reason: the shadow, not the ring, is what traces the
     * silhouette wherever the ring itself is invisible. Taken as a shade of the ground rather than as black so a
     * light palette gets a shadow rather than a hard outline.
     */
    private fun drawRim(canvas: Canvas, cx: Float, cy: Float, radius: Float, shortSide: Float, ground: Int) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = shortSide * RimShare
            color = Shades.scale(ground, ShadowShade)
            maskFilter = BlurMaskFilter(shortSide * RimBlurShare, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, radius, halo)
        canvas.drawCircle(cx, cy, radius, halo.apply { maskFilter = null; color = ground })
    }

    /**
     * [Vortices] centers and spins, as `x, y, spin` triples in the short side's metric — scattered over a square a
     * little wider than the disc at ([cx], [cy]).
     *
     * **Over the disc, not over the frame**, which is gart's placement read honestly: its square frame *is* the
     * disc's neighbourhood, and about half its centers fall inside the disc. Scattered over a 1080×2400 wallpaper
     * instead they land almost entirely above a disc that occupies a third of its height, leaving it in their far
     * field where the sum is near uniform — a disc of straight bands, with a vortex look that drew the same picture
     * as *Bands*.
     */
    private fun vortices(random: Random, cx: Float, cy: Float, radius: Float): FloatArray =
        FloatArray(Vortices * VortexStride) { i ->
            when (i % VortexStride) {
                0 -> cx + (random.nextFloat() * 2f - 1f) * radius * VortexSpread
                1 -> cy + (random.nextFloat() * 2f - 1f) * radius * VortexSpread
                else -> if (random.nextBoolean()) 1f else -1f
            }
        }

    /** [color] carrying [alpha] of its own, `0..1` — the gradient stops are built rather than tinted afterwards. */
    private fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * ChannelMax).toInt() shl AlphaShift) or (color and RgbMask)

    private const val AlphaShift = 24
    private const val ChannelMax = 255f
    private const val RgbMask = 0xFFFFFF

    /** The disc's radius as a share of the frame's short side — gart's `0.39` sits near the middle. */
    private const val MinRadius = 0.26f
    private const val MaxRadius = 0.46f

    /**
     * How finely the pigment is divided, and how far the flow drags it.
     *
     * The low end of the slabs is three poured regions — `Orb3`, whose palette has four colors and whose disc reads
     * as spilled paint — and the high end is the fine strand marbling of `Orb2`.
     */
    private const val MinSlabs = 4
    private const val MaxSlabs = 28

    /** How far the flow drags the pigment, in radii — gart's `Orb3` runs at about a quarter of one. */
    private const val MinDrag = 0.05f
    private const val MaxDrag = 0.9f

    /** How far the flow carries pigment in one step, as a share of the short side. */
    private const val StepShare = 0.004f

    /**
     * How wide the field is resolved before it is stretched over the disc, and the floor under that.
     *
     * **The cap is the design's whole cost**, since the walk is per resolved pixel: `512` is about a quarter of a
     * second at the default stir on a phone, and it is where a solid field stops gaining from being finer — there is
     * no grain or stroke edge in one for the upsample to smear, only a boundary, and a boundary survives it.
     */
    private const val WorkSide = 640
    private const val MinWorkSide = 64

    /** The ring's width and the blur of the halo under it, as shares of the short side — gart's `15` and `20` of `1024`. */
    private const val RimShare = 0.0146f
    private const val RimBlurShare = 0.0195f

    /** How far the halo is darkened below the ground, so it reads as a shadow the planet sits in. */
    private const val ShadowShade = 0.35f

    /** [DesignParams.variant]'s three fields — gart's three orbs, which are one program otherwise. */
    private const val LookMarbled = 1
    private const val LookVortices = 2

    /**
     * The refraction: gart's air-to-glass ratio and its displacement strength.
     *
     * `Orb1` passes `1 / 1.3`, a weaker bend than the `drawGlassBall` default, which keeps the pattern readable
     * through the middle instead of collapsing it into a lens.
     */
    private const val Eta = 1f / 1.3f
    private const val Thickness = 1.2f

    /** The Fresnel rim's opacity where it starts to bite and where it meets the edge — gart's `0x70` and `0xBB`. */
    private const val RimInner = 0.44f
    private const val RimEdge = 0.73f

    /** The diffuse highlight: gart's offset, size, mix toward white, and the two alphas of its falloff. */
    private const val HighlightOffsetX = 0.3f
    private const val HighlightOffsetY = 0.35f
    private const val HighlightRadius = 0.5f
    private const val HighlightMix = 0.85f
    private const val HighlightCore = 0.31f
    private const val HighlightEdge = 0.09f

    private const val White = 0xFFFFFFFF.toInt()

    /**
     * gart's field constants, converted from *per pixel on a 1024 frame* to per short side — see the class note.
     *
     * The marbled field's cross term is the one to leave alone: its frequency climbs with the product of the
     * coordinates, so the field is smooth near one corner and fine at the far one, and that unevenness is what its
     * filigree is made of rather than an accident of the numbers.
     */
    private const val BandAcross = 10.24f
    private const val BandDown = 5.12f
    private const val BandSwing = 0.698f // 40 degrees, in radians
    private const val MarbleAcross = 17.4f
    private const val MarbleDown = 13.3f
    private const val MarbleSum = 7.17f
    private const val MarbleDiff = 23.55f
    private const val MarbleCross = 52.4f
    private const val MarbleSpeed = 5.12f
    private const val MarbleSlow = 0.6f
    private const val MarbleFast = 0.8f

    /** What each of the marbled field's five terms contributes to the sum — gart's own five weights. */
    private const val MarbleAcrossWeight = 0.5f
    private const val MarbleDownWeight = 0.3f
    private const val MarbleSumWeight = 0.4f
    private const val MarbleDiffWeight = 0.2f
    private const val MarbleCrossWeight = 0.3f

    /** A vortex is three floats: where it sits, and which way it turns. */
    private const val VortexStride = 3

    private const val Vortices = 12

    /** How far past the rim a vortex center may sit, in radii — gart's frame is `1.28` of its own disc. */
    private const val VortexSpread = 1.25f

    /**
     * How strongly a vortex circulates, **against the uniform drift it is added to** — which is the reference it has
     * to be sized by and not the one gart's own number suggests.
     *
     * gart's `2000` is a velocity in a sum with no drift term in it at all, so its magnitude cancels in the `atan2`
     * and only the *shape* of the field survives. Converting it as though it were a length gave a circulation fifty
     * times smaller than the drift here: the field came out uniform at the default turbulence and the disc drew
     * straight bands — a coherent picture, and not this one.
     */
    private const val VortexStrength = 0.3f

    /**
     * The softening on a vortex's core, which keeps pigment that lands on a center from being thrown across the disc
     * — gart's `400` against pixel coordinates, which is this against the short side's square.
     */
    private const val VortexCore = 0.000381f

    /** Half, where it means the middle of a cell or of a span rather than a fraction of something. */
    private const val Center = 0.5f

    private const val FullTurn = 2f * PI.toFloat()
    private const val QuarterTurn = PI.toFloat() / 2f
}
