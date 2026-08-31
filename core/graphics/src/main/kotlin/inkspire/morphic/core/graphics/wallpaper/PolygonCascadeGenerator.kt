package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A rosette of rotating, shrinking polygon outlines cascading into the frame's center — *Polygon Cascade* (gart's
 * `arts/spirograph`, `arts/harmongraph`).
 *
 * **Not a field like the others — a single shape drawn many times.** Every other design samples a field per pixel; this
 * one draws one regular polygon over and over, each copy turned a little further and scaled a little smaller than the
 * last, so the overlapping edges weave the moiré rosette a spirograph makes. There is no noise in the base look at all:
 * the cascade is pure rotation and scale, which is what gives it the crisp, geometric register the flow designs cannot.
 *
 * **The knobs, mapped to a rosette:** [DesignParams.density] sets how many polygons stack up (a sparse star to a dense
 * weave); [DesignParams.variant] picks the *shape* — `0` a triangle, each step up adding a side; and
 * [DesignParams.irregularity] wobbles each vertex off true, from a crisp rosette at `0` to a hand-drawn one at `1`. The
 * polygons climb the palette's lighter stops as they shrink, over the darkest as a ground. Deterministic in [seed].
 *
 * [iterationCount], [sides] and [polygon] are pure and tested — a polygon whose vertices do not close, or a cascade that
 * scales past zero, is silently wrong geometry the bitmap only confirms.
 */
object PolygonCascadeGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Iterations* slider's own range. */
    private val Amount = AmountKnob.Count("Iterations", 16..60)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Wobble",
        // Named by side count, off the same bounds [sides] clamps to — a hand-written list of six would be a second
        // statement of the range, and a seventh segment silently drawing an octagon.
        variant = VariantKnob("Sides", (MinSides..MaxSides).map { it.toString() }),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground the rosette sits on
        val ramp = Palette(if (palette.size > 1) palette.colors.dropLast(1) else palette.colors)

        val shortSide = min(width, height)
        val cx = width / 2f
        val cy = height * CenterHeightFraction // a touch above center, so the rosette does not sit under the clock
        val maxRadius = shortSide / 2f * RadiusFraction
        val sides = sides(params.variant)
        val iterations = iterationCount(params.density)
        val jitterPx = params.irregularity.coerceIn(0f, 1f) * MaxJitterFraction * shortSide
        val random = Random(seed)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = StrokeFraction * shortSide
        }

        for (i in 0 until iterations) {
            val t = i.toFloat() / iterations
            val radius = maxRadius * (MinScale + (1f - MinScale) * (1f - t)) // cascade inward: large first, small last
            val rotation = i * RotateDelta
            paint.color = LinearGradientGenerator.colorAt(t, ramp) // climb the ramp as the cascade tightens
            canvas.drawPath(closedPath(polygon(sides, cx, cy, radius, rotation, jitterPx, random)), paint)
        }
        return bitmap
    }

    /** How many polygons [density] asks for — a sparse star up to a dense weave. */
    internal fun iterationCount(density: Float): Int = Amount.at(density)

    /** The polygon's side count for [variant] — `0` a triangle, each step adding a side, capped at [MaxSides]. */
    internal fun sides(variant: Int): Int = (MinSides + variant.coerceAtLeast(0)).coerceAtMost(MaxSides)

    /**
     * The vertices of a regular [sides]-gon centered at ([cx], [cy]) in pixels, radius [radius], turned by [rotation]
     * radians, each vertex nudged up to [jitterPx] off true — interleaved `x, y`, the ring **closed** (the first vertex
     * repeated last) so the stroked path has no gap at the seam.
     *
     * The jitter draws two values per vertex from [random] whether or not [jitterPx] is zero, so the seeded stream does
     * not shift as the irregularity knob moves — only the amplitude does.
     */
    internal fun polygon(
        sides: Int,
        cx: Float,
        cy: Float,
        radius: Float,
        rotation: Float,
        jitterPx: Float,
        random: Random,
    ): FloatArray {
        val out = FloatArray((sides + 1) * 2)
        val step = (2.0 * PI / sides).toFloat()
        for (k in 0..sides) {
            val angle = rotation + k % sides * step // k == sides reuses vertex 0, closing the ring
            val jx = (random.nextFloat() * 2f - 1f) * jitterPx
            val jy = (random.nextFloat() * 2f - 1f) * jitterPx
            out[k * 2] = cx + cos(angle) * radius + jx
            out[k * 2 + 1] = cy + sin(angle) * radius + jy
        }
        return out
    }

    /**
     * The interleaved *pixel* vertices as a [Path]. Unlike [Streamlines.pathOf] these need no frame scaling — the
     * cascade works in pixels so its polygons stay circular on a non-square frame — which is why this is its own builder.
     */
    private fun closedPath(points: FloatArray): Path = Path().apply {
        moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            lineTo(points[i], points[i + 1])
            i += 2
        }
    }

    private const val MinSides = 3
    private const val MaxSides = 8

    /** The angle each successive polygon is turned, in radians — small, so consecutive copies weave rather than align. */
    private const val RotateDelta = 0.22f

    /** The innermost polygon's radius as a fraction of the outermost — the cascade shrinks to this, never to nothing. */
    private const val MinScale = 0.12f

    /** The outermost polygon's radius as a fraction of half the short side — how much of the frame the rosette fills. */
    private const val RadiusFraction = 0.92f

    /** Where the rosette's center sits down the frame — a touch above the middle. */
    private const val CenterHeightFraction = 0.42f

    /** Stroke width as a fraction of the short side — a fine line, so the overlapping edges read as a weave. */
    private const val StrokeFraction = 0.0016f

    /** The most a vertex may wander at full irregularity, as a fraction of the short side. */
    private const val MaxJitterFraction = 0.03f
}
