package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The old-school demoscene *plasma* — overlapping sine waves summed into a rippling field, read through the palette.
 *
 * **Summed sinusoids, the classic recipe, ported from gart's `arts/plasma`.** Each pixel's value is a sum of a few
 * sine terms — one along x, one along y, one along the diagonal, one radial from the center — and that scalar picks a
 * color off the palette ramp. Where the waves reinforce the value is high, where they cancel it is low, and the
 * interference is the marbled plasma. gart animates it by drifting phases per frame; a wallpaper is one still frame, so
 * the phases are drawn *once* from [seed] — which is what makes it deterministic and a shuffle a new still.
 *
 * **The value wraps through the palette rather than clamping.** Plasma's charm is the banding where the field rolls
 * past the last stop and back to the first, so [sample] returns a value taken **mod 1**, and the ramp is sampled as a
 * loop (last stop back to the first) so there is no seam at the wrap. [DesignParams.density] sets the wave frequency —
 * broad swells or a busy ripple.
 *
 * [sample] is pure and tested: the summed-sine field is arithmetic that is silently wrong (a flat or banded-wrong
 * wallpaper) with no bitmap needed to see it.
 */
object PlasmaGenerator : Generator {

    /** The phase offsets that make one plasma still distinct from another — drawn once from the seed. */
    internal data class Phases(val x: Float, val y: Float, val diagonal: Float, val radial: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val phases = phases(seed)
        val frequency = frequency(params.density)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                pixels[y * width + x] =
                    LinearGradientGenerator.colorLooping(sample(nx, ny, frequency, phases), palette)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** The wave frequency [density] asks for — [MinFrequency] broad swells up to [MaxFrequency] a busy ripple. */
    internal fun frequency(density: Float): Float =
        MinFrequency + density.coerceIn(0f, 1f) * (MaxFrequency - MinFrequency)

    /** Four phase offsets in `0..2π` for [seed], so each seed is a different still of the same plasma. */
    internal fun phases(seed: Long): Phases {
        val random = Random(seed)
        val twoPi = (2.0 * PI).toFloat()
        return Phases(random.nextFloat() * twoPi, random.nextFloat() * twoPi, random.nextFloat() * twoPi, random.nextFloat() * twoPi)
    }

    /**
     * The plasma value at ([nx], [ny]) in the unit square, **wrapped to `0..1`** — the sum of four sine terms scaled to
     * a loop. The radial term is measured from the center, which is what gives plasma its concentric heart rather than
     * a purely woven look.
     */
    internal fun sample(nx: Float, ny: Float, frequency: Float, phases: Phases): Float {
        val radial = hypot(nx - 0.5f, ny - 0.5f)
        val sum = sin(nx * frequency + phases.x) +
            sin(ny * frequency + phases.y) +
            sin((nx + ny) * frequency * 0.5f + phases.diagonal) +
            sin(radial * frequency + phases.radial)
        // Four terms span [-4, 4]; fold to [0, 1) and let the color wrap, so the banding is the plasma rather than a clip.
        val unit = (sum / 8f) + 0.5f
        return unit - floor(unit)
    }

    // Softened toward broad swells: the default density now opens on calm marbling rather than a busy ripple (W7).
    private const val MinFrequency = 6f
    private const val MaxFrequency = 26f
}
