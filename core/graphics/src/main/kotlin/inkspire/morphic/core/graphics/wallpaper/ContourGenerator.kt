package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette

/**
 * A noise field read as a map — inked contour lines on bare paper by default, or filled height bands — the *Topography*
 * look (gart's `arts/layers` relief / `plasmeander` contour tracing).
 *
 * **Iso-lines, not traced polylines.** A textbook topographic map runs marching squares to extract each contour as a
 * path; a wallpaper needs only the *look* of one, which falls out of the field for free. Sample [PerlinNoise2d] at each
 * pixel and snap the value to one of N bands; a pixel whose band differs from a neighbour's is a contour line and takes
 * the palette's darkest stop, and every other pixel is the bare paper (the default) or its band's fill color (the
 * variant). Same trick [VoronoiGenerator] uses for its seams, on a scalar field instead of a nearest-seed one — no
 * geometry to get wrong.
 *
 * **Two octaves of noise, so the map has both broad landmasses and finer coastline.** One octave is a smooth blob;
 * adding a half-scale octave gives the ridged detail that reads as terrain, and [DesignParams.irregularity] sets *how
 * much* of that octave is mixed in — broad smooth elevations at `0`, a crinkled ridged survey at `1` (Smart Launcher's
 * *Variation*). [DesignParams.density] sets how many bands there are — a few bold elevations or a densely-lined survey
 * map. Deterministic in [seed] (the noise is).
 *
 * **[DesignParams.variant] chooses the look: lines on paper (`0`, the default) or filled bands (`1`).** The default is
 * the thin-line *Contour-lines* Topography — Smart Launcher's own default here, the community favorite, and the reason
 * this design leads the thin-line family; the variant is the older filled relief, its bands colored up the palette ramp.
 * Both fall out of the same banded field for free: the same boundary detection, over the lightest stop or over a filled
 * ramp.
 *
 * [bandCount] and [band] are pure and tested: which band a height falls in is the off-by-one that either doubles a
 * contour or drops one, and it needs no bitmap to check.
 */
object ContourGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Levels* slider's own range. */
    private val Amount = AmountKnob.Count("Levels", 5..18)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Variation",
        variant = VariantKnob("Look", listOf("Lines", "Filled")),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val noise = PerlinNoise2d(seed)
        val bands = bandCount(params.density)
        val detailWeight = params.irregularity.coerceIn(0f, 1f) // 0.5 → the shipped half-weight detail octave
        val filled = params.variant == VariantFilled
        val line = palette.colorAt(palette.size - 1) // darkest stop by convention — the inked contour
        val paper = palette.colorAt(0) // lightest stop — the ground the lines are drawn on in the default lines look

        // The band each pixel's height falls in, one pass, so the next can find a boundary by comparing neighbours.
        val bandOf = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                bandOf[y * width + x] = band(heightAt(nx, ny, noise, detailWeight), bands)
            }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                pixels[i] = when {
                    onContour(bandOf, x, y, width, height) -> line
                    // Filled variant: the band's color, its index as a fraction of the ramp (low ground first, high last).
                    filled -> LinearGradientGenerator.colorAt(bandOf[i].toFloat() / (bands - 1).coerceAtLeast(1), palette)
                    // Default lines look: bare paper between the inked contours — a survey map, no fill.
                    else -> paper
                }
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many elevation bands [density] asks for — bold steps up to a fine survey. */
    internal fun bandCount(density: Float): Int = Amount.at(density)

    /**
     * Which band a height in `0..1` falls in, `0 until [bands]`. **Clamped at the top**, so a height of exactly `1`
     * lands in the last band rather than overflowing into a band that does not exist.
     */
    internal fun band(height: Float, bands: Int): Int =
        (height.coerceIn(0f, 1f) * bands).toInt().coerceIn(0, bands - 1)

    /**
     * The terrain height at ([nx], [ny]) in `0..1` — a broad octave plus a half-scale detail octave weighted by
     * [detailWeight], mapped from the noise's `-1..1` into `0..1`. The combined field spans `±(1 + detailWeight)`, so it
     * is normalized by that half-range, keeping the result in `0..1` for any weight rather than only the shipped `0.5`.
     */
    private fun heightAt(nx: Float, ny: Float, noise: PerlinNoise2d, detailWeight: Float): Float {
        val broad = noise.at(nx * BroadFrequency, ny * BroadFrequency)
        val detail = noise.at(nx * BroadFrequency * DetailOctave, ny * BroadFrequency * DetailOctave)
        val combined = broad + detail * detailWeight
        val halfRange = 1f + detailWeight
        val normalized = combined / halfRange * 0.5f + 0.5f
        return normalized.coerceIn(0f, 1f)
    }

    /** Whether the pixel at ([x], [y]) sits on a band boundary — its band differs from a four-neighbour's. */
    private fun onContour(bandOf: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        val here = bandOf[y * width + x]
        if (x > 0 && bandOf[y * width + (x - 1)] != here) return true
        if (x < width - 1 && bandOf[y * width + (x + 1)] != here) return true
        if (y > 0 && bandOf[(y - 1) * width + x] != here) return true
        if (y < height - 1 && bandOf[(y + 1) * width + x] != here) return true
        return false
    }

    /** [DesignParams.variant] selecting the filled-bands look — the colored relief — over the default lines on paper. */
    private const val VariantFilled = 1

    /** How many noise cycles span the frame at the broad octave — the size of the landmasses. */
    private const val BroadFrequency = 3f

    /** The detail octave's frequency relative to the broad one — half-scale, so twice the cycles. */
    private const val DetailOctave = 2f
}
