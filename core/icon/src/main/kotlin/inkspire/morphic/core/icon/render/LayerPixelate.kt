package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.roundToInt

/**
 * How large a pixelate's cells are, how much of each a dot covers, and what colour it comes out.
 *
 * [LayerRipple]'s and [LayerGrain]'s reason: only the bake draws this, so nothing is competing with the arithmetic —
 * it is separated because `IconRenderer` needs an emulator for every line, and the averaging below is the sort of
 * thing that is wrong in a way you would have to look for.
 */
object LayerPixelate {

    /**
     * One cell's side in pixels.
     *
     * Floored at a pixel because it is a loop bound and a divisor, and [LayerEffect.Pixelate.isIdentity] already
     * refuses a size of zero — this is the guard for a stored recipe that never went through it, and for one so fine
     * the cell rounds away on a small bake.
     */
    fun cellPx(pixelate: LayerEffect.Pixelate, sizePx: Int): Float =
        (pixelate.cellSize * sizePx).coerceAtLeast(MinCellPx)

    /** How far in from its cell's edge a dot starts — half the gap, since the dot is centred in the cell. */
    fun insetPx(cellPx: Float, fill: Float): Float = cellPx * (1f - fill.coerceIn(0f, 1f)) / 2f

    /**
     * The dot's corner radius in pixels, for a dot of [dotPx] a side.
     *
     * **A fraction of the dot rather than a length**, which is what keeps a roundness of 1 a circle at every fill and
     * every bake size — a stored radius would come out as a square with nicked corners on a large dot and as a
     * circle on a small one.
     */
    fun cornerRadiusPx(dotPx: Float, roundness: Float): Float = dotPx / 2f * roundness.coerceIn(0f, 1f)

    /**
     * The average colour of the [cellPx]-square block of [pixels] whose top-left corner is ([left], [top]).
     *
     * **Averaged premultiplied, then un-premultiplied**, and that is the whole reason this is a named function
     * rather than four running totals inline. Straight ARGB averaging weights a fully transparent pixel's colour
     * equally with an opaque one — and a transparent pixel is almost always transparent *black*, so every cell that
     * straddles the artwork's edge comes out darker than the artwork it is standing in for. The icon would gain a
     * dark fringe that looks like a rendering fault rather than like a mistake in an average.
     *
     * A block reaching past the edge of the bitmap contributes nothing rather than being clamped, so the last cell
     * in a row averages only the pixels that exist.
     */
    fun averageArgb(pixels: IntArray, sizePx: Int, left: Int, top: Int, cellPx: Int): Int {
        var alphaSum = 0L
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var counted = 0

        for (y in top until minOf(top + cellPx, sizePx)) {
            for (x in left until minOf(left + cellPx, sizePx)) {
                val argb = pixels[y * sizePx + x]
                val alpha = argb ushr 24 and 0xFF
                alphaSum += alpha
                // Weighted by alpha, which *is* premultiplying — a transparent pixel then contributes nothing to
                // the colour rather than contributing black to it.
                redSum += (argb shr 16 and 0xFF) * alpha
                greenSum += (argb shr 8 and 0xFF) * alpha
                blueSum += (argb and 0xFF) * alpha
                counted++
            }
        }
        if (counted == 0 || alphaSum == 0L) return 0

        val alpha = (alphaSum.toFloat() / counted).roundToInt().coerceIn(0, 255)
        // Divided by the *alpha* total rather than the pixel count, which is what un-premultiplies it.
        val red = (redSum.toFloat() / alphaSum).roundToInt().coerceIn(0, 255)
        val green = (greenSum.toFloat() / alphaSum).roundToInt().coerceIn(0, 255)
        val blue = (blueSum.toFloat() / alphaSum).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /** Below a pixel a cell is finer than the grid it is sampled on, so there is nothing left to average. */
    private const val MinCellPx = 1f
}
