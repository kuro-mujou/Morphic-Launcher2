package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import kotlin.math.roundToInt

/**
 * Reduces a [Palette] to the colors a [WallpaperColorMode] allows — the one place the studio's restraint knob is
 * turned into actual colors.
 *
 * **Applied before a generator runs, so every generator honors the mode for free.** A design does not branch on the
 * mode; it is simply handed a two-color palette for bichromatic, a one-hue ramp for monochromatic, or the palette
 * untouched for colorful, and it draws what it always draws. That is what lets sixteen generators become restrainable
 * with no per-design code — the shared-derivation rule, applied to color instead of geometry.
 *
 * **The light-to-dark ordering is preserved on purpose.** Generators lean on it — the last stop is a dark ground for
 * flow/blobs/confetti, the first a light sky for waves — so every mode returns its colors in that same order and those
 * roles keep working whatever the mode.
 *
 * Pure `IntArray` color math, checkable without a bitmap: a shade ramp that drifts hue, or a two-color pick that lands
 * on the same color twice, is silently wrong and needs no emulator to catch.
 */
object PaletteColorMode {

    /** [palette] reduced to what [mode] permits, colors kept light-to-dark. A palette too short to reduce is returned as is. */
    fun resolve(palette: Palette, mode: WallpaperColorMode): Palette = when (mode) {
        WallpaperColorMode.COLORFUL -> palette
        WallpaperColorMode.BICHROMATIC -> bichromatic(palette)
        WallpaperColorMode.MONOCHROMATIC -> monochromatic(palette)
    }

    /**
     * The palette's lightest and darkest stops — its two ends. Chosen over two vivid middles because the ends are what
     * the ordering *means* (a light ground and a dark one), so a generator's light/dark roles survive the reduction;
     * the pairing is stark and legible, the point of the mode. A one-stop palette has no second end and is returned as is.
     */
    internal fun bichromatic(palette: Palette): Palette {
        if (palette.size < 2) return palette
        return Palette(listOf(palette.colorAt(0), palette.colorAt(palette.size - 1)))
    }

    /**
     * One hue in five steps from a light tint to a dark shade. The hue is the palette's **middle** stop — the most
     * likely to carry real chroma, where the ends are often a near-white or a near-black — mixed toward white for the
     * tints and toward black for the shades, so the ramp is one color's own light-to-dark run.
     */
    internal fun monochromatic(palette: Palette): Palette {
        val base = palette.colorAt(palette.size / 2)
        // Symmetric mixes about the base: lightest first (toward white), through the base, to darkest (toward black).
        val mixes = floatArrayOf(0.72f, 0.36f, 0f, -0.36f, -0.68f)
        return Palette(
            mixes.map { m ->
                when {
                    m > 0f -> mix(base, White, m)
                    m < 0f -> mix(base, Black, -m)
                    else -> base
                }
            },
        )
    }

    /** [from] blended [t] of the way to [to], per ARGB channel, alpha carried through. */
    private fun mix(from: Int, to: Int, t: Float): Int {
        val a = channel(from ushr 24, to ushr 24, t)
        val r = channel(from shr 16, to shr 16, t)
        val g = channel(from shr 8, to shr 8, t)
        val b = channel(from, to, t)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(from: Int, to: Int, t: Float): Int {
        val f = from and 0xFF
        return (f + ((to and 0xFF) - f) * t).roundToInt().coerceIn(0, 255)
    }

    private const val White = 0xFFFFFFFF.toInt()
    private const val Black = 0xFF000000.toInt()
}
