package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Wedges of palette color fanning out from an off-center point — the sunburst / *Rays* (gart's `arts/rayz`,
 * `arts/sf`).
 *
 * **The angular sibling of [RingsGenerator].** Rings band a pixel's *distance* from a center; Rays band its **angle**.
 * Each pixel takes its color from the wedge its bearing falls in, so the whole design is an `atan2` and a palette
 * lookup — a cheap full-screen pass with no geometry and no overdraw.
 *
 * **The center is seeded and off-center**, so the fan sweeps asymmetrically like light through a gap rather than a
 * symmetrical pinwheel. [DesignParams.density] sets how many wedges. Deterministic in [seed].
 *
 * **[DesignParams.roundness] is how soft the seam between two wedges is, and until the quality pass there was no such
 * knob: every edge was hard, at full palette contrast, across the whole frame.** That made this the loudest design in
 * the catalog by some distance — a wallpaper an icon has to survive being read on top of. `0` is exactly that
 * sunburst, kept because at a low ray count it is a genuinely good graphic; the other end blends each wedge the whole
 * way into its neighbors, which is a **conic gradient** and a second design rather than a softened first one. The
 * default `0.5` lands between the two, and that is the point of the change rather than a side effect: a knob whose
 * default reproduced the picture the finding was about would have fixed nothing for anyone who never drags it.
 *
 * The bearing math is pure and tested in three pieces — [bearing], [wedgeAt] and [neighborMix]. Splitting it is what
 * lets the render loop take the `atan2` once and then ask two questions of it; [wedge] is the composition, and the
 * mapping it names is what decides whether the rays meet cleanly at the center or tear.
 */
object RaysGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Rays* slider's own range. */
    private val Amount = AmountKnob.Count("Rays", 4..16)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Unevenness",
        roundness = "Softness",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val random = Random(seed)
        val cx = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val cy = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val rays = rayCount(params.density)
        // Drawn after the center, so a stored recipe's fan stays where it was before the knob existed.
        val edges = edges(rays, params.irregularity, random)
        val softness = params.roundness.coerceIn(0f, 1f)
        // A flat palette stop per wedge, cycling — hoisted, since the loop would otherwise take the modulo per pixel.
        val wedgeColors = IntArray(edges.size) { palette.colorAt(it % palette.size) }
        // The bearing has to be taken on the screen rather than in the unit square — see [bearing].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                pixels[y * width + x] =
                    shadeAt(bearing(nx, ny, cx, cy, heightOverWidth), edges, wedgeColors, softness)
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many wedges [density] asks for — a few broad fans up to a fine starburst. */
    internal fun rayCount(density: Float): Int = Amount.at(density)

    /**
     * The color a pixel on this [bearing] takes — its wedge's stop, leaned toward a neighbor's near a seam.
     *
     * The neighbor wraps with the *fan* rather than with the palette, so the last wedge blends into the first. That
     * seam is the one that shows: with a ray count that is not a multiple of the palette, the two wedges meeting at
     * bearing `0` hold stops that are not adjacent in the ramp, and it is the only place a hard step could survive the
     * knob.
     */
    private fun shadeAt(bearing: Float, edges: FloatArray, colors: IntArray, softness: Float): Int {
        val index = wedgeAt(bearing, edges)
        val lean = neighborMix(bearing, index, edges, softness)
        if (lean == 0f) return colors[index]
        val neighbor = if (lean < 0f) index + edges.size - 1 else index + 1
        return LinearGradientGenerator.lerpArgb(colors[index], colors[neighbor % edges.size], abs(lean))
    }

    /**
     * The bearing from ([cx], [cy]) to ([nx], [ny]) as a share of the full turn, `0..1` — `atan2`'s `-π..π` shifted so
     * the wedges tile the turn and the first meets the last cleanly.
     *
     * **The bearing is taken on the screen, which [heightOverWidth] is the whole of.** Both coordinates arrive as
     * shares of their own side, so a step of `0.1` down a 1080×2400 frame is more than twice the pixels a step of
     * `0.1` across it — and an `atan2` of the two reads an angle that exists nowhere on the display. Wedges cut at
     * equal angles in that space arrive on the phone wildly unequal: a fan of even rays comes out as a couple of broad
     * blocks and a handful of slivers, which reads as an accident rather than as a starburst. Nothing reports it,
     * because a fan of uneven rays is still a fan.
     */
    internal fun bearing(nx: Float, ny: Float, cx: Float, cy: Float, heightOverWidth: Float): Float {
        val angle = atan2((ny - cy) * heightOverWidth, nx - cx) // -π..π
        return (angle / (2f * PI.toFloat())) + Halfway // 0..1
    }

    /** Which wedge [bearing] falls in, `0 until edges.size`. */
    internal fun wedgeAt(bearing: Float, edges: FloatArray): Int {
        // The edges ascend and the first is 0, so the last one at or below the bearing owns it. A linear walk beats a
        // search at these counts, and it is the same handful of comparisons whatever the fan.
        var found = 0
        for (i in edges.indices) {
            if (edges[i] > bearing) break
            found = i
        }
        return found
    }

    /** Which wedge the bearing from ([cx], [cy]) to ([nx], [ny]) falls in — [bearing] read by [wedgeAt]. */
    internal fun wedge(nx: Float, ny: Float, cx: Float, cy: Float, edges: FloatArray, heightOverWidth: Float): Int =
        wedgeAt(bearing(nx, ny, cx, cy, heightOverWidth), edges)

    /**
     * How far a pixel on this [bearing], inside wedge [index], leans toward a neighboring wedge at this [softness] —
     * `-0.5` half-way into the one before, `+0.5` half-way into the one after, `0` its own flat color.
     *
     * **Half a wedge either side is the whole range, which is what makes both ends real designs.** [softness] `0`
     * returns `0` everywhere, so the hard-edged sunburst survives exactly; `1` puts a transition across every part of
     * every wedge, leaving no flat interior at all — a conic gradient through the palette. Anything between is a flat
     * core with soft seams.
     *
     * **A bearing exactly on a seam reads [Halfway] whichever of the two wedges it is asked about**, which is what
     * makes the blend continuous across an edge without either side knowing the other exists. The two ramps meeting
     * there are not the same *width* when the fan is uneven — each is a share of its own wedge — so the transition is
     * steeper on the narrower side, but it arrives at the same color and nothing tears. Eased by [Easing.smoothstep]
     * rather than run straight, so the ramp is flat where it meets both the seam and the wedge's flat core: a linear
     * one creases at the core's border, and at full softness that crease is a visible spoke down the middle of every
     * ray.
     */
    internal fun neighborMix(bearing: Float, index: Int, edges: FloatArray, softness: Float): Float {
        val reach = softness.coerceIn(0f, 1f) * Halfway
        if (reach <= 0f) return 0f
        val low = edges[index]
        val high = if (index + 1 < edges.size) edges[index + 1] else 1f
        if (high <= low) return 0f
        val within = ((bearing - low) / (high - low)).coerceIn(0f, 1f)
        return when {
            within < reach -> -Halfway * Easing.smoothstep(1f - within / reach)
            within > 1f - reach -> Halfway * Easing.smoothstep(1f - (1f - within) / reach)
            else -> 0f
        }
    }

    /**
     * Where the wedges begin, ascending from `0`, for a fan of [rays] at this [irregularity] — the design's organic
     * axis, and the one it never had.
     *
     * **`0` is a fan of exactly equal wedges**, which is the picture this design has always drawn and the rigid end
     * the field's contract asks for; climbing the knob walks each edge off its even position. [MaxTravel] is what
     * keeps the edges in order without a check — two neighbors can each walk that far *toward* one another, so a
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
     * Half of something, three times over: half a turn, which is what shifts `atan2`'s range onto `0..1`; half a
     * wedge, the furthest a seam's blend may reach into one; and the half-and-half color the seam itself takes.
     */
    private const val Halfway = 0.5f

    /**
     * How far an edge may walk off its even position, as a share of one wedge.
     *
     * Two neighbors may each walk this far toward one another, so a wedge keeps at least a fifth of its even share —
     * enough that the fan reads as uneven rather than as one with a ray missing, and enough that nothing has to check.
     */
    private const val MaxTravel = 0.4f
}
