package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Evenly-spaced streamlines combed through a noise field, with orbs floating among them — *Flow Field*, after gart's
 * `arts/flowforce/eclectic` and `arts/flowforce/perl`.
 *
 * **The function count is over detekt's limit and is suppressed rather than worked around.** Six of them are the six
 * knobs the reference exposes, each a named mapping the tests pin to a measured number; the rest are one step of the
 * drawing apiece. Merging any of them to score better would put two knobs in one function, which is the divergence
 * this file was rebuilt to remove — and splitting the object across files would separate the mappings from the
 * render that is their only caller.
 *
 * **One set of streamlines is grown for the whole picture and each is given a color, rather than a set grown per
 * tone.** This is the correction that decides how the design reads, and it was settled by measuring the reference
 * rather than by reasoning about it: at its default thickness *no two marks overlap, whatever their colors*, and
 * marks of one color routinely sit in adjacent lanes with nothing between them. Neither is possible with a pass per
 * tone — passes that cannot see each other pile different colors on top of each other, and a pass that keeps its own
 * separation can never put two of its own marks side by side. What per-tone passes actually produce is a muddier
 * picture at the same settings, which is the gap this design had. gart's `perl` traces one set for the same reason;
 * its `eclectic` is the one that loops over the palette, and it is the older of the two arts.
 *
 * **A trail stops growing when it comes near another, and fresh seeds are dropped off its flanks as it goes.** That
 * is Jobard and Lefer's evenly-spaced streamlines, gart's `StreamlineTracer`, and it is what packs the frame at
 * exactly the separation asked for. Sowing a fixed number of trails at random and letting them collide leaves the
 * picture at whatever spacing the scatter happened to have — measured against the reference, half its ink.
 *
 * **Every length in the picture is a multiple of the separation, which is why *Density* moves more than the count.**
 * Wind theirs down and the marks do not merely spread out: they become long and fat as well, and wind it up and they
 * become short and thin. So the separation is the design's unit — the step, a mark's width and a dash's length are
 * all expressed in it — and *Thickness* is a multiplier on the width alone, leaving the paths untouched, exactly as
 * dragging theirs does.
 *
 * **[DesignParams.irregularity] is their *Irregularity*, and it is a second, finer octave added to the field — not
 * the range the field sweeps.** Theirs leaves the large-scale flow alone and makes each mark serpentine at a
 * wavelength far shorter than the swirls; a knob on the sweep instead re-draws the whole picture, which is visibly
 * not what theirs does. `0` is a perfectly smooth field in both looks.
 *
 * **[DesignParams.variant] is their *Style*, and what separates the two looks is [Weave] — where a mark's color and
 * its width come from.** *Eclectic* draws both at random per mark; *Pearls* reads both off **one** cosine across the
 * frame, so its picture resolves into broad bands, bold and dark at the edges and thinning down the ramp to
 * hairlines at the center. That is the whole of why theirs reads as composed and ours read as noise, and it is
 * measurable: averaged down their *Pearls* the ink runs `48% -> 7% -> 40%` across the frame with the dominant tone
 * following it, where their *Eclectic* has every tone at `10..25%` of every column. Both looks dash, and the field
 * is the same field swept differently. An earlier build gave *Pearls* whole undashed paths, *Eclectic*'s fat random
 * colors and widths, and a field a third as fine — four ways of drawing it as the other look.
 *
 * **[DesignParams.depth] is their *Orbs*, and in *Pearls* an orb carries a ring of the ground color.** gart's `perl`
 * strokes its circle in the background color after filling it, which reads as a hole cut through the picture rather
 * than a disc laid on top; `eclectic` fills only, and the reference keeps its two looks apart the same way. The orbs
 * are drawn *between* trails so marks pass both in front of and behind them. Their *Orb size* has no field here yet,
 * so the radius comes from [seed].
 *
 * **The ground is the palette's *last* stop and the marks are the tones above it** — the mirror of the mosaic
 * designs, because a palette is ordered light-to-dark and this design is lit marks on a dark ground.
 * [RampTones.belowGround] is that mirror, with the same floor: a palette reduced to two stops still gets three tones
 * to comb with rather than one.
 */
@Suppress("TooManyFunctions")
object FlowFieldGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — the *spacing* the marks pack at, which is what the reference's own
     * *Density* moves and the one thing a count cannot express here.
     *
     * **A count saturates and a spacing does not, which is the whole reason this is a [AmountKnob.Fraction].** How
     * many marks a frame holds is decided by how close they may come to each other, not by how many trails are
     * seeded: past the point where the frame is packed, every extra trail dies on its first step and the picture
     * stops changing. Ours was a count of seeds and did exactly that. Theirs reads `1..100` rather than a number of
     * anything, which is the same admission.
     */
    private val Amount = AmountKnob.Fraction("Density")

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
        irregularity = "Irregularity",
        depth = "Orbs",
        depthScale = "Orb size",
        variant = VariantKnob("Style", Look.entries.map { it.label }),
    )

    /**
     * *Dots* is offered for *Pearls* and withheld from *Eclectic*, which has no beads to control.
     *
     * The reference does the same, and "absent, not disabled" is why: a *Dots* slider beside *Eclectic*'s working
     * knobs would drag, re-render, and change nothing.
     */
    override fun styleFor(variant: Int): DesignStyle =
        if (lookAt(variant).beadable) style.copy(roundness = "Dots") else style

    /**
     * Which of the design's two looks is drawn — the reference's *Style*, and gart's two arts.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property frequency how many noise cycles span the frame's **long** side — the size of the swirls, and the
     *   number [span] trades against. What the eye reads is the two multiplied: how far the flow turns per pixel.
     *   Measured as an orientation-decorrelation on both references — the mean angle between two points `320px`
     *   apart along a row — theirs turns `20.5°` on *Eclectic* and `27.6°` on *Pearls*, a ratio of `1.35`, where the
     *   spans alone would predict `2.09`. So the reference's *Pearls* reads a **coarser** field, by about the amount
     *   that keeps the two looks turning at a similar rate under very different sweeps, and `2.4` is `3.4` divided by
     *   what is left. Ours had `1`, which overshot the correction into a different, emptier design; the same
     *   measurement on `6` came back at `46.6°`, nearly twice theirs.
     * @property span the **whole** angle range the field sweeps, in radians. *Pearls* sweeps a full turn, which is
     *   what puts vortices in the field and gives it concentric whorls; *Eclectic* sweeps gart's three radians, which
     *   only tilts a drift.
     * @property bias the angle the field sweeps *around*, in radians. gart offsets both of its arts the same way
     *   (`n - 0.5`, `n + 0.5`); a field centred on zero points due east on average, which on a portrait frame draws
     *   stripes across the picture rather than the drift down it that theirs has. Meaningless for a look that sweeps
     *   a whole turn, since a bias only rotates the same field — hence *Pearls* leaves it at zero.
     * @property stepShare how far one hop carries a trail, as a share of the separation. *Pearls* steps far finer
     *   because its field turns much faster, and a hop long enough for a drift visibly polygonizes a tight vortex.
     * @property weave where a mark's color and width come from — the one thing that separates the two looks, and the
     *   reason they read as different designs rather than as one design at two settings.
     * @property ringedOrbs whether an orb is stroked in the ground color after being filled, so it reads as a hole
     *   rather than a disc. gart's `perl` does this and its `eclectic` does not.
     */
    // Eight facts about a look, each named at its two use sites; folding any two together to score under the limit
    // would put two unrelated numbers behind one name.
    @Suppress("LongParameterList")
    internal enum class Look(
        val label: String,
        val frequency: Float,
        val span: Float,
        val bias: Float,
        val stepShare: Float,
        val weave: Weave,
        val ringedOrbs: Boolean,
    ) {
        /** Scattered dashes drifting across the frame — gart's `eclectic`, and the reference's own default. */
        ECLECTIC(
            "Eclectic",
            frequency = 3.4f,
            span = 3f,
            bias = QuarterTurn,
            stepShare = 0.2f,
            weave = Weave.SCATTERED,
            ringedOrbs = false,
        ),

        /** Fine dashes through a field of vortices, graded across the frame — gart's `perl`. */
        PEARLS(
            "Pearls",
            frequency = 2.4f,
            span = TwoPi,
            bias = 0f,
            stepShare = 0.08f,
            weave = Weave.GRADED,
            ringedOrbs = true,
        ),
        ;

        /** Whether this look offers the *Dots* knob at all — the reference beads only its graded look. */
        val beadable get() = weave == Weave.GRADED
    }

    /**
     * Where a mark's color and its width come from — **the difference between the two looks, and the finding this
     * design was rebuilt on twice.**
     *
     * [SCATTERED] draws both at random per mark: any tone, any width from a hairline to a full lane. [GRADED] reads
     * both off **one** cosine across the frame's short side, so the picture resolves into broad vertical bands —
     * bold marks in the palette's first tone at the edges, thinning and stepping down the ramp to hairlines at the
     * center. Nothing else in the drawing differs: both looks dash, and both take their field from the same
     * frequency.
     *
     * **Measured, not inferred.** Averaged down the reference's *Pearls* the ink runs `48% → 7% → 40%` across the
     * frame and the dominant tone with it, and both halves of the frame give the same profile — a 2D field would
     * wash out under that average, a function of `x` cannot. The same measurement on its *Eclectic* finds all four
     * tones at `10..25%` in every column. So one look is organized and the other is not, and ours had built both as
     * the second.
     *
     * **This is why theirs reads as composed and ours read as noise.** Random color per mark is what a flow field
     * looks like when nobody has decided anything; the grade is the decision.
     */
    internal enum class Weave { SCATTERED, GRADED }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val look = lookAt(params.variant)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val ground = palette.colorAt(palette.size - 1)
        canvas.drawColor(ground)

        val tones = RampTones.belowGround(palette)
        // An all-ground palette has nothing to comb with, which is the honest picture rather than an error.
        if (tones.isEmpty()) return bitmap

        val shortSide = min(width, height).toFloat()
        val longSide = max(width, height).toFloat()
        val separation = spacing(params.density) * shortSide
        val detail = detailSpan(params.irregularity)
        val angleAt = fieldOf(look, longSide, detail, seed)
        val walk = Walk(look, separation, detail, longSide, width, height, angleAt)

        val random = Random(seed)
        val trails = growTrails(walk, random)
        val thickness = thicknessScale(params.scale)

        // The orbs draw from a seed of their own, so moving their slider adds and removes orbs instead of re-rolling
        // every trail behind them.
        val orbRandom = Random(seed xor OrbSeed)
        val orbs = orbCount(params.depth)
        val orbSize = orbScale(params.depthScale)
        val beaded = if (look.beadable) beadedShare(params.roundness) else 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        var placed = 0
        for ((index, trail) in trails.withIndex()) {
            while (placed < orbs && index * orbs >= placed * trails.size) {
                drawOrb(canvas, look, tones, ground, Frame(width, height, shortSide, orbSize), paint, orbRandom)
                placed++
            }
            drawTrail(canvas, Ink(look, tones, shortSide, thickness), trail, walk, beaded, paint, random)
        }
        while (placed < orbs) {
            drawOrb(canvas, look, tones, ground, Frame(width, height, shortSide, orbSize), paint, orbRandom)
            placed++
        }
        return bitmap
    }

    /**
     * The field's angle at a pixel — a broad sweep, plus a finer octave of [detail] radians on top.
     *
     * The two octaves read **different noise**, not one field sampled at two frequencies: a single permutation table
     * puts both octaves' zero crossings on the same lattice, so the wiggle would fade out exactly where the sweep
     * does and the disturbance would follow the flow instead of cutting across it.
     *
     * Both spans are the *whole* range swept rather than an amplitude, which is what [NoiseAmplitude] converts.
     *
     * **The frequency is counted over the frame's *long* side, not its short one.** gart renders square, so its own
     * `smooth` says nothing about which to use; a phone frame is more than twice as tall as it is wide, and counting
     * over the short side puts two and a half times as many turns down the picture as across it. The marks then
     * snake rather than drift, which is not what theirs does at any setting of *Irregularity*.
     */
    private fun fieldOf(look: Look, longSide: Float, detail: Float, seed: Long): (Float, Float) -> Float {
        val sweep = PerlinNoise2d(seed)
        val grain = PerlinNoise2d(seed xor DetailSeed)
        val broad = look.frequency / longSide
        val fine = broad * DetailRatio
        return { x, y ->
            look.bias + sweep.at(x * broad, y * broad) * look.span * NoiseAmplitude +
                grain.at(x * fine, y * fine) * detail * NoiseAmplitude
        }
    }

    /**
     * How close two marks may come, as a share of the short side, at [density] — a sparse comb at `0`, a packed one
     * at `1`.
     *
     * **Geometric rather than linear**, because spacing is a ratio: halfway between a hundred and forty pixels and
     * twenty-eight is not eighty-four, it is sixty-one. The ends are measured off theirs on a 1080-wide frame — a
     * `140px` pitch wound all the way down, a `35px` one near the top of the knob, with a little headroom past it.
     */
    internal fun spacing(density: Float): Float =
        MaxSpacing * (MinSpacing / MaxSpacing).pow(density.coerceIn(0f, 1f))

    /**
     * What [scale] multiplies a mark's width by — the reference's *Thickness*, which moves the ink and nothing else.
     *
     * **Geometric, with `0.5` landing on exactly `1`**, so the default frame draws the width the separation alone
     * implies and the knob reads as a departure from it in either direction. Past the top a mark is wider than the
     * lane it was sown in, which is their *Thickness* `100`: the marks stop reading as separate and the picture goes
     * to overlapping blobs — theirs does the same, which is why the top is not clamped short of it.
     */
    internal fun thicknessScale(scale: Float): Float =
        MinThickness * (MaxThickness / MinThickness).pow(scale.coerceIn(0f, 1f))

    /**
     * How many radians of fine wiggle [irregularity] adds on top of the field's own sweep — `0` is a perfectly smooth
     * field, and the top is enough for a mark to visibly double back on itself.
     *
     * Linear, unlike the other two knobs, because this one is an *amplitude* added to a fixed sweep rather than a
     * ratio between two extents: zero has to be reachable, and a geometric scale never reaches it.
     */
    internal fun detailSpan(irregularity: Float): Float = irregularity.coerceIn(0f, 1f) * MaxDetailSpan

    /** How many orbs [depth] asks for — none at all up to the reference's own ten, `0.5` landing on five. */
    internal fun orbCount(depth: Float): Int = (depth.coerceIn(0f, 1f) * MaxOrbs).roundToInt()

    /**
     * What [depthScale] multiplies an orb's radius by — the reference's *Orb size*.
     *
     * **`0` collapses the orbs entirely, which is what theirs does and why it is worth stating**: their *Orb size*
     * `0` renders exactly the picture their *Orbs* `0` does, so the two knobs reach the same place from either side
     * rather than the size bottoming out at some small disc. `0.5` is the shipped radius, so the knob reads as a
     * departure from it in both directions.
     */
    internal fun orbScale(depthScale: Float): Float = depthScale.coerceIn(0f, 1f) * 2f

    /**
     * What share of *Pearls*' lines are drawn as chains of dots rather than stroked — the reference's *Dots*, on
     * [DesignParams.roundness].
     *
     * **It is the roundness knob because that is what it does to a mark**: a stroked streamline is a long lozenge,
     * and beading it is that same mark made as round as a mark can be. The field's documented sense — `0` sharp, `1`
     * as round as the shape allows, a narrow tile becoming a pill — is this exactly, one design over.
     *
     * **Curved so `0.5` lands on gart's one-in-six**, which is the shipped look and the rule the rest of the studio
     * keeps for a knob added over an existing design. A linear ramp would put half the lines in beads at the default
     * and turn the look into the field of dots an earlier build already made of it. `0` is every line stroked and `1`
     * every line beaded, which is what dragging theirs to each end does.
     */
    internal fun beadedShare(roundness: Float): Float = roundness.coerceIn(0f, 1f).pow(BeadBias)

    /**
     * Everything one mark needs to become ink, and it is asked **per mark rather than per trail** — the difference
     * between a picture of scattered strokes and a picture of dashed lanes.
     *
     * **A streamline is traced whole and then cut, but nothing about a mark should remember that.** Giving a trail
     * one color and one width makes every dash along it a repeat of its neighbour, so a lane reads as a single long
     * dashed line and the frame reads as corduroy — which is what ours drew and what the reference plainly does not:
     * rotate its *Eclectic* so the flow runs across the page and a hairline, a fat lozenge and a medium stroke sit
     * end to end on the same line, in three colors. Drawn per mark, the lane stops being visible at all.
     *
     * **The graded look needs no exception**, which is what says the rule belongs here rather than on [Weave]:
     * [gradeOf] reads a cosine of the mark's own midpoint, so two dashes a lane apart resolve to nearly the same
     * width and tone anyway and *Pearls* keeps the continuity it is built on.
     *
     * @property shortSide the frame's short side in pixels — what the graded look's cosine spans.
     * @property thickness what *Thickness* multiplies every width by.
     */
    private class Ink(val look: Look, val tones: IntArray, val shortSide: Float, val thickness: Float)

    /**
     * Grows trails through [angleAt] until the frame is packed, and returns them in the order they were grown.
     *
     * **That order is the drawing order, and it is not arbitrary.** Seeds are popped at random from a queue fed off
     * the flanks of trails already grown, so the list runs outward from wherever the first seed fell rather than
     * sweeping across the frame — which is what lets the orbs be spread through it and land at every depth rather
     * than stacking up in one corner.
     */
    private fun growTrails(walk: Walk, random: Random): List<FloatArray> {
        val width = walk.width
        val height = walk.height
        val separation = walk.separation
        val grid = SeparationGrid(width, height, separation, gridCapacity(width, height, separation, walk.step))

        val seeds = ArrayList<Float>()
        seeds.add(random.nextFloat() * width)
        seeds.add(random.nextFloat() * height)
        val trails = ArrayList<FloatArray>()

        while (seeds.size >= 2 && trails.size < MaxTrails) {
            // Popped at random rather than in order, so the field fills outward from everywhere at once instead of
            // sweeping across it — gart pops a random index for the same reason.
            val pick = random.nextInt(seeds.size / 2) * 2
            val seedX = seeds[pick]
            val seedY = seeds[pick + 1]
            seeds[pick] = seeds[seeds.size - 2]
            seeds[pick + 1] = seeds[seeds.size - 1]
            seeds.removeAt(seeds.size - 1)
            seeds.removeAt(seeds.size - 1)
            if (outside(seedX, seedY, width, height) || grid.crowded(seedX, seedY, separation, NoOwner)) continue

            val owner = trails.size
            val ahead = half(seedX, seedY, walk, grid, seeds, owner, 1f)
            val behind = half(seedX, seedY, walk, grid, seeds, owner, -1f)

            val points = FloatArray(ahead.size + behind.size)
            var at = 0
            for (i in behind.size / 2 - 1 downTo 0) {
                points[at++] = behind[i * 2]
                points[at++] = behind[i * 2 + 1]
            }
            for (v in ahead) points[at++] = v
            trails.add(points)
        }
        return trails
    }

    /**
     * Where a trail sits on its look's own scale, `0..1` — the one number [widthShare] and [toneIndex] both read.
     *
     * **[Weave.GRADED] takes it from the trail's midpoint, which is the reference's rule and gart's.** `perl` reads
     * `cos(midpoint.x * 0.01)`, and theirs is the same cosine fitted to the frame: one period across the short side,
     * peaking at both edges and troughing at the center, which is exactly the `48% → 7% → 40%` ink profile measured
     * off it. The midpoint rather than the seed, so a long mark is graded by where it *is* rather than by where it
     * happened to start.
     *
     * [Weave.SCATTERED] draws it, which is where *Eclectic*'s independence of color and width comes from — and it
     * draws it here, at the same point in the stream the width used to, so the look is unchanged.
     */
    private fun gradeOf(ink: Ink, points: FloatArray, random: Random): Float {
        if (ink.look.weave == Weave.SCATTERED) return random.nextFloat()
        val midX = points[points.size / 4 * 2]
        return (cos(midX * TwoPi / ink.shortSide) + 1f) / 2f
    }

    /**
     * A mark's width as a share of its lane at [grade], before *Thickness*.
     *
     * **[Weave.SCATTERED] is an uneven draw biased toward the fat end.** gart draws a mark anywhere from a fifth of
     * the lane to two thirds of it, uniformly. The reference's marks span a wider range and are not uniform over it:
     * mostly full-bodied lozenges, with a hairline threading between them every few lanes, and those hairlines are
     * much of what makes the design read as *eclectic* rather than as corduroy. An exponent under one is the
     * cheapest thing that reproduces that while still paying for the coverage the one-lane ceiling gives up.
     *
     * **[Weave.GRADED] is a plain ramp over a far narrower band, and the narrowness is the finding.** Measured off
     * the reference's *Pearls* on a 1080-wide frame: marks of `22-23px` at the frame's edges and `5-10px` at its
     * center, on the same `38px` lane — an eighth to three fifths of a lane, where *Eclectic* runs past two. Ours
     * drew *Pearls* on *Eclectic*'s distribution and so drew it two to ten times too fat, which is most of why the
     * two looks read as the same heavy design rather than as a bold one and an airy one.
     *
     * Its caller multiplies by *Thickness*, so that knob moves the whole range rather than only its top.
     */
    internal fun widthShare(look: Look, grade: Float): Float = when (look.weave) {
        Weave.SCATTERED -> MinWidthShare + (MaxWidthShare - MinWidthShare) * grade.pow(WidthBias)
        Weave.GRADED -> MinGradedWidth + (MaxGradedWidth - MinGradedWidth) * grade
    }

    /**
     * Which tone a mark at [grade] takes, of [count] on the ramp above the ground.
     *
     * **[Weave.GRADED] runs the ramp against the width — the thickest marks on the palette's opening stop.** That is
     * the reference's own order (its boldest marks take its palette's first color and its hairlines its last) and it
     * is the reading that survives a palette which is a luminance ramp rather than a color set: the bold marks land
     * furthest from the ground and carry the picture, and the hairlines land nearest it and read as texture.
     *
     * [Weave.SCATTERED] draws independently of the width, which is what the reference's *Eclectic* measures as —
     * every tone present at `10..25%` of every column, against a profile that sweeps the whole ramp on *Pearls*.
     */
    internal fun toneIndex(look: Look, grade: Float, count: Int, random: Random): Int = when (look.weave) {
        Weave.SCATTERED -> random.nextInt(count)
        Weave.GRADED -> ((1f - grade) * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    /**
     * How many points the separation grid must hold: about one every [step] along every lane of [separation] over the
     * whole frame, doubled for the trails that overrun before being stopped.
     *
     * **Derived rather than a constant, because a constant is silently wrong at one end of *Density*.** A budget
     * sized for a sparse frame truncates a dense one — and a grid that has stopped recording points stops enforcing
     * separation, so the trails quietly begin crossing rather than failing.
     */
    private fun gridCapacity(width: Int, height: Int, separation: Float, step: Float): Int =
        (width.toFloat() * height / (separation * step) * 2f).toInt().coerceIn(MinGridPoints, MaxGridPoints)

    /**
     * Everything one walk of a streamline needs that does not change between its two halves.
     *
     * **Built from the separation rather than handed the five lengths derived from it**, so the one place that turns
     * a separation into a step, a stopping distance and a sowing rate is here. Passing the derivations in is what
     * lets two of them be computed from different separations without anything saying so.
     *
     * @property separation how far off each flank a fresh seed candidate is dropped — the design's unit.
     * @property step how far one hop carries, in pixels — the *shorter* of what the separation implies and what the
     *   field's turning allows, since a trail is drawn as a polyline through the points it hops between.
     * @property stop how close the head may come to another trail before this one is finished — **not**
     *   [separation]; see [StopShare].
     * @property sow how often, in hops, candidates are dropped off the flanks.
     * @property steps the most hops one half may take.
     */
    private class Walk(
        look: Look,
        val separation: Float,
        detail: Float,
        longSide: Float,
        val width: Int,
        val height: Int,
        val angleAt: (Float, Float) -> Float,
    ) {
        val step = (separation * look.stepShare)
            .coerceAtMost(smoothStep(look, detail, longSide))
            // A floor, so the tightest field cannot make a frame unaffordable. It is a share of the separation
            // because that is what bounds the work: the points laid down are about one every step along every lane.
            .coerceAtLeast(separation * MinStepShare)
        val stop = separation * StopShare
        // Both are lengths -- a sowing interval and a trail's reach -- so they are counted off the *actual* step
        // rather than off `stepShare`. Counting them off the share would silently sow four times as densely and run
        // trails four times as far the moment the step was shortened for smoothness.
        val sow = (SeedSpan * separation / step).roundToInt().coerceAtLeast(1)
        val steps = (TrailSpan * separation / step).toInt()
    }

    /**
     * The longest hop that still draws a smooth line, in pixels — what the *field* allows, against what the
     * separation implies.
     *
     * **A trail is a polyline through the points it hops between, so the step has to resolve the field and not just
     * the spacing.** The step was a share of the separation alone, which is a number that knows nothing about how
     * fast the direction turns: at *Irregularity* `0` that came to five degrees of turn per hop and drew smoothly,
     * and at `1` to nineteen degrees over nine hops per wiggle — visible corners, with the round joins bulging at
     * each one. It read as a low-quality line rather than as a wrong one, which is why it survived the ink
     * measurements and the knob guard alike.
     *
     * Both octaves sweep their span over about half a wavelength, so each contributes `2 x span / wavelength`
     * radians of turn per pixel; the hop is then the turn budget divided by their sum. The detail octave dominates,
     * being [DetailRatio] times the finer of the two — which is the whole reason this only ever showed with
     * *Irregularity* wound up.
     */
    internal fun smoothStep(look: Look, detail: Float, longSide: Float): Float {
        val base = longSide / look.frequency
        val fine = base / DetailRatio
        return MaxTurnPerStep / (2f * (look.span / base + detail / fine))
    }

    /**
     * Walks one half of a streamline from ([startX], [startY]), laying its points into [grid] and dropping fresh seed
     * candidates beside it as it goes; [way] is `1` forward along the field and `-1` back against it.
     *
     * **The seeds are what fill the frame, and they are the whole difference from growing a fixed set of trails.**
     * Dropping a candidate one separation off each flank is Jobard and Lefer's rule, and it packs the frame at
     * exactly the separation asked for rather than at whatever a random scatter happened to leave.
     */
    private fun half(
        startX: Float,
        startY: Float,
        walk: Walk,
        grid: SeparationGrid,
        seeds: ArrayList<Float>,
        owner: Int,
        way: Float,
    ): FloatArray {
        val points = ArrayList<Float>()
        var x = startX
        var y = startY
        points.add(x)
        points.add(y)
        grid.insert(x, y, owner)

        for (i in 1..walk.steps) {
            val angle = walk.angleAt(x, y)
            val dx = cos(angle) * walk.step * way
            val dy = sin(angle) * walk.step * way
            val nx = x + dx
            val ny = y + dy
            if (outside(nx, ny, walk.width, walk.height) || grid.crowded(nx, ny, walk.stop, owner)) break

            points.add(nx)
            points.add(ny)
            grid.insert(nx, ny, owner)
            if (i % walk.sow == 0) {
                val flank = walk.separation / walk.step
                offer(seeds, nx - dy * flank, ny + dx * flank, walk.width, walk.height)
                offer(seeds, nx + dy * flank, ny - dx * flank, walk.width, walk.height)
            }
            x = nx
            y = ny
        }
        return points.toFloatArray()
    }

    /** Adds a seed candidate if it is on the frame at all; whether it is *free* is settled when it is popped. */
    private fun offer(seeds: ArrayList<Float>, x: Float, y: Float, width: Int, height: Int) {
        if (outside(x, y, width, height)) return
        seeds.add(x)
        seeds.add(y)
    }

    /** Whether ([x], [y]) has left the frame, which ends a trail as surely as a neighbor does. */
    private fun outside(x: Float, y: Float, width: Int, height: Int): Boolean =
        x < 0f || y < 0f || x >= width || y >= height

    /**
     * Draws one trail in [paint]'s color, at [width] pixels — cut into dashes, or beaded whole.
     *
     * **Both looks dash, and beading replaces the dashing rather than the stroking.** *Pearls* had been drawing
     * whole paths here; the reference's *Pearls* is as plainly dashed as its *Eclectic*, at every setting of all six
     * of its knobs — round-capped segments of two to six lanes with ground between them. What its beaded minority
     * replaces is the whole lane, which is why a bead chain runs unbroken where the strokes beside it do not.
     *
     * **The share is only consulted when there is one**, so a look with no beads draws nothing from [random] here
     * and its stream stays where it was.
     *
     * **A trail that only managed a step or two is still drawn**, rather than dropped the way gart drops the ones
     * that missed a third of its step budget: a discarded streamline has already laid its points into the separation
     * grid, so it goes on holding its lane against every later trail while drawing nothing. Dropping them cost a
     * third of the frame's ink, and the reference has short stubs everywhere.
     */
    @Suppress("LongParameterList")
    private fun drawTrail(
        canvas: Canvas,
        ink: Ink,
        points: FloatArray,
        walk: Walk,
        beaded: Float,
        paint: Paint,
        random: Random,
    ) {
        if (points.size < MinDrawnValues) return
        if (beaded > 0f && random.nextFloat() < beaded) {
            // A beaded lane is beaded whole, so it is the one mark that takes its ink from the trail.
            val grade = gradeOf(ink, points, random)
            paint.color = ink.tones[toneIndex(ink.look, grade, ink.tones.size, random)]
            drawBeads(canvas, points, widthOf(ink, walk, grade), walk.step, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        for (dash in dashes(ink, points, walk, random)) {
            paint.color = ink.tones[toneIndex(ink.look, dash.grade, ink.tones.size, random)]
            paint.strokeWidth = widthOf(ink, walk, dash.grade)
            canvas.drawPath(Streamlines.pathOfPixels(dash.points), paint)
        }
    }

    /** What a mark at [grade] is stroked at, in pixels — its share of a lane, times *Thickness*. */
    private fun widthOf(ink: Ink, walk: Walk, grade: Float): Float =
        walk.separation * ink.thickness * widthShare(ink.look, grade)

    /**
     * Cuts one streamline into the dashes *Eclectic* is drawn as, at seeded lengths and from a seeded phase.
     *
     * **The marks are dashes along a long streamline, not short streamlines.** Evenly-spaced seeding fills the frame
     * with lines that run right across it, and the reference's *Eclectic* marks are plainly short — but cutting the
     * *tracing* short is not the same thing: nothing then fills the space beyond where a trail stopped, because a
     * seed dropped ahead of it sits inside its own exclusion and is refused. Tracing long and cutting at the
     * *drawing* keeps the even spacing across the flow and gives the broken run along it, which is both halves of
     * what theirs looks like.
     *
     * The lengths are in **separations**, not steps, because that is what theirs holds fixed: wind their *Density*
     * down and the marks lengthen in step with the lanes widening, rather than staying the length they were.
     */
    private fun dashes(ink: Ink, points: FloatArray, walk: Walk, random: Random): List<Dash> {
        val out = ArrayList<Dash>()
        val perSeparation = walk.separation / walk.step
        val shortest = (MinDashSpan * perSeparation).toInt().coerceAtLeast(2)
        val longest = (MaxDashSpan * perSeparation).toInt().coerceAtLeast(shortest + 1)
        var at = random.nextInt(shortest)
        while (at * 2 < points.size) {
            val end = minOf(at + shortest + random.nextInt(longest - shortest), points.size / 2)
            if (end - at < 2) break
            val cut = points.copyOfRange(at * 2, end * 2)
            val grade = gradeOf(ink, cut, random)
            out.add(Dash(cut, grade))
            // The round caps stand a full width proud of the two points a gap runs between, so a gap measured only
            // in lanes is *closed* by any mark wider than it — which is most of them, and it is why an earlier
            // build's fat marks ran on as unbroken ribbons while its hairlines broke correctly. The cap clearance is
            // the floor, and it is read off **this** mark's own width, which is why the grade is drawn before the
            // gap it pays for rather than after the whole trail is cut.
            val gap = max(DashGapSpan * walk.separation, widthOf(ink, walk, grade) * GapPerWidth)
            at = end + (gap / walk.step).toInt().coerceAtLeast(1)
        }
        return out
    }

    /** One mark: the stretch of streamline it covers, and where it sits on its look's scale — see [Ink]. */
    private class Dash(val points: FloatArray, val grade: Float)

    /**
     * Draws a streamline as the chain of dots *Pearls* beads a few of its lines into — gart's one-in-six.
     *
     * The dots are spaced by their own width rather than by a step count, so a fat line beads coarsely and a hairline
     * finely; gart walks the path taking every n-th point until two consecutive ones fall close enough together,
     * which is the same rule reached from the other side. Measured off the reference: `21px` beads at a `31px`
     * pitch, in a band whose strokes are `22-23px` — a bead is the stroke's own width, at about one and a half of
     * them apart.
     *
     * **They are drawn translucent, and that is measurable rather than a taste.** Every bead color in the reference
     * is its stroke color composited on the ground at `0.70` to three decimal places in all three channels —
     * `073B4C` beads read `507176`, `06D6A0` read `4FDEB1`, `EF476F` read `F4798E`. gart does the same at a much
     * lower alpha. Opaque beads read as a second, louder mark competing with the strokes; at `0.70` the chain sits
     * behind them, which is the depth the look is named for.
     */
    private fun drawBeads(canvas: Canvas, points: FloatArray, width: Float, step: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        val opaque = paint.color
        paint.alpha = BeadAlpha
        val stride = (width * BeadSpacing / step).toInt().coerceAtLeast(1)
        var i = 0
        while (i * 2 + 1 < points.size) {
            canvas.drawCircle(points[i * 2], points[i * 2 + 1], width / 2f, paint)
            i += stride
        }
        paint.color = opaque
    }

    /**
     * Draws one orb, and in *Pearls* the ring of ground color around it.
     *
     * The ring is why *Pearls*' orbs read as holes punched through the picture rather than as discs laid on it: gart
     * strokes the circle in the background color after filling it, and the reference keeps the same dark band on its
     * *Pearls* and none at all on its *Eclectic*.
     *
     * **Orbs are placed by two independent draws and are *not* kept apart, which was checked rather than assumed.**
     * Driving theirs to *Orbs* `10` puts three and four discs into overlapping clusters on both looks, at centre
     * distances down to `0.72` of the sum of their radii — so a rejection rule against the orbs already placed
     * would be a rule the reference does not have, and it would thin the clusters that are half of what the knob
     * draws. What makes an overlap read as one disc in front of another rather than as a lump is the ring, plus
     * [MinOrb] being high enough that no disc is small beside its neighbours. That is where the fix went.
     */
    /** Where the orbs may fall and how big they are drawn — the frame, plus what *Orb size* multiplies a radius by. */
    private class Frame(val width: Int, val height: Int, val shortSide: Float, val orbScale: Float)

    @Suppress("LongParameterList")
    private fun drawOrb(
        canvas: Canvas,
        look: Look,
        tones: IntArray,
        ground: Int,
        frame: Frame,
        paint: Paint,
        random: Random,
    ) {
        val x = random.nextFloat() * frame.width
        val y = random.nextFloat() * frame.height
        val spread = MinOrb + random.nextFloat() * (MaxOrb - MinOrb)
        val radius = spread * frame.orbScale * frame.shortSide
        // Drawn even at a radius of zero would be a stray dot from the round cap; *Orb size* `0` means no orb at all.
        if (radius <= 0f) return
        paint.style = Paint.Style.FILL
        paint.color = tones[random.nextInt(tones.size)]
        canvas.drawCircle(x, y, radius, paint)
        if (!look.ringedOrbs) return
        paint.style = Paint.Style.STROKE
        // Capped against the radius so a small orb keeps a visible middle rather than being swallowed by its own
        // ring — gart's is a flat twenty pixels because its orbs are all large.
        paint.strokeWidth = min(OrbRing * frame.shortSide, radius * MaxRingShare)
        paint.color = ground
        canvas.drawCircle(x, y, radius, paint)
    }

    /** The look at [variant], clamping an index this design does not have rather than failing on a stored recipe. */
    private fun lookAt(variant: Int): Look = Look.entries[variant.coerceIn(0, Look.entries.lastIndex)]

    /**
     * A uniform grid over the frame answering "is any *other* trail's point within a distance of here" — the spatial
     * index the collision rule needs to be affordable.
     *
     * Testing a head against every point of every trail is what gart does, and it is `O(trails × points)` per step:
     * fine on a desktop at 1024², a freeze on a phone at 1080×2400 with hundreds of trails. Cells are one separation
     * across, so a query only ever reads the nine around it.
     */
    private class SeparationGrid(width: Int, height: Int, private val cell: Float, capacity: Int) {

        private val cols = (width / cell).toInt() + 1
        private val rows = (height / cell).toInt() + 1
        private val buckets = Array(cols * rows) { ArrayList<Int>() }
        private val xs = FloatArray(capacity)
        private val ys = FloatArray(capacity)
        private val owners = IntArray(capacity)
        private var count = 0

        fun insert(x: Float, y: Float, owner: Int) {
            if (count >= xs.size) return
            xs[count] = x
            ys[count] = y
            owners[count] = owner
            buckets[bucketOf(x, y)].add(count)
            count++
        }

        /** Whether a point of some trail other than [owner] lies within [distance] of ([x], [y]). */
        fun crowded(x: Float, y: Float, distance: Float, owner: Int): Boolean {
            val col = (x / cell).toInt()
            val row = (y / cell).toInt()
            val squared = distance * distance
            for (r in (row - 1)..(row + 1)) {
                for (c in (col - 1)..(col + 1)) {
                    val inside = r >= 0 && r < rows && c >= 0 && c < cols
                    if (inside && bucketCrowded(r * cols + c, x, y, squared, owner)) return true
                }
            }
            return false
        }

        /** Whether one bucket holds a point of some trail other than [owner] within `sqrt(squared)` of ([x], [y]). */
        private fun bucketCrowded(bucket: Int, x: Float, y: Float, squared: Float, owner: Int): Boolean {
            for (i in buckets[bucket]) {
                if (owners[i] != owner) {
                    val dx = xs[i] - x
                    val dy = ys[i] - y
                    if (dx * dx + dy * dy < squared) return true
                }
            }
            return false
        }

        private fun bucketOf(x: Float, y: Float): Int {
            val col = (x / cell).toInt().coerceIn(0, cols - 1)
            val row = (y / cell).toInt().coerceIn(0, rows - 1)
            return row * cols + col
        }
    }

    /**
     * The separation at the ends of *Density*, as a share of the short side — **measured off theirs**, not chosen.
     *
     * **The middle is fitted through the ink rather than by eye, because a pitch is the one thing in this design a
     * capture will not show you directly.** Marks overlap and curve, so no scan line crosses lanes cleanly; but ink
     * coverage does not depend on the pitch at all (a width is a share of a lane, so shrinking the lane shrinks the
     * mark with it) — it is `mean width share x duty`, and *that* pins the share, which with the measured widths in
     * pixels pins the lane. Their *Eclectic* covers `63%` of the frame with marks whose median is `25px` and whose
     * mean is about `28px`, at a dash duty near `0.85`; that is a mean share of `0.74` and so a lane of **`38px`**,
     * on a 1080-wide frame at *Density* `50`.
     *
     * Ours had `55px`, from an earlier attempt to read the pitch off a scan line, and being half again too loose is
     * what made the marks too long (a dash is measured in lanes) at the same time as it made them too fat. Both
     * halves of "ours looks like long dashed lines and theirs like short varied strokes" came from this one number.
     *
     * The ends are the same fit run at *Density* `0` and `75`, off the length distribution, which scales with the
     * lane: `1.73x` the default at the bottom and `0.70x` at three quarters, so the knob's whole travel is a factor
     * of about `3.5`, not the `5` ours had.
     */
    private const val MaxSpacing = 0.066f
    private const val MinSpacing = 0.019f

    /**
     * How far apart the two octaves sit — the wiggle's wavelength against the sweep's.
     *
     * Measured off their *Irregularity* at full: the serpentine runs about a fortieth of the frame, against swirls
     * that run a third of it. Below about four the wiggle stops reading as a disturbance and merely loosens the
     * flow; far above it there are too few steps per wave to draw one, and the marks go ragged instead of wavy.
     */
    private const val DetailRatio = 7f

    /**
     * The most fine wiggle *Irregularity* adds, in radians end to end — enough for a mark to double back on itself.
     *
     * Fitted to the *middle* of their knob rather than its top, which is the only place a span can be fitted: their
     * `0` and `100` are a smooth field and a serpentine one in any scaling, while `50` has to land on the shipped
     * look. At half of `2.4` it read as their `100`, and at half of `1.6` the marks still hooked back on themselves
     * where theirs merely lean.
     */
    private const val MaxDetailSpan = 1.1f

    /** What *Thickness* multiplies a mark's width by at the ends of the knob; `0.5` lands on exactly `1`. */
    private const val MinThickness = 0.4f
    private const val MaxThickness = 2.5f

    /**
     * How wide a mark may be as a share of its lane, before *Thickness*, and how the draw is biased within that.
     *
     * **The ceiling is nearly two lanes, and the draw is biased thin.** Both were the other way round, on the
     * reasoning that a ceiling of one lane keeps marks from merging into blobs; the reference simply does not obey
     * it. Fitted to its measured widths against the `38px` lane above: `3px` at the tenth percentile, `7` at the
     * quarter, `25` at the half, `37` at three quarters, `55` at the ninetieth and `71` at the top — that is
     * `0.08..1.87` lanes with an exponent near `2`, and a quarter of its marks are hairlines under a fifth of a lane.
     * Ours drew `0.12..1` biased *fat*, which put `4%` of its marks under `8px` where theirs has `25%`, and left it
     * with no hairlines to thread between the lozenges.
     *
     * **The overlap the old ceiling was protecting against is what the design is made of.** A mark wider than its
     * lane crosses its neighbours, and that crossing is most of why theirs reads as scattered strokes rather than as
     * ruled lanes. It is also where the last of the ink comes from: the fitted mean share is `0.72`, which at a
     * `0.85` duty is `61%` against their measured `63%`.
     */
    private const val MinWidthShare = 0.08f
    private const val MaxWidthShare = 1.87f
    private const val WidthBias = 1.8f

    /**
     * The same, for [Weave.GRADED] — an eighth of a lane to three fifths of one, a *much* narrower band.
     *
     * Measured off the reference's *Pearls*: `5-10px` marks at the frame's center and `22-23px` at its edges, on the
     * `38px` lane [MaxSpacing] fits. The ceiling matters more than the floor — staying under a lane is what keeps
     * that look airy (its ink runs `7..48%` of a column against *Eclectic*'s `63%` over the frame), and it is what
     * stops a fat mark from swallowing the ground its neighbours are read against.
     */
    private const val MinGradedWidth = 0.13f
    private const val MaxGradedWidth = 0.60f

    /**
     * What a *whole* angle span is worth as a noise amplitude — a half, because [PerlinNoise2d] runs `-1..1`.
     *
     * gart's Perlin answers `0..1`, so its `noise * 3` is three radians end to end. Read the other way here it would
     * be six, and the difference is the design curling twice as hard as the art it is taken from.
     */
    private const val NoiseAmplitude = 0.5f

    /** The fewest interleaved values a trail can be stroked from — two points, so four floats. */
    private const val MinDrawnValues = 4

    /**
     * How close a head may come to another trail before this one is finished, as a share of the separation.
     *
     * **A trail is seeded at one separation and stopped at half of one, and using a single distance for both is what
     * empties the frame.** It is Jobard and Lefer's `d_test` against their `d_sep`, and the reason for the two is
     * mechanical: a seed dropped at exactly one separation off a flank starts its trail on the very boundary of the
     * test that would stop it, so it dies within a step or two of being born. Every lane then holds a stub instead of
     * a streamline — which reads as a sparse, broken picture rather than as an error, and is what the first build of
     * this rebuild produced. gart never hits it because its `HashGrid.isFree` short-circuits on a non-empty home
     * cell and skips the neighbour search entirely, so its test is far more permissive than the radius it names. The
     * paper's two distances are the honest version of the same leniency.
     */
    private const val StopShare = 0.5f

    /**
     * How much arc a streamline covers between dropping fresh seed candidates, in separations — gart's rate.
     *
     * **In separations rather than in steps, because the two looks step at very different sizes.** gart sows every
     * four hops of one pixel against a separation of eighteen, which is a candidate pair every two-ninths of a
     * separation; counted in *hops* here instead, *Eclectic* would sow four times more sparsely than the art it comes
     * from and leave a third of its lanes unfilled.
     */
    private const val SeedSpan = 0.22f

    /** How far one half of a streamline may run, in separations — long enough that collision is what ends it. */
    private const val TrailSpan = 40f

    /** How many marks a frame may hold, so the tightest spacing stays affordable. A safety valve, not a knob. */
    private const val MaxTrails = 5000

    /**
     * The most the field may turn between two traced points, in radians — the smoothness budget [smoothStep] spends.
     *
     * About five degrees. It is set where *Irregularity* `0` already sat, since that end was never the one that
     * looked wrong: the knob's job is to make the marks serpentine, not to make them faceted, and holding the turn
     * per hop where the smooth end already had it is what keeps the whole range drawing the same quality of line.
     */
    private const val MaxTurnPerStep = 0.09f

    /** The shortest hop, as a share of the separation, so the tightest field stays affordable. */
    private const val MinStepShare = 0.03f

    /** The bounds on the derived separation-grid budget — see [gridCapacity]. */
    private const val MinGridPoints = 4096
    private const val MaxGridPoints = 250_000

    /** An owner no trail has, for the test that asks whether a point is free of *every* trail. */
    private const val NoOwner = -1

    /**
     * How long a dash runs and how much flow is left bare after it, **in separations**.
     *
     * Measured off theirs at both ends of *Density*: a mark runs about three to six and a half lanes and the gap
     * after it about half a lane, and both hold as the lanes widen — which is why they are counted in lanes here
     * rather than in steps, and it is the reason winding their *Density* down lengthens the marks instead of only
     * spreading them. In pixels that is a median near `154px` at *Density* `50`, which the old spans reproduced as
     * `236px` purely because the lane they were counted in was too wide.
     *
     * [GapPerWidth] is the floor the round caps impose, in multiples of the mark's own width — see [dashes].
     */
    private const val MinDashSpan = 2.6f
    private const val MaxDashSpan = 6.4f
    private const val DashGapSpan = 0.5f
    private const val GapPerWidth = 1.15f

    /**
     * The curve on *Dots* that puts its middle on gart's one-in-six — `0.5.pow(2.6)` is `0.165`.
     *
     * Stated as the exponent rather than as the share because the two ends are fixed at `0` and `1` by what the knob
     * means, so the only free choice is where the default sits.
     */
    private const val BeadBias = 2.6f

    /** How far apart beads sit, as a multiple of their diameter — the reference's `31px` pitch on `21px` beads. */
    private const val BeadSpacing = 1.6f

    /** What a bead is drawn at, of 255 — the reference's measured `0.70` over the ground. */
    private const val BeadAlpha = 179

    /** The most orbs the sky may hold — the reference's own ten, so `depth` `0.5` is five. */
    private const val MaxOrbs = 10

    /**
     * An orb's radius at the middle of *Orb size*, as a share of the short side — **measured off theirs, and the
     * floor is the number that matters.**
     *
     * Read off two captures driven to the same place (*Orbs* `10`, *Orb size* at its own default, *Density* and
     * *Thickness* wound down so the discs sit almost alone) and fitted by erosion rather than by area, so a disc
     * that a mark crosses still measures as the circle it is: `112, 168, 228, 232` on *Pearls* and `120, 128, 208`
     * on *Eclectic*, all on a 1080-wide frame. That is `0.10..0.215` of the short side, a spread of about two.
     *
     * Ours was `0.05..0.15` — a spread of **three**, whose small end is half of the smallest disc theirs will
     * draw. That is the whole of why our orbs read as lumps: a `54px` moon overlapping a `162px` one is not two
     * discs, it is a bite taken out of one, and the ground-colored ring that would have said "in front" instead
     * traces the bite. Theirs overlaps just as freely and never produces it, because it never draws the small one.
     */
    internal const val MinOrb = 0.10f
    internal const val MaxOrb = 0.215f

    /** The most of its own radius an orb's ring may take, so a small orb is not swallowed by it. */
    private const val MaxRingShare = 0.25f

    /** The ring *Pearls* strokes around an orb, as a share of the short side — gart's `20f` on a 1024 canvas. */
    private const val OrbRing = 0.0195f

    /** Mixed into the seed so the orbs, and the finer octave, draw from streams of their own. */
    private const val OrbSeed = 0x4F52_4253_4846_4C44L
    private const val DetailSeed = 0x4752_4149_4E46_4C44L

    /**
     * A whole turn in radians — **spelled out, and it has to be `const`.**
     *
     * [Look]'s entries are constructed when the enum first loads, which is while `style`'s own initializer is
     * running — before any `val` declared further down this object has been assigned. A computed
     * `private val TwoPi = (2.0 * PI).toFloat()` here would therefore hand *Pearls* a span of zero, and a zero span
     * is not a crash: it is a flow field pointing due east everywhere, which draws a picture of straight parallel
     * lines rather than an error. A `const val` is resolved by the compiler and has no initialization order at all.
     */
    private const val TwoPi = 6.2831855f

    /** A quarter turn in radians, and `const` for [TwoPi]'s reason — it is read while [Look] loads. */
    private const val QuarterTurn = 1.5707964f
}
