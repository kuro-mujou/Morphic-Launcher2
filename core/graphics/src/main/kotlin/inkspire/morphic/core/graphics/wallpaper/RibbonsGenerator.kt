package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.random.Random

/**
 * One bundle of fine curves sweeping the frame, converging almost to a point at one end and fanning wide at the other
 * — *Neon Ribbons*.
 *
 * **A single family of curves, not a field of them, and that is the whole design.** Every other line design here
 * scatters strokes across the frame ([FlowLinesGenerator] combs the whole of it); this draws *one* gesture and leaves
 * the rest of the frame empty. The negative space is the composition — three quarters of the picture is ground, and
 * the bundle reads as a single swept object because the lines are nested rather than independent.
 *
 * **Nested because they share a spine.** All of them are the same cubic, offset perpendicular by a spread that
 * *grows* along the curve: near zero where they start, wide where they end. That one asymmetry is what makes the
 * bundle look like a fan of light rather than a stack of parallel lines, and it is why [Spine] carries two spreads
 * instead of a thickness.
 *
 * **[DesignParams.irregularity] is the splay**, and it is the axis a bundle has instead of noise. At `0` every line is
 * a pure translate of its neighbour and the fan is perfectly ruled; climbing it shears the *interior* control points in
 * opposite directions, so the lines stop being parallel and the bundle twists through itself. The ends stay anchored
 * either way — a splay that moved them too would just be a different spread.
 *
 * **[DesignParams.scale] and [DesignParams.variant] are the two spreads, re-cut.** The reference exposes them as a
 * *Start area* and an *End area* percentage, one per end. The same square of possibilities is reached here by asking
 * the two questions a person actually has: **how wide** is the bundle ([DesignParams.scale]), and **which end is
 * tight** ([DesignParams.variant] — a fan that converges at one end, or a weave open at both). Two ends is the
 * implementation; wide-and-fanned is the intent.
 *
 * **The glow is drawn on the ground, not around each stroke.** Measured off the reference, its lines are hard-edged —
 * the pixels step straight from ground to line with no falloff — while the *ground* brightens along a wide ridge that
 * follows the bundle, roughly tripling in luminance at its center. So this lays a few progressively wider, barely
 * opaque copies of each path down first and draws the crisp lines over them: the copies accumulate where lines run
 * close together, which is exactly where a real bundle of light would be brightest. It is deliberately **not** the
 * studio's Vignette filter, which darkens the corners of any picture and knows nothing about where the bundle is.
 *
 * [lineControls] is pure and tested: the offsets have to stay *ordered* — line `i` inside line `i + 1` at every control
 * point — or the bundle self-intersects into a scribble, and it does that quietly, one seed in a handful.
 */
object RibbonsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Lines* slider's own range. */
    private val Amount = AmountKnob.Count("Lines", 6..24)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Spread",
        irregularity = "Splay",
        variant = VariantKnob("Shape", listOf("Fan", "Weave")),
    )

    /**
     * The bundle's backbone: one cubic in unit coordinates, plus how far the lines are spread at each of its ends.
     *
     * @property xs the four control points' x, running off both edges so the bundle enters and leaves the frame
     *   rather than starting inside it.
     * @property ys their y — an S, with each end overshooting so the curve arcs before it settles.
     * @property startSpread how far apart the lines sit where they begin, as a fraction of the frame's height.
     * @property endSpread the same where they end. **Larger, and how much larger is the design's shape**: a fan closes
     *   its start to almost nothing against this, a weave keeps it nearly as open.
     */
    internal class Spine(
        val xs: FloatArray,
        val ys: FloatArray,
        val startSpread: Float,
        val endSpread: Float,
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground

        val count = lineCount(params.density)
        val spine = spine(seed, params.scale, params.variant)
        val splay = params.irregularity.coerceIn(0f, 1f)
        // The stops the lines are drawn from, swept across the bundle so a full palette reads as one gradient of
        // light rather than as a set of differently-colored lines. Asked of `StopContrast` rather than taken as
        // "everything but the ground": the stop *next* to the ground is a tone away from it, and lines drawn in it
        // fade out instead of reading as the dark end of the sweep.
        val ramp = Palette(StopContrast.readableAgainst(palette.size - 1, palette.size).map { palette.colorAt(it) })

        val shortSide = min(width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Every line's path, built once: the glow lays all of them down before any crisp line is drawn, so a later
        // line's halo cannot wash out an earlier line's stroke.
        val paths = List(count) { i ->
            val c = lineControls(i, count, spine, splay)
            val px = FloatArray(Controls) { c[it * 2] * width }
            val py = FloatArray(Controls) { c[it * 2 + 1] * height }
            Path().apply {
                moveTo(px[0], py[0])
                cubicTo(px[1], py[1], px[2], py[2], px[LastControl], py[LastControl])
            }
        }
        val colors = IntArray(count) {
            LinearGradientGenerator.colorAt(if (count <= 1) 0f else it.toFloat() / (count - 1), ramp)
        }

        for ((widthFraction, alpha) in Halos) {
            paint.strokeWidth = widthFraction * shortSide
            for (i in 0 until count) {
                paint.color = colors[i] and RgbMask or (alpha shl AlphaShift)
                canvas.drawPath(paths[i], paint)
            }
        }

        paint.strokeWidth = StrokeFraction * shortSide
        for (i in 0 until count) {
            paint.color = colors[i]
            canvas.drawPath(paths[i], paint)
        }
        return bitmap
    }

    /** How many lines [density] asks for — a loose handful up to a dense sheaf. */
    internal fun lineCount(density: Float): Int = Amount.at(density)

    /**
     * One line's cubic, as interleaved `x, y` control points — four of them, eight floats.
     *
     * The line's place in the bundle is `-0.5` at one edge to `+0.5` at the other, which is what the spread is
     * multiplied by; the middle line sits exactly on the spine. **The y offset grows control point by control point**
     * from [Spine.startSpread] to [Spine.endSpread], which is the fan, and it is monotonic in [index] at every one of
     * them, which is what keeps the lines nested.
     *
     * [splay] widens the bundle at one interior control point and narrows it at the other, scaled by the same
     * place-in-the-bundle — so the lines stop being translates of each other and the bundle twists through its own
     * length. **It acts across the sweep, not along it**: moving a control point *along* the curve only changes how
     * fast the line travels it and leaves the drawn shape almost exactly where it was, which is a knob that appears to
     * do nothing. The ends are deliberately left alone — widening those would open the fan, which is the spread's job.
     *
     * [MaxSplay] stays under the narrowest spread it is subtracted from, so the lines stay ordered even at full splay:
     * the bundle twists without pinching to a point and crossing itself.
     */
    internal fun lineControls(index: Int, count: Int, spine: Spine, splay: Float): FloatArray {
        val place = if (count <= 1) 0f else index.toFloat() / (count - 1) - 0.5f
        val out = FloatArray(8)
        for (k in 0 until Controls) {
            val along = k.toFloat() / (Controls - 1)
            val spread = spine.startSpread + (spine.endSpread - spine.startSpread) * along
            out[k * 2] = spine.xs[k]
            out[k * 2 + 1] = spine.ys[k] + place * (spread + splay * MaxSplay * Splay[k])
        }
        return out
    }

    /**
     * The bundle's backbone for [seed] — an S sweeping the frame's width, its ends overshooting so the curve arcs into
     * and out of the frame instead of running straight off it.
     *
     * Mirrored on either axis by the seed, which is all the variety this design's shape needs: the *gesture* is the
     * design, so re-rolling it into an unrecognizably different curve would make the shuffle read as a different
     * wallpaper rather than another take on this one.
     */
    internal fun spine(seed: Long, scale: Float, variant: Int): Spine {
        val random = Random(seed)
        val start = StartBand.first + random.nextFloat() * (StartBand.second - StartBand.first)
        val end = EndBand.first + random.nextFloat() * (EndBand.second - EndBand.first)
        val overshoot = OvershootBand.first + random.nextFloat() * (OvershootBand.second - OvershootBand.first)

        val xs = floatArrayOf(-Bleed, InnerX, 1f - InnerX, 1f + Bleed)
        val ys = floatArrayOf(start, start - overshoot, end + overshoot, end)
        if (random.nextBoolean()) for (k in xs.indices) xs[k] = 1f - xs[k] // sweep the other way across
        if (random.nextBoolean()) for (k in ys.indices) ys[k] = 1f - ys[k] // and rise instead of fall

        // How wide the bundle is at its open end, and how far the other end closes: a fan pinches to almost nothing,
        // a weave stays open and fills the frame.
        val open = MinSpread + scale.coerceIn(0f, 1f) * (MaxSpread - MinSpread)
        val closed = open * if (variant == VariantWeave) WeaveEndRatio else FanEndRatio
        return Spine(xs, ys, closed, open)
    }

    /** Which way each control point is pushed by the splay: the two interior ones, oppositely; the ends, not at all. */
    private val Splay = floatArrayOf(0f, 1f, -1f, 0f)

    /** Control points in a cubic — the move-to plus the three [Path.cubicTo] takes. */
    private const val Controls = 4

    /** The last of them: where the line ends, and the wide end of the fan. */
    private const val LastControl = Controls - 1

    /** How far past the frame's sides the bundle's ends sit, so it enters and leaves rather than beginning on screen. */
    private const val Bleed = 0.06f

    /** Where the two interior control points sit across the frame — inside the bleed, so the S turns on screen. */
    private const val InnerX = 0.36f

    /** Where the spine begins and ends vertically, and how far each end overshoots before settling. */
    private val StartBand = 0.14f to 0.34f
    private val EndBand = 0.62f to 0.84f
    private val OvershootBand = 0.16f to 0.30f

    /** [DesignParams.variant] selecting the weave — open at both ends — over the default converging fan. */
    private const val VariantWeave = 1

    /** How far apart the lines sit at the bundle's open end, as a fraction of the height, across the spread knob. */
    private const val MinSpread = 0.24f
    private const val MaxSpread = 0.95f

    /** What the *other* end measures against the open one: nearly closed for a fan, nearly as open for a weave. */
    private const val FanEndRatio = 0.08f
    private const val WeaveEndRatio = 0.72f

    /**
     * The halo: stroke width as a fraction of the short side, against the alpha it is laid down at.
     *
     * Widening and fading, so the falloff is smooth rather than a visible band, and each is faint enough that one line
     * barely lifts the ground — the brightness comes from *many* of them overlapping, which is what puts the light
     * where the bundle is densest instead of spreading it evenly.
     *
     * **The alphas are this low because fifteen lines times three passes is forty-five overlapping strokes**, and alpha
     * compounds: values that look plausible for a single stroke lifted the ground to three quarters of the line's own
     * brightness and turned the bundle into a milky smear. They are set against the reference measured directly — its
     * ground peaks at roughly three times its own base, and around a third of its line brightness.
     */
    private val Halos = listOf(0.09f to 0x06, 0.19f to 0x04, 0.38f to 0x02)

    /** The low 24 bits of an ARGB color — its RGB, alpha masked off, so a halo can restate the alpha. */
    private const val RgbMask = 0x00FFFFFF

    /** Where the alpha byte sits in a packed ARGB color. */
    private const val AlphaShift = 24

    /**
     * How much the splay opens one interior control point and closes the other, as a fraction of the height.
     *
     * Bounded by the *smaller* of the two interior spreads (the second, at `0.447` of the way up the ramp), because
     * this is subtracted there — take more and that control point's lines meet, and the bundle pinches to a waist and
     * crosses itself on the far side of it.
     */
    private const val MaxSplay = 0.22f

    /** A hairline: fine enough to read as drawn light rather than as a painted band. */
    private const val StrokeFraction = 0.0026f
}
