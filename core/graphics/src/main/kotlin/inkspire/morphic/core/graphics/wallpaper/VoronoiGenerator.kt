package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.random.Random

/**
 * The frame broken into flat cells around scattered seed points, each cell edged in the palette's darkest tone — the
 * pebble mosaic.
 *
 * **It is ours, and it is neither of the reference designs it used to claim.** Their *Modern Mosaic* is a packing of
 * **rounded rectangles** with a wide grout, and their *Vitrall* cuts the frame with **edge-to-edge chords** into long
 * shards — see [VitrallGenerator], which is that one. A Voronoi is the third thing: cells built *around points*, so
 * each is a compact blob of roughly its neighbours' size, with no rectangles and no shards. Worth keeping for exactly
 * that reason, and worth not naming after something else.
 *
 * **A nearest-seed diagram, not a polygon Voronoi — the same choice [TriangularFacetsGenerator] makes.** The textbook
 * Voronoi is the dual of a Delaunay triangulation: real polygon geometry, circumcenters, edge-walking — machinery with
 * degenerate cases that fail silently at the corners. A wallpaper needs none of it. Every pixel simply takes the color
 * of the seed nearest it, which *is* the Voronoi partition by definition, with no algorithm to get wrong. That is
 * `O(pixels × sites)` with a few dozen sites — the same budget [MeshGradientGenerator] already spends.
 *
 * **The seams are what make it read as mosaic rather than as flat blobs.** A pixel whose nearest seed differs from a
 * four-neighbour's sits on a cell boundary and is painted the palette's darkest stop — the leading between panes of
 * glass. Without them the cells, colored by height off one gradient, would melt together like a coarse
 * [MESH][MeshGradientGenerator].
 *
 * **Each cell is the palette gradient at its seed's height, jittered a shade** (via [LinearGradientGenerator.colorAt],
 * so a mosaic and a plain gradient of the same palette agree about the ramp — the shared derivation Facets keeps too).
 * [DesignParams.density] sets how many cells there are, and [DesignParams.irregularity] how *evenly* they are placed:
 * the seeds come from [PointScatter], a lattice at low irregularity (an even honeycomb) scattering to a crazed one at
 * high. Deterministic in [seed]: seed positions and their color jitter are drawn from seeded
 * `Random`s, so a recipe reproduces and a shuffle is a new seed.
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
    )

    /** One seed: where it sits in the unit square, and the flat color its cell is filled with. */
    internal data class Site(val x: Float, val y: Float, val argb: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val sites = sites(siteCount(params.density), params.irregularity, palette, seed)
        val seam = palette.colorAt(palette.size - 1) // the darkest stop by convention — the leading between cells

        // Which seed owns each pixel, one pass, so the next pass can find a boundary by comparing neighbours.
        val owner = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                owner[y * width + x] = nearestSite(nx, ny, sites)
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
     * scatter when irregular), each cell colored by the palette gradient at the seed's height nudged by up to
     * [ColorJitter] so cells at the same height still separate.
     *
     * The shade jitter runs on a **salted stream of its own**, independent of the position stream, so it stays fixed as
     * the position knob slides. (A cell's *base* color still tracks its height, by design — a cell that moves down the
     * frame is colored for where it lands — so the salt keeps the shade stable, not the whole color.) One recipe is one
     * fixed mosaic.
     */
    internal fun sites(count: Int, irregularity: Float, palette: Palette, seed: Long): List<Site> {
        val positions = PointScatter.gridJitter(count, irregularity, seed)
        val shadeRandom = Random(seed xor ColorSalt)
        return List(count) { i ->
            val x = positions[i * 2]
            val y = positions[i * 2 + 1]
            val shade = (shadeRandom.nextFloat() * 2f - 1f) * ColorJitter
            Site(x, y, LinearGradientGenerator.colorAt((y + shade).coerceIn(0f, 1f), palette))
        }
    }

    /**
     * The index of the seed nearest ([nx], [ny]) in the unit square — the pixel's cell.
     *
     * Compares squared distance (the square root is monotonic, so nearest by `d²` is nearest by `d`) and keeps the
     * **first** on a tie, so a pixel exactly between two seeds falls to the lower-indexed cell rather than flickering.
     */
    internal fun nearestSite(nx: Float, ny: Float, sites: List<Site>): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in sites.indices) {
            val dx = nx - sites[i].x
            val dy = ny - sites[i].y
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
}
