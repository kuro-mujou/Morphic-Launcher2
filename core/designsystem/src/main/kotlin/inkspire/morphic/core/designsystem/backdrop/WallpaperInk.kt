package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.luminance
import inkspire.morphic.core.designsystem.theme.MorphicColors
import inkspire.morphic.core.model.wallpaper.WallpaperBrightness
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What text drawn straight onto one spot of the wallpaper needs in order to be read there.
 *
 * @property light whether the light ink wins — the dark palette's content — rather than the dark one.
 * @property backingAlpha how opaque the rounded backing behind the text has to be; `0f` where the ink alone reads.
 */
@Immutable
data class Ink(val light: Boolean, val backingAlpha: Float)

/**
 * The luminances [inkOver] weighs, read off [MorphicColors] so the rule scores the colors that are actually painted:
 * each ink, and the backing drawn behind it — which is the ink's own palette's `background`.
 */
class InkPalette(val lightInk: Float, val lightBacking: Float, val darkInk: Float, val darkBacking: Float) {
    companion object {
        val Morphic = InkPalette(
            lightInk = MorphicColors.Dark.content.luminance(),
            lightBacking = MorphicColors.Dark.background.luminance(),
            darkInk = MorphicColors.Light.content.luminance(),
            darkBacking = MorphicColors.Light.background.luminance(),
        )
    }
}

/** WCAG AA for body text. Small launcher labels are exactly the text it was written for. */
const val InkContrastTarget = 4.5f

/** A backing past this stops reading as a soft lift and starts reading as a chip, so a spot is left short instead. */
private const val MaxBackingAlpha = 0.6f

/** How much better the other ink has to score before a spot that already shows one switches — see [inkOver]. */
private const val InkHysteresis = 1.25f

/** Which of a spot's cells count as its dark and bright ends — see [inkOver] for why not the extremes. */
private const val LowPercentile = 0.05f
private const val HighPercentile = 0.95f

/**
 * The [Ink] for a spot whose cells have the luminances in the first [count] entries of [samples]. **Sorts them in
 * place.**
 *
 * **Judged by the worst patch, not the average.** A label across sky and flowers is not half readable; it is unreadable
 * where the flowers are. So each ink is scored against the patch hardest *for it* — the bright end for light ink, the
 * dark end for dark ink — and the better of the two wins. Percentiles rather than the extremes, so one white petal under
 * a white label does not decide it. A tie goes to light ink: its halo still rescues it over a bright patch.
 *
 * **The backing is sized, not switched** — just opaque enough to lift that worst patch to [InkContrastTarget], computed
 * in sRGB because that is the space it is composited in. A spot the ink already reads on gets none, which keeps the
 * backing to spots that straddle a boundary, and a label swiped across one fades it rather than popping it.
 *
 * @param current the ink this spot already shows, or null for a first reading. The incumbent keeps winning until the
 *   other ink scores [InkHysteresis] times better, so a label parked on a boundary does not flip on every frame.
 */
fun inkOver(
    samples: FloatArray,
    count: Int = samples.size,
    palette: InkPalette = InkPalette.Morphic,
    current: Boolean? = null,
): Ink {
    if (count <= 0) return Ink(light = true, backingAlpha = 0f)
    samples.sort(0, count)
    val dark = samples[((count - 1) * LowPercentile).roundToInt()]
    val bright = samples[((count - 1) * HighPercentile).roundToInt()]
    val lightScore = contrast(palette.lightInk, bright)
    val darkScore = contrast(palette.darkInk, dark)
    val light = when (current) {
        null -> lightScore >= darkScore
        true -> lightScore * InkHysteresis >= darkScore
        false -> lightScore > darkScore * InkHysteresis
    }
    return if (light) {
        Ink(light = true, backingAlpha = backingFor(bright, palette.lightInk, palette.lightBacking))
    } else {
        Ink(light = false, backingAlpha = backingFor(dark, palette.darkInk, palette.darkBacking))
    }
}

/**
 * Whether HOME **as a whole** wants light ink — the theme for everything on it that is not placed spot by spot.
 *
 * A measured picture takes [inkOver] over every cell, so the screen is judged by the same rule as a label rather than
 * by an average that a half-dark picture always lands in the middle of. A reported one takes the system's verdict as
 * it is: dark ink only when the whole picture is bright with almost no dark area.
 */
fun WallpaperBrightness.wantsLightInk(): Boolean = when (this) {
    is WallpaperBrightness.Measured -> inkOver(map.toFloatArray()).light
    is WallpaperBrightness.Reported -> !supportsDarkText
}

/** The WCAG contrast ratio of two relative luminances, in either order. */
internal fun contrast(a: Float, b: Float): Float = (max(a, b) + ContrastFlare) / (min(a, b) + ContrastFlare)

/** WCAG's allowance for ambient flare, added to both sides of a contrast ratio. */
private const val ContrastFlare = 0.05f

/** The sRGB transfer function's constants (IEC 61966-2-1). */
private const val SrgbLinearLimit = 0.0031308f
private const val SrgbToeSlope = 12.92f
private const val SrgbScale = 1.055f
private const val SrgbOffset = 0.055f
private const val SrgbInverseGamma = 1 / 2.4

/**
 * The least alpha of a [backing] layer over a [patch] that brings [ink] to [InkContrastTarget] against it, capped at
 * [MaxBackingAlpha] where the target cannot be reached.
 */
private fun backingFor(patch: Float, ink: Float, backing: Float): Float {
    if (contrast(ink, patch) >= InkContrastTarget) return 0f
    // The luminance the patch has to be pushed to, on the far side from the ink.
    val goal = if (backing < ink) {
        (ink + ContrastFlare) / InkContrastTarget - ContrastFlare
    } else {
        InkContrastTarget * (ink + ContrastFlare) - ContrastFlare
    }
    val from = srgb(patch)
    val layer = srgb(backing)
    if (layer == from) return MaxBackingAlpha
    return ((srgb(goal.coerceIn(0f, 1f)) - from) / (layer - from)).coerceIn(0f, MaxBackingAlpha)
}

/** A linear luminance back in sRGB's gamma — the space a translucent layer is blended in. */
private fun srgb(linear: Float): Float = if (linear <= SrgbLinearLimit) {
    linear * SrgbToeSlope
} else {
    SrgbScale * linear.toDouble().pow(SrgbInverseGamma).toFloat() - SrgbOffset
}
