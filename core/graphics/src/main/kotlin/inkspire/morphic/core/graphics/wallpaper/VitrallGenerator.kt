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
// The count is the design's own vocabulary — cutting, glazing, toning, planning, drawing and now morphing — and each
// step is a named thing rather than a fragment of a longer one. Splitting the object to score better would put a
// window's construction in two files.
@Suppress("TooManyFunctions")
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
     * The cut, before anything is glazed into it — what [panes] hands back, and the pure-geometry half of a [Plan].
     *
     * @property panes each pane's outline, interleaved `x, y` in a frame [aspect] wide and one tall. A bowed cut
     *   leaves the arc sampled into short segments, so a pane is a plain polygon however curved it looks.
     * @property bones the first few cuts, as full chords across whatever they cut — drawn in heavier lead. Two points
     *   for a straight cut, the sampled arc chain for a bowed one.
     * @property aspect how wide the cut frame was, in units of its own height. Everything above is in that frame.
     * @property tree the cuts themselves, kept rather than discarded once the panes fall out of them. **[panes] is
     *   derived from this and not built beside it** — one window, cut once — which is what lets a scrub re-cut the
     *   frame at a moment between two windows and get a partition rather than a pile of polygons.
     */
    internal class Window(
        val panes: List<FloatArray>,
        val bones: List<FloatArray>,
        val aspect: Float,
        val tree: GlassTree.Branch,
    )

    /**
     * A pane and the glass cut for it — the unit a window is planned in, and the unit two windows would be paired by.
     *
     * @property outline the pane's corners, interleaved `x, y`, in the cut frame (see [Plan.aspect]).
     * @property tone where on the palette's ramp this pane's glass is cut from, `0..1`. **A position, not a color** —
     *   the palette is [draw]'s input, so one plan can be recolored without being re-cut, and two windows' tones can
     *   be interpolated without their palettes having to agree.
     * @property flashed whether this is one of the rare much paler pieces. A decision rather than a tone, because the
     *   lift it earns is applied to the resolved color and is deliberately independent of the ramp.
     * @property angle which way this pane's own gradient runs, in radians.
     * @property lift how far that gradient's bright end is lifted; the dark end drops [DropBias] of it.
     */
    internal class Pane(
        val outline: FloatArray,
        val tone: Float,
        val flashed: Boolean,
        val angle: Float,
        val lift: Float,
    )

    /**
     * A window cut and glazed but not yet painted — everything [draw] needs, at no particular size and in no
     * particular palette.
     *
     * **Resolution-independent, which is the property that earns the split.** Nothing here is measured in pixels:
     * every coordinate is in the cut frame ([aspect] wide, one tall) and [glass] and [leading] are the `0..1` their
     * knobs give. So one plan serves a draft, a full-size bake and a scrub frame at whatever size it can afford,
     * and none of them can disagree about the geometry because none of them re-derives it.
     *
     * **[Window] is the cut; this is the cut plus what was glazed into it.** They are separate because the cut is
     * pure geometry and is tested as such, where the glazing spends a random stream that only a whole window can
     * account for.
     *
     * @property glass how thick the glass reads — [DesignParams.depth], kept unresolved so it interpolates. [draw]
     *   spends it on the rim; [plan] has already spent it on each pane's [Pane.lift].
     * @property leading how heavy the came is — [DesignParams.scale], likewise unresolved: it becomes a stroke width
     *   only against a frame size.
     * @property tree the cuts [panes] were derived from, for [morph] to interpolate. **Null on a plan that is itself
     *   a moment of a morph** — that window was never cut from a recipe of its own, and nothing scrubs from a
     *   moment.
     */
    internal class Plan(
        val panes: List<Pane>,
        val bones: List<FloatArray>,
        val aspect: Float,
        val glass: Float,
        val leading: Float,
        val tree: GlassTree.Branch? = null,
    )

    /**
     * Two windows prepared to interpolate — built once when a scrub begins, asked for a moment on each of its frames.
     *
     * **The split between this and [morph] is the performance of the whole feature.** Merging two trees walks both of
     * them and counts subtrees as it goes; asking for a moment re-cuts the frame, which is one clip per cut. Merging
     * per frame would put the structure back into the inner loop for an answer that cannot change while a finger is
     * down.
     */
    internal class Morph(private val from: Plan, private val to: Plan, private val blend: GlassTree.Blend) {

        /**
         * The window [t] of the way across, `0` being [from] and `1` being [to].
         *
         * **Both ends hand back the original plan rather than a re-cut of it.** Re-cutting reproduces them — that is
         * asserted rather than assumed — but the plan is already in hand, so taking it costs nothing and leaves no
         * question about whether the ends of a scrub are the windows it was between.
         */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> cut(t)
        }

        private fun cut(t: Float): Plan {
            val aspect = GlassTree.lerp(from.aspect, to.aspect, t)
            val panes = ArrayList<Pane>(maxOf(from.panes.size, to.panes.size))
            val bones = ArrayList<FloatArray>()
            GlassTree.cells(blend, t, frameRect(aspect), bones) { a, b, outline ->
                panes.add(glazed(from.panes.getOrNull(a), to.panes.getOrNull(b), outline, t))
            }
            return Plan(
                panes = panes,
                bones = bones,
                aspect = aspect,
                glass = GlassTree.lerp(from.glass, to.glass, t),
                leading = GlassTree.lerp(from.leading, to.leading, t),
            )
        }

        /**
         * The glass filling one pane at [t] — between [a]'s and [b]'s, or one of them alone where that pane is only
         * arriving or only leaving.
         */
        private fun glazed(a: Pane?, b: Pane?, outline: FloatArray, t: Float): Pane = when {
            a == null -> Pane(outline, b!!.tone, b.flashed, b.angle, b.lift)
            b == null -> Pane(outline, a.tone, a.flashed, a.angle, a.lift)
            else -> Pane(
                outline = outline,
                tone = GlassTree.lerp(a.tone, b.tone, t),
                // A flash is a decision, not a quantity — there is no half-flashed pane to draw, so it switches at the
                // midpoint. It lands on about one pane in fifty and moves that pane's color by a fifth, which is a
                // step small enough and rare enough to cost less than fading two fills over each other.
                flashed = if (t < 0.5f) a.flashed else b.flashed,
                angle = GlassTree.lerpAngle(a.angle, b.angle, t),
                lift = GlassTree.lerp(a.lift, b.lift, t),
            )
        }
    }

    /**
     * [from] and [to] merged into one set of cuts, ready to divide the frame at any moment between them.
     *
     * **What interpolates is the cutting, not the panes** — which is this design and the second answer the question
     * has had. Pairing panes off and interpolating each one against its partner looks reasonable and cannot work:
     * two panes are neighbours *because one cut made both*, so partners chosen pane by pane pull a shared edge in
     * two directions, and a window that is a partition at both ends of a scrub is a pile of shards over open lead in
     * the middle of it. Cuts have no such coupling to break. [GlassTree] carries the measurement that settled it.
     */
    internal fun morph(from: Plan, to: Plan): Morph = Morph(
        from, to,
        GlassTree.merge(
            requireNotNull(from.tree) { "a scrub starts on a window that was cut, not on a moment of one" },
            requireNotNull(to.tree) { "a scrub ends on a window that was cut, not on a moment of one" },
            frameRect(from.aspect),
        ),
    )

    /** The cut frame: [aspect] wide and one tall, which every cut is applied to in turn. */
    private fun frameRect(aspect: Float): FloatArray = floatArrayOf(0f, 0f, aspect, 0f, aspect, 1f, 0f, 1f)


    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(width, height, params, seed), palette, width, height)
        return bitmap
    }

    /**
     * The window [params] and [seed] describe, in a frame shaped like `[width]` × `[height]`.
     *
     * **The size is read for its aspect and nothing else**, so a plan made against one frame size is valid at every
     * frame size of that shape — which is what lets a scrub redraw at whatever resolution it can afford without
     * re-cutting the window.
     */
    internal fun plan(width: Int, height: Int, params: DesignParams, seed: Long): Plan {
        // Cut in a frame that is `aspect` wide and 1 tall, not in the unit square: in the unit square a 45° cut on
        // a 1080×2400 frame draws as a near-vertical one, so the grain collapses toward the long axis and the window
        // fills with needles. Aspect-true, one unit is one unit, and the height is the scale for both axes.
        val window = panes(Amount.at(params.density), params.irregularity, seed, width.toFloat() / height)
        val glass = params.depth.coerceIn(0f, 1f)
        val tint = Tint.entries[params.variant.coerceIn(0, Tint.entries.lastIndex)]

        val random = Random(seed xor ToneSalt)
        val noise = PerlinNoise2d(seed xor FieldSalt)
        // One stream for the whole window, drawn in a fixed order per pane — tone, flash, angle, lift. **That order
        // is the picture**: re-ordering these four, or hoisting one out of the loop, re-glazes every pane after it
        // and the window silently becomes a different one at the same seed. They are locals rather than constructor
        // arguments so the order is stated rather than inherited from the argument list.
        val glazed = window.panes.map { outline ->
            val tone = tone(outline, tint, noise, random, window.aspect)
            val flashed = random.nextFloat() < FlashChance
            val angle = random.nextFloat() * GlassCut.Turn
            val lift = lift(glass, random)
            Pane(outline, tone, flashed, angle, lift)
        }
        return Plan(glazed, window.bones, window.aspect, glass, params.scale.coerceIn(0f, 1f), window.tree)
    }

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette].
     *
     * **It is handed a canvas rather than making one, and that is the half of the split that matters**: the same plan
     * paints into a software bitmap for the bake and into a hardware canvas for a scrub, so the two cannot drift.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        val glass = plan.glass
        val lead = plan.leading * MaxLeading * min(width, height)
        val came = palette.colorAt(palette.size - 1) // darkest stop by convention — the lead between panes
        canvas.drawColor(came)

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
        val paths = plan.panes.map { path(it.outline, scale) }
        paths.forEachIndexed { i, path ->
            val pane = plan.panes[i]
            var base = LinearGradientGenerator.colorAt(pane.tone, palette)
            // The odd flashed pane, a stop-independent lift — a real window carries a few pieces of much paler glass,
            // and they are what the eye reads as light coming through rather than as color laid on.
            if (pane.flashed) base = TriangularFacetsGenerator.shade(base, FlashLift)
            fill.shader = glassShader(pane, base, scale)
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
            plan.bones.forEach { canvas.drawPath(path(it, scale, close = false), heavy) }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(came1).apply {
                strokeWidth = lead * FrameWidth
            })
        }
    }

    /**
     * A window of about [count] panes, of which [curves] bow into arcs, from [seed], in a frame [aspect] wide and
     * one tall.
     *
     * The recursion is depth-first over a stack rather than by call, so a fine window cannot run the frame out of
     * stack; [MaxTries] attempts per region and a hard cap on the pane count keep a pathological seed bounded.
     *
     * **The cuts are recorded as they are made and the panes derived from them at the end, rather than kept as they
     * fall out.** Two ways of saying what one window is would be two things to hold in agreement, and the one a scrub
     * reads is the one no bake would ever exercise — so there is one, and the bake is what proves it.
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

        val root = GlassTree.Branch()
        val settled = ArrayList<Region>()
        val pending = ArrayDeque<Region>()
        pending.addLast(Region(frameRect(frame), root, 1f, 0))
        while (pending.isNotEmpty() && settled.size < count * PaneCap) {
            val here = pending.removeLast()
            // Small enough for this branch's own stopping area, or too awkward a shape to cut — either way, it stays.
            val made = if (GlassCut.area(here.outline) < target * here.spread) {
                null
            } else {
                cut(here.outline, grain, bowing, target, here.depth, random)
            }
            if (made == null) settled.add(here) else divide(here, made, pending, random)
        }
        settled.addAll(pending)

        var next = 0
        for (pane in settled) next = glaze(pane.outline, pane.branch, next, target, bowing, random)
        val outlines = arrayOfNulls<FloatArray>(next)
        val bones = ArrayList<FloatArray>()
        GlassTree.cells(root, frameRect(frame), outlines, bones)
        return Window(outlines.filterNotNull(), bones, frame, root)
    }

    /**
     * A region of the frame on its way to being a pane: its outline, the node standing for it, the stopping area its
     * own branch drew, and how deep it sits.
     */
    private class Region(
        val outline: FloatArray,
        val branch: GlassTree.Branch,
        val spread: Float,
        val depth: Int,
    )

    /** A cut that landed: the two sides it left, and the recipe that would make it again over any region. */
    private class Made(val sides: GlassTree.Sides, val recipe: GlassTree.Cutting)

    /**
     * [here] recorded as cut by [made], and its two pieces queued to be cut in their turn.
     *
     * **The pieces are queued in the order the geometry produced them and hung on the tree by the cut's own sides,
     * and those two orders differ whenever a cut bows the far way.** The queue order is what the random stream is
     * spent against, so changing it re-glazes every pane after this one; the tree's order is what makes a bow and a
     * straight cut name their sides alike, without which a scrub swaps two subtrees the instant a bow flattens
     * through zero. Conflating them is silent either way round.
     */
    private fun divide(here: Region, made: Made, pending: ArrayDeque<Region>, random: Random) {
        val plus = GlassTree.Branch()
        val minus = GlassTree.Branch()
        here.branch.cut = made.recipe
        here.branch.bone = here.depth <= BoneDepth
        here.branch.plus = plus
        here.branch.minus = minus
        val plusOutline = made.sides.plus!!
        val minusOutline = made.sides.minus!!
        val depth = here.depth + 1
        if (made.recipe.curve >= 0f) {
            pending.addLast(Region(plusOutline, plus, nextSpread(random), depth))
            pending.addLast(Region(minusOutline, minus, nextSpread(random), depth))
        } else {
            pending.addLast(Region(minusOutline, minus, nextSpread(random), depth))
            pending.addLast(Region(plusOutline, plus, nextSpread(random), depth))
        }
    }

    /**
     * One cut of [region] — straight or bowed — or null after [MaxTries] tries, which the caller reads as "leave
     * this pane whole".
     *
     * Retrying is the whole reason this can fail: a cut that would not leave exactly two pieces is refused, and both
     * halves have to be worth keeping ([MinHalf] of the target area), so a pane that has been bitten into an awkward
     * shape needs several angles offered before one lands.
     *
     * **The recipe is struck first and the pieces come from applying it**, rather than the two being made side by
     * side. That is what makes the window a bake reproduces and the window a scrub re-cuts the same window by
     * construction instead of by inspection.
     */
    @Suppress("LongParameterList") // The window's settings plus the region; every one is read on the first line.
    private fun cut(
        region: FloatArray,
        grain: FloatArray,
        bowing: Float,
        target: Float,
        depth: Int,
        random: Random,
    ): Made? {
        repeat(MaxTries) {
            val angle = cutAngle(grain, random)
            val box = GlassCut.bounds(region)
            val px = GlassCut.centroidX(region) + (random.nextFloat() * 2f - 1f) * PointDrift * (box[2] - box[0])
            val py = GlassCut.centroidY(region) + (random.nextFloat() * 2f - 1f) * PointDrift * (box[3] - box[1])
            // A bow is likelier on the first cuts, where it becomes the window's tracery rather than a wobble.
            val bows = random.nextFloat() < min(1f, bowing * (if (depth <= EarlyDepth) EarlyBowGain else 1f))
            val recipe = if (bows) {
                GlassTree.bowed(angle, px, py, bowReach(box, depth, random))
            } else {
                GlassTree.straight(angle, px, py)
            }
            val sides = recipe.cut(region)
            val plus = sides.plus
            val minus = sides.minus
            val clean = plus != null && minus != null &&
                GlassCut.area(plus) > target * MinHalf && GlassCut.area(minus) > target * MinHalf
            if (clean) return Made(sides, recipe)
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
     * [region] left as one pane, or glazed into a run of two to four parallel courses, recorded onto [branch].
     *
     * Straight courses run near-vertical or near-horizontal, or — once in [LongEdgeChance] — along the pane's own
     * longest diagonal, which is where the reference's runs of parallel strips come from; the rest are concentric
     * arcs. Only a pane with room is glazed at all: courses thinner than the target's own fraction are slivers.
     *
     * **The arc courses are gated on [bowing], where the reference model's are unconditional.** That is a departure
     * and it is the knob's fault, not the model's: *Curves* at `0` has to leave every cut straight, and a glazing
     * pass that keeps striking arcs there makes the rigid end of the knob a lie about a fifth of the window.
     *
     * @return the next unused pane index, this pane's courses having taken the ones from [first] up.
     */
    @Suppress("LongParameterList") // A pane, where it hangs, what it is numbered from, and the window's settings.
    private fun glaze(
        region: FloatArray,
        branch: GlassTree.Branch,
        first: Int,
        target: Float,
        bowing: Float,
        random: Random,
    ): Int {
        if (random.nextFloat() >= GlazeChance || GlassCut.area(region) <= target * GlazeFloor) return whole(branch, first)
        val courses = min(MinStrips + random.nextInt(StripSpread), (GlassCut.area(region) / (target * StripFloor)).toInt())
        if (courses < MinStrips) return whole(branch, first)
        return if (random.nextFloat() < ArcCourseChance * bowing) {
            arcCourses(region, branch, first, courses, random)
        } else {
            straightCourses(region, branch, first, courses, random)
        }
    }

    /** [branch] left as the single pane it already is, numbered [index]. */
    private fun whole(branch: GlassTree.Branch, index: Int): Int {
        branch.index = index
        return index + 1
    }

    /**
     * [node] cut into the course it keeps and the remainder that carries on, the remainder being handed back.
     *
     * A run of courses is a chain rather than a fan: each cut takes one strip off what is left, so the tree under a
     * glazed pane leans all the way to one side. That costs nothing — it is four deep at most — and it is what makes
     * a course an ordinary cut, interpolable like every other.
     */
    private fun course(node: GlassTree.Branch, cut: GlassTree.Cutting, index: Int): GlassTree.Branch {
        val kept = GlassTree.Branch()
        val rest = GlassTree.Branch()
        node.cut = cut
        node.plus = kept
        node.minus = rest
        kept.index = index
        return rest
    }

    /** [count] courses cut off [region] by parallel lines — the plain leaded band. */
    private fun straightCourses(
        region: FloatArray,
        branch: GlassTree.Branch,
        first: Int,
        count: Int,
        random: Random,
    ): Int {
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
        var rest = region
        var node = branch
        var index = first
        for (s in 1 until count) {
            val at = lo + (hi - lo) * (s.toFloat() / count + (random.nextFloat() * 2f - 1f) * StripJitter)
            // (nx·at, ny·at) projects to `at` on the normal, so it sits on the cut line.
            val halves = GlassCut.split(rest, nx * at, ny * at, cos(angle), sin(angle))
            if (halves.size != 2) continue
            node = course(node, GlassTree.straight(angle, nx * at, ny * at), index++)
            rest = halves[1]
        }
        return whole(node, index)
    }

    /**
     * [count] courses cut off [region] by concentric circles struck from a center outside it — the curved glazing a
     * rose window is leaded in.
     */
    private fun arcCourses(
        region: FloatArray,
        branch: GlassTree.Branch,
        first: Int,
        count: Int,
        random: Random,
    ): Int {
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
        var rest = region
        var node = branch
        var index = first
        for (s in 1 until count) {
            val at = near + (far - near) * (s.toFloat() / count + (random.nextFloat() * 2f - 1f) * CourseJitter)
            val bowed = GlassCut.bowAbout(rest, cx, cy, at)
            if (bowed.panes.size != 2) continue
            node = course(node, GlassTree.about(cx, cy, at, rest), index++)
            rest = bowed.panes[1]
        }
        return whole(node, index)
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
     * The shader a pane is filled with: [base] lifted at one end of the pane and dropped at the other, along the
     * pane's own [Pane.angle] and by its own [Pane.lift].
     *
     * The sweep spans the pane's own extent along that angle, so a small pane gets the whole of it too, and the drop
     * is [DropBias] of the lift — glass reads as tinted rather than lit when its dark end goes further than its
     * bright one.
     */
    private fun glassShader(pane: Pane, base: Int, scale: Float): Shader {
        val lx = cos(pane.angle)
        val ly = sin(pane.angle)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in pane.outline.indices step 2) {
            val t = (pane.outline[i] * lx + pane.outline[i + 1] * ly) * scale
            lo = min(lo, t); hi = max(hi, t)
        }
        return LinearGradient(
            hi * lx, hi * ly, lo * lx, lo * ly,
            TriangularFacetsGenerator.shade(base, 1f + pane.lift),
            TriangularFacetsGenerator.shade(base, 1f - pane.lift * DropBias),
            Shader.TileMode.CLAMP,
        )
    }

    /**
     * How far one pane's gradient lifts — a share, never less than [LiftFloor] of it, of what [glass] allows.
     *
     * Drawn per pane rather than shared, which is the difference between a window of hand-cut glass and a sheet with
     * a gradient over it: a real window's pieces each catch the light their own way.
     */
    private fun lift(glass: Float, random: Random): Float {
        val reach = MinLift + glass * (MaxLift - MinLift)
        return reach * (LiftFloor + random.nextFloat() * (1f - LiftFloor))
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
