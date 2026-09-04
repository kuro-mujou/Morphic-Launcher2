package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * A fan of long rounded bars, each carrying a gradient down its own length — *Rounded Tiles*.
 *
 * **Built beside [TruchetGenerator] rather than over it.** That design tiles the frame with quarter-arcs meeting each
 * cell edge at its midpoint, so they *join* into a maze; this lays separate capsules side by side and turns each one
 * a little further than the last, so they radiate. The two share a name in the reference's catalogue and nothing
 * else — the Truchet is a good picture that is simply not this one, which is why it stays where it is. Same call as
 * the Mondrian beside Bauhaus and the Halftone beside Dot Grid.
 *
 * **A bar is a capsule, and their *Margin* is what proves it.** Driving that knob to its top shortens every bar until
 * it is a **circle** — which happens only if the cap radius is half the thickness, so the shape is a stadium rather
 * than a rounded rectangle with a radius of its own. [DesignParams.roundness] is that length here: at `1` every bar
 * has shortened to its own cap.
 *
 * **[DesignParams.rotation] is their *Direction*, and it is the design.** At `0` every bar sits parallel and the
 * picture is a stack of stripes, which is how theirs opens and why its name looks wrong until the knob is moved;
 * climbing it gives bar `i` its own angle, so the set opens into a fan and the rounded caps swing into the frame.
 *
 * **The fan's *overall* angle is seeded rather than exposed.** Theirs has a second orientation knob for it and there
 * is one field in that family, so the identity takes it and the aim comes from [render]'s seed — which is what
 * [PolygonCascadeGenerator] does with its heading, and it means a shuffle re-aims the picture.
 *
 * The rest: [DesignParams.density] is their *Count* (`1..10`, and the bars always fill the frame, so it sets their
 * thickness too), [DesignParams.scale] their *Spacing* — **signed**, so its low end overlaps the bars rather than
 * merely closing the gaps — [DesignParams.depth] their *Inner shadow* read as an amount, and [DesignParams.finish]
 * their *Blend mode*, which is visible only where bars overlap and so is a knob about [DesignParams.scale]'s low end.
 *
 * [barCount], [lanes] and [blendOf] are pure and tested: a lane set that does not tile the frame, or a blend that
 * silently falls back to painting over, is wrong in a way a bitmap only confirms after the fact.
 */
object RoundedTilesGenerator : Generator {

    /**
     * How overlapping bars combine — the reference's *Blend mode*, which here is **two** options where Soft Overlaps'
     * is nine. The name recurs across their designs and does not mean the same thing twice.
     *
     * **Plus leads because theirs opens on it.** It only shows where bars overlap, so at a positive spacing the two
     * are nearly one picture and at a negative one they are two.
     */
    internal enum class TileBlend(val label: String, val mode: PorterDuff.Mode?) {
        PLUS("Plus", PorterDuff.Mode.ADD),
        NORMAL("Normal", null),
    }

    /** What [DesignParams.density] resolves to — the bars, over the range the reference itself offers. */
    private val Amount = AmountKnob.Count("Count", 1..10)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Spacing",
        irregularity = "Fan",
        roundness = "Length",
        rotation = "Angle",
        depth = "Inner shadow",
        finish = VariantKnob("Blend", TileBlend.entries.map { it.label }),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // the ground is the darkest stop, as theirs is
        val tones = RampTones.belowGround(palette)
        if (tones.isEmpty()) return bitmap // a single-stop palette is all ground

        val count = barCount(params.density)
        val blend = blendOf(params.finish)
        val random = Random(seed)
        // Their *Rotation*: the whole rank's aim. A half turn covers every aim a set of bars has, since a bar turned
        // 180° is the same bar — which is also what their own `0..100 → 0..180°` measured out to.
        val aim = params.rotation.coerceIn(0f, 1f) * PI.toFloat()
        // **Both extents are measured against the aim, not against the diagonal.** The rank has to fill the frame
        // whichever way it points, and the frame is not square: laying the lanes across its diagonal instead spreads
        // `Count` bars over `2630px` of a `1080px`-wide frame, so a vertical rank shows two and a half of them, each
        // three times too fat. `across` is the frame's extent along the lane axis and `along` its extent down a bar.
        val across = abs(sin(aim)) * width + abs(cos(aim)) * height
        val along = abs(cos(aim)) * width + abs(sin(aim)) * height
        // What the shuffle moves, now that the aim is a knob: which part of the rank the frame happens to show.
        val phase = random.nextFloat() - 0.5f
        val lanes = lanes(count)
        val pitch = across / count
        // Their *Spacing* is signed, so its low end **overlaps** the bars rather than merely closing the gaps — which
        // is the only setting where the blend knob has anything to combine.
        val thickness = pitch * (MaxShare - (MaxShare - MinShare) * params.scale.coerceIn(0f, 1f))
        // **Squared, so the shipped look sits at the knob's middle.** Their *Margin* opens at `20` of `100` — a fifth
        // off — where a straight reading would take half the length off at the default and leave the bars too stubby
        // to cross once the fan opens.
        val shorten = params.roundness.coerceIn(0f, 1f).let { it * it } * LengthSpan
        // **Floored at the thickness, which is what makes the knob's top a circle.** A capsule shorter than it is
        // wide is not a shorter capsule — it is one lying the other way, which is what a plain fraction of the frame
        // draws here and is not what the reference's own top does.
        val length = max(thickness, along * LengthBase * (1f - shorten))
        // **Cubed, and the curve is doing real work.** Theirs opens the fan at `0` — a plain parallel rank — where
        // every fraction here opens at `0.5`, so a straight reading puts a hard fan at the default: the pencil's
        // vertex lands within a frame's height and every bar piles into one bright knot, which reads as a peacock
        // rather than as a rank of beams. Cubed, the default splays them a few degrees apart and the knob's top still
        // crosses the rank right over itself.
        val fan = params.irregularity.coerceIn(0f, 1f).let { it * it * it } * MaxFan

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            blend.mode?.let { xfermode = PorterDuffXfermode(it) }
        }
        val shadow = innerShadowPaint(params.depth, thickness)

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(Math.toDegrees(aim.toDouble()).toFloat())
        for (i in 0 until count) {
            canvas.save()
            // **Each bar turns about its own centre, and the centres do not move** — which is the whole of what makes
            // this a fan rather than a pinwheel. Turning the frame per bar instead swings the *centre* round the
            // middle as well as the bar, so the rank collapses into a splayed arc that never crosses itself, and the
            // blend knob is left with nothing to combine. Isolated on three bars, theirs plainly keeps every centre
            // on the lane line and tilts each one in place.
            canvas.translate(0f, (lanes[i] + phase / count) * across)
            canvas.rotate(Math.toDegrees((fan * lanes[i] * 2f).toDouble()).toFloat())
            val bar = RectF(-length / 2f, -thickness / 2f, length / 2f, thickness / 2f)
            // The cap radius is half the thickness — a stadium, which is what makes a fully shortened bar a circle.
            val cap = thickness / 2f
            paint.shader = LinearGradient(
                bar.left, bar.top, bar.right, bar.top,
                tones[i % tones.size], tones[(i + 1) % tones.size],
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(bar, cap, cap, paint)
            shadow?.let {
                // **Clipped to the bar, which is what makes the shadow inner.** A stroke sits centred on the outline,
                // so half of any width falls outside; clipping to the bar's own path throws that half away and leaves
                // the blurred remainder decaying inward from the edge — including around the caps, since the clip is
                // the capsule rather than its two long sides.
                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(bar, cap, cap, Path.Direction.CW) })
                canvas.drawRoundRect(bar, cap, cap, it)
                canvas.restore()
            }
            canvas.restore()
        }
        canvas.restore()
        return bitmap
    }

    /** How many bars [density] asks for — one across the whole frame, up to a fine rank of them. */
    internal fun barCount(density: Float): Int = Amount.at(density)

    /** The blend [finish] picks, clamped to the two rather than wrapping. */
    internal fun blendOf(finish: Int): TileBlend =
        TileBlend.entries[finish.coerceIn(TileBlend.entries.indices)]

    /**
     * Where each of [count] bars sits across the fan, as a signed share of the reach — evenly spaced and **centred on
     * zero**, so the set straddles the frame's middle whatever the count.
     *
     * Centred rather than counted from an edge because the middle is what the fan turns about: a set laid out from
     * one edge would swing off the frame as the fan opens, where a centred one opens symmetrically. A single bar sits
     * at exactly `0`, which is why `Count` `1` draws one bar across the middle of the frame rather than a sliver at
     * its top.
     */
    internal fun lanes(count: Int): FloatArray {
        val n = count.coerceAtLeast(1)
        return FloatArray(n) { (it - (n - 1) / 2f) / n }
    }

    /**
     * The brush a bar's inner shadow is drawn with at [depth], or `null` where there is none.
     *
     * **A blurred stroke clipped to the bar, not a hard one — and the difference is the whole look.** Theirs is a
     * smooth inset: profiled across a bar, the color falls to `×0.72` about eight pixels in and climbs back to `×1.0`
     * by fifty, on a bar `254px` thick. That is a gradient roughly a **fifth of the thickness** deep, easing rather
     * than stepping — extrapolated to the edge itself it is about `×0.59`. A flat stroke at a fixed alpha, which is
     * what this drew first, reads as a dark outline drawn round every bar instead, and no amount of tuning its width
     * fixes that: the fault is the hard edge, not the size.
     *
     * **The blur has to be wider than the stroke, or there is no gradient at all.** A stroke thick against its blur
     * keeps a solid core, and clipped to the bar that core is a flat dark band with a soft edge on one side — which
     * measured here as a plateau at `×0.2` right across the middle of the bar, darker and deader than the thing it
     * replaced. Thin against a wide blur leaves only the tail, which is the ease.
     */
    private fun innerShadowPaint(depth: Float, thickness: Float): Paint? {
        val strength = depth.coerceIn(0f, 1f)
        if (strength == 0f || thickness <= 0f) return null
        val inset = thickness * InnerShadowDepth
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            alpha = (strength * MaxShadowAlpha * ChannelMax).toInt()
            strokeWidth = inset * InnerShadowStroke
            maskFilter = BlurMaskFilter(inset * InnerShadowSoftness, BlurMaskFilter.Blur.NORMAL)
        }
    }

    /**
     * A bar's thickness as a share of its lane, at [DesignParams.scale] `0` and `1`.
     *
     * The top is **over `1`** on purpose: theirs' *Spacing* is signed and its negative end overlaps the bars into one
     * another, which is the only place its blend knob shows. Ours' overlap is gentler than theirs, which runs all the
     * way to a frame with no ground left in it. The pair straddles `0.7` at the knob's middle, which is what theirs
     * measures at its own default — bars a little over twice the width of the gaps between them.
     */
    private const val MaxShare = 1.25f
    private const val MinShare = 0.15f

    /**
     * A bar's full length as a multiple of the frame's extent down it, and how much of that the length knob may take
     * off before the thickness floor catches it.
     *
     * **Over `1` because theirs overruns the frame at its default**: their bars leave the picture at both ends and
     * read as beams, where a bar ending inside the frame reads as an object lying on it. Their *Margin* opens at a
     * fifth off and the bars still cross the whole frame, so the length it is taking a fifth off must be longer than
     * the frame is.
     */
    private const val LengthBase = 1.4f
    private const val LengthSpan = 0.97f

    /**
     * The furthest the outermost bar is turned from the rank's aim, in radians.
     *
     * A quarter turn is as far as the set opens before the outermost bars are perpendicular to the middle one, which
     * is the point past which a fan stops reading as one. The lane offset is doubled where this is used, since lanes
     * run to `±0.5` and it is the *outermost* bar this bounds.
     */
    private const val MaxFan = PI.toFloat() / 2f

    /**
     * The inner shadow's reach as a share of a bar's thickness, the stroke and blur that shape it, and how dark it
     * goes at full [DesignParams.depth].
     *
     * **Tuned against a profile of theirs rather than by eye**, and the two now agree within a few percent the whole
     * way in — `×0.72` at three hundredths of the thickness, `×0.87` at a tenth, back to `×1.00` by a fifth:
     *
     * | share of thickness | theirs | ours |
     * |---|---|---|
     * | `0.03` | `0.72` | `0.72` |
     * | `0.08` | `0.85` | `0.83` |
     * | `0.13` | `0.95` | `0.93` |
     * | `0.20` | `1.00` | `0.99` |
     *
     * **[InnerShadowSoftness] is small because a `BlurMaskFilter` reaches about four times its radius**, which is the
     * one number here that is not obvious and the reason two earlier attempts washed the whole bar: at a radius of a
     * tenth of the thickness the darkening had still not cleared by the bar's centre.
     */
    private const val InnerShadowDepth = 0.2f
    private const val InnerShadowStroke = 0.3f
    private const val InnerShadowSoftness = 0.2f
    private const val MaxShadowAlpha = 0.45f

    /** A byte's greatest value — what a `0..1` strength scales to when it becomes a paint's alpha. */
    private const val ChannelMax = 255
}
