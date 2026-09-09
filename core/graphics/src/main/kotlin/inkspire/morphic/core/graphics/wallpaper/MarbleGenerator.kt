package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A polished stone slab — a clouded body cut by veins that wander across it — the marble.
 *
 * **The vein is `|sin|` of a *turbulent* coordinate, which is the whole design in one line.** Parallel bands are
 * `|sin(p)|`; feeding that a position with fractal noise added to it bends every band by the same field, so they stay
 * a related set of curves rather than becoming independent squiggles. That relatedness is what reads as one piece of
 * stone — the veins in real marble were one crack system, and a picture whose lines are each independently random
 * reads as scribble. [DesignParams.irregularity] is the amplitude of that noise, and `0` is the ruled stripe set the
 * formula degenerates to.
 *
 * **The turbulence is measured in *vein periods*, not in frame widths — [PlasmaGenerator]'s lesson, and it is silently
 * wrong here in the same way.** The vein count spans a ten-fold range, so a fixed push in frame units is an
 * imperceptible nudge at one vein and total noise at ten: one slider quietly meaning two different things depending on
 * the slider beside it. A share of a period is the same amount of *wander* wherever the count sits.
 *
 * **A vein is a crisp core inside a soft halo, and a single smoothstep across its width is what it must not be.** That
 * version is the one this design was first built as, and it renders as an airbrushed wave rather than as rock: with the
 * darkness arriving gradually there is no line anywhere, only a broad smudge. Real veining is a hard mineral seam that
 * has stained the stone either side of it, so [rampAt] draws exactly that — full depth inside [CoreShare] of the reach,
 * then [HaloWeight] of it out to the reach itself.
 *
 * **Two vein generations, one field.** Marble is a coarse system with hairlines threaded through it, and a single set
 * draws only the coarse half. The hairlines are the same phase read [HairPeriods] times as often, so they cost one more
 * `sin` and no more noise, and they inherit the same bend scaled up with them (a fine vein wanders more than a thick
 * one, which is also true of the rock). Two things keep them from reading as engraving, which is what they did at
 * first: the multiplier is **deliberately not a whole number** (an integer lands every hairline on top of a coarse vein
 * and the second system disappears), and they are **gated by the body's own cloud** — fine veining follows the mineral
 * banding, so it comes and goes in patches rather than ruling the whole frame at even spacing.
 *
 * **Gradient noise, not the value noise this algorithm is usually written with.** [PerlinNoise2d]'s own KDoc carries
 * the argument: value noise picks a number *at* each lattice point, so its structure lands on the integer grid — and
 * low turbulence, where the veins are nearly ruled, is the one place this design could not hide the resulting comb of
 * squares.
 *
 * **The colors come from the palette, not from the stone.** Every published version of this algorithm names three
 * colors — a body, a mottle and a vein — and that is the mistake [MondrianGenerator] already made with its three
 * primaries: the palette is what carries color in this studio, so a design that hard-codes any is a design the color
 * modes and the curated palettes cannot touch. So the whole picture is **one position on the ramp**: the body wanders
 * over the first [MottleSpan] of it and a vein pulls that position to the far end. The stops in between are then spent
 * exactly where marble wants them — as the blush around a vein rather than as a fourth flat color — and
 * [DesignParams.colorLayout] chooses which end of the ramp the veins take, which is the difference between a Carrara
 * and a black-and-gold.
 *
 * **[DesignParams.depth] lights the vein's *shoulder* and not the frame, which is the difference between stone and
 * corrugated plastic.** Lighting by the ridge field's slope alone was the first attempt: the slope falls off only as
 * `|sin|` climbs, so the shading spreads over the whole gap between two veins and the picture reads as pleated fabric.
 * Confined to a band [ShoulderReach] wide around each vein it is a bevel on the seam instead, which is what a chiselled
 * groove in a flat surface actually looks like.
 *
 * **It is a nudge along that same ramp rather than a change in brightness, which is what keeps it in the palette.**
 * Scaling the resolved color ([Shades]) would brighten by clamping channels toward white, so a saturated palette's
 * relief would drift its hue; a ramp position cannot leave the colors the user chose.
 *
 * **No grain knob, on purpose** — `WallpaperFilter.GRAIN` is a pass over the finished bitmap and applies to every
 * design, so a per-design one here would be the same idea offered twice.
 *
 * [rampAt], [phaseOf], [halfWidthAt], [fadeAt], [thicknessFor] and [degreesFor] are pure and tested: a vein a fraction
 * of a period from where it belongs, or a thickness knob whose two ends resolve to the same ridge, is a plausible slab
 * rather than a broken one.
 */
object MarbleGenerator : Generator {

    /** What [DesignParams.density] resolves to — the count, and the *Veins* slider's own range. */
    private val Amount = AmountKnob.Count("Veins", 1..10)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
        taper = "Variation",
        irregularity = "Turbulence",
        depth = "Relief",
        rotation = "Direction",
        colorLayout = VariantKnob("Colors", listOf("Light stone", "Dark stone")),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val axis = frameAxis(degreesFor(params.rotation), width, height)
        val veins = Amount.at(params.density)
        val turbulence = params.irregularity.coerceIn(0f, 1f)
        val thickness = thicknessFor(params.scale)
        val variation = params.taper.coerceIn(0f, 1f)
        val relief = params.depth.coerceIn(0f, 1f)

        // Three fields, salted apart: correlating the bend with the body would cloud the stone exactly where the veins
        // wander, which is one field wearing two names.
        val bend = PerlinNoise2d(seed xor BendSalt)
        val swell = PerlinNoise2d(seed xor SwellSalt)
        val body = PerlinNoise2d(seed xor BodySalt)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            // Both axes over the frame's *width*, so a swirl is as wide as it is tall on a phone. The vein axis is in
            // pixels and handles the aspect itself; these are the fields riding on it.
            val v = y.toFloat() / max(1, width)
            for (x in 0 until width) {
                val u = x.toFloat() / max(1, width)
                val phase = phaseOf(
                    axis.at(x.toFloat(), y.toFloat()),
                    fbm(bend, u * BendFrequency, v * BendFrequency, BendOctaves),
                    veins,
                    turbulence,
                )
                val swelling = swell.at(u * SwellFrequency, v * SwellFrequency)
                val mottle = unitOf(fbm(body, u * BodyFrequency, v * BodyFrequency, BodyOctaves))
                val ramp = rampAt(
                    phase = phase,
                    halfWidth = halfWidthAt(thickness, variation, swelling),
                    fade = fadeAt(variation, swelling),
                    mottle = mottle,
                    relief = relief,
                )
                pixels[y * width + x] = LinearGradientGenerator.colorAt(laidOut(ramp, params.colorLayout), palette)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Where on the palette's ramp a pixel lands, `0` at the first stop and `1` at the last — the body clouded by
     * [mottle] and beveled by [relief], with a vein pulling it to the far end.
     *
     * **The body is confined to the ramp's first [MottleSpan], and that is what leaves the middle stops for the veins'
     * shoulders.** Letting the cloud roam the whole ramp is the version that reads as a smear: the body then arrives in
     * the vein's own color somewhere in every frame, and the vein stops being a vein.
     *
     * @param phase the vein phase here, from [phaseOf] — `|sin|` of it is `0` on a vein's spine.
     * @param halfWidth how far from the spine a vein reaches, in `|sin|` — from [halfWidthAt].
     * @param fade how much of its depth this vein keeps, `0..1` — from [fadeAt].
     * @param mottle the body's cloud here, `0..1`.
     * @param relief how strongly the seam is beveled, `0..1` — `0` is flat.
     */
    internal fun rampAt(phase: Float, halfWidth: Float, fade: Float, mottle: Float, relief: Float): Float {
        val wave = sin(phase)
        val ridge = abs(wave)
        // The seam and the stain it left either side of it — see the class note on why one smoothstep will not do.
        val coarse = max(veinAt(ridge, halfWidth * CoreShare), veinAt(ridge, halfWidth) * HaloWeight)
        val hair = veinAt(abs(sin(phase * HairPeriods)), halfWidth * HairThickness) * HairWeight * hairGate(mottle)
        // The two generations combined by the *stronger* rather than by a sum: added, a hairline crossing a coarse vein
        // would push past the ramp's end and clamp, leaving a flat patch exactly where the two meet.
        val vein = max(coarse, hair) * fade
        // The band either side of the seam, and the ridge field's own slope through it — which flips sign across a
        // spine, so one shoulder lifts off the ramp and the other sinks and the seam reads as cut rather than drawn.
        val shoulder = veinAt(ridge, halfWidth * ShoulderReach) * (1f - vein)
        val slope = if (wave >= 0f) cos(phase) else -cos(phase)
        val lit = (MottleSpan * mottle + relief * ReliefReach * slope * shoulder).coerceIn(0f, 1f)
        return lit + (1f - lit) * vein
    }

    /**
     * The vein phase [axisPos] of the way across the frame — [veins] lobes of `|sin|` over the axis, the whole set bent
     * by [bend].
     *
     * The bend is scaled in **periods**, so [turbulence] means the same wander at any vein count — see the class note.
     */
    internal fun phaseOf(axisPos: Float, bend: Float, veins: Int, turbulence: Float): Float =
        (axisPos * veins + turbulence * MaxBendPeriods * bend) * PI.toFloat()

    /**
     * How far a vein reaches from its spine here, in `|sin|` — [thickness], swollen or thinned by [variation] of the
     * local [swell].
     *
     * **In `|sin|` rather than in pixels, which makes it a share of the *gap* between veins.** A thickness in pixels
     * would have the vein count silently rescale it — ten veins as thick as one are veins that touch — where a share of
     * the period draws the same-looking stone at either end of that slider.
     *
     * Floored just above zero rather than at it: a vein the swell has thinned away entirely leaves a visible *break* in
     * a line that is meant to be one system, and a broken vein reads as a rendering fault rather than as rock.
     */
    internal fun halfWidthAt(thickness: Float, variation: Float, swell: Float): Float =
        (thickness * (1f + variation * SwellReach * swell)).coerceIn(ThinnestRidge, WidestRidge)

    /**
     * How much of its full depth a vein keeps here — `1` untouched, down to `1 - `[FadeReach] where [variation] is full
     * and the [swell] is at its lowest.
     *
     * **The same field that thins a vein also fades it, which is not a second meaning for the knob but what makes the
     * first one believable.** A vein at a fifth of its width and full depth is a *drawn line* — uniform ink, uniform
     * edge, thinner. Rock does not do that: a seam carrying less mineral is both narrower and paler, and reading both
     * off one field is what keeps the two agreeing.
     */
    internal fun fadeAt(variation: Float, swell: Float): Float =
        1f - variation.coerceIn(0f, 1f) * FadeReach * (1f - unitOf(swell))

    /** The reach a *Thickness* of [scale] asks for, before the swell moves it — see [halfWidthAt] for the unit. */
    internal fun thicknessFor(scale: Float): Float =
        MinThickness + scale.coerceIn(0f, 1f) * (MaxThickness - MinThickness)

    /**
     * Which way the axis *across* the veins runs at [rotation], in degrees clockwise from the horizontal —
     * [frameAxis]' convention, so a marble's veins and a gradient's ramp run at the same angle on the screen.
     *
     * `0` leaves the veins upright, which is the "untouched" every fraction in [DesignParams] promises, and the knob
     * sweeps a half turn from there — enough to reach every axis, since a vein has no head or tail. The two ends of the
     * sweep are that same axis read from opposite sides, and they are still two different slabs: the bend field does
     * not turn with the axis, so reversing it re-cuts the stone rather than mirroring it.
     */
    internal fun degreesFor(rotation: Float): Float = rotation.coerceIn(0f, 1f) * HalfTurn

    /** The ramp read from whichever end [colorLayout] puts the veins at — see the class note. */
    internal fun laidOut(ramp: Float, colorLayout: Int): Float =
        if (colorLayout == LayoutDarkStone) 1f - ramp else ramp

    /** How strongly a vein claims a pixel [ridge] from its spine — `1` on the spine, `0` past [halfWidth]. */
    private fun veinAt(ridge: Float, halfWidth: Float): Float =
        if (halfWidth <= 0f) 0f else Easing.smoothstep(1f - ridge / halfWidth)

    /** How much of the hairline set shows where the body's cloud reads [mottle] — see the class note. */
    private fun hairGate(mottle: Float): Float = Easing.smoothstep((mottle - HairFloor) * HairGate)

    /**
     * Fractal noise at ([x], [y]) — [octaves] octaves, each [OctaveGain] the weight and [Lacunarity] the frequency of
     * the one below it, **divided by the weights** so the range does not grow with the octave count.
     *
     * Private, as [ContourGenerator]'s and [VitrallGenerator]'s are. The three are not one derivation wearing three
     * names: each is a field belonging to one design and tuned against that design's own look, and none of them has to
     * *agree* with another — which is the case the shared-derivation rule is about. Unifying them would trade a formula
     * each generator states in six readable lines for a constructor call nobody can read a picture out of.
     */
    private fun fbm(noise: PerlinNoise2d, x: Float, y: Float, octaves: Int): Float {
        var value = 0f
        var weights = 0f
        var weight = 1f
        var frequency = 1f
        repeat(octaves) {
            value += weight * noise.at(x * frequency, y * frequency)
            weights += weight
            weight *= OctaveGain
            frequency *= Lacunarity
        }
        return if (weights <= 0f) 0f else value / weights
    }

    /**
     * A signed noise reading recentered into `0..1` over the range it *actually* reaches — [NoiseGain]'s whole job.
     *
     * **Gradient noise's nominal range is far wider than its useful one, which is what made the first render's stone a
     * flat cream.** Measured over 400k samples, one octave spans `±0.66` between its 1st and 99th percentiles and the
     * three-octave average only `±0.45`; folded in raw, a body given a third of the palette to cloud through spent a
     * seventh of it. The gain is set for the fbm, so the single-octave swell clips at both ends — deliberately, since a
     * saturated patch there is a vein that has genuinely faded out rather than one merely on the pale side.
     */
    private fun unitOf(field: Float): Float = ((field * NoiseGain + 1f) * Half).coerceIn(0f, 1f)

    /** The color layout that puts the veins at the palette's light end — a black-and-gold slab rather than a Carrara. */
    private const val LayoutDarkStone = 1

    /** The sweep the *Direction* knob covers — see [degreesFor]. */
    private const val HalfTurn = 180f

    /** How far the bend may push the vein phase at full *Turbulence*, in vein periods. */
    private const val MaxBendPeriods = 1.4f

    /** The bend field: how many swirls span the frame's width, and how much finer structure rides on them. */
    private const val BendFrequency = 2.2f
    private const val BendOctaves = 4

    /** Each octave's weight and frequency against the one below it, for both fbm fields here. */
    private const val OctaveGain = 0.5f
    private const val Lacunarity = 2f

    /** The vein-width field: broader than the bend, so a vein swells and thins *along its length* rather than dashing. */
    private const val SwellFrequency = 0.9f

    /** How far the swell may move a vein's reach at full *Variation* — a share of that reach. */
    private const val SwellReach = 0.85f

    /** How much depth a vein may lose at full *Variation*, where the swell has thinned it — see [fadeAt]. */
    private const val FadeReach = 0.55f

    /** The body's cloud: coarse enough to read as one piece of stone, with finer octaves of grain on it. */
    private const val BodyFrequency = 2.4f
    private const val BodyOctaves = 3

    /** How much of the ramp the clouded body wanders over, leaving the rest to the veins — see [rampAt]. */
    private const val MottleSpan = 0.36f

    /** The share of a vein's reach that carries full depth, and what the halo beyond it keeps — see the class note. */
    private const val CoreShare = 0.45f
    private const val HaloWeight = 0.5f

    /** The reach a *Thickness* of `0` and of `1` ask for, in `|sin|` — see [halfWidthAt]. */
    private const val MinThickness = 0.015f
    private const val MaxThickness = 0.28f

    /** The bounds a swollen or thinned vein is held to, so it neither vanishes nor floods the gap. */
    private const val ThinnestRidge = 0.003f
    private const val WidestRidge = 0.9f

    /** The hairline generation: how much oftener it repeats, how thin it is, and how far down the ramp it reaches. */
    private const val HairPeriods = 3.37f
    private const val HairThickness = 0.35f
    private const val HairWeight = 0.45f

    /** Where in the body's cloud the hairlines begin to show, and how sharply they arrive — see [hairGate]. */
    private const val HairFloor = 0.42f
    private const val HairGate = 3.2f

    /** How wide a band either side of a seam the relief lights, as a share of the vein's own reach. */
    private const val ShoulderReach = 2f

    /** How far that lighting may move the body along the ramp at full *Relief*. */
    private const val ReliefReach = 0.3f

    /** What a signed noise reading is scaled by before it is folded into `0..1` — see [unitOf]. */
    private const val NoiseGain = 2.2f

    private const val Half = 0.5f

    /** Keeps the three fields off one another's streams — see [render]. */
    private const val BendSalt = 0x243F6A88L
    private const val SwellSalt = 0x85A308D3L
    private const val BodySalt = 0x13198A2EL
}
