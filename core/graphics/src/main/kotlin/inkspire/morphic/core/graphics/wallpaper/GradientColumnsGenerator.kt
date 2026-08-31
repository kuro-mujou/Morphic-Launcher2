package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
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
 * **A soft shadow on each column's right edge is what gives it depth.** Without it the columns are flat panels; darkening
 * the last sliver of each toward the seam reads as the next column standing slightly proud of it — the subtle relief
 * Smart Launcher's *Shadow* knob adds. [DesignParams.density] sets the column count and [DesignParams.irregularity] their
 * width variation. Deterministic in [seed].
 *
 * [columnCount] is the only pure mapping of this design's own; the banding is tested in [Bands] and the ramp in
 * [LinearGradientGenerator], and the per-pixel shade is judged in the render harness.
 */
object GradientColumnsGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val count = columnCount(params.density)
        val boundaries = Bands.boundaries(count, params.irregularity, seed)
        // One color per column, precomputed: the palette ramp stepped across the columns, low stop left to high right.
        val columnColors = IntArray(count) { i ->
            LinearGradientGenerator.colorAt(i.toFloat() / (count - 1).coerceAtLeast(1), palette)
        }

        val pixels = IntArray(width * height)
        for (x in 0 until width) {
            val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            val band = Bands.bandAt(nx, boundaries)
            val left = if (band == 0) 0f else boundaries[band - 1]
            val right = if (band < boundaries.size) boundaries[band] else 1f
            val localT = if (right > left) (nx - left) / (right - left) else 0f
            val color = shade(columnColors[band], edgeShade(localT))
            // A column is one color top to bottom, so the whole vertical run is filled from a single computed pixel.
            for (y in 0 until height) pixels[y * width + x] = color
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many columns [density] asks for — [MinColumns] a few broad panels up to [MaxColumns] a fine gradient. */
    internal fun columnCount(density: Float): Int =
        MinColumns + (density.coerceIn(0f, 1f) * (MaxColumns - MinColumns)).roundToInt()

    /**
     * The brightness a pixel at position [localT] within its column takes, `≤ 1` — flat across most of the column,
     * dipping to `1 - [ShadowDepth]` in the last [ShadowFraction] toward the right seam, so the column edge reads as a
     * shadow the next column casts.
     */
    private fun edgeShade(localT: Float): Float {
        val into = ((localT - (1f - ShadowFraction)) / ShadowFraction).coerceIn(0f, 1f)
        return 1f - ShadowDepth * into
    }

    /** [argb] with its color channels scaled by [factor] (`0..1`), alpha kept — a plain darken toward black. */
    private fun shade(argb: Int, factor: Float): Int {
        val a = argb ushr 24 and 0xFF
        val r = ((argb shr 16 and 0xFF) * factor).roundToInt().coerceIn(0, 0xFF)
        val g = ((argb shr 8 and 0xFF) * factor).roundToInt().coerceIn(0, 0xFF)
        val b = ((argb and 0xFF) * factor).roundToInt().coerceIn(0, 0xFF)
        val packed = (a shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }

    private const val MinColumns = 4
    private const val MaxColumns = 16

    /** The fraction of each column, at its right edge, the shadow occupies. */
    private const val ShadowFraction = 0.35f

    /** How far the shadow darkens at the seam — subtle, a hint of depth rather than a black line. */
    private const val ShadowDepth = 0.35f
}
