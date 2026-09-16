package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.ui.graphics.luminance
import inkspire.morphic.core.designsystem.theme.MorphicColors
import inkspire.morphic.core.model.wallpaper.WallpaperBrightness
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The luminances [inkOver] weighs, read off [MorphicColors] so the rule scores the inks that are actually painted. */
class InkPalette(val lightInk: Float, val darkInk: Float) {
    companion object {
        val Morphic = InkPalette(
            lightInk = MorphicColors.Dark.content.luminance(),
            darkInk = MorphicColors.Light.content.luminance(),
        )
    }
}

/** How much better the other ink has to score before a spot that already shows one switches — see [inkOver]. */
private const val InkHysteresis = 1.25f

/** Which of a spot's cells count as its dark and bright ends — see [inkOver] for why not the extremes. */
private const val LowPercentile = 0.05f
private const val HighPercentile = 0.95f

/**
 * Whether **light ink** wins over a spot whose cells have the luminances in the first [count] entries of [samples].
 * **Sorts them in place.**
 *
 * **Judged by the worst patch, not the average.** A label across sky and flowers is not half readable; it is unreadable
 * where the flowers are. So each ink is scored against the patch hardest *for it* — the bright end for light ink, the
 * dark end for dark ink — and the better of the two wins. Percentiles rather than the extremes, so one white petal under
 * a white label does not decide it. A tie goes to light ink. Where neither ink reads everywhere, the glow around the
 * text ([inkGlow]) carries the rest.
 *
 * @param current the ink this spot already shows, or null for a first reading. The incumbent keeps winning until the
 *   other ink scores [InkHysteresis] times better, so a label parked on a boundary does not flip on every frame.
 */
fun inkOver(
    samples: FloatArray,
    count: Int = samples.size,
    palette: InkPalette = InkPalette.Morphic,
    current: Boolean? = null,
): Boolean {
    if (count <= 0) return true
    samples.sort(0, count)
    val dark = samples[((count - 1) * LowPercentile).roundToInt()]
    val bright = samples[((count - 1) * HighPercentile).roundToInt()]
    val lightScore = contrast(palette.lightInk, bright)
    val darkScore = contrast(palette.darkInk, dark)
    return when (current) {
        null -> lightScore >= darkScore
        true -> lightScore * InkHysteresis >= darkScore
        false -> lightScore > darkScore * InkHysteresis
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
    is WallpaperBrightness.Measured -> inkOver(map.toFloatArray())
    is WallpaperBrightness.Reported -> !supportsDarkText
}

/** The WCAG contrast ratio of two relative luminances, in either order. */
internal fun contrast(a: Float, b: Float): Float = (max(a, b) + ContrastFlare) / (min(a, b) + ContrastFlare)

/** WCAG's allowance for ambient flare, added to both sides of a contrast ratio. */
private const val ContrastFlare = 0.05f
