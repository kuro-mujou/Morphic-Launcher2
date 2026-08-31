package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Hard-edged wedges of palette color fanning out from an off-center point — the sunburst / *Rays* (gart's `arts/rayz`,
 * `arts/sf`).
 *
 * **The angular sibling of [RingsGenerator].** Rings band a pixel's *distance* from a center; Rays band its *angle*.
 * Each pixel takes the palette stop for the wedge its bearing falls in, as a **flat** color with a hard edge to the
 * next wedge — a starburst, where Rings gives smooth haloes. No geometry, just `atan2` — a cheap full-screen pass.
 *
 * **The center is seeded and off-center**, so the fan sweeps asymmetrically like light through a gap rather than a
 * symmetrical pinwheel. [DesignParams.density] sets how many wedges. Deterministic in [seed].
 *
 * [wedge] is pure and tested: mapping a bearing to a wedge index is the arithmetic that decides whether the rays meet
 * cleanly at the center or tear, and it needs no bitmap.
 */
object RaysGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val random = Random(seed)
        val cx = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val cy = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val rays = rayCount(params.density)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                // A flat palette stop per wedge, cycling — the hard step between stops is the ray edge.
                pixels[y * width + x] = palette.colorAt(wedge(nx, ny, cx, cy, rays) % palette.size)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many wedges [density] asks for — [MinRays] a few broad fans up to [MaxRays] a fine starburst. */
    internal fun rayCount(density: Float): Int =
        MinRays + (density.coerceIn(0f, 1f) * (MaxRays - MinRays)).roundToInt()

    /**
     * Which wedge the bearing from ([cx], [cy]) to ([nx], [ny]) falls in, `0 until [rays]`. The angle from `atan2`
     * (`-π..π`) is normalized to `0..1` first, so the wedges tile the full turn and the first meets the last cleanly.
     */
    internal fun wedge(nx: Float, ny: Float, cx: Float, cy: Float, rays: Int): Int {
        val angle = atan2(ny - cy, nx - cx) // -π..π
        val normalized = (angle / (2f * PI.toFloat())) + 0.5f // 0..1
        return (normalized * rays).toInt().coerceIn(0, rays - 1)
    }

    // Softened toward broad fans: the default density now opens on a few wide wedges rather than a fine starburst (W7).
    private const val MinRays = 4
    private const val MaxRays = 16

    /** How far off the frame's center the fan's origin is kept, so the rays sweep asymmetrically across it. */
    private const val CenterInset = 0.2f
}
