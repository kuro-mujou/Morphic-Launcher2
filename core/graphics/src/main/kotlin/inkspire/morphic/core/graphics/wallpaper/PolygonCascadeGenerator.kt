package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One shape drawn many times along a line, each copy a little smaller and a little more turned than the last —
 * *Polygon Cascade*.
 *
 * **A tween between two fully-specified shapes, which is what "cascade" means literally.** The design is two
 * positions, a first size, a last size and a last angle; every copy is a step along that interpolation and
 * [DesignParams.density] only decides how many steps there are. That was measured rather than assumed: driving the
 * reference's *Iterations* from 10 to 17 leaves the ink's bounding box identical to the pixel, and both of its
 * "delta" knobs land the **last** copy on the number shown whatever the count — so neither is a per-copy rate.
 * Reading them as rates is what the previous build of this file did, and it makes the count re-compose the picture.
 *
 * **The identity finding this was rebuilt for: the copies march across the frame.** The previous version stacked every
 * copy on one fixed centre, which draws a concentric rosette — the same ingredients arranged into a different picture
 * entirely. The two endpoints are the design.
 *
 * **The knobs.** [DesignParams.variant] picks the shape from a named vocabulary of six ([CascadeShape]) rather than a
 * side count, because the reference's own list has a circle and a star in it and neither is a regular polygon.
 * [DesignParams.scale] is the first copy's size and [DesignParams.taper] the last copy's, with `0` a cascade of
 * identical copies — the rigid end the old fixed shrink could not reach. [DesignParams.rotation] is the turn spent
 * over the whole cascade, [DesignParams.roundness] softens the corners (which is where the reference's own rectangle
 * sits), and [DesignParams.irregularity] is *Wobble*, ours rather than theirs. Deterministic in [render]'s seed.
 *
 * **[DesignParams.finish] is their *Mode*, and the two are different pictures of the same geometry.** Stroked, every
 * copy is an outline and the whole cascade shows through itself — the ground, and every copy behind the one in
 * front. Filled, each copy is opaque and hides what it covers, so the picture becomes overlapping paper and only the
 * leading sliver of each copy survives. Nothing about the cascade moves between them: it is one geometry and two
 * ways of inking it, which is why it is a *finish* rather than a second set of shapes.
 *
 * **[DesignParams.depth] is their *Shadow*, and it belongs to the filled finish alone.** Measured off the reference:
 * a blurred silhouette of each copy drawn **behind** it with **no offset at all** — an outer glow in black rather
 * than a thrown shadow, which is what scanning across a copy's left and right edges settled, both giving the same
 * profile mirrored. The radius is theirs and fixed (~6.5% of the frame width); the knob is the opacity, as theirs is.
 * A stroked copy has no interior to lift off the page, so [styleFor] withholds the knob there rather than letting it
 * drag and change nothing.
 *
 * **One of theirs is still not a knob here.** Their *Thickness* has no field left — [DesignParams.scale] is spent on
 * *Size*, which is the composition where a stroke weight is a finish, and the teardown's note on the spacing family
 * says the bigger of two members wins the field. Their two nudge pads are seeded rather than exposed — theirs
 * re-randomizes the pair on every pick, so a shuffle is already the same gesture.
 *
 * [copyCount], [shapeOf] and [ring] are pure and tested: a ring whose vertices sit off their radius, or a shape whose
 * proportions are wrong, is silently wrong geometry the bitmap only confirms afterwards.
 */
object PolygonCascadeGenerator : Generator {

    /**
     * The shapes a cascade can be built from — the reference's own vocabulary, which is a list of **names** rather
     * than a side count.
     *
     * That distinction is why this type exists. The previous build offered "sides, `3..8`", which draws three of
     * these six, cannot draw a circle, a star or a rectangle at all, and offers a pentagon, heptagon and octagon the
     * reference does not have. A count is not a vocabulary.
     *
     * **Positional, like every [VariantKnob] here**: index `n` is `variant = n`, so a stored recipe depends on the
     * order and the labels stay free to be reworded.
     */
    internal enum class CascadeShape(val label: String) {
        // The star leads because index `0` is a design's *default* look, and theirs opens on a star — a circle first
        // would be following the reference's strip order at the cost of the picture it actually shows.
        STAR("Star"),
        CIRCLE("Circle"),
        TRIANGLE("Triangle"),
        HEXAGON("Hexagon"),
        SQUARE("Square"),
        RECTANGLE("Rectangle"),
    }

    /**
     * How a copy is inked — the reference's *Mode*, and the whole of what [DesignParams.finish] chooses here.
     *
     * **Stroke leads because it is theirs' default and the design's**: an outline is what lets a cascade read as a
     * cascade, since every copy stays visible through the ones in front of it. A fill is the bolder picture and shows
     * the palette properly, but it is a stack rather than a trail.
     */
    internal enum class CascadeFinish(val label: String) {
        STROKE("Stroke"),
        FILL("Fill"),
    }

    /** What [DesignParams.density] resolves to — the copies between the two ends, and the *Iterations* slider's range. */
    private val Amount = AmountKnob.Count("Iterations", 2..24)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Size",
        taper = "Taper",
        irregularity = "Wobble",
        roundness = "Roundness",
        rotation = "Turn",
        // Named off the vocabularies themselves, so a seventh shape or a third finish cannot arrive without the
        // panel offering it.
        variant = VariantKnob("Shape", CascadeShape.entries.map { it.label }),
        finish = VariantKnob("Mode", CascadeFinish.entries.map { it.label }),
    )

    /**
     * Two knobs come and go with a choice, and both are "absent, not disabled" rather than a preference.
     *
     * A **circle** has no corners to soften and no orientation to turn, so it offers neither, and [render] skips both
     * rather than letting a stored value move pixels behind a control that is not on screen. The **shadow** is the
     * mirror case: it exists only for the filled finish, because what it lifts off the page is a copy's *interior*
     * and an outline has none — a stroked cascade with a shadow behind every hairline is a smudge, not depth.
     */
    override fun styleFor(params: DesignParams): DesignStyle {
        val shadowed = if (finishOf(params.finish) == CascadeFinish.FILL) style.copy(depth = "Shadow") else style
        return if (shapeOf(params.variant) == CascadeShape.CIRCLE) {
            shadowed.copy(roundness = null, rotation = null)
        } else {
            shadowed
        }
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // the ground is stop 0, and the copies are the ramp above it
        val copies = copyCount(params.density)
        val tones = RampTones.aboveGround(palette)
        if (tones.isEmpty()) return bitmap // a single-stop palette is all ground, with nothing to draw on it
        // Spent **continuously between those tones** rather than at one rung per copy. Asking [RampTones] for a rung
        // per copy is the obvious thing and it washes the design out: a rung is a share of the ramp measured *from
        // the ground*, so thirteen of them put the first copy a thirteenth of the way up and it disappears into the
        // ground it is drawn on. Theirs spends the ramp over the cascade's length — the first copy on the first tone
        // above the ground and the last on the palette's final stop, with more distinct tones in between than the
        // palette has stops.
        val ramp = Palette(tones.toList())

        val shortSide = min(width, height)
        val random = Random(seed)
        val shape = shapeOf(params.variant)
        val ring = ring(shape)
        val wobble = Wobble(params.irregularity.coerceIn(0f, 1f) * MaxWobble, random)

        // Their two nudge pads, seeded — a heading and a run, the segment centred on the frame.
        //
        // **The heading is drawn uniformly in the frame's own space, not in the plane.** A uniform angle sends a
        // cascade across a phone-shaped wallpaper as often as down it, and a run across leaves two thirds of the
        // frame empty; stretching the draw by the frame's own proportions makes a tall frame mostly draw tall
        // cascades without ever forbidding the others. The run is then a share of the frame's extent along whichever
        // heading came up, so the long way round gets the long travel.
        val bearing = random.nextFloat() * TwoPi
        val across = cos(bearing) * width
        val down = sin(bearing) * height
        val span = hypot(across, down)
        val headingX = across / span
        val headingY = down / span
        val run = (abs(headingX) * width + abs(headingY) * height) *
            (MinRun + random.nextFloat() * (MaxRun - MinRun))
        val firstX = width / 2f - headingX * run / 2f
        val firstY = height / 2f - headingY * run / 2f

        val firstRadius = shortSide * (MinSize + (MaxSize - MinSize) * params.scale.coerceIn(0f, 1f))
        val lastRadius = firstRadius * (1f - MaxTaper * params.taper.coerceIn(0f, 1f))
        // Half a turn covers every one of these shapes' symmetry periods — a rectangle's is the longest, at 180° — so
        // nothing is unreachable and the knob's top is not a repeat of its bottom. Which way is the seed's.
        val turn = (if (random.nextBoolean()) 1f else -1f) * PI.toFloat() * params.rotation.coerceIn(0f, 1f)
        val roundness = params.roundness.coerceIn(0f, 1f)

        val filled = finishOf(params.finish) == CascadeFinish.FILL
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = StrokeFraction * shortSide
        }
        // Only the filled finish casts one — see [styleFor] — so a stroked cascade never pays for the blur either.
        val shadow = if (filled) shadowPaint(params.depth, shortSide) else null

        // The first copy first, so the later and smaller ones sit in front — the order theirs draws in. Each copy's
        // shadow goes down immediately before the copy itself, which is what puts it over everything already drawn
        // and under the copy casting it.
        for (i in 0 until copies) {
            val t = i.toFloat() / (copies - 1)
            val radius = firstRadius + (lastRadius - firstRadius) * t
            paint.color = LinearGradientGenerator.colorAt(t, ramp)
            paint.pathEffect = when {
                shape == CascadeShape.CIRCLE || roundness == 0f -> null
                else -> CornerPathEffect(MaxCorner * roundness * radius)
            }
            val path = ringPath(
                ring = ring,
                cx = firstX + headingX * run * t,
                cy = firstY + headingY * run * t,
                radius = radius,
                turn = if (shape == CascadeShape.CIRCLE) 0f else turn * t,
                wobble = wobble,
            )
            if (shadow != null) {
                shadow.pathEffect = paint.pathEffect // the shadow is this copy's silhouette, rounded corners and all
                canvas.drawPath(path, shadow)
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    /**
     * The brush a copy's shadow is laid down with at [shadow] strength, or `null` where there is none to draw.
     *
     * **A blur with no offset, which is the measured shape of theirs and not the obvious one.** A shadow is normally
     * thrown some direction, and reading one off a render invites fitting an offset to it; scanning across a copy's
     * left edge and its right edge gives the *same* falloff mirrored, which no throw can do. So it is an outer glow
     * in black — a blurred copy of the path, drawn behind it, its inner half hidden under the fill it belongs to.
     */
    private fun shadowPaint(shadow: Float, shortSide: Int): Paint? {
        val strength = shadow.coerceIn(0f, 1f)
        if (strength == 0f) return null
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            alpha = (strength * MaxShadowAlpha * ChannelMax).roundToInt()
            maskFilter = BlurMaskFilter(ShadowRadiusFraction * shortSide, BlurMaskFilter.Blur.NORMAL)
        }
    }

    /** How many copies [density] asks for — two ends and nothing between, up to a dense fan. */
    internal fun copyCount(density: Float): Int = Amount.at(density)

    /** The shape [variant] picks, clamped to the vocabulary rather than wrapping, as every variant knob here is. */
    internal fun shapeOf(variant: Int): CascadeShape =
        CascadeShape.entries[variant.coerceIn(CascadeShape.entries.indices)]

    /** How [finish] says to ink a copy, clamped the same way. */
    internal fun finishOf(finish: Int): CascadeFinish =
        CascadeFinish.entries[finish.coerceIn(CascadeFinish.entries.indices)]

    /**
     * One copy of [shape] as a closed ring of vertices in unit space — interleaved **angle in radians, radius**, the
     * radius a share `0..1` of the circumradius.
     *
     * **Angle-and-radius rather than `x, y`**, because both things done to a ring are angular: the wobble displaces a
     * vertex along its own radius, and a shape's identity *is* how its vertices are spaced around the circle — a
     * rectangle's four sit at unequal angles at one radius, a star's ten at equal angles alternating radius. Every
     * shape is one list of those pairs, so nothing below this function branches on which shape it is drawing.
     *
     * The first vertex is straight up, which is where the reference's star and triangle carry their point.
     */
    internal fun ring(shape: CascadeShape): FloatArray = when (shape) {
        // Fine enough that a stroked ring reads as a circle, and fine enough that the corner effect could not round a
        // segment shorter than itself — which is what makes *Roundness* honestly absent here rather than merely small.
        CascadeShape.CIRCLE -> regular(CircleSides)
        CascadeShape.STAR -> FloatArray(StarPoints * 2 * 2) { k ->
            val vertex = k / 2
            when {
                k % 2 == 0 -> Top + vertex * (PI.toFloat() / StarPoints)
                vertex % 2 == 0 -> 1f
                else -> StarInner
            }
        }

        CascadeShape.TRIANGLE -> regular(TriangleSides)
        CascadeShape.HEXAGON -> regular(HexagonSides)
        // Turned a half-step, so the square sits square-on rather than balanced on a corner.
        CascadeShape.SQUARE -> regular(SquareSides, first = Top + PI.toFloat() / SquareSides)
        CascadeShape.RECTANGLE -> {
            val diagonal = sqrt(RectangleAspect * RectangleAspect + 1f)
            val corner = atan2(1f / diagonal, RectangleAspect / diagonal)
            floatArrayOf(-corner, 1f, corner, 1f, PI.toFloat() - corner, 1f, PI.toFloat() + corner, 1f)
        }
    }

    /** A regular [sides]-gon's ring, every vertex on the circumradius, the first of them at [first]. */
    private fun regular(sides: Int, first: Float = Top): FloatArray =
        FloatArray(sides * 2) { k -> if (k % 2 == 0) first + k / 2 * (TwoPi / sides) else 1f }

    /**
     * [ring] placed at ([cx], [cy]) in pixels at [radius], turned by [turn] radians and deformed by [wobble] — a
     * **closed** path, so the corner effect rounds the seam like every other vertex.
     */
    private fun ringPath(ring: FloatArray, cx: Float, cy: Float, radius: Float, turn: Float, wobble: Wobble): Path =
        Path().apply {
            var k = 0
            while (k < ring.size) {
                val angle = ring[k] + turn
                val r = radius * ring[k + 1] * wobble.at(ring[k])
                val x = cx + cos(angle) * r
                val y = cy + sin(angle) * r
                if (k == 0) moveTo(x, y) else lineTo(x, y)
                k += 2
            }
            close()
        }

    /**
     * The *Wobble* knob: a smooth radial deformation of the ring, shared by every copy so the cascade still reads as
     * one shape repeated rather than a pile of unrelated ones.
     *
     * **A few low harmonics rather than a nudge per vertex**, which is what the previous build did. A per-vertex
     * nudge reads as a hand-drawn triangle and as *noise* on a sixty-four-sided circle, because its frequency is the
     * vertex spacing rather than anything about the shape; a low harmonic bends a circle into a lumpy blob and a
     * triangle into a bowed one, which is the same gesture whatever the vertex count.
     *
     * The phases are drawn whether or not [amplitude] is zero, so moving the knob changes how far the shape bends and
     * never which way — the seeded stream does not shift underneath it.
     */
    private class Wobble(private val amplitude: Float, random: Random) {
        private val phases = FloatArray(WobbleHarmonics.size) { random.nextFloat() * TwoPi }

        /** The factor the radius at [angle] is multiplied by — exactly `1` everywhere at amplitude zero. */
        fun at(angle: Float): Float {
            var sum = 0f
            for (h in WobbleHarmonics.indices) {
                sum += WobbleWeights[h] * sin(phases[h] + WobbleHarmonics[h] * angle)
            }
            return 1f + amplitude * sum
        }
    }

    private const val TwoPi = 2f * PI.toFloat()

    /** Where a ring's first vertex sits — straight up, so the shapes with a point carry it at the top. */
    private const val Top = -PI.toFloat() / 2f

    private const val CircleSides = 64
    private const val StarPoints = 5
    private const val TriangleSides = 3
    private const val HexagonSides = 6
    private const val SquareSides = 4

    /**
     * The reference star's inner radius as a share of its outer, measured off the one copy in a cascade that is never
     * occluded — and deliberately not the canonical pentagram's `0.382`. Theirs is a fatter star, and at a glance
     * that is the difference between their star and a geometer's.
     */
    private const val StarInner = 0.451f

    /** The reference rectangle's width over its height, measured the same way. */
    private const val RectangleAspect = 2f

    /** The first copy's circumradius as a share of the short side, at [DesignParams.scale] `0` and `1`. */
    private const val MinSize = 0.16f
    private const val MaxSize = 0.46f

    /** How much of the first copy's size the taper may take at [DesignParams.depth] `1` — never all of it. */
    private const val MaxTaper = 0.92f

    /** The cascade's run as a share of the frame's extent along its heading, at the two ends of the seed's draw. */
    private const val MinRun = 0.55f
    private const val MaxRun = 0.80f

    /**
     * The corner radius at full [DesignParams.roundness], as a share of a copy's own circumradius — the reference's
     * rectangle, whose corners measure `0.3` of its short side, lands almost exactly here.
     */
    private const val MaxCorner = 0.3f

    /**
     * The shadow's blur radius as a share of the short side, and how dark it may go at full strength.
     *
     * Both measured off the reference, which puts its own knob on the darkness alone: at *Shadow* `54` an edge fell
     * to `0.56` of the color under it and at `100` to `0.29`, while the reach stayed at about `70px` on a 1080-wide
     * frame — `0.065` of it — at both. So the strength is an alpha and the blur is a constant.
     */
    private const val ShadowRadiusFraction = 0.065f
    private const val MaxShadowAlpha = 0.6f

    /** A byte's greatest value — what a `0..1` strength scales to when it becomes a paint's alpha. */
    private const val ChannelMax = 255

    /**
     * How far a vertex may leave its radius at full *Wobble*, as a share of that radius.
     *
     * Kept small because the reference has **no** organic knob on this design at all — its shapes are exact — so
     * every bit of this is a departure from theirs rather than a setting of theirs being reproduced. The default
     * half of it bends the outline visibly and still reads as the shape it is.
     */
    private const val MaxWobble = 0.14f

    /** The harmonics the wobble is built from, and how much of the deflection each carries (the weights sum to `1`). */
    private val WobbleHarmonics = intArrayOf(3, 5, 7)
    private val WobbleWeights = floatArrayOf(0.5f, 0.3f, 0.2f)

    /**
     * Stroke width as a share of the short side, for the stroked finish — a fill ignores it.
     *
     * **The one knob of theirs with no field here**, fixed at what their *Thickness* opens on — `5` of `1..100`,
     * which measured `13px` on a 1080-wide frame. Their slider runs from a hairline to a weight that all but fills
     * the shape, so this is a real axis given up rather than a rounding. Theirs hides it under *Fill* for the same
     * reason it is inert here.
     */
    private const val StrokeFraction = 0.0125f
}
