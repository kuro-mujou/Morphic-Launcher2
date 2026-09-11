package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt

/**
 * Vertical columns stepping once through the palette, each shaded on one edge for depth — *Gradient Columns*.
 *
 * **A single sweep through the palette, cut into columns — not the repeating stripes of [DiagonalBandsGenerator].**
 * Diagonal Bands *cycles* the palette, so its stripes repeat; Gradient Columns *progresses* through it once left to
 * right, each column a flat step of the ramp, so the frame reads as one coarse gradient rendered in panels. The shared
 * [Bands] does the variable-width splitting; the difference is entirely in the color — a progression, not a cycle — and
 * in the shadow.
 *
 * **[DesignParams.depth] is the light on the panels, and it is two shadows rather than one.** A soft shadow on each
 * column's right edge ([edgeShade]) reads as the next column standing slightly proud of it — the relief Smart
 * Launcher's *Shadow* knob adds. Beside it, a fall in brightness **along** each column ([rakeShade]) reads as light
 * raking across the set from one end. Until the quality pass neither was a knob and the second did not exist: the
 * columns were flat top to bottom, which is the whole of why this was the plainest design in the catalog next to
 * [LouversGenerator], and why nothing but its width varied along its long axis.
 *
 * **The two are one field because they are one fiction** — how far these panels stand out of the plane — so `0` is
 * genuinely flat, which is what [DesignParams.depth]'s contract asks for and what this design could not draw at all
 * before. The default `0.5` reproduces the seam shadow it always had, and adds the rake it never did.
 *
 * **It is still not [LouversGenerator], and the distinction is which thing varies.** Louvers runs the *palette* along
 * each strip and slides it from strip to strip; here the palette steps only **across** the set, and what moves along a
 * column is its **brightness**. A column keeps one stop from end to end, which is what leaves the seams hard and the
 * progression legible as panels rather than as one continuous ramp.
 *
 * [DesignParams.density] sets the column count and [DesignParams.irregularity] their width variation. Deterministic in
 * [seed].
 *
 * **[DesignParams.rotation] is which way the columns run, and until the quality pass they could only stand upright.**
 * A design whose whole content is a direction had no control over it: every render was the same rank of vertical
 * panels, and the knob is what makes a stack of horizontal bands or a diagonal sweep the same design rather than two
 * more it does not have. `0` is the upright columns it has always drawn, and the knob sweeps a half turn. The axis is
 * [frameAxis], the same one [DiagonalBandsGenerator] and [LouversGenerator] measure their angles on, so an angle means
 * one thing across the catalog — and reading it costs the per-column fill, since a turned band is no longer one column
 * of the screen.
 *
 * **Half a turn is a knowing limit here too**, for [LinearGradientGenerator]'s reason: these columns are a
 * *progression* rather than a symmetric stripe pattern — low stop to high, with the shadow on one edge of each — so
 * reversing the axis is a different picture, and reaching those takes a full turn the knob guard rejects.
 *
 * [columnCount], [edgeShade] and [rakeShade] are this design's own pure mappings; the banding is tested in [Bands] and
 * the ramp in [LinearGradientGenerator], and how the two shades read together is judged in the render harness.
 *
 * **The columns are drawn as shapes, not classified a pixel at a time**, so the bake and a scrub issue the same canvas
 * calls ([plan] and [draw]) and a shuffle can be scrubbed — see docs/MORPH_ENGINE_PLAN.md's field survey for why.
 */
object GradientColumnsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 4..16)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Variation",
        depth = "Relief",
        rotation = "Direction",
    )

    /**
     * A set of columns planned but not painted — at no particular size and in no particular palette.
     *
     * **The seed moves only [boundaries]**, and only under *Variation*: the colors step across the columns by index
     * and the light is a knob, so a shuffle re-cuts the panel widths and changes nothing else.
     *
     * @property degrees which way the columns step, from the horizontal — [DesignParams.rotation] over a half turn.
     * @property relief [DesignParams.depth] over [ShippedRelief] — what [edgeShade] and [rakeShade] are handed.
     * @property boundaries the edges between columns, as shares of the axis, sorted — [Bands.boundaries].
     */
    internal class Plan(val degrees: Float, val relief: Float, val boundaries: FloatArray)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(params, seed), palette, width, height)
        return bitmap
    }

    /** The columns [params] and [seed] describe. */
    internal fun plan(params: DesignParams, seed: Long): Plan = Plan(
        degrees = params.rotation.coerceIn(0f, 1f) * HalfTurn,
        relief = params.depth.coerceIn(0f, 1f) / ShippedRelief,
        boundaries = Bands.boundaries(columnCount(params.density), params.irregularity, seed),
    )

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike.
     *
     * **Each column is a flat slab of its stop, and both shades are black laid over it** — which over an opaque color
     * scales it by one minus the black's opacity, so two layers multiply exactly as [edgeShade] times [rakeShade]
     * always did. The seam shadow is a gradient across the last [ShadowFraction] of each column; the rake is one
     * gradient down the whole frame, its stops sampling [rakeShade]'s smoothstep, since a shader only ramps linearly
     * between stops.
     *
     * **Each column runs from its own start to past the frame, and the next one covers the rest**, so every seam is
     * antialiased once. The column's seam shadow is laid before the next column goes down, so that column's edge
     * covers it exactly where it covers the column. **Half a pixel in**, because the axis reads pixel `x` at `x` and a
     * canvas puts that pixel's center at `x + 0.5`.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        // The two axes of the design: one steps the columns, the other runs down them. A second [frameAxis] a quarter
        // turn on rather than a perpendicular of this one's own, so the rake turns with the design for free.
        val across = frameAxis(plan.degrees, width, height)
        val down = frameAxis(plan.degrees + QuarterTurn, width, height)
        val count = plan.boundaries.size + 1
        val reach = (width + height).toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val seam = blackAt(ShadowDepth * plan.relief)

        canvas.save()
        canvas.translate(PixelCenter, PixelCenter)
        for (column in 0 until count) {
            val low = if (column == 0) 0f else plan.boundaries[column - 1]
            val high = if (column < plan.boundaries.size) plan.boundaries[column] else 1f
            // The palette ramp stepped across the columns, low stop to high.
            fill.color = LinearGradientGenerator.colorAt(column.toFloat() / (count - 1).coerceAtLeast(1), palette)
            canvas.drawPath(across.slabPath(if (column == 0) -Beyond else low, Beyond, reach), fill)
            if (plan.relief > 0f && high > low) {
                val from = high - ShadowFraction * (high - low)
                shadow.shader = LinearGradient(
                    across.xAt(from), across.yAt(from), across.xAt(high), across.yAt(high),
                    Transparent, seam, Shader.TileMode.CLAMP,
                )
                canvas.drawPath(across.slabPath(from, if (column == count - 1) Beyond else high, reach), shadow)
            }
        }
        if (plan.relief > 0f) {
            val stops = rakeStops()
            shadow.shader = LinearGradient(
                down.xAt(0f), down.yAt(0f), down.xAt(1f), down.yAt(1f),
                IntArray(RakeStops) { blackAt(1f - rakeShade(stops[it], plan.relief)) },
                stops,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(-1f, -1f, width + 1f, height + 1f, shadow)
        }
        canvas.restore()
    }

    /** Where along a column the rake's gradient is pinned to [rakeShade] — [RakeStops] of them, evenly, `0..1`. */
    internal fun rakeStops(): FloatArray = FloatArray(RakeStops) { it.toFloat() / (RakeStops - 1) }

    /** Black at [opacity] — laid over a color, it scales that color by `1 - opacity`. */
    private fun blackAt(opacity: Float): Int = (opacity * ChannelMax).roundToInt().coerceIn(0, ChannelMax) shl AlphaShift

    /**
     * A scrub between two sets of columns: the seams slide, and nothing else moves.
     *
     * **Like Diagonal Bands', and as subtle as a shuffle of this design is.** The seed only cuts the widths, so at
     * *Variation* `0` two seeds are one picture and the scrub is a still one; two sorted edge lists interpolated edge
     * for edge stay sorted, so no column turns inside out.
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

    /** Two sets prepared to interpolate, seam for seam — or **null where they hold different numbers of columns**. */
    internal fun morph(from: Plan, to: Plan): Morph? =
        if (from.boundaries.size == to.boundaries.size) Morph(from, to) else null

    /** Two sets of columns and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /** The columns [t] of the way across; the ends are the plans themselves. */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                degrees = from.degrees + (to.degrees - from.degrees) * t,
                relief = from.relief + (to.relief - from.relief) * t,
                boundaries = FloatArray(from.boundaries.size) {
                    from.boundaries[it] + (to.boundaries[it] - from.boundaries[it]) * t
                },
            )
        }
    }

    /** The sweep [DesignParams.rotation] takes the columns through — see the class note for why it is not a full one. */
    private const val HalfTurn = 180f

    /** From the axis the columns step along to the axis they run down. */
    private const val QuarterTurn = 90f

    /** Where a pixel's center sits within it, in canvas units — see [draw]. */
    private const val PixelCenter = 0.5f

    /** How far past the axis' ends a first or last column's fill reaches, as a share of it — beyond the frame. */
    private const val Beyond = 2f

    /**
     * How many stops the rake's gradient samples its smoothstep at. A shader ramps linearly between stops, and at
     * this many the most that departs from the curve is well under a level of 255 even at full relief.
     */
    private const val RakeStops = 17

    /** A clear color — the start of every seam shadow. */
    private const val Transparent = 0

    /** Where alpha sits in a packed color, and the most it can be. */
    private const val AlphaShift = 24
    private const val ChannelMax = 255

    /** How many columns [density] asks for — a few broad panels up to a fine gradient. */
    internal fun columnCount(density: Float): Int = Amount.at(density)

    /**
     * The brightness a pixel at position [localT] *across* its column takes, `≤ 1` — flat over most of it, dipping to
     * `1 - [ShadowDepth] × [relief]` in the last [ShadowFraction] toward the seam, so a column edge reads as a shadow
     * the next column casts.
     *
     * [relief] is [DesignParams.depth] over [ShippedRelief], so the default `0.5` answers exactly `1` and draws the
     * shadow this design has always drawn. Curved that way rather than mapped straight for [TruchetGenerator]'s
     * reason: the shipped look is the one the knob's *middle* has to keep, and the ends are where the new pictures
     * are.
     */
    internal fun edgeShade(localT: Float, relief: Float): Float {
        val into = ((localT - (1f - ShadowFraction)) / ShadowFraction).coerceIn(0f, 1f)
        return 1f - ShadowDepth * relief * into
    }

    /**
     * The brightness a pixel [alongColumn] of the way down its column takes, `≤ 1` — the light raking across the set,
     * and the variation this design had none of.
     *
     * **Eased rather than run straight, which is what keeps it from becoming a second gradient.** The palette already
     * progresses linearly *across* the columns, so a linear fall along them makes the frame one bilinear field — a
     * corner-to-corner ramp with seams drawn on it, which is [LinearGradientGenerator] wearing this design's clothes.
     * [Easing.smoothstep] holds both ends flat and spends the fall in the middle, so the panels read as *lit* rather
     * than as gradiented.
     *
     * Bounded well under `1` at full [relief], since this multiplies with [edgeShade]: the darkest pixel in the frame
     * is the far seam of the far column, and two unbounded shades meeting there would take it to black.
     */
    internal fun rakeShade(alongColumn: Float, relief: Float): Float =
        1f - RakeDepth * relief * Easing.smoothstep(alongColumn)

    /** The fraction of each column, at its trailing edge, the seam shadow occupies. */
    private const val ShadowFraction = 0.35f

    /** How far the seam shadow darkens at the default relief — subtle, a hint of depth rather than a black line. */
    private const val ShadowDepth = 0.35f

    /** How far the rake darkens by the far end of a column, at the default relief. */
    private const val RakeDepth = 0.22f

    /**
     * The [DesignParams.depth] at which both shades draw the design's shipped look — its own default, so an untouched
     * recipe keeps the seam shadow it had. Above it the relief runs to twice that and the panels stand well out of the
     * plane; at `0` they are perfectly flat, which is the end this design could not draw at all before.
     */
    private const val ShippedRelief = 0.5f
}
