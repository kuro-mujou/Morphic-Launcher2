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
 * **A soft shadow on each column's right edge is what gives it depth.** Without it the columns are flat panels; darkening
 * the last sliver of each toward the seam reads as the next column standing slightly proud of it — the subtle relief
 * Smart Launcher's *Shadow* knob adds. [DesignParams.density] sets the column count and [DesignParams.irregularity] their
 * width variation. Deterministic in [seed].
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
 * [columnCount] is the only pure mapping of this design's own; the banding is tested in [Bands] and the ramp in
 * [LinearGradientGenerator], and the per-pixel shade is judged in the render harness.
 */
object GradientColumnsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 4..16)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Variation",
        rotation = "Direction",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val count = columnCount(params.density)
        val boundaries = Bands.boundaries(count, params.irregularity, seed)
        // One color per column, precomputed: the palette ramp stepped across the columns, low stop left to high right.
        val columnColors = IntArray(count) { i ->
            LinearGradientGenerator.colorAt(i.toFloat() / (count - 1).coerceAtLeast(1), palette)
        }

        val axis = frameAxis(params.rotation.coerceIn(0f, 1f) * HalfTurn, width, height)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val along = axis.at(x.toFloat(), y.toFloat())
                val band = Bands.bandAt(along, boundaries)
                val low = if (band == 0) 0f else boundaries[band - 1]
                val high = if (band < boundaries.size) boundaries[band] else 1f
                val localT = if (high > low) (along - low) / (high - low) else 0f
                pixels[y * width + x] = Shades.scale(columnColors[band], edgeShade(localT))
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** The sweep [DesignParams.rotation] takes the columns through — see the class note for why it is not a full one. */
    private const val HalfTurn = 180f

    /** How many columns [density] asks for — a few broad panels up to a fine gradient. */
    internal fun columnCount(density: Float): Int = Amount.at(density)

    /**
     * The brightness a pixel at position [localT] within its column takes, `≤ 1` — flat across most of the column,
     * dipping to `1 - [ShadowDepth]` in the last [ShadowFraction] toward the right seam, so the column edge reads as a
     * shadow the next column casts.
     */
    private fun edgeShade(localT: Float): Float {
        val into = ((localT - (1f - ShadowFraction)) / ShadowFraction).coerceIn(0f, 1f)
        return 1f - ShadowDepth * into
    }

    /** The fraction of each column, at its right edge, the shadow occupies. */
    private const val ShadowFraction = 0.35f

    /** How far the shadow darkens at the seam — subtle, a hint of depth rather than a black line. */
    private const val ShadowDepth = 0.35f
}
