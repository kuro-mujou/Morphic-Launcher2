package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette

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

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val count = columnCount(params.density)
        val boundaries = Bands.boundaries(count, params.irregularity, seed)
        // One color per column, precomputed: the palette ramp stepped across the columns, low stop left to high right.
        val columnColors = IntArray(count) { i ->
            LinearGradientGenerator.colorAt(i.toFloat() / (count - 1).coerceAtLeast(1), palette)
        }

        val degrees = params.rotation.coerceIn(0f, 1f) * HalfTurn
        // The two axes of the design: one steps the columns, the other runs down them. A second [frameAxis] a quarter
        // turn on rather than a perpendicular of this one's own, so the rake turns with the design for free.
        val across = frameAxis(degrees, width, height)
        val down = frameAxis(degrees + QuarterTurn, width, height)
        val relief = params.depth.coerceIn(0f, 1f) / ShippedRelief

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val fx = x.toFloat()
                val fy = y.toFloat()
                val position = across.at(fx, fy)
                val band = Bands.bandAt(position, boundaries)
                val low = if (band == 0) 0f else boundaries[band - 1]
                val high = if (band < boundaries.size) boundaries[band] else 1f
                val localT = if (high > low) (position - low) / (high - low) else 0f
                val shade = edgeShade(localT, relief) * rakeShade(down.at(fx, fy), relief)
                pixels[y * width + x] = Shades.scale(columnColors[band], shade)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** The sweep [DesignParams.rotation] takes the columns through — see the class note for why it is not a full one. */
    private const val HalfTurn = 180f

    /** From the axis the columns step along to the axis they run down. */
    private const val QuarterTurn = 90f

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
