package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Horizontal bands of flat palette color separated by wavy seams, all undulating in unison — *Wave Dividers*.
 *
 * **[DiagonalBandsGenerator]'s bands with a wave under them.** The same variable-width banding ([Bands]), the same
 * cycling color, but the dividers are not straight: a per-column offset — a sum of a couple of sines across the frame —
 * shifts the whole stack of boundaries up and down together, so the bands ripple as parallel waves rather than lie flat.
 * That is the one difference from Diagonal Bands, and the difference from [WavesGenerator]: Waves stacks *overlapping*
 * filled dunes rising from a baseline, this is clean *adjacent* bands whose shared seams happen to wave.
 *
 * **The offset depends only on the column, so a whole vertical slice shifts as one** — which is what keeps the bands
 * parallel instead of smearing into noise. [DesignParams.density] sets how many bands, [DesignParams.irregularity] how
 * deep the waves cut — near-flat at `0`, deeply rolling at `1` (Smart Launcher's *Wideness*). Deterministic in [seed].
 *
 * [bandCount] and [waveOffset] are pure and tested — an offset that runs past the band heights would fold the stack
 * over itself, and it needs no bitmap to bound.
 */
object WaveDividersGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Bands* slider's own range. */
    private val Amount = AmountKnob.Count("Bands", 4..16)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Wave depth",
    )

    /** One sine term of the divider wave: how tall, how many cycles across the frame, and where it starts. */
    internal data class Term(val amplitude: Float, val frequency: Float, val phase: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val count = bandCount(params.density)
        val boundaries = Bands.boundaries(count, params.irregularity, seed)
        val terms = terms(params.irregularity, seed)

        // The wave offset for each column, computed once — the whole vertical slice shares it, keeping the bands parallel.
        val offsets = FloatArray(width) { x ->
            val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            waveOffset(nx, terms)
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                // Shift the pixel against the column's offset: the band whose flat boundary brackets the shifted position
                // is the one whose wavy boundary brackets this pixel.
                val band = Bands.bandAt(ny - offsets[x], boundaries)
                pixels[y * width + x] = palette.colorAt(band % palette.size)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many bands [density] asks for — a few broad waves up to a finely rippled stack. */
    internal fun bandCount(density: Float): Int = Amount.at(density)

    /**
     * The divider wave's sine terms for [seed], their amplitude scaled by [irregularity] — [Terms] terms of shrinking
     * amplitude (a broad swell plus a ripple). At `irregularity = 0` the amplitude is [MinAmplitudeScale] of the base
     * (near-flat bands); it grows to [MaxAmplitudeScale] at `1` (deeply rolling).
     */
    internal fun terms(irregularity: Float, seed: Long): List<Term> {
        val random = Random(seed)
        val scale = MinAmplitudeScale + irregularity.coerceIn(0f, 1f) * (MaxAmplitudeScale - MinAmplitudeScale)
        val twoPi = (2.0 * PI).toFloat()
        return List(Terms) {
            Term(
                amplitude = BaseAmplitude / (it + 1) * scale,
                frequency = MinCycles + random.nextFloat() * (MaxCycles - MinCycles),
                phase = random.nextFloat() * twoPi,
            )
        }
    }

    /** The wave's vertical offset at column position [nx] (`0..1`) — the summed sines, in the same units as a band height. */
    internal fun waveOffset(nx: Float, terms: List<Term>): Float {
        var offset = 0f
        for (term in terms) offset += term.amplitude * sin(nx * term.frequency + term.phase)
        return offset
    }

    /** How many sine terms sum into the divider wave — one swell plus one ripple. */
    private const val Terms = 2

    /** The broadest sine's amplitude, as a fraction of the frame height — the swell of the wave. */
    private const val BaseAmplitude = 0.06f

    /** The wave amplitude scale at the irregularity extremes — near-flat to deeply rolling. */
    private const val MinAmplitudeScale = 0.2f
    private const val MaxAmplitudeScale = 1.6f

    /** How many cycles a term spans across the frame — a wide roll up to a few waves. */
    private const val MinCycles = 1.5f
    private const val MaxCycles = 3.5f
}
