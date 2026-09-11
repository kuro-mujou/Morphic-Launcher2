package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Bands of flat palette color separated by one wave, drawn again and again down the frame — *Wave Dividers*.
 *
 * **Every divider is the same wave at the same phase, and the bands between them are exactly equal.** That is the
 * design, and driving the reference is what settled it: at every setting of every knob its bands measure the same
 * height as each other and its dividers sit parallel. What this replaced jittered the band widths by
 * [DesignParams.irregularity] and gave each divider a sum of two sines at *random* frequencies and phases, so no
 * setting of ours drew the rank of identical waves theirs draws at all of them.
 *
 * **The wavelength is measured against the frame's *height*, not against the axis the wave runs along** — theirs,
 * measured: at *Waves* `9` the period is `267px` on a 2400px frame, which is `height / 9` to within a pixel. It
 * matters because of [DesignParams.variant]: an axis-relative wavelength would redraw the same setting at a different
 * scale the moment the stack is turned, since that axis is the frame's width at one angle and its diagonal at another.
 * [FrameAxis.lengthPx] is what converts, and it was added for this.
 *
 * **[DesignParams.irregularity] is *Wave depth* — the amplitude — because `0` there is the design's real rigid end.**
 * The reference calls the same knob *Wideness*, and winding it down gives dead-straight dividers: flat bands, no wave
 * left. That is exactly what the field's contract asks `0` to mean, which is why the amplitude sits here rather than
 * on [DesignParams.scale]. It is **squared**, so the uniform `0.5` default lands on the reference's own restrained
 * amplitude (a twentieth of the frame, measured) rather than halfway to a stack that folds through itself.
 *
 * **[DesignParams.scale] is *Wavelength*, running from a tight ripple to one broad sweep across the frame** — the
 * reference's *Waves* `20..1`, read the other way so the field keeps its own direction (`0` tight, `1` sprawling).
 *
 * **[DesignParams.variant] is their *Rotation*, sampled** — the call [DiagonalBandsGenerator] and [LouversGenerator]
 * both make, and for their reason: a continuous rotation wants an orientation field on [DesignParams] that no design
 * yet has. Theirs is continuous over `-179..180°` and opens on `2°`, so index `0` here is **flat**, which is also what
 * Diagonal Bands' KDoc already promised this design would be at rest.
 *
 * Two of the reference's six knobs are deliberately not ported. Its *Offset* is a **phase** — one full period across
 * its `-50..50` travel, which is why `-49` and `50` draw the same picture — and a phase is what [seed] is for; the
 * studio's shuffle should choose it rather than a slider. Its *Irregularity* roughens the shared waveform into a
 * jagged silhouette, which is a real look, but its own default is `0` and the field that would carry it is spent on
 * the amplitude whose zero is the actual rigid end; a second noise field for one design would be a model in a vacuum.
 *
 * The palette **cycles through every stop**, including the first: unlike [DiagonalBandsGenerator] this design reserves
 * no ground, which the reference confirms — at *Count* `20` all four of its stops paint bands and none is held back.
 *
 * **The bands are painted as shapes, not floored a pixel at a time**, so the bake and a scrub issue the same canvas
 * calls ([plan] and [draw]) and a shuffle can be scrubbed — see docs/MORPH_ENGINE_PLAN.md's field survey for why.
 */
object WaveDividersGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Bands* slider's own range. */
    private val Amount = AmountKnob.Count("Bands", 2..20)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Wavelength",
        irregularity = "Wave depth",
        variant = VariantKnob("Direction", Direction.entries.map { it.label }),
    )

    /**
     * Which way the stack of bands runs — the reference's *Rotation*, at the stops a segmented control can offer.
     *
     * Ascending, and **flat is first** because it has to be both: the panel reads a segmented control as one axis, so
     * the angles want sorting, and the model's contract is that `variant = 0` is the design's default look — which
     * theirs is, opening at `2°`.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property degrees the angle the dividers run at, measured from the horizontal.
     */
    internal enum class Direction(val label: String, val degrees: Float) {
        /** Level dividers marching down the frame — theirs at rest, and so the one at index `0`. */
        FLAT("0°", degrees = 0f),

        /** The shallow slope the reference's other banded designs open on. */
        SHALLOW("20°", degrees = 20f),

        /** The true diagonal. */
        DIAGONAL("45°", degrees = 45f),

        /** Upright dividers marching across the frame. */
        UPRIGHT("90°", degrees = 90f),

        /** The diagonal mirrored. */
        REVERSE("135°", degrees = 135f),
    }

    /**
     * A stack of dividers planned but not painted — at no particular size and in no particular palette.
     *
     * **The seed is only [phase]**: every other field is a knob, which is why a shuffle of this design slides the whole
     * stack of waves along their length and changes nothing else.
     *
     * @property count how many bands the stack divides the frame into.
     * @property depth how far a divider swings off straight, as a share of the axis across the bands — [waveDepth].
     * @property cycles how many wave cycles fit in one frame *height* — [waveCycles].
     * @property phase where along its cycle every divider starts, in radians — the reference's *Offset*, seeded.
     */
    internal class Plan(
        val direction: Direction,
        val count: Int,
        val depth: Float,
        val cycles: Float,
        val phase: Float,
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(params, seed), palette, width, height)
        return bitmap
    }

    /** The stack [params] and [seed] describe. */
    internal fun plan(params: DesignParams, seed: Long): Plan = Plan(
        direction = Direction.entries[params.variant.coerceIn(0, Direction.entries.lastIndex)],
        count = bandCount(params.density),
        depth = waveDepth(params.irregularity),
        cycles = waveCycles(params.scale),
        // The phase is the reference's Offset, taken from the seed instead: it is variety, not a choice worth a knob.
        phase = Random(seed).nextFloat() * TwoPi,
    )

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike.
     *
     * **A band is everything between two dividers, and each is filled from its divider to past the far side of the
     * frame, the next one over it** — so every shared edge is antialiased once. The stack keeps cycling past both ends,
     * as it always did: a band displaced off the frame wraps rather than clamping into a flat strip along the edge, so
     * the dividers run from the one that can reach the frame's near side to the one that can reach its far side.
     *
     * **Half a pixel in**, because the axes read pixel `x` at `x` and a canvas puts that pixel's center at `x + 0.5`.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        val across = frameAxis(plan.direction.degrees + QuarterTurn, width, height)
        val along = frameAxis(plan.direction.degrees, width, height)
        // Cycles over the axis the wave runs along, from a wavelength set against the frame's height. See the KDoc.
        val turns = along.lengthPx * plan.cycles / height
        val ls = samplesAlong(plan, along, across, turns)

        val first = floor(-plan.depth * plan.count).toInt()
        val last = floor((1f + plan.depth) * plan.count).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        canvas.save()
        canvas.translate(PixelCenter, PixelCenter)
        paint.color = palette.colorAt(first.mod(palette.size))
        canvas.drawRect(-1f, -1f, width + 1f, height + 1f, paint)
        for (band in first + 1..last) {
            paint.color = palette.colorAt(band.mod(palette.size))
            canvas.drawPath(beyond(ls, divider(plan, band, ls, turns), along, across), paint)
        }
        canvas.restore()
    }

    /**
     * Where the dividers are sampled along their length, as shares of the along axis — a little past both ends, so the
     * traced edges reach the frame's corners.
     *
     * **The step is set by the wave's own steepest bend**, so a chord never strays more than [ChordError] pixels
     * from the curve: at full depth and the tightest wavelength a divider swings hundreds of pixels in a period of a
     * hundred or so, and needs a sample a pixel; a straight one needs almost none.
     */
    private fun samplesAlong(plan: Plan, along: FrameAxis, across: FrameAxis, turns: Float): FloatArray {
        val amplitude = plan.depth * across.lengthPx
        val wavenumber = if (along.lengthPx <= 0f) 0f else turns * TwoPi / along.lengthPx
        val bend = amplitude * wavenumber * wavenumber
        val step = if (bend <= 0f) MaxStep else sqrt(ChordBound * ChordError / bend).coerceIn(MinStep, MaxStep)
        val length = along.lengthPx.coerceAtLeast(1f)
        val count = ceil(length * (1f + 2f * Overhang) / step).toInt().coerceAtLeast(1)
        return FloatArray(count + 1) { -Overhang + (1f + 2f * Overhang) * it / count }
    }

    /** Divider [band]'s shares of the across axis at each of [ls] — the band boundary the pixel loop floored against. */
    internal fun divider(plan: Plan, band: Int, ls: FloatArray, turns: Float): FloatArray =
        FloatArray(ls.size) { band.toFloat() / plan.count + plan.depth * sin(ls[it] * turns * TwoPi + plan.phase) }

    /**
     * The region on the far side of the [edge] traced at the along-axis shares [ls] — out to well past the frame — in
     * pixels.
     *
     * **A point is the two axes' own ends combined**, `alongStart + l·(alongEnd − alongStart)` plus the same across,
     * which is exact because the two axes are perpendicular and each spans the frame corner to corner. Working the
     * point out from the angle instead would be [FrameAxis]' projection written a second time.
     */
    private fun beyond(ls: FloatArray, edge: FloatArray, along: FrameAxis, across: FrameAxis): Path = Path().apply {
        fun point(l: Float, a: Float, first: Boolean) {
            val x = pixelX(l, a, along, across)
            val y = pixelY(l, a, along, across)
            if (first) moveTo(x, y) else lineTo(x, y)
        }
        for (j in ls.indices) point(ls[j], edge[j], j == 0)
        point(ls.last(), Beyond, first = false)
        point(ls.first(), Beyond, first = false)
        close()
    }

    /** The pixel column where [along] reads [l] and [across] reads [a] — see [beyond]. */
    internal fun pixelX(l: Float, a: Float, along: FrameAxis, across: FrameAxis): Float =
        along.startX + l * (along.endX - along.startX) + across.startX + a * (across.endX - across.startX)

    /** The pixel row where [along] reads [l] and [across] reads [a] — see [beyond]. */
    internal fun pixelY(l: Float, a: Float, along: FrameAxis, across: FrameAxis): Float =
        along.startY + l * (along.endY - along.startY) + across.startY + a * (across.endY - across.startY)

    /**
     * A scrub between two stacks: the waves slide along their length to the next phase, and that is all a shuffle of
     * this design is.
     *
     * **The phase turns the short way**, so a shuffle that lands a few degrees on slides a few degrees rather than most
     * of a cycle; at *Wave depth* `0` there is no wave to slide and the scrub is a still one.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(params, from), plan(params, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h) }
    }

    /** Two stacks prepared to interpolate — or **null where they divide the frame into different numbers of bands**. */
    internal fun morph(from: Plan, to: Plan): Morph? = if (from.count == to.count) Morph(from, to) else null

    /** Two stacks and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /** The stack [t] of the way across; the ends are the plans themselves. */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                // A choice, and one a shuffle never changes.
                direction = if (t < 0.5f) from.direction else to.direction,
                count = from.count,
                depth = from.depth + (to.depth - from.depth) * t,
                cycles = from.cycles + (to.cycles - from.cycles) * t,
                phase = lerpAngle(from.phase, to.phase, t),
            )
        }
    }

    /** How many bands [density] asks for — a pair of broad sweeps up to a finely rippled stack. Theirs exactly. */
    internal fun bandCount(density: Float): Int = Amount.at(density)

    /**
     * How many wave cycles fit in one frame *height* at [scale] — the reference's *Waves*, read the other way so the
     * field keeps its own direction: `0` is a tight ripple and `1` one broad sweep.
     */
    internal fun waveCycles(scale: Float): Float =
        MaxCycles - scale.coerceIn(0f, 1f) * (MaxCycles - MinCycles)

    /**
     * How far a divider swings off straight at [irregularity], as a share of the axis across the bands.
     *
     * **Squared**, so the `0.5` every design still opens at lands on the reference's own restrained amplitude — a
     * twentieth of the frame, measured off its default — rather than halfway to a stack that folds through itself.
     * Third design in two slices to buy a per-design default with an exponent, which is the argument for having real
     * ones made once more.
     */
    internal fun waveDepth(irregularity: Float): Float {
        val amount = irregularity.coerceIn(0f, 1f)
        return MaxDepth * amount * amount
    }

    /** The cycles per frame height at the ends of the wavelength knob — theirs, whose *Waves* runs `1..20`. */
    private const val MinCycles = 1f
    private const val MaxCycles = 20f

    /** The furthest a divider may swing off straight, as a share of the axis across the bands. */
    private const val MaxDepth = 0.19f

    /** From the dividers' own direction to the axis that measures across them. */
    private const val QuarterTurn = 90f

    /** Where a pixel's center sits within it, in canvas units — see [draw]. */
    private const val PixelCenter = 0.5f

    /** The most a traced divider may stray from the true wave between two samples, in pixels. */
    private const val ChordError = 0.25f

    /** A chord of length `L` across a curve of bend `κ` strays by `κL²/8` — so `L = √(8·error/κ)`. */
    private const val ChordBound = 8f

    /** The closest and furthest two samples along a divider may sit, in pixels. */
    private const val MinStep = 1f
    private const val MaxStep = 8f

    /** How far past each end of the along axis a divider is traced, as a share of it — enough to reach the corners. */
    private const val Overhang = 0.01f

    /** How far across the axis a band's fill reaches past its divider — beyond the frame at any depth. */
    private const val Beyond = 3f

    private val TwoPi = (2.0 * PI).toFloat()
}
