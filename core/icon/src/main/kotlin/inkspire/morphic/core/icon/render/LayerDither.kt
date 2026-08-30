package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.DitherKernel
import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.roundToInt

/**
 * The arithmetic of dithering: how coarse the palette is, where a channel rounds to, and how the rounding error is
 * spread — everything but the loop that walks the pixels.
 *
 * [LayerPixelate]'s and [LayerRipple]'s reason: only the bake draws a dither, so nothing competes with this
 * arithmetic, and it is pulled out of `IconRenderer` because that class needs an emulator for every line while these
 * are the parts that are *silently* wrong. A step size off by one crushes the palette a notch too far and looks like
 * a slightly bolder dither; a diffusion weight in the wrong cell tilts the whole grain and looks like a different,
 * plausible kernel. Neither throws.
 */
object LayerDither {

    /** One tap of an error-diffusion kernel: push [weight] of the error [dx],[dy] cells from the current one. */
    data class Diffusion(val dx: Int, val dy: Int, val weight: Float)

    /**
     * One dither cell's side in pixels — floored at a pixel because it is a grid step and a divisor.
     *
     * A fraction of the box, so the grain is fixed to the icon rather than to the bake's resolution — see
     * [LayerEffect.Dither.coarseness]. Rounded to whole pixels because a cell is a block of them.
     */
    fun cellPx(coarseness: Float, sizePx: Int): Int =
        (coarseness * sizePx).roundToInt().coerceAtLeast(MinCellPx)

    /**
     * The gap between two adjacent palette steps, for a palette of [levels] steps per channel.
     *
     * `255 / (levels - 1)` so the ends land exactly on 0 and 255 — a two-level palette steps by 255 (off or full),
     * an eight-level one by 36. Guarded above one level, which [quantize] would divide by zero on and which the model
     * refuses anyway.
     */
    fun stepSize(levels: Int): Int = 255 / (levels.coerceAtLeast(2) - 1)

    /** [value] rounded to the nearest palette step of [stepSize], clamped to a channel's `0..255`. */
    fun quantize(value: Int, stepSize: Int): Int =
        ((value.toFloat() / stepSize).roundToInt() * stepSize).coerceIn(0, 255)

    /**
     * The diffusion taps for [kernel], or **null** for an ordered one — which has no error to diffuse and is drawn a
     * different way. The renderer branches on the null, which is what keeps the sequential scan and the parallel
     * threshold from pretending to be one loop.
     */
    fun diffusionOf(kernel: DitherKernel): Array<Diffusion>? = when (kernel) {
        DitherKernel.FLOYD_STEINBERG -> floydSteinberg
        DitherKernel.ATKINSON -> atkinson
        DitherKernel.ORDERED -> null
    }

    /**
     * The ordered-dither bias for the cell at ([cellX], [cellY]), in channel units — added to a channel before it is
     * quantized, so which side of the step it lands on depends on its place in the 4×4 grid.
     *
     * **Centered on zero**, unlike the raw Bayer index: the matrix runs 0..15, and used directly it only ever pushes
     * a channel *up*, which brightens the whole icon as it dithers. Mapping it to `−½..+½` of a step instead biases
     * as many cells down as up, so the average tone is preserved and only the texture is added.
     */
    fun orderedThreshold(cellX: Int, cellY: Int, stepSize: Int): Int {
        val cell = bayer4x4[cellY.mod(4)][cellX.mod(4)]
        return ((cell / 16f - 0.5f) * stepSize).roundToInt()
    }

    private val floydSteinberg = arrayOf(
        Diffusion(1, 0, 7f / 16f),
        Diffusion(-1, 1, 3f / 16f),
        Diffusion(0, 1, 5f / 16f),
        Diffusion(1, 1, 1f / 16f),
    )

    // Only six eighths of the error is spread; the missing quarter is dropped on purpose, which is what blows out the
    // highlights and crushes the shadows into Atkinson's high-contrast look.
    private val atkinson = arrayOf(
        Diffusion(1, 0, 1f / 8f),
        Diffusion(2, 0, 1f / 8f),
        Diffusion(-1, 1, 1f / 8f),
        Diffusion(0, 1, 1f / 8f),
        Diffusion(1, 1, 1f / 8f),
        Diffusion(0, 2, 1f / 8f),
    )

    // The recursive 4×4 Bayer matrix, values 0..15 — the classic ordered-dither threshold grid.
    private val bayer4x4 = arrayOf(
        intArrayOf(0, 8, 2, 10),
        intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9),
        intArrayOf(15, 7, 13, 5),
    )

    /** Below a pixel a cell is finer than the grid it is sampled on, so there is nothing to quantize. */
    private const val MinCellPx = 1
}
