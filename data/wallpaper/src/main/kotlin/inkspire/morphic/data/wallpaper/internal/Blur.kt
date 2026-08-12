package inkspire.morphic.data.wallpaper.internal

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** Side of the square [dominantColor] reduces an image to before weighing it. L1's 32. */
private const val ACCENT_GRID = 32

/** Added to every pixel's chroma weight, so a fully greyscale wallpaper averages instead of dividing by zero. */
private const val GREY_FLOOR = 8.0

/** What [dominantColor] returns when there is nothing at all to weigh. */
private const val NEUTRAL_GREY = 0xFF808080.toInt()

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
 * walked into: [dominantColor] is deliberately **saturation-weighted** so a vivid accent beats washed-out grey, which
 * is right for "what color is this wallpaper?" and wrong for "how bright is it?". The brightness signal therefore has
 * its own unweighted luminance mean in the repository rather than reusing this.
 */

/**
 * [source] downscaled by [downscale] and box-blurred [passes] times, which approximates a gaussian.
 *
 * **Cheap because the blur runs on the small bitmap, not the big one.** A backdrop is upscaled at draw time anyway, so
 * a low-resolution blur is not a compromise — it is the same picture. The two together are what make this affordable
 * to compute on a wallpaper change rather than per frame.
 *
 * A [radius] below 1 returns the plain downscale, which is the honest reading of "no blur at this strength" and saves
 * the passes.
 */
internal fun downscaleAndBlur(source: Bitmap, downscale: Int, radius: Int, passes: Int): Bitmap {
    val w = (source.width / downscale).coerceAtLeast(1)
    val h = (source.height / downscale).coerceAtLeast(1)
    val small = source.scale(w, h)
    if (radius < 1) return small
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    val scratch = IntArray(w * h)
    // Separable: a horizontal pass then a vertical one is O(n) per pass instead of O(radius²) per pixel, and the two
    // together are what makes each repeat a full 2-D blur.
    repeat(passes) {
        blurPass(pixels, scratch, w, h, radius, horizontal = true)
        blurPass(scratch, pixels, w, h, radius, horizontal = false)
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

/**
 * A representative accent color (ARGB) for [source] — what `BackdropEffect.MaterialYou` washes a frosted surface in.
 *
 * **A saturation-weighted average, not a plain one**, and that is the whole trick: a plain mean of a photograph is
 * mud, because every colorful pixel is dragged toward the grey majority. Weighting each pixel by its own chroma
 * (`max - min` of its channels) lets a vivid minority carry the result, which is what "the wallpaper's color" means
 * to a person looking at it. The `+ 8` floor keeps a fully greyscale image from dividing by zero and lets it average
 * normally.
 *
 * Over a 32×32 downscale, because a representative color needs no more resolution than that and the cost is then a
 * thousand additions. Mid-grey when there is nothing to weigh at all.
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
        val weight = (maxOf(r, g, b) - minOf(r, g, b)) + GREY_FLOOR
        sumR += r * weight
        sumG += g * weight
        sumB += b * weight
        sumW += weight
    }
    if (sumW <= 0.0) return NEUTRAL_GREY
    val r = (sumR / sumW).roundToInt().coerceIn(0, 0xFF)
    val g = (sumG / sumW).roundToInt().coerceIn(0, 0xFF)
    val b = (sumB / sumW).roundToInt().coerceIn(0, 0xFF)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * One separable box-blur pass over [src] into [dst], along rows when [horizontal] and down columns otherwise.
 *
 * A **sliding window**: each output pixel adds the one entering the window and subtracts the one leaving, so the cost
 * is independent of [radius]. `count` is tracked rather than assumed because the window is clipped at both ends — the
 * edges average only real pixels instead of repeating the border, which is what stops a bright edge from smearing
 * inward. L1's arithmetic, kept exactly.
 */
private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lineCount = if (horizontal) h else w
    val lineLen = if (horizontal) w else h
    val step = if (horizontal) 1 else w
    for (line in 0 until lineCount) {
        val base = if (horizontal) line * w else line
        var sa = 0
        var sr = 0
        var sg = 0
        var sb = 0
        // Seed with only the in-bounds pixels of the first window, [0 .. min(radius, lineLen - 1)].
        var count = 0
        val initialHi = minOf(radius, lineLen - 1)
        for (j in 0..initialHi) {
            val p = src[base + j * step]
            sa += (p ushr 24) and 0xFF
            sr += (p ushr 16) and 0xFF
            sg += (p ushr 8) and 0xFF
            sb += p and 0xFF
            count++
        }
        for (i in 0 until lineLen) {
            dst[base + i * step] =
                ((sa / count) shl 24) or ((sr / count) shl 16) or ((sg / count) shl 8) or (sb / count)
            val outIdx = i - radius
            if (outIdx >= 0) {
                val p = src[base + outIdx * step]
                sa -= (p ushr 24) and 0xFF
                sr -= (p ushr 16) and 0xFF
                sg -= (p ushr 8) and 0xFF
                sb -= p and 0xFF
                count--
            }
            val inIdx = i + radius + 1
            if (inIdx < lineLen) {
                val p = src[base + inIdx * step]
                sa += (p ushr 24) and 0xFF
                sr += (p ushr 16) and 0xFF
                sg += (p ushr 8) and 0xFF
                sb += p and 0xFF
                count++
            }
        }
    }
}
