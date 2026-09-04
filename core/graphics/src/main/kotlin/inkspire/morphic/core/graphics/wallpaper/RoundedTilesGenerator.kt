package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
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
import kotlin.math.hypot
import kotlin.math.max
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
        roundness = "Length",
        rotation = "Fan",
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
        // Their *Rotation*: the fan's aim, seeded because their *Direction* took the field. A half turn covers every
        // aim a set of bars has — a bar turned 180° is the same bar — so the draw needs no more than that.
        val aim = random.nextFloat() * PI.toFloat()
        // Long enough that a bar still crosses the frame when the fan has swung it onto the diagonal.
        val reach = hypot(width.toFloat(), height.toFloat())
        val lanes = lanes(count)
        val pitch = reach / count
        // Their *Spacing* is signed, so its low end **overlaps** the bars rather than merely closing the gaps — which
        // is the only setting where the blend knob has anything to combine.
        val thickness = pitch * (MaxShare - (MaxShare - MinShare) * params.scale.coerceIn(0f, 1f))
        // **Floored at the thickness, which is what makes the knob's top a circle.** A capsule shorter than it is
        // wide is not a shorter capsule — it is one lying the other way, which is what a plain fraction of the frame
        // draws here and is not what the reference's own top does.
        val length = max(thickness, reach * (1f - LengthSpan * params.roundness.coerceIn(0f, 1f)))
        val fan = params.rotation.coerceIn(0f, 1f) * MaxFan

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            blend.mode?.let { xfermode = PorterDuffXfermode(it) }
        }
        val shadow = innerShadowPaint(params.depth, thickness)

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        for (i in 0 until count) {
            canvas.save()
            // Each bar turned a little further than the last is the whole fan; at `fan` zero they stay parallel.
            canvas.rotate(Math.toDegrees((aim + fan * lanes[i]).toDouble()).toFloat())
            val bar = RectF(-length / 2f, lanes[i] * reach - thickness / 2f, length / 2f, lanes[i] * reach + thickness / 2f)
            // The cap radius is half the thickness — a stadium, which is what makes a fully shortened bar a circle.
            val cap = thickness / 2f
            paint.shader = LinearGradient(
                bar.left, bar.top, bar.right, bar.top,
                tones[i % tones.size], tones[(i + 1) % tones.size],
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(bar, cap, cap, paint)
            shadow?.let {
                it.strokeWidth = cap * InnerShadowWidth
                // **Clipped to the bar, which is what makes the shadow inner.** A stroke sits centred on the outline,
                // so half of any width spills outside and reads as a dark halo around every bar — the opposite of the
                // inset theirs draws. Clipping to the bar's own path throws that half away.
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
     * **Stroked *inside* the bar rather than behind it**, which is what "inner" means and what theirs draws: a dark
     * inset just within each edge, leaving the middle of the bar its own color. The stroke is clipped to the bar at
     * the call site, without which half its width spills outside and reads as a halo around every bar.
     */
    private fun innerShadowPaint(depth: Float, thickness: Float): Paint? {
        val strength = depth.coerceIn(0f, 1f)
        if (strength == 0f || thickness <= 0f) return null
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.BLACK
            alpha = (strength * MaxShadowAlpha * ChannelMax).toInt()
        }
    }

    /**
     * A bar's thickness as a share of its lane, at [DesignParams.scale] `0` and `1`.
     *
     * The top is **over `1`** on purpose: theirs' *Spacing* is signed and its negative end overlaps the bars into one
     * another, which is the only place its blend knob shows. Ours' overlap is gentler than theirs, which runs all the
     * way to a frame with no ground left in it.
     */
    private const val MaxShare = 1.5f
    private const val MinShare = 0.15f

    /** How much of its length the roundness knob may take off a bar before the thickness floor catches it. */
    private const val LengthSpan = 0.97f

    /**
     * The furthest a bar at the edge of the fan is turned from the middle one, in radians.
     *
     * A quarter turn either side is as far as the set can open before the outermost bars are perpendicular to the
     * innermost, which is the point past which a fan stops reading as one.
     */
    private const val MaxFan = PI.toFloat() / 2f

    /** The inner shadow's width as a share of the cap, and how dark it goes at full [DesignParams.depth]. */
    private const val InnerShadowWidth = 0.5f
    private const val MaxShadowAlpha = 0.55f

    /** A byte's greatest value — what a `0..1` strength scales to when it becomes a paint's alpha. */
    private const val ChannelMax = 255
}
