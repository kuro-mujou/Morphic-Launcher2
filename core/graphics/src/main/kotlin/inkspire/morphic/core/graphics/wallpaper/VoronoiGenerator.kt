package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The frame broken into flat cells around scattered seed points, each cell edged in the palette's darkest tone — the
 * pebble mosaic.
 *
 * **It is ours, and it is neither of the reference designs it used to claim.** Their *Modern Mosaic* is a packing of
 * **rounded rectangles** with a wide grout, and their *Vitrall* cuts the frame with **edge-to-edge chords** into long
 * shards — see [VitrallGenerator], which is that one. A Voronoi is the third thing: cells built *around points*, so
 * each is a compact blob of roughly its neighbors' size, with no rectangles and no shards. Worth keeping for exactly
 * that reason, and worth not naming after something else.
 *
 * **A nearest-seed diagram, not a polygon Voronoi — the same choice [TriangularFacetsGenerator] makes.** The textbook
 * Voronoi is the dual of a Delaunay triangulation: real polygon geometry, circumcenters, edge-walking — machinery with
 * degenerate cases that fail silently at the corners. A wallpaper needs none of it. Every pixel simply takes the color
 * of the seed nearest it, which *is* the Voronoi partition by definition, with no algorithm to get wrong. That is
 * `O(pixels × sites)` with a few dozen sites — the same budget [MeshGradientGenerator] already spends.
 *
 * **The seams are what make it read as mosaic rather than as flat blobs.** A pixel whose nearest seed differs from a
 * four-neighbor's sits on a cell boundary and is painted the palette's darkest stop — the leading between panes of
 * glass. Without them the cells, colored by height off one gradient, would melt together like a coarse
 * [MESH][MeshGradientGenerator].
 *
 * **Which is why a cell's fill stops short of that stop — see [fillCeiling].** The cells read the same ramp the
 * leading is taken from, so the ones at the bottom of the frame arrived at the leading's own color and the mosaic
 * lost its seams exactly there: flat blobs, which is the thing the seams exist to prevent, in the third of the frame
 * where it is least noticeable as a *fault* and most noticeable as the design going soft.
 *
 * **Each cell is the palette gradient somewhere along it, jittered a shade** (via [LinearGradientGenerator.colorAt],
 * so a mosaic and a plain gradient of the same palette agree about the ramp — the shared derivation Facets keeps too).
 * *Where* along it is [DesignParams.colorLayout] — see [rampPosition]. Until the quality pass there was no such
 * chooser and the answer was always the seed's **height**: a good layout, and for a design whose whole subject is
 * where its cells sit, a thin one to be the only one — every design driven against the reference got the pick.
 * [DesignParams.density] sets how many cells there are, and [DesignParams.irregularity] how *evenly* they are placed:
 * the seeds come from [PointScatter], a lattice at low irregularity (an even honeycomb) scattering to a crazed one at
 * high. Deterministic in [seed]: seed positions, their color jitter and the scattered layout's draws are taken from
 * seeded `Random`s, so a recipe reproduces and a shuffle is a new seed.
 *
 * [siteCount], [sites] and [nearestSite] are pure and tested — which seed owns a pixel is index arithmetic that is
 * silently wrong (a wallpaper of one flat color) long before a bitmap could show it.
 */
object VoronoiGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Cells* slider's own range. */
    private val Amount = AmountKnob.Count("Cells", 8..40)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Irregularity",
        colorLayout = VariantKnob("Colors", listOf("Vertical", "Radial", "Scattered")),
    )

    /** One seed: where it sits in the unit square, and the flat color its cell is filled with. */
    internal data class Site(val x: Float, val y: Float, val argb: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        // The cells have to be shaped by the screen at both steps: the lattice is laid for the frame and the
        // ownership below is measured in it — see [nearestSite].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width
        val sites = sites(
            siteCount(params.density),
            params.irregularity,
            palette,
            seed,
            heightOverWidth,
            params.colorLayout,
        )
        val seam = palette.colorAt(palette.size - 1) // the darkest stop by convention — the leading between cells

        // Which seed owns each pixel, one pass, so the next pass can find a boundary by comparing neighbors.
        val owner = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                owner[y * width + x] = nearestSite(nx, ny, sites, heightOverWidth)
            }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                pixels[i] = if (onBoundary(owner, x, y, width, height)) seam else sites[owner[i]].argb
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many cells [density] asks for — when sparse up to when dense. */
    internal fun siteCount(density: Float): Int = Amount.at(density)

    /**
     * [count] seeds for [seed] — positions from [PointScatter.gridJitter] at [irregularity] (a lattice when even, a
     * scatter when irregular), each cell colored by the palette gradient where [layout] puts it ([rampPosition]),
     * nudged by up to [ColorJitter] so two cells landing on the same place still separate, over the span
     * [fillCeiling] leaves them.
     *
     * The shade jitter and the scattered layout's draws each run on a **salted stream of their own**, independent of
     * the position stream, so they stay fixed as the position knob slides. (A cell's *base* color still tracks where
     * it sits, by design — a cell that moves is colored for where it lands — so the salt keeps the shade stable, not
     * the whole color.) The scatter is drawn for every cell whatever the layout is, for the same reason one wedge of
     * [RaysGenerator] draws whatever its knob says: a stream that advanced only sometimes would make one layout's
     * colors depend on which layout had been asked for.
     */
    internal fun sites(
        count: Int,
        irregularity: Float,
        palette: Palette,
        seed: Long,
        heightOverWidth: Float = 1f,
        layout: Int = LayoutVertical,
    ): List<Site> {
        val positions = PointScatter.gridJitter(count, irregularity, seed, heightOverWidth)
        val shadeRandom = Random(seed xor ColorSalt)
        val scatterRandom = Random(seed xor ScatterSalt)
        val ceiling = fillCeiling(palette.size)
        return List(count) { i ->
            val x = positions[i * 2]
            val y = positions[i * 2 + 1]
            val shade = (shadeRandom.nextFloat() * 2f - 1f) * ColorJitter
            val scattered = scatterRandom.nextFloat()
            val along = rampPosition(layout, x, y, scattered, heightOverWidth) + shade
            Site(x, y, LinearGradientGenerator.colorAt(along.coerceIn(0f, 1f) * ceiling, palette))
        }
    }

    /**
     * Where on the ramp a cell at ([x], [y]) reads, `0..1`, for [layout] — before the shade jitter and before
     * [fillCeiling] scales the result.
     *
     * - **Vertical** is the seed's height, which is what this design drew before it had a chooser at all, so `0`
     *   leaves every stored recipe on the mosaic it was saved as.
     * - **Radial** is its distance from the middle of the frame over the distance to a corner, so the palette opens
     *   at the center and darkens outward — a bloom rather than a wash, and the layout that stops the mosaic reading
     *   as a gradient someone cut up.
     * - **Scattered** is [scattered], a draw of its own, so a cell's color says nothing about where it sits. That is
     *   the stained-glass reading, and the only one of the three where a cell's neighbors are no guide to it.
     *
     * **The radial distance is measured on the screen, not in the unit square** — [heightOverWidth] for
     * [nearestSite]'s reason, and it shows more here than there: without it the bloom would be an ellipse on a phone
     * while the cells around it stayed round, which reads as the color and the geometry belonging to two different
     * pictures.
     */
    internal fun rampPosition(layout: Int, x: Float, y: Float, scattered: Float, heightOverWidth: Float): Float =
        when (layout) {
            LayoutRadial ->
                hypot(x - Center, (y - Center) * heightOverWidth) / hypot(Center, Center * heightOverWidth)
            LayoutScattered -> scattered
            else -> y
        }

    /**
     * How far down the ramp a cell's fill may reach, for a palette of [stops] — the scale a `0..1` position is read
     * through, so the darkest cell lands one tone short of the seam instead of on it.
     *
     * **The step is [RampTones]', not one of this design's own**, and it lives there rather than here now that
     * [SprayGenerator] wants the same bound: that object already answers "the ramp *below* the ground" for a design
     * whose ground is the palette's last stop, which is exactly what the seam is. Two designs deriving a margin apart
     * is how they would come to disagree about where the ground begins, and nothing would notice.
     */
    internal fun fillCeiling(stops: Int): Float = RampTones.spanBelowGround(stops)

    /**
     * The index of the seed nearest ([nx], [ny]) — the pixel's cell.
     *
     * Compares squared distance (the square root is monotonic, so nearest by `d²` is nearest by `d`) and keeps the
     * **first** on a tie, so a pixel exactly between two seeds falls to the lower-indexed cell rather than flickering.
     *
     * **[heightOverWidth] is what makes "nearest" mean nearest *on the screen*.** Both coordinates arrive as shares of
     * their own side, so without it the comparison runs in a space the frame stretches — and a Voronoi diagram of
     * points in one metric drawn over points placed in another is not the diagram of the points you can see. It showed
     * as cells wider than the lattice that made them, at every count, and it is only visible as a *wrongness* if you
     * know which points are there.
     */
    internal fun nearestSite(nx: Float, ny: Float, sites: List<Site>, heightOverWidth: Float = 1f): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in sites.indices) {
            val dx = nx - sites[i].x
            val dy = (ny - sites[i].y) * heightOverWidth
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /**
     * Whether the pixel at ([x], [y]) sits on a cell boundary — its owner differs from the pixel to its left, right,
     * above or below. Edges of the frame are not boundaries (a cell reaching the frame is not a seam).
     */
    private fun onBoundary(owner: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        val here = owner[y * width + x]
        return x > 0 && owner[y * width + (x - 1)] != here ||
            x < width - 1 && owner[y * width + (x + 1)] != here ||
            y > 0 && owner[(y - 1) * width + x] != here ||
            y < height - 1 && owner[(y + 1) * width + x] != here
    }

    /** How far a cell's color may sit from the gradient at its height, `±` — enough to separate equal-height cells. */
    private const val ColorJitter = 0.12f

    /** Salts the shade-jitter stream apart from position generation, so it stays fixed as the irregularity knob slides. */
    private const val ColorSalt = 0x9E3779B9L

    /** Salts the scattered layout's stream apart from both the others, so picking a layout moves nothing but color. */
    private const val ScatterSalt = 0x517CC1B7L

    /** The middle of the frame, which the *Radial* layout measures from. */
    private const val Center = 0.5f

    /** [DesignParams.colorLayout] selecting the ramp read down the frame — what this design drew before the chooser. */
    private const val LayoutVertical = 0

    /** [DesignParams.colorLayout] selecting the ramp read outward from the middle of the frame. */
    private const val LayoutRadial = 1

    /** [DesignParams.colorLayout] selecting a place on the ramp per cell, unrelated to where the cell sits. */
    private const val LayoutScattered = 2
}
