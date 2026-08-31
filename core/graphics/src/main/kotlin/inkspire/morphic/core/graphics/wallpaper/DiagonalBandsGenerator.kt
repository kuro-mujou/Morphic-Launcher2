package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette

/**
 * Parallel bands of flat palette color marching across the frame — *Diagonal Bands*, the calmest staple. gart's
 * `arts/stripes`.
 *
 * **The trivial-but-restrained design the loud generators are measured against.** Every pixel is projected onto one axis
 * and the position on that axis picks a band; the band picks a flat palette color, cycling. There is no noise, no
 * geometry, no overdraw — a stripe pattern — which is exactly why it reads as composed rather than busy, and why in
 * bichromatic it is a clean two-tone that the wallpaper's own content can sit on.
 *
 * **[DesignParams.variant] sets the direction** — `0` a `↘` diagonal (the default and the design's name), `1` a `↗`
 * diagonal, `2` vertical, `3` horizontal. [DesignParams.density] sets how many bands, and [DesignParams.irregularity]
 * how uneven their widths are — perfectly even at `0`, a jittered, hand-torn set at `1` (Smart Launcher's *Variation*).
 * Deterministic in [seed].
 *
 * The variable-width banding is [Bands], shared with the columns; [project] is this design's own — which axis a pixel
 * is measured along.
 */
object DiagonalBandsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Bands* slider's own range. */
    private val Amount = AmountKnob.Count("Bands", 4..22)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Variation",
        variant = VariantKnob("Direction", listOf("Diagonal", "Reverse", "Vertical", "Horizontal")),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val count = bandCount(params.density)
        val boundaries = Bands.boundaries(count, params.irregularity, seed)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                val band = Bands.bandAt(project(nx, ny, params.variant), boundaries)
                pixels[y * width + x] = palette.colorAt(band % palette.size)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many bands [density] asks for — a few bold stripes up to a fine set. */
    internal fun bandCount(density: Float): Int = Amount.at(density)

    /**
     * The pixel at ([nx], [ny]) projected onto the band axis for [variant], normalized to `0..1` — the position that
     * picks its band. `0` a `↘` diagonal, `1` a `↗` diagonal, `2` vertical (by `nx`), `3` horizontal (by `ny`); any
     * other index falls to the diagonal.
     */
    internal fun project(nx: Float, ny: Float, variant: Int): Float = when (variant) {
        VariantDiagonalUp -> (nx - ny + 1f) / 2f // ↗ : nx - ny ranges -1..1, shifted to 0..1
        VariantVertical -> nx
        VariantHorizontal -> ny
        else -> (nx + ny) / 2f // ↘ (variant 0, the default) : nx + ny ranges 0..2
    }

    /** [DesignParams.variant] direction indices — `0` (the default) is the `↘` diagonal, handled by the `else` branch. */
    private const val VariantDiagonalUp = 1
    private const val VariantVertical = 2
    private const val VariantHorizontal = 3
}
