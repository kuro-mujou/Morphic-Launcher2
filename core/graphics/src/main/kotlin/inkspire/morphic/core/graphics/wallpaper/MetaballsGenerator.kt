package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A handful of seeded charges whose potential fields merge into gooey blobs, banded through the palette — the
 * lava-lamp *Flowing Blobs* (gart's `arts/blob`, `arts/plasma/plasmeander`).
 *
 * **A metaball is a potential field, not a circle — which is why the blobs *merge*.** Each charge adds
 * `radius² / distance²` to every pixel, so two charges near each other sum to a field that bulges and joins between
 * them into one shape with a pinched waist — the defining metaball look a plain disc cannot give. The field is then
 * **banded** (like [ContourGenerator], but on this potential instead of noise) so the blobs read as glowing onion
 * layers rather than a smooth wash — the difference from [MeshGradientGenerator], which blends colors and never draws
 * an edge.
 *
 * **The bands run bright core to dark ground**: high field near a charge takes the last stops, empty space the first,
 * so the blobs glow. [DesignParams.density] sets how many charges. Deterministic in [seed].
 *
 * [field] and [band] are pure and tested — the summed potential is `Double` arithmetic that is silently wrong (blobs
 * that never merge, or a field that saturates flat) with no bitmap needed to see it.
 */
object MetaballsGenerator : Generator {

    /** One charge: where it sits in the unit square, and its radius (its pull). */
    internal data class Charge(val x: Float, val y: Float, val radius: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val charges = charges(chargeCount(params.density), seed)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                // A flat palette *stop*, not an interpolated ramp — the hard steps between stops are the onion rings.
                pixels[y * width + x] = palette.colorAt(band(field(nx, ny, charges), palette.size))
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many charges [density] asks for — [MinCharges] a few fat blobs up to [MaxCharges] a busy lamp. */
    internal fun chargeCount(density: Float): Int =
        MinCharges + (density.coerceIn(0f, 1f) * (MaxCharges - MinCharges)).roundToInt()

    /** [count] charges for [seed] — positions in the unit square, radii spread over [MinRadius]..[MaxRadius]. */
    internal fun charges(count: Int, seed: Long): List<Charge> {
        val random = Random(seed)
        return List(count) {
            Charge(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = MinRadius + random.nextFloat() * (MaxRadius - MinRadius),
            )
        }
    }

    /**
     * The summed metaball potential at ([nx], [ny]) — `Σ radius² / (distance² + ε)`. The `ε` keeps a pixel sitting on a
     * charge finite; the sum is unbounded above, so the caller maps it, not this.
     */
    internal fun field(nx: Float, ny: Float, charges: List<Charge>): Float {
        var sum = 0f
        for (charge in charges) {
            val dx = nx - charge.x
            val dy = ny - charge.y
            sum += (charge.radius * charge.radius) / (dx * dx + dy * dy + Softness)
        }
        return sum
    }

    /**
     * A potential in `0..∞` mapped to a **discrete palette stop index**, `0 until [stops]`. First the field is rolled
     * to `0..1` by `field / (field + 1)` — a smooth curve that reaches 1 only at a charge's center and stays near 0 in
     * empty space — then snapped to a stop. The hard step between one stop and the next is what draws the glowing onion
     * ring of a metaball rather than the blurry wash a ramp would give (the visible difference from
     * [MeshGradientGenerator], which interpolates and never bands).
     */
    internal fun band(field: Float, stops: Int): Int {
        val smooth = field / (field + 1f)
        return (smooth * stops).toInt().coerceIn(0, stops - 1)
    }

    private const val MinCharges = 3
    private const val MaxCharges = 9

    /** A charge's radius range, as a fraction of the frame — its pull, and so how far it reaches to merge with another. */
    private const val MinRadius = 0.12f
    private const val MaxRadius = 0.24f

    /** The `ε` that keeps a pixel on a charge's center finite rather than dividing by zero. */
    private const val Softness = 0.0008f
}
