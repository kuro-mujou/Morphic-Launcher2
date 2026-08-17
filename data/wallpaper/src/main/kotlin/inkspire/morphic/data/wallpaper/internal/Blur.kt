package inkspire.morphic.data.wallpaper.internal

import android.graphics.Bitmap
import inkspire.morphic.core.graphics.BitmapBlur
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** Side of the square [dominantColor] reduces an image to before weighing it. L1's 32. */
private const val ACCENT_GRID = 32

/** Added to every pixel's chroma weight, so a fully grayscale wallpaper averages instead of dividing by zero. */
private const val GRAY_FLOOR = 8.0

/** What [dominantColor] returns when there is nothing at all to weigh. */
private const val NEUTRAL_GRAY = 0xFF808080.toInt()

/**
 * L1's `Blur.kt`, both halves: the downscale-then-box-blur a frosted surface samples, and the representative color
 * `BackdropEffect.MaterialYou` tints itself with.
 *
 * **Where it lives is a correction to the port plan.** That said `Blur.kt` "belongs beside the graphics/icon code, in
 * neither repository's module", which was right when the wallpaper lived inside `data:settings` — image processing has
 * no business in a preferences store. `data:wallpaper` is not that: it exists *because* it decodes bitmaps, and
 * `cropAndScale` plus the sampled decode are already in the file next door. Moving these somewhere abstract would
 * separate them from their only caller to satisfy a sentence written about a module that no longer holds it.
 *
 * **The two functions read the same image and must not be confused for each other**, which is the trap S5f-1 nearly
 * walked into: [dominantColor] is deliberately **saturation-weighted** so a vivid accent beats washed-out gray, which
 * is right for "what color is this wallpaper?" and wrong for "how bright is it?". The brightness signal therefore has
 * its own unweighted luminance mean in the repository rather than reusing this.
 */

/**
 * [source] reduced by [downscale] and blurred by [radius] — the picture a frosted surface samples.
 *
 * **The kernel is [BitmapBlur]'s now, shared with `core:icon`.** This file had the only real blur in the launcher and
 * the icon renderer could not reach it — a `core` module cannot depend on a `data` one — so that one approximated a
 * blur with a `Bitmap.scale` down and back up, and looked terraced. Moving the arithmetic to `core:graphics` gave
 * both callers the same one; what stays here is the *decision*, which is how much of the picture to keep.
 *
 * **The reduction follows the blur rather than being a constant, and that is the fix for the backdrop's own worst
 * artifact.** It used to be a flat eighth of the screen at every strength — so even at a strength of zero, where no
 * blur is applied at all, a frosted surface was showing an eighth-resolution copy of the wallpaper stretched back up.
 * That reads as a low-quality image rather than as glass, and no amount of blur strength could rescue it because the
 * blur was never what was wrong. See [BitmapBlur.downscaleFor].
 *
 * A [radius] below 1 is nothing to do, and at that point [downscale] is 1 too, so what comes back is the picture
 * itself.
 */
internal fun downscaleAndBlur(source: Bitmap, downscale: Int, radius: Int, passes: Int): Bitmap {
    val w = (source.width / downscale.coerceAtLeast(1)).coerceAtLeast(1)
    val h = (source.height / downscale.coerceAtLeast(1)).coerceAtLeast(1)
    val small = if (w == source.width && h == source.height) source else source.scale(w, h)
    if (radius < 1) return small

    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    BitmapBlur.blur(pixels, w, h, radius, passes)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

/**
 * A representative accent color (ARGB) for [source] — what `BackdropEffect.MaterialYou` washes a frosted surface in.
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
