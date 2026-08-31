package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt

/**
 * A gradient climbing the frame through the palette's stops — the studio's simplest real design and W0's proof that
 * the whole pipeline (recipe → generator → bitmap) is wired, without any of the gart engine.
 *
 * **It ignores [seed] and [DesignParams], and that is honest rather than lazy.** A gradient through a fixed palette
 * has no variation to seed and no density to tune; a generator reads only the inputs its look depends on. The
 * interface still hands them over because *other* generators need them, and a total interface beats one that grows a
 * parameter list per design.
 *
 * **The color math is [colorAt], pulled out and tested.** Interpolating packed ARGB across a run of stops is the part
 * that is silently wrong when it is wrong — a transposed channel tints the whole wallpaper, an off-by-one at a stop
 * boundary bands it — and it is `IntArray` arithmetic that needs no bitmap, so it lives where it can be checked
 * without an emulator, the same split the icon renderer keeps.
 */
object LinearGradientGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val row = IntArray(width)
        for (y in 0 until height) {
            // Top-to-bottom: the first stop at the top, the last at the bottom. A one-pixel-tall frame is all top.
            val fraction = if (height <= 1) 0f else y.toFloat() / (height - 1)
            row.fill(colorAt(fraction, palette))
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    /**
     * The color [fraction] of the way down the gradient, `0` at the first stop and `1` at the last, interpolated in
     * sRGB between the two stops it falls between.
     *
     * **sRGB, not OKLab, for now.** A perceptual ramp (the `Oklab` work the tritone effect added) is the eventual
     * home for this — a two-stop sRGB blend dips through a muddy midpoint — but reaching it means sharing `Oklab`
     * across `core:icon` and here, which is a relocation this skeleton does not need. Left plain, and marked.
     */
    internal fun colorAt(fraction: Float, palette: Palette): Int {
        val f = fraction.coerceIn(0f, 1f)
        if (palette.size <= 1) return palette.colorAt(0)

        // Where along the run of stops the fraction lands, and the two stops bracketing it.
        val scaled = f * (palette.size - 1)
        val lower = scaled.toInt().coerceIn(0, palette.size - 1)
        val upper = (lower + 1).coerceAtMost(palette.size - 1)
        return lerpArgb(palette.colorAt(lower), palette.colorAt(upper), scaled - lower)
    }

    /**
     * [fraction] of the way through the palette as a **loop** — the last stop rejoined to the first — so a value
     * rolling past the end bands back to the start seamlessly instead of hitting a hard edge at the final stop. The
     * looped sibling of [colorAt], which stops at the ends; shared by [PlasmaGenerator] and [RingsGenerator], whose
     * fields both wrap.
     */
    internal fun colorLooping(fraction: Float, palette: Palette): Int {
        if (palette.size <= 1) return palette.colorAt(0)
        // Map 0..1 across size stops that wrap: the segment after the last stop returns to the first.
        val scaled = fraction.coerceIn(0f, 1f) * palette.size
        val lower = scaled.toInt() % palette.size
        val upper = (lower + 1) % palette.size
        return lerpArgb(palette.colorAt(lower), palette.colorAt(upper), scaled - scaled.toInt())
    }

    /**
     * [from] blended [t] of the way to [to], every ARGB channel — alpha included, since a palette stop may be
     * translucent. Shared with [colorLooping] and the generators that loop the ramp: interpolating packed ARGB is the
     * arithmetic that tints a whole wallpaper when a channel is transposed, so every ramp blends through this one
     * function rather than each risking it.
     */
    internal fun lerpArgb(from: Int, to: Int, t: Float): Int {
        val a = lerpChannel(from ushr 24 and 0xFF, to ushr 24 and 0xFF, t)
        val r = lerpChannel(from shr 16 and 0xFF, to shr 16 and 0xFF, t)
        val g = lerpChannel(from shr 8 and 0xFF, to shr 8 and 0xFF, t)
        val b = lerpChannel(from and 0xFF, to and 0xFF, t)
        val packed = (a shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }

    /** One channel byte, [from] moved [t] toward [to], clamped. */
    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, ChannelMax)

    private const val ChannelMax = 255
}
