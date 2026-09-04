package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.atan2
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

    /** What [DesignParams.density] resolves to for this design — the count, and the *Rays* slider's own range. */
    private val Amount = AmountKnob.Count("Rays", 4..16)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Unevenness",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val random = Random(seed)
        val cx = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val cy = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val rays = rayCount(params.density)
        // Drawn after the centre, so a stored recipe's fan stays where it was before the knob existed.
        val edges = edges(rays, params.irregularity, random)
        // The bearing has to be taken on the screen rather than in the unit square — see [wedge].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                // A flat palette stop per wedge, cycling — the hard step between stops is the ray edge.
                pixels[y * width + x] =
                    palette.colorAt(wedge(nx, ny, cx, cy, edges, heightOverWidth) % palette.size)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many wedges [density] asks for — a few broad fans up to a fine starburst. */
    internal fun rayCount(density: Float): Int = Amount.at(density)

    /**
     * Which wedge the bearing from ([cx], [cy]) to ([nx], [ny]) falls in, `0 until [rays]`. The angle from `atan2`
     * (`-π..π`) is normalized to `0..1` first, so the wedges tile the full turn and the first meets the last cleanly.
     *
     * **The bearing is taken on the screen, which [heightOverWidth] is the whole of.** Both coordinates arrive as
     * shares of their own side, so a step of `0.1` down a 1080×2400 frame is more than twice the pixels a step of
     * `0.1` across it — and an `atan2` of the two reads an angle that exists nowhere on the display. Wedges cut at
     * equal angles in that space arrive on the phone wildly unequal: a fan of even rays comes out as a couple of broad
     * blocks and a handful of slivers, which reads as an accident rather than as a starburst. Nothing reports it,
     * because a fan of uneven rays is still a fan.
     */
    internal fun wedge(nx: Float, ny: Float, cx: Float, cy: Float, edges: FloatArray, heightOverWidth: Float): Int {
        val angle = atan2((ny - cy) * heightOverWidth, nx - cx) // -π..π
        val normalized = (angle / (2f * PI.toFloat())) + 0.5f // 0..1
        // The edges ascend and the first is 0, so the last one at or below the bearing owns it. A linear walk beats a
        // search at these counts, and it is the same handful of comparisons whatever the fan.
        var found = 0
        for (i in edges.indices) {
            if (edges[i] > normalized) break
            found = i
        }
        return found
    }

    /**
     * Where the wedges begin, ascending from `0`, for a fan of [rays] at this [irregularity] — the design's organic
     * axis, and the one it never had.
     *
     * **`0` is a fan of exactly equal wedges**, which is the picture this design has always drawn and the rigid end
     * the field's contract asks for; climbing the knob walks each edge off its even position. [MaxTravel] is what
     * keeps the edges in order without a check — two neighbours can each walk that far *toward* one another, so a
     * wedge keeps at least `1 − 2 × MaxTravel` of its share and can never collapse or turn inside out. The first edge
     * is pinned at `0` rather than drawn, since the turn has to start somewhere and moving it only spins the whole
     * fan — which [DesignParams.rotation] would be for, and this design has no such knob yet.
     *
     * Consumes one draw per wedge whatever [irregularity] is, so tuning the knob changes how far the edges move and
     * never which way — the seeded stream does not shift underneath it.
     */
    internal fun edges(rays: Int, irregularity: Float, random: Random): FloatArray {
        val count = rays.coerceAtLeast(1)
        val gap = 1f / count
        val travel = irregularity.coerceIn(0f, 1f) * MaxTravel
        return FloatArray(count) { i ->
            val nudge = (random.nextFloat() * 2f - 1f) * travel * gap
            if (i == 0) 0f else i * gap + nudge
        }
    }

    // Softened toward broad fans: the default density now opens on a few wide wedges rather than a fine starburst (W7).

    /** How far off the frame's center the fan's origin is kept, so the rays sweep asymmetrically across it. */
    private const val CenterInset = 0.2f

    /**
     * How far an edge may walk off its even position, as a share of one wedge.
     *
     * Two neighbours may each walk this far toward one another, so a wedge keeps at least a fifth of its even share —
     * enough that the fan reads as uneven rather than as one with a ray missing, and enough that nothing has to check.
     */
    private const val MaxTravel = 0.4f
}
