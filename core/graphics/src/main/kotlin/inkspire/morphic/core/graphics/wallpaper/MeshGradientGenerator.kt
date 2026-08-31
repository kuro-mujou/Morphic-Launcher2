package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt

/**
 * A soft field of color where a handful of seeded points each pull the picture toward their palette color — the
 * lava-lamp blend the studio calls *Mesh Gradient*.
 *
 * **Inverse-distance weighting, not a real gradient mesh.** A true coons-patch mesh is a lot of machinery for a look
 * this reaches with none: every pixel is a blend of the control points weighted by `1/distance²`, so near a point its
 * color dominates and between points they melt together. That is `O(pixels × points)` with a handful of points, and
 * it is smooth everywhere by construction — no seams to hide.
 *
 * **Grid + jitter, not free scatter — the placement is [PointScatter].** The control points sit on a lattice that
 * [DesignParams.irregularity] loosens: even and quilt-like when regular, lava-lamp when scattered. (This is the
 * teardown's correction — Smart Launcher's Mesh is a grid with a Jitter knob, not the uniformly-random points the first
 * cut placed.) [DesignParams.density] sets how many points there are — more points, a busier field. Deterministic in
 * [seed], so the same recipe is the same field every time and a shuffle is a new seed.
 *
 * The blend is [colorAt], pulled out and tested: weighting colors by distance is `IntArray` arithmetic that is
 * silently wrong when a channel is transposed or the alpha is not premultiplied, and it needs no bitmap to check.
 */
object MeshGradientGenerator : Generator {

    /** One control point: where it sits in the unit square, and the color it pulls toward. */
    internal data class Point(val x: Float, val y: Float, val argb: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val points = points(pointCount(params.density), params.irregularity, palette, seed)
        val bitmap = createBitmap(width, height)
        val row = IntArray(width)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                row[x] = colorAt(nx, ny, points)
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    /** How many control points [density] asks for — [MinPoints] when sparse up to [MaxPoints] when dense. */
    internal fun pointCount(density: Float): Int =
        (MinPoints + (density.coerceIn(0f, 1f) * (MaxPoints - MinPoints)).roundToInt())

    /**
     * [count] control points for [seed] — positions from [PointScatter.gridJitter] at [irregularity] (a lattice when
     * even, a scatter when irregular), colors **cycled** through the palette so every stop is represented and the same
     * color does not clump by chance.
     */
    internal fun points(count: Int, irregularity: Float, palette: Palette, seed: Long): List<Point> {
        val positions = PointScatter.gridJitter(count, irregularity, seed)
        return List(count) { i ->
            Point(x = positions[i * 2], y = positions[i * 2 + 1], argb = palette.colorAt(i % palette.size))
        }
    }

    /**
     * The color at ([nx], [ny]) in the unit square — every point's color, weighted by `1/(distance² + ε)` and
     * blended premultiplied so a translucent point contributes its coverage rather than dragging the blend to black.
     *
     * The `ε` ([Softness]) is what keeps a point's own pixel finite and sets how tightly its color clings before it
     * melts into its neighbours: larger is softer.
     */
    internal fun colorAt(nx: Float, ny: Float, points: List<Point>): Int {
        var weightSum = 0.0
        var alphaSum = 0.0
        var redSum = 0.0
        var greenSum = 0.0
        var blueSum = 0.0

        for (point in points) {
            val dx = nx - point.x
            val dy = ny - point.y
            val weight = 1.0 / (dx * dx + dy * dy + Softness)

            val alpha = (point.argb ushr 24 and 0xFF).toDouble()
            val red = point.argb shr 16 and 0xFF
            val green = point.argb shr 8 and 0xFF
            val blue = point.argb and 0xFF

            val weightedAlpha = weight * alpha
            weightSum += weight
            alphaSum += weightedAlpha
            // Premultiplied: each color weighted by its own alpha as well as its distance, so a transparent point
            // adds no color rather than adding black.
            redSum += weightedAlpha * red
            greenSum += weightedAlpha * green
            blueSum += weightedAlpha * blue
        }

        if (alphaSum <= 0.0) return 0
        val a = (alphaSum / weightSum).roundToInt().coerceIn(0, ChannelMax)
        val r = (redSum / alphaSum).roundToInt().coerceIn(0, ChannelMax)
        val g = (greenSum / alphaSum).roundToInt().coerceIn(0, ChannelMax)
        val b = (blueSum / alphaSum).roundToInt().coerceIn(0, ChannelMax)
        val packed = (a shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }

    private const val ChannelMax = 255
    private const val MinPoints = 4
    private const val MaxPoints = 12

    /** How tightly a point holds its color before melting into its neighbours — the `ε` on the inverse-square weight. */
    private const val Softness = 0.0015
}
