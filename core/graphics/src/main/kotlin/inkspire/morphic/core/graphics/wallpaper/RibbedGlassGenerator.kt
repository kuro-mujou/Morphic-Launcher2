package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.floor

/**
 * A palette gradient seen through fluted glass — vertical ribs that refract and shade the light behind them, *Ribbed
 * Glass* (gart's `arts/shad`).
 *
 * **A background gradient, resampled per rib like a row of cylindrical lenses.** Behind the glass is a smooth diagonal
 * palette gradient. Each vertical rib acts as a lens: across its width the horizontal sampling is compressed onto a
 * narrow window around the rib's center, so the rib shows a *magnified* slice of the background, and neighbouring ribs
 * step across it — the sliced, offset repeat that reads as reeded glass. A specular lens darkens each rib toward its
 * edges and leaves it bright down the middle, which is the sheen that sells it as glass rather than as columns (the flat
 * panels of [GradientColumnsGenerator]).
 *
 * **[DesignParams.irregularity] is the refraction depth** — a faint fluting at `0`, a strong lens that fans the
 * background wide at `1`. [DesignParams.density] sets how many ribs. There is no seed dependence worth having (the glass
 * is a deterministic function of the gradient and the rib count), so it renders the same for any [seed].
 *
 * [ribCount] and [refraction] are the pure mappings; the gradient is [LinearGradientGenerator]'s and the lens shading is
 * judged in the render harness.
 */
object RibbedGlassGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Ribs* slider's own range. */
    private val Amount = AmountKnob.Count("Ribs", 8..28)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Refraction",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val ribs = ribCount(params.density)
        val refraction = refraction(params.irregularity)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                val scaled = nx * ribs
                val rib = floor(scaled).toInt().coerceIn(0, ribs - 1)
                val localX = scaled - rib // 0..1 across this rib
                val ribCenter = (rib + 0.5f) / ribs
                // The lens compresses the rib's width onto a narrow window of the background around its center.
                val sampleX = (ribCenter + (localX - 0.5f) * refraction).coerceIn(0f, 1f)
                val base = LinearGradientGenerator.colorAt((sampleX + ny) / 2f, palette) // diagonal background
                // Specular lens: bright down the rib's center, darkening to its edges — the glass sheen.
                val d = 2f * (localX - 0.5f)
                pixels[y * width + x] = Shades.scale(base, 1f - LensDepth * d * d)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many ribs [density] asks for — broad flutes up to a fine reeding. */
    internal fun ribCount(density: Float): Int = Amount.at(density)

    /**
     * How wide a slice of the background each rib fans across, for a given [irregularity] — [MinRefraction] a faint
     * fluting up to [MaxRefraction] a strong lens. Never zero, so the ribs are always visible glass rather than a plain
     * gradient at the low end.
     */
    internal fun refraction(irregularity: Float): Float =
        MinRefraction + irregularity.coerceIn(0f, 1f) * (MaxRefraction - MinRefraction)

    /** The refraction window at the irregularity extremes, as a fraction of the background width. */
    private const val MinRefraction = 0.05f
    private const val MaxRefraction = 0.24f

    /** How far a rib darkens at its edges — the depth of the specular valley between ribs. */
    private const val LensDepth = 0.5f
}
