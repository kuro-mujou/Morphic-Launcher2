package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.hypot
import kotlin.math.min
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
 * **Each cell is a polygon, cut from the frame by half-planes — not a Delaunay dual, and not a pixel search.** A seed's
 * cell is everything nearer it than any other seed, which is the frame clipped by the perpendicular bisector between
 * it and every other seed ([cells]). That is `O(sites²)` clips of a polygon of a handful of corners — a few thousand
 * steps for the densest mosaic — with none of Delaunay's circumcenters or degenerate corners. Asking every *pixel*
 * which seed is nearest gives the same partition at `O(pixels × sites)`, over a second for a full frame, and nothing a
 * canvas can draw; polygons are drawn by the same calls into a bitmap for the bake and into the hardware canvas for a
 * scrub, which is what lets a shuffle be scrubbed — see docs/MORPH_ENGINE_PLAN.md.
 *
 * **The seams are what make it read as mosaic rather than as flat blobs.** Every edge two cells share is stroked in
 * the palette's darkest stop — the leading between panes of glass; an edge on the frame is not a seam. Without them
 * the cells, colored by height off one gradient, would melt together like a coarse [MESH][MeshGradientGenerator].
 *
 * **Which is why a cell's fill stops short of that stop — see [fillCeiling].** The cells read the same ramp the
 * leading is taken from, so the ones at the bottom of the frame arrived at the leading's own color and the mosaic
 * lost its seams exactly there: flat blobs, which is the thing the seams exist to prevent, in the third of the frame
 * where it is least noticeable as a *fault* and most noticeable as the design going soft.
 *
 * **Each cell is the palette gradient somewhere along it, jittered a shade** (via [LinearGradientGenerator.colorAt],
 * so a mosaic and a plain gradient of the same palette agree about the ramp — the shared derivation Facets keeps too).
 * *Where* along it is [DesignParams.colorLayout] — see [rampPosition]. [DesignParams.density] sets how many cells
 * there are, and [DesignParams.irregularity] how *evenly* they are placed: the seeds come from [PointScatter], a
 * lattice at low irregularity (an even honeycomb) scattering to a crazed one at high. Deterministic in the seed: seed
 * positions, their color jitter and the scattered layout's draws are taken from seeded `Random`s, so a recipe
 * reproduces and a shuffle is a new seed.
 *
 * [siteCount], [sites] and [cells] are pure and tested — which seed owns which part of the frame is geometry that is
 * silently wrong (cells that overlap, or leave the ground showing between them) long before a bitmap could show it.
 */
object VoronoiGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Cells* slider's own range. */
    private val Amount = AmountKnob.Count("Cells", 8..40)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Irregularity",
        colorLayout = VariantKnob("Colors", listOf("Vertical", "Radial", "Scattered")),
    )

    /**
     * One seed: where it sits, and where on the ramp its cell's color is read.
     *
     * @property x across the frame, `0..1`.
     * @property y down the frame, `0..1`.
     * @property tone the ramp position its cell is filled from, already inside [fillCeiling]. **A position, not a
     *   color**, so a scrub between two cells walks the ramp rather than mixing two colors into a third.
     */
    internal data class Site(val x: Float, val y: Float, val tone: Float)

    /**
     * One seed's cell, ready to paint.
     *
     * @property outline the cell's corners, interleaved `x, y`, in a frame one wide and [Plan.aspect] tall — convex,
     *   since it is a rectangle cut by half-planes.
     * @property tone its seed's [Site.tone].
     */
    internal class Cell(val outline: FloatArray, val tone: Float)

    /**
     * A mosaic planned but not painted — everything [draw] needs, at no particular size and in no particular palette.
     *
     * **Aspect-true, like Vitrall's frame**: one wide and [aspect] tall, so a unit is the same length on both axes and
     * "nearest" means nearest on the screen. The size is read for its shape and nothing else.
     *
     * @property sites the seeds the [cells] were cut around, kept for a scrub to move.
     * @property aspect the frame's height over its width.
     */
    internal class Plan(val sites: List<Site>, val cells: List<Cell>, val aspect: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(width, height, params, palette.size, seed), palette, width, height)
        return bitmap
    }

    /**
     * The mosaic [params] and [seed] describe, in a frame shaped like `[width]` × `[height]`, for a palette of [stops].
     *
     * **It takes the palette's stop count and not its colors**, because that is all [fillCeiling] reads — so a recolor
     * to a palette of the same size is a redraw of this plan.
     */
    internal fun plan(width: Int, height: Int, params: DesignParams, stops: Int, seed: Long): Plan {
        // The cells have to be shaped by the screen at both steps: the lattice is laid for the frame and the
        // bisectors below are measured in it.
        val aspect = if (width <= 0) 1f else height.toFloat() / width
        val sites = sites(siteCount(params.density), params.irregularity, stops, seed, aspect, params.colorLayout)
        return Plan(sites, cells(sites, aspect), aspect)
    }

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike, so the two cannot drift.
     *
     * **The ground is the seam's color**, so wherever two antialiased fills meet and leave a hairline between them,
     * what shows through is the leading that is about to be stroked there anyway.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        val seam = palette.colorAt(palette.size - 1) // the darkest stop by convention — the leading between cells
        canvas.drawColor(seam)
        val sx = width.toFloat()
        val sy = height / plan.aspect
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val seams = Path()
        for (cell in plan.cells) {
            fill.color = LinearGradientGenerator.colorAt(cell.tone, palette)
            canvas.drawPath(outline(cell.outline, sx, sy), fill)
            addSeams(seams, cell.outline, plan.aspect, sx, sy)
        }
        canvas.drawPath(
            seams,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = seam
                strokeWidth = SeamWidth * min(width, height)
                // Round, because every seam is its own segment and three of them meet at each corner of a cell; a
                // butt end leaves a notch at every junction.
                strokeCap = Paint.Cap.ROUND
            },
        )
    }

    /** [outline] as a closed path, scaled from the plan's frame to pixels. */
    private fun outline(outline: FloatArray, sx: Float, sy: Float): Path = Path().apply {
        moveTo(outline[0] * sx, outline[1] * sy)
        for (i in 2 until outline.size step 2) lineTo(outline[i] * sx, outline[i + 1] * sy)
        close()
    }

    /**
     * Adds every edge of [outline] that two cells share to [seams] — which is every edge not lying along the frame.
     *
     * An edge on the frame's border has both ends exactly on it, because the frame's own corners are exact and a
     * clip only ever interpolates *along* a border edge; so the test is an equality, not a tolerance. Each shared edge
     * arrives twice, once per cell, and strokes to the same pixels either time.
     */
    private fun addSeams(seams: Path, outline: FloatArray, aspect: Float, sx: Float, sy: Float) {
        val count = outline.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            val ax = outline[i * 2]
            val ay = outline[i * 2 + 1]
            val bx = outline[j * 2]
            val by = outline[j * 2 + 1]
            val onBorder = ax == 0f && bx == 0f || ax == 1f && bx == 1f || ay == 0f && by == 0f ||
                ay == aspect && by == aspect
            if (onBorder) continue
            seams.moveTo(ax * sx, ay * sy)
            seams.lineTo(bx * sx, by * sy)
        }
    }

    /**
     * Each site's cell: the frame, clipped by the bisector between that site and every other one, in a frame one wide
     * and [aspect] tall.
     *
     * **The frame is aspect-true, and that is what makes "nearest" mean nearest *on the screen*.** The sites arrive
     * as shares of their own side; measured in that unit square, a Voronoi diagram of points in one metric is drawn
     * over points placed in another, which shows as cells wider than the lattice that made them. So `y` is stretched
     * by [aspect] before any bisector is taken.
     *
     * A cell always holds its own site, so none comes back empty in practice; two sites exactly coincident have no
     * bisector and are simply not clipped against each other.
     */
    internal fun cells(sites: List<Site>, aspect: Float): List<Cell> {
        val frame = floatArrayOf(0f, 0f, 1f, 0f, 1f, aspect, 0f, aspect)
        return sites.indices.mapNotNull { i -> cellOf(i, sites, aspect, frame)?.let { Cell(it, sites[i].tone) } }
    }

    /** The [i]-th site's cell: [frame] clipped by its bisector with every other site, or null if nothing is left. */
    private fun cellOf(i: Int, sites: List<Site>, aspect: Float, frame: FloatArray): FloatArray? {
        val x = sites[i].x
        val y = sites[i].y * aspect
        var cell = frame
        for (j in sites.indices) {
            val nx = sites[j].x - x
            val ny = sites[j].y * aspect - y
            if (nx == 0f && ny == 0f) continue // itself, or a seed exactly on top of it: no bisector to cut by
            cell = GlassCut.clip(cell, x + nx / 2f, y + ny / 2f, nx, ny) ?: return null
        }
        return cell
    }

    /**
     * A scrub between two mosaics: the seeds slide and the cells are re-cut around them every frame, at full
     * resolution.
     *
     * **A subdivision, like Vitrall, and the same answer**: the cells tile the frame, so they cannot be paired off and
     * moved one by one — but here the structure behind them is only the seeds, and the seeds pair by index, since
     * they sit on `PointScatter`'s lattice. A Voronoi diagram moves continuously with its seeds, so re-cutting at every
     * moment is the whole morph; no cut has to be merged, as Vitrall's must.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(width, height, params, palette.size, from), plan(width, height, params, palette.size, to))
            ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h) }
    }

    /** Two mosaics prepared to interpolate, seed for seed — or **null where they are not cut around as many seeds**. */
    internal fun morph(from: Plan, to: Plan): Morph? =
        if (from.sites.size == to.sites.size && from.aspect == to.aspect) Morph(from, to) else null

    /** Two mosaics and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /** The mosaic [t] of the way across; the ends are the plans themselves, as every design's are. */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> {
                val sites = List(from.sites.size) {
                    val a = from.sites[it]
                    val b = to.sites[it]
                    Site(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.tone + (b.tone - a.tone) * t)
                }
                Plan(sites, cells(sites, from.aspect), from.aspect)
            }
        }
    }

    /** How many cells [density] asks for — when sparse up to when dense. */
    internal fun siteCount(density: Float): Int = Amount.at(density)

    /**
     * [count] seeds for [seed] — positions from [PointScatter.gridJitter] at [irregularity] (a lattice when even, a
     * scatter when irregular), each cell read off the ramp where [layout] puts it ([rampPosition]), nudged by up to
     * [ColorJitter] so two cells landing on the same place still separate, over the span [fillCeiling] leaves a
     * palette of [stops].
     *
     * The shade jitter and the scattered layout's draws each run on a **salted stream of their own**, independent of
     * the position stream, so they stay fixed as the position knob slides. (A cell's *base* color still tracks where
     * it sits, by design — a cell that moves is colored for where it lands — so the salt keeps the shade stable, not
     * the whole color.) The scatter is drawn for every cell whatever the layout is: a stream that advanced only
     * sometimes would make one layout's colors depend on which layout had been asked for.
     */
    @Suppress("LongParameterList") // Each is one knob or one fact about the frame, read once.
    internal fun sites(
        count: Int,
        irregularity: Float,
        stops: Int,
        seed: Long,
        heightOverWidth: Float = 1f,
        layout: Int = LayoutVertical,
    ): List<Site> {
        val positions = PointScatter.gridJitter(count, irregularity, seed, heightOverWidth)
        val shadeRandom = Random(seed xor ColorSalt)
        val scatterRandom = Random(seed xor ScatterSalt)
        val ceiling = fillCeiling(stops)
        return List(count) { i ->
            val x = positions[i * 2]
            val y = positions[i * 2 + 1]
            val shade = (shadeRandom.nextFloat() * 2f - 1f) * ColorJitter
            val scattered = scatterRandom.nextFloat()
            val along = rampPosition(layout, x, y, scattered, heightOverWidth) + shade
            Site(x, y, along.coerceIn(0f, 1f) * ceiling)
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
     * **The radial distance is measured on the screen, not in the unit square** — [heightOverWidth] for [cells]'
     * reason, and it shows more here than there: without it the bloom would be an ellipse on a phone while the cells
     * around it stayed round, which reads as the color and the geometry belonging to two different pictures.
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
     * How wide a seam is stroked, as a share of the frame's short side — two pixels on a 1080-wide phone.
     *
     * **A share rather than a pixel count**, so a draft, a thumbnail and the bake are one picture at three sizes; a
     * fixed pixel width would make the leading three times as heavy, relative to the cells, in a draft a third the
     * size.
     */
    private const val SeamWidth = 0.002f

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
