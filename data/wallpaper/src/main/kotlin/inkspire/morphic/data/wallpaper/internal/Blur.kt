package inkspire.morphic.data.wallpaper.internal

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** Side of the square [dominantColor] reduces an image to before weighing it. L1's 32. */
private const val ACCENT_GRID = 32

/** Added to every pixel's chroma weight, so a fully grayscale wallpaper averages instead of dividing by zero. */
private const val GRAY_FLOOR = 8.0

/** What [dominantColor] returns when there is nothing at all to weigh. */
private const val NEUTRAL_GRAY = 0xFF808080.toInt()

/**
 * L1's `Blur.kt`, and the half of it that is still here: the representative color `BackdropTint.WALLPAPER` washes
 * a frosted surface in.
 *
 * **The blur half has left, and it left in two directions.** The kernel went to `core:graphics` (`BitmapBlur`), so
 * `core:icon` could have the same one; the *reduction* went to the decode, where `inSampleSize` is free. What used to
 * sit between them — a wrapper that reduced a bitmap and then blurred it — had nothing left to decide, so the two
 * statements now stand together in `WallpaperRepositoryImpl.blurBackdrop`. That is also what retired the bug the
 * wrapper hid: the reduction was split between a power-of-two decode and a residual `scale`, and integer division
 * threw the residue away silently, so the radius was computed for a bitmap smaller than the one it ran on.
 *
 * **What survives must not be confused with the brightness read**, which is the trap S5f-1 nearly walked into:
 * [dominantColor] is deliberately **saturation-weighted** so a vivid accent beats washed-out gray, which is right for
 * "what color is this wallpaper?" and wrong for "how bright is it?". The brightness signal therefore has its own
 * unweighted luminance mean in the repository rather than reusing this.
 */

/**
 * A representative accent color (ARGB) for [source] — what `BackdropTint.WALLPAPER` washes a frosted surface in.
 *
 * **A saturation-weighted average, not a plain one**, and that is the whole trick: a plain mean of a photograph is
 * mud, because every colorful pixel is dragged toward the gray majority. Weighting each pixel by its own chroma
 * (`max - min` of its channels) lets a vivid minority carry the result, which is what "the wallpaper's color" means
 * to a person looking at it. The `+ 8` floor keeps a fully grayscale image from dividing by zero and lets it average
 * normally.
 *
 * Over a 32×32 downscale, because a representative color needs no more resolution than that and the cost is then a
 * thousand additions. Mid-gray when there is nothing to weigh at all.
 *
 * **Only needed below API 27, or when a live wallpaper publishes no colors.** Above that, `WallpaperColors` answers
 * the same question about the wallpaper *actually displayed* — including another app's, which we cannot read — so this
 * is the fallback rather than the primary path. L1 used it the same way, for the same API reason.
 */
internal fun dominantColor(source: Bitmap): Int {
    val small = source.scale(ACCENT_GRID, ACCENT_GRID)
    val pixels = IntArray(ACCENT_GRID * ACCENT_GRID)
    small.getPixels(pixels, 0, ACCENT_GRID, 0, 0, ACCENT_GRID, ACCENT_GRID)
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var sumW = 0.0
    for (p in pixels) {
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val weight = (maxOf(r, g, b) - minOf(r, g, b)) + GRAY_FLOOR
        sumR += r * weight
        sumG += g * weight
        sumB += b * weight
        sumW += weight
    }
    if (sumW <= 0.0) return NEUTRAL_GRAY
    val r = (sumR / sumW).roundToInt().coerceIn(0, 0xFF)
    val g = (sumG / sumW).roundToInt().coerceIn(0, 0xFF)
    val b = (sumB / sumW).roundToInt().coerceIn(0, 0xFF)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
