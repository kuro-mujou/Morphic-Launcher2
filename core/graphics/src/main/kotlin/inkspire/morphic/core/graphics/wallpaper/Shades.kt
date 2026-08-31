package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.roundToInt

/**
 * Scales an ARGB color's brightness — the shared darken behind the designs that fake depth with light
 * ([GradientColumnsGenerator]'s seam shadow, [RibbedGlassGenerator]'s rib lens).
 *
 * **One place for the channel math, extracted on the second consumer.** Multiplying the RGB channels of a packed color
 * while carrying alpha through is exactly the arithmetic that tints a whole wallpaper when a channel is transposed or the
 * alpha is dropped — the same reason [LinearGradientGenerator.lerpArgb] is shared — so the two designs that shade a
 * color both call this rather than each risking it.
 */
object Shades {

    /** [argb] with its color channels scaled by [factor] (`0..1` darkens toward black), the alpha kept unchanged. */
    fun scale(argb: Int, factor: Float): Int {
        val a = argb ushr 24 and 0xFF
        val r = ((argb shr 16 and 0xFF) * factor).roundToInt().coerceIn(0, 0xFF)
        val g = ((argb shr 8 and 0xFF) * factor).roundToInt().coerceIn(0, 0xFF)
        val b = ((argb and 0xFF) * factor).roundToInt().coerceIn(0, 0xFF)
        val packed = (a shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }
}
