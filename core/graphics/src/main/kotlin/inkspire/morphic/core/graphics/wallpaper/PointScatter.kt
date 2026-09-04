package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Scatters `count` points across the unit square on a **jittered lattice** — the shared derivation behind every design
 * whose organic-noise knob is *how irregularly its points are placed* — the Voronoi's cells, [SoftOverlapsGenerator]'s
 * discs, [FlowLinesGenerator]'s starts. (It named "Modern Mosaic / Vitrall's cells" until W11j established that the
 * Voronoi is neither of those designs, and Vitrall cuts chords rather than placing points at all.)
 *
 * **A lattice nudged by irregularity, not a lerp toward uniform-random — because "regular" needs a target to be
 * regular against.** A set of uniformly-random points cannot be made *more even*; there is no grid to snap back to. So
 * the points start on a grid and each is pushed off its cell by up to half a cell at `irregularity = 1`. At `0` the
 * result is a clean lattice (square mosaic cells, an even mesh); at `1` a point can reach its neighbour's edge, which is
 * the shattered, irregular look. This is also the correction the teardown forces — *Mesh is a grid + jitter*, not the
 * random control points the first cut used.
 *
 * **Several consumers, one placement.** [VoronoiGenerator]'s cells, [SoftOverlapsGenerator]'s discs and
 * [FlowLinesGenerator]'s starts all need exactly this and would otherwise each re-derive the grid/jitter arithmetic —
 * where an off-by-one in the cell math is silently wrong (points bunched in a corner, a column missing).
 * [MeshGradientGenerator] used to be here too and no longer is: it needs the lattice's *corners* rather than its cell
 * centres, and its edge nodes pinned, which is a different placement wearing the same word. Pure `FloatArray` output,
 * tested without a bitmap.
 */
object PointScatter {

    /**
     * [count] points in the unit square, interleaved `x, y`, drawn from [seed]. They sit on a near-square grid, each
     * nudged off its cell centre by up to half a cell scaled by [irregularity] (`0..1`): `0` is a clean lattice, `1` is
     * fully scattered. The grid is sized `⌈√count⌉` columns so the cells stay roughly square in the unit square before
     * the frame stretches them.
     *
     * The random stream is consumed two draws per point regardless of [irregularity], so tuning the knob does not
     * reshuffle *which* points move — only how far — keeping a shuffle stable as the slider slides.
     *
     * **[aspect] is the frame's height over its width, for a consumer whose cells have to be square on the *screen*.**
     * The default `1` keeps the lattice square in the unit square, which is what a design scattering forms across the
     * frame wants — [SoftOverlapsGenerator]'s discs are placed and sized in that space. A design that then *measures*
     * in pixels needs the other one: a placement and a metric that disagree about which neighbour is nearer draw cells
     * that are neither the shape of the lattice nor the shape of the frame, which is what [VoronoiGenerator] did.
     */
    fun gridJitter(count: Int, irregularity: Float, seed: Long, aspect: Float = 1f): FloatArray {
        val n = count.coerceAtLeast(1)
        // Columns for a lattice whose cells are square once the frame stretches it: a taller frame wants fewer of them.
        val cols = ceil(sqrt(n / aspect.coerceAtLeast(MinAspect).toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(n.toDouble() / cols).toInt().coerceAtLeast(1)
        val jitter = irregularity.coerceIn(0f, 1f)
        val random = Random(seed)

        val points = FloatArray(n * 2)
        var i = 0
        for (idx in 0 until n) {
            val c = idx % cols
            val r = idx / cols
            val cx = (c + 0.5f) / cols
            val cy = (r + 0.5f) / rows
            // Half a cell of travel at full irregularity, so a point can just reach its neighbour's edge but not cross it.
            val jx = (random.nextFloat() * 2f - 1f) * jitter * 0.5f / cols
            val jy = (random.nextFloat() * 2f - 1f) * jitter * 0.5f / rows
            points[i++] = (cx + jx).coerceIn(0f, 1f)
            points[i++] = (cy + jy).coerceIn(0f, 1f)
        }
        return points
    }

    /** A floor on [gridJitter]'s aspect, so a degenerate frame cannot ask for zero columns. */
    private const val MinAspect = 0.01f
}
