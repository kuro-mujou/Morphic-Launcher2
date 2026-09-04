package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A few enormous soft-cornered forms laid over a dark ground, their colors mixing where they cross — *Soft Overlaps*.
 *
 * **The name means soft *shapes*, not soft edges, and reading it the other way is what the previous build did.** The
 * reference draws four flat forms whose silhouettes are crisp; what is soft about them is that they are big, round
 * and organic, and that their overlaps make new colors. The old version faded every disc to nothing at its rim, so
 * it had no silhouette anywhere and read as a bokeh blur. Driving the reference settled it beyond argument: its
 * *Mode* knob has two looks, and the fading one — **Glow** — is precisely what ours drew at every setting. Ours was
 * not a poor version of this design; it was their alternate look, standing in for the whole of it, with their
 * default unreachable. Bauhaus and Dot Grid failed the same way.
 *
 * **A shape is a closed curve through [ShapePoints] points spaced evenly around an ellipse, each pushed off its
 * radius.** That construction is what their two shape knobs give away: *Complexity* counts the points and
 * *Irregularity* pushes them, and at *Irregularity* `0` every form is an **exact ellipse** while *Complexity* does
 * nothing at all. Nothing else explains a generator that draws a squircle, an egg and a circle from one description.
 * The curve is smoothed by running quadratics through the midpoints of the point ring, so no vertex shows.
 *
 * **The knobs.** [DesignParams.density] is their *Count* (`1..10`, and `1` — one form alone on the ground — is a real
 * setting theirs offers), [DesignParams.scale] their *Radius*, [DesignParams.taper] their *Size variation*,
 * [DesignParams.irregularity] their *Position jitter*, [DesignParams.variant] their *Mode* and
 * [DesignParams.finish] their *Blend mode*. Deterministic in [render]'s seed.
 *
 * **[DesignParams.roundness] is their *Irregularity*, and it runs the other way on purpose.** Theirs counts how far a
 * form departs from an ellipse; this field counts how *round* a design's shapes are, and `0` means sharp everywhere
 * it is read. So `roundness` `1` is their `0` — the exact ellipse — and `roundness` `0` is their `100`, a hooked
 * form with deep concavities and tight curvature. Matching the name and inverting the direction was the alternative,
 * and it would make one knob in the studio read backwards against every other.
 *
 * **Three of theirs are deliberately not knobs here.**
 * - Their *Complexity* has no field left. Both fields the shape family owns are spent — [DesignParams.roundness] on
 *   the deformation, which is the identity, and [DesignParams.irregularity] on the scatter, which is the only field a
 *   placement knob can honestly take. It is fixed at their default; see [ShapePoints].
 * - Their *Blur* is a grade over the whole composed picture rather than anything about a shape, so it belongs to the
 *   **Filters** stage — where this studio already has one. Building it per-design would be a second control over one
 *   effect, which is the argument that sent Ribbed Glass's *Vibrancy* there.
 * - Their *Distance from centre* pushes every form radially outward to hollow the middle. It is a composition knob
 *   with a small range (`0..50`) and no field is spare.
 *
 * [blobCount], [radii] and [blendOf] are pure and tested: a shape whose radii leave their bounds, or a blend that
 * silently falls back to painting over, is wrong in a way a bitmap only confirms after the fact.
 */
object SoftOverlapsGenerator : Generator {

    /**
     * How a form is inked — the reference's *Mode*.
     *
     * **Fill leads because it is theirs' default and the design's**, and because the other one is what this file used
     * to draw exclusively. A glow is the same shape with its interior spent on a falloff instead of a color.
     */
    internal enum class OverlapLook(val label: String) {
        FILL("Fill"),
        GLOW("Glow"),
    }

    /**
     * How overlapping forms combine — the reference's *Blend mode*, four of its nine.
     *
     * **Screen leads because theirs opens on it**, and that is most of why their overlaps glow rather than stack:
     * index `0` is a design's default, so the order here is the studio's rather than a compositor's.
     *
     * **Four rather than nine, because a segmented control divides its width evenly.** Nine options on a phone is
     * about a hundred pixels each — the same arithmetic that kept the cascade's shapes and modes on two controls.
     * These four are one from each family theirs offers: paint over, lighten, darken, and contrast. *Lighten*,
     * *Darken*, *Plus*, *Color Burn* and *Color Dodge* are near neighbours of ones kept.
     */
    internal enum class OverlapBlend(val label: String, val mode: PorterDuff.Mode?) {
        SCREEN("Screen", PorterDuff.Mode.SCREEN),
        NORMAL("Normal", null),
        MULTIPLY("Multiply", PorterDuff.Mode.MULTIPLY),
        OVERLAY("Overlay", PorterDuff.Mode.OVERLAY),
    }

    /** What [DesignParams.density] resolves to — the forms, over the range the reference itself offers. */
    private val Amount = AmountKnob.Count("Count", 1..10)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Size",
        taper = "Size variation",
        irregularity = "Scatter",
        roundness = "Roundness",
        variant = VariantKnob("Mode", OverlapLook.entries.map { it.label }),
        finish = VariantKnob("Blend", OverlapBlend.entries.map { it.label }),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // the ground is the darkest stop, as theirs is
        val tones = RampTones.belowGround(palette)
        if (tones.isEmpty()) return bitmap // a single-stop palette is all ground

        val count = blobCount(params.density)
        val centers = PointScatter.gridJitter(count, params.irregularity, seed)
        val shortSide = min(width, height)
        val look = lookOf(params.variant)
        val blend = blendOf(params.finish)
        // Salted apart from the placement stream, so the scatter knob moves forms without reshaping or resizing them.
        val random = Random(seed xor ShapeSalt)

        val baseRadius = shortSide * (MinRadius + (MaxRadius - MinRadius) * params.scale.coerceIn(0f, 1f))
        val spread = params.taper.coerceIn(0f, 1f)
        // Their *Irregularity*, read off the round end — see the class note.
        val deform = (1f - params.roundness.coerceIn(0f, 1f)) * MaxDeform

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            blend.mode?.let { xfermode = PorterDuffXfermode(it) }
            // A glow has no silhouette to speak of, so its edge is softened rather than drawn — the falloff below
            // still leaves a rim wherever the form is narrower than the circle its gradient is measured on.
            if (look == OverlapLook.GLOW) {
                maskFilter = BlurMaskFilter(GlowBlurFraction * shortSide, BlurMaskFilter.Blur.NORMAL)
            }
        }

        for (i in 0 until count) {
            val cx = centers[i * 2] * width
            val cy = centers[i * 2 + 1] * height
            // Each form is smaller than [baseRadius] by up to the spread, never larger — which is the direction
            // theirs moves: winding *Size variation* up shrinks most of the forms rather than spreading them either way.
            val radius = baseRadius * (1f - spread * random.nextFloat())
            val aspect = MinAspect + random.nextFloat() * (MaxAspect - MinAspect)
            val rx = radius * aspect
            val ry = radius / aspect
            val tone = tones[i % tones.size]
            when (look) {
                OverlapLook.FILL -> {
                    paint.shader = null
                    paint.color = tone
                }
                // Measured on the form's own extent, so a wide ellipse fades over its width rather than in a circle.
                OverlapLook.GLOW -> paint.shader = RadialGradient(
                    cx, cy, max(rx, ry),
                    intArrayOf(tone, tone and RgbMask),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            // After the color, which resets it — and it modulates the shader too, so one line covers both looks.
            paint.alpha = (FormAlpha * ChannelMax).roundToInt()
            canvas.drawPath(blobPath(cx, cy, rx, ry, radii(ShapePoints, deform, random)), paint)
        }
        return bitmap
    }

    /** How many forms [density] asks for — one alone on the ground, up to a frame full of them. */
    internal fun blobCount(density: Float): Int = Amount.at(density)

    /** The look [variant] picks, clamped to the two rather than wrapping. */
    internal fun lookOf(variant: Int): OverlapLook =
        OverlapLook.entries[variant.coerceIn(OverlapLook.entries.indices)]

    /** The blend [finish] picks, clamped the same way. */
    internal fun blendOf(finish: Int): OverlapBlend =
        OverlapBlend.entries[finish.coerceIn(OverlapBlend.entries.indices)]

    /**
     * One radius factor per point of a form's ring, each `1 ± deform` — `1` exactly when [deform] is `0`, which is
     * the ellipse the reference draws at its *Irregularity* `0`.
     *
     * Two values are drawn from [random] per point whether or not [deform] is zero, so moving the knob changes how
     * far the ring departs and never which way — the seeded stream does not shift underneath it.
     */
    internal fun radii(points: Int, deform: Float, random: Random): FloatArray =
        FloatArray(points) { 1f + (random.nextFloat() * 2f - 1f) * deform.coerceIn(0f, 1f) }

    /**
     * A closed, smooth curve **through** the ring of [factors] around the ellipse at ([cx], [cy]) with radii [rx],
     * [ry].
     *
     * **Through the points, not past them, and the difference is the whole measurement.** The obvious smoothing —
     * quadratics from midpoint to midpoint with each ring point as the control — is tangent to the ring rather than
     * on it, so it draws a rounded polygon *inscribed* in the ring: at [factors] all `1` it gives a rounded octagon
     * where the reference gives an **exact ellipse**, and it quietly halves what the deformation knob is worth. This
     * is a Catmull-Rom spline written as cubics, whose defining property is that it passes through every point it is
     * given; over eight points on an ellipse it is within a fraction of a percent of one.
     */
    private fun blobPath(cx: Float, cy: Float, rx: Float, ry: Float, factors: FloatArray): Path {
        val n = factors.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (k in 0 until n) {
            val angle = k * (2f * PI.toFloat() / n)
            xs[k] = cx + cos(angle) * rx * factors[k]
            ys[k] = cy + sin(angle) * ry * factors[k]
        }
        return Path().apply {
            moveTo(xs[0], ys[0])
            for (k in 0 until n) {
                val before = (k - 1 + n) % n
                val next = (k + 1) % n
                val after = (k + 2) % n
                // A Catmull-Rom tangent at a point is the chord across its neighbours, a sixth of it per handle.
                cubicTo(
                    xs[k] + (xs[next] - xs[before]) / CatmullRomTension,
                    ys[k] + (ys[next] - ys[before]) / CatmullRomTension,
                    xs[next] - (xs[after] - xs[k]) / CatmullRomTension,
                    ys[next] - (ys[after] - ys[k]) / CatmullRomTension,
                    xs[next],
                    ys[next],
                )
            }
            close()
        }
    }

    /**
     * How many points a form's ring is built from — the reference's *Complexity*, fixed at its default of `8`.
     *
     * It is a knob there (`3..16`, where `3` draws smooth eggs and `16` many-lobed forms) and a constant here because
     * the shape family's two fields are both spent: `roundness` on the deformation, which is the identity, and
     * `irregularity` on the scatter, which is the only field a placement knob can honestly take. A real axis given
     * up, not a rounding.
     */
    private const val ShapePoints = 8

    /**
     * The divisor turning a Catmull-Rom chord into a Bezier handle — `6` is the uniform spline, and moving it
     * tightens or slackens every curve at once.
     */
    private const val CatmullRomTension = 6f

    /**
     * A form's radius as a share of the short side, at [DesignParams.scale] `0` and `1`.
     *
     * Fitted to the reference's *Radius* `60..400`, whose own unit never resolved: it re-rolls the shape as it moves,
     * so two settings are not the same form and the ratio of their bounding boxes says nothing. What is measured is
     * that its floor draws a form about `0.29` of the frame's width across and its default about `0.88`.
     */
    private const val MinRadius = 0.15f
    private const val MaxRadius = 0.70f

    /** How far from circular a form may be drawn, as a ratio applied one way to each axis. */
    private const val MinAspect = 0.8f
    private const val MaxAspect = 1.3f

    /** How far a ring point may leave its radius at [DesignParams.roundness] `0`, as a share of that radius. */
    private const val MaxDeform = 0.5f

    /** The opacity a form is laid at, so overlaps mix rather than paint over — most visible under *Normal*. */
    private const val FormAlpha = 0.85f

    /** How far a glow's edge is softened, as a share of the short side. */
    private const val GlowBlurFraction = 0.03f

    /** The low 24 bits of an ARGB color — its RGB, alpha masked off, which is a glow's transparent rim. */
    private const val RgbMask = 0x00FFFFFF

    /** A byte's greatest value — what a `0..1` opacity scales to when it becomes a paint's alpha. */
    private const val ChannelMax = 255

    /** Keeps shape and size independent of placement, so the scatter knob moves forms without redrawing them. */
    private const val ShapeSalt = 0x85EBCA6BL
}
