package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A disc of stirred pigment on a dark ground, ringed and shadowed so it sits in the page — the planet (gart's
 * `flowforce/Orb1`, `Orb2`, `Orb3`).
 *
 * **The catalog's first design that is an *object* rather than a surface.** Everything else fills the frame edge to
 * edge; this draws one thing on a ground, with air around it. That is the teardown's first principle — restraint and
 * negative space — which the catalog has kept naming and had no design of.
 *
 * **It is [SprayGenerator]'s mechanism with a clip round it, and the clip is the whole idea.** Particles advected
 * through a flow field, each leaving a small translucent dot: uncontained that is a mist over the frame, but confined
 * to a disc the marks pile against the rim, the currents have to turn back on themselves, and what accumulates reads
 * as weather on a sphere. gart's three orbs are one program with three fields and nothing else different, which is
 * what makes them [DesignParams.variant] here rather than three designs.
 *
 * **[fieldAngle] is those three fields, and they are smooth.** gart writes them against **pixel** coordinates on its
 * 1024 frame, so `sin(x * 0.01)` is about 1.6 cycles across it — read as a unit square the same constant would be a
 * sixth of a cycle, and read as [SprayGenerator]'s regime it would be noise. Every frequency here is therefore
 * expressed per **short side** (gart's constant times 1024), which draws gart's picture at any render size instead of
 * tying it to the pixel grid.
 *
 * **A particle's color is fixed where it is born and never changes as it travels**, off the ramp between the disc and
 * the ground. That is what makes the currents legible: a streak carries one tone the whole way, so the eye follows it
 * rather than seeing a wash. Two particles born a little apart differ a little, which is what mixes where they cross.
 *
 * **The rim is a ring in the ground's own color with a blurred shadow under it**, gart's own finish. It is not
 * decoration — without it the disc is a hole in the ground rather than something lying on it, and with it the marks
 * that ran up against the clip are covered by the thing that stopped them.
 *
 * [discRadius], [particleCount], [trailSteps] and [fieldAngle] are pure and tested.
 */
object PlanetGenerator : Generator {

    override val style = DesignStyle(
        // A fraction rather than a count: what it sets is how thickly the disc is stirred, and nobody counts a
        // thousand overlapping particle trails.
        amount = AmountKnob.Fraction("Density"),
        scale = "Size",
        irregularity = "Turbulence",
        roundness = "Trail length",
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

        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.colorAt(0) }
        canvas.drawCircle(cx, cy, radius, disc)
        if (RampTones.countFor(palette.size) <= 0) return bitmap // a single-stop palette has no pigment to stir

        val look = params.variant.coerceIn(0, LookVortices)
        val turbulence = params.irregularity.coerceIn(0f, 1f)
        val particles = particleCount(params.density)
        val steps = trailSteps(params.roundness)
        val stride = shortSide * StepShare
        val perShort = 1f / shortSide
        val random = Random(seed)
        val vortices = vortices(random)

        // One bucket per tone, grown rather than sized: a particle puts every one of its dots in its own band, and
        // how many particles fall in a band is not known before they are placed.
        val buckets = Array(ColorBands) { FloatArray(InitialBucket) }
        val counts = IntArray(ColorBands)

        repeat(particles) {
            // Born anywhere in the disc's bounding square and skipped if outside it, which is a uniform scatter over
            // the disc without an angle-and-radius draw that would crowd the middle.
            var x = cx + (random.nextFloat() * 2f - 1f) * radius
            var y = cy + (random.nextFloat() * 2f - 1f) * radius
            val band = (bandAt(x * perShort, y * perShort) * ColorBands).toInt().coerceIn(0, ColorBands - 1)

            var step = 0
            while (step < steps && inDisc(x, y, cx, cy, radius)) {
                if (counts[band] * 2 == buckets[band].size) {
                    buckets[band] = buckets[band].copyOf(buckets[band].size * 2)
                }
                val at = counts[band] * 2
                buckets[band][at] = x
                buckets[band][at + 1] = y
                counts[band]++

                val angle = fieldAngle(x * perShort, y * perShort, look, turbulence, vortices)
                val speed = fieldSpeed(x * perShort, y * perShort, look)
                x += cos(angle) * stride * speed
                y += sin(angle) * stride * speed
                step++
            }
        }

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = shortSide * DotShare
        }
        for (b in 0 until ColorBands) {
            if (counts[b] == 0) continue
            dot.color = toneAt((b + BandMiddle) / ColorBands, palette)
            dot.alpha = DotAlpha // setting the color carries its own alpha in, so this has to follow it
            canvas.drawPoints(buckets[b], 0, counts[b] * 2, dot)
        }

        drawRim(canvas, cx, cy, radius, shortSide, ground)
        return bitmap
    }

    /** The disc's radius — gart's is `0.39` of its frame, which is near the middle of this range. */
    internal fun discRadius(size: Float, shortSide: Float): Float =
        shortSide * (MinRadius + (MaxRadius - MinRadius) * size.coerceIn(0f, 1f))

    /** How many particles [density] stirs into the disc. */
    internal fun particleCount(density: Float): Int =
        MinParticles + ((MaxParticles - MinParticles) * density.coerceIn(0f, 1f)).toInt()

    /** How many steps each particle takes before it stops — a short comma up to a current across the whole disc. */
    internal fun trailSteps(length: Float): Int =
        MinSteps + ((MaxSteps - MinSteps) * length.coerceIn(0f, 1f)).toInt()

    /**
     * Which way the field carries a particle at ([nx], [ny]) — shares of the frame's **short side**, so the three
     * fields draw the same picture at any render size. See the class note for why they are not shares of their own
     * side and not pixels.
     *
     * **[turbulence] scales the field against a plain heading**, so `0` is the rigid end the field's contract asks
     * for: every particle running one way and the disc combed into straight streaks. For [LookVortices] that plain
     * heading is a uniform drift the circulation is added to, since a swirl has no amplitude of its own to turn down.
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
     * Where on the ramp a particle born at ([nx], [ny]) reads — a diagonal across the frame, gart's own.
     *
     * Fixed at birth and carried the whole way, which is what makes a current legible: a streak holds one tone along
     * its length, so the eye follows the flow instead of reading a wash. Two particles born a little apart differ a
     * little, and that is what mixes where their paths cross.
     */
    internal fun bandAt(nx: Float, ny: Float): Float = ((nx + ny) * ColorSlope).coerceIn(0f, 1f)

    /**
     * The color at [position] on the ramp — strictly **between** the disc and the ground, which are the palette's
     * first and last stops.
     *
     * Both ends have to be avoided here where every other design avoids one: a mark in the ground's color is
     * [SprayGenerator]'s vanishing dot, and a mark in the disc's color is the same failure against the thing the disc
     * is made of. The floor is [RampTones]' own first tone and the ceiling its [RampTones.spanBelowGround], so this
     * is the intersection of two bounds the codebase already keeps rather than a third.
     */
    internal fun toneAt(position: Float, palette: Palette): Int {
        val tones = RampTones.countFor(palette.size)
        if (tones <= 0) return palette.colorAt(0)
        val first = 1f / tones
        val last = RampTones.spanBelowGround(palette.size)
        return LinearGradientGenerator.colorAt(first + position.coerceIn(0f, 1f) * (last - first), palette)
    }

    /** Whether ([x], [y]) is still on the disc — the clip, done as a test rather than a canvas layer. */
    private fun inDisc(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= radius * radius
    }

    /** [Vortices] centers and spins, as `x, y, spin` triples in the short side's metric. */
    private fun vortices(random: Random): FloatArray = FloatArray(Vortices * VortexStride) { i ->
        if (i % VortexStride == 2) if (random.nextBoolean()) 1f else -1f else random.nextFloat()
    }

    /**
     * The ring and its shadow — a blurred dark halo with a sharp ring of the ground's color over it.
     *
     * gart draws one stroke carrying a drop shadow; two passes are the same picture with the tools here, and the
     * order matters: the halo has to go under the ring or the blur washes the ring's own edge out. It covers the
     * marks that piled against the clip, which is half of why the disc reads as an object rather than a hole.
     */
    private fun drawRim(canvas: Canvas, cx: Float, cy: Float, radius: Float, shortSide: Float, ground: Int) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = shortSide * RimShare
            color = ground
            maskFilter = BlurMaskFilter(shortSide * RimBlurShare, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, radius, halo)
        canvas.drawCircle(cx, cy, radius, halo.apply { maskFilter = null })
    }

    /** The disc's radius as a share of the frame's short side — gart's `0.39` sits near the middle. */
    private const val MinRadius = 0.26f
    private const val MaxRadius = 0.46f

    /** How thickly the disc is stirred, and how far each particle travels before it stops. */
    private const val MinParticles = 300
    private const val MaxParticles = 2500
    private const val MinSteps = 20
    private const val MaxSteps = 160

    /** How far a particle moves per step and how wide its mark is, as shares of the short side — gart's, scaled. */
    private const val StepShare = 0.004f
    private const val DotShare = 0.0045f

    /** How opaque one dot is — gart's `100` of `255`, a little lower for a single pass over its many frames. */
    private const val DotAlpha = 90

    /** The ring's width and the blur of the halo under it, as shares of the short side — gart's `15` and `20` of `1024`. */
    private const val RimShare = 0.0146f
    private const val RimBlurShare = 0.0195f

    /** [DesignParams.variant]'s three fields — gart's three orbs, which are one program otherwise. */
    private const val LookMarbled = 1
    private const val LookVortices = 2

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

    /**
     * How many vortices the third field is made of, how strongly each circulates, and the softening on its core.
     *
     * The core term is what keeps a particle that wanders onto a center from being thrown across the disc — gart's
     * `400` against pixel coordinates, which is this against the short side's square.
     */
    /** A vortex is three floats: where it sits, and which way it turns. */
    private const val VortexStride = 3

    private const val Vortices = 12

    /**
     * How strongly a vortex circulates, **against the uniform drift it is added to** — which is the reference it has
     * to be sized by and not the one gart's own number suggests.
     *
     * gart's `2000` is a velocity in a sum with no drift term in it at all, so its magnitude cancels in the `atan2`
     * and only the *shape* of the field survives. Converting it as though it were a length gave a circulation fifty
     * times smaller than the drift here: the field came out uniform at the default turbulence and the disc drew
     * straight streaks — a coherent picture, and not this one. Sized against the drift instead, the field turns
     * through `1.16` radians across the disc at `0.5` where the old number managed `0.03`.
     */
    private const val VortexStrength = 0.3f
    private const val VortexCore = 0.000381f

    /** Where the color ramp is read along the frame's diagonal — gart's `x * 0.01 + y * 0.01` on a 1024 frame. */
    private const val ColorSlope = 0.5f

    /** How many tones the disc is drawn in, which is a batching number as much as a color one — see [SprayGenerator]. */
    private const val ColorBands = 24

    /** A band's tone is read at its middle rather than its edge, so the ramp is centered on the dots it colors. */
    private const val BandMiddle = 0.5f

    /** Room for a band's first dots; it doubles from here, since a band's share of them is not known in advance. */
    private const val InitialBucket = 4096

    private const val FullTurn = 2f * PI.toFloat()
    private const val QuarterTurn = PI.toFloat() / 2f
}
