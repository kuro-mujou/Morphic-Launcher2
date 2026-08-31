package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.ceil
import kotlin.random.Random

/**
 * An even lattice of square tiles, each carrying one flat arc from a four-shape vocabulary — the *Bauhaus blocks*
 * poster look.
 *
 * **Arcs on a lattice, not a subdivision.** The whole character is that the shapes are drawn at *cell scale* and cut
 * off by the cell: a quarter disc has the cell's full width for a radius, so it sweeps corner to corner, and where two
 * neighbours happen to face each other their arcs read as one larger circle spanning both. That emergent joining is
 * what makes the pattern look composed rather than tiled, and it is why the radii are what they are — a quarter disc
 * drawn at half scale would sit *inside* its cell and the pattern would fall apart into isolated tokens.
 *
 * **Every edge is flush, and there is no ink line anywhere.** Flat color meeting flat color is the whole graphic
 * language here; a stroke between tiles would turn a poster into a grid. [MondrianGenerator] is the one that rules its
 * pieces, and that difference is how the two are told apart.
 *
 * **[DesignParams.irregularity] is *variety*, and it is the geometric-rigidity axis this design has.** At `0` every
 * tile carries the same shape at the same turn — a strict wallpaper repeat — and each step up lets more tiles roll
 * their own, reaching a fully mixed field at `1`. Colors are drawn per tile at *every* setting: rigid describes the
 * geometry, and a one-color grid would be a different (and duller) claim.
 *
 * **[DesignParams.variant] is what the shapes sit on.** `0` gives every tile its own ground, so the frame is packed
 * edge to edge with color — the loud poster. `1` floats the shapes on a single dark ground and draws nothing at all
 * for a plain tile, which turns the same lattice into something mostly negative space. The second is the restrained
 * one and the pair is the design's real range.
 *
 * [plan] is pure and tested: which shape, which turn, and above all **which two palette stops** — a tile that drew its
 * arc in its own ground color would be an invisible shape, and no bitmap is needed to catch that.
 */
object BauhausGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 2..8)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Variety",
        variant = VariantKnob("Ground", listOf("Tiles", "Floating")),
    )

    /**
     * The vocabulary. Four shapes including *nothing*, so a quarter of a fully-varied field is plain — the air the
     * pattern needs, without a knob of its own.
     */
    internal enum class Tile { PLAIN, QUARTER, HALF, DISC }

    /**
     * One tile's roll: what to draw, which way up, and the two stops to draw it with.
     *
     * @property turn quarter-turns clockwise, `0..3` — which corner a [Tile.QUARTER] is anchored to, and which edge a
     *   [Tile.HALF] sits on. [Tile.DISC] and [Tile.PLAIN] look the same at every turn.
     * @property ground the palette index the tile is filled with. Unused by the floating variant, which has one ground
     *   for the whole frame.
     * @property shape the palette index the arc is drawn in — **never equal to [ground]**, or the shape would be
     *   invisible.
     */
    internal data class Cell(val tile: Tile, val turn: Int, val ground: Int, val shape: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val cols = columnCount(params.density)
        // Square cells sized to fit the columns exactly; the bottom row is whatever is left, which reads as the
        // pattern continuing past the frame rather than as a mis-sized grid.
        val cell = width.toFloat() / cols
        val rows = ceil(height / cell).toInt().coerceAtLeast(1)
        // Square cells cannot also divide the height, so the lattice always overhangs — and the overhang is split
        // across both edges rather than left at the bottom. A single leftover strip reads as a mis-measured grid; the
        // same pixels taken off the top and bottom read as the pattern carrying on past the frame.
        val overhang = (height - rows * cell) / 2f
        val floating = params.variant == VariantFloating
        val cells = plan(cols, rows, params.irregularity, palette.size, floating, seed)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // The floating variant's single ground: the darkest stop, this palette's convention for a ground.
        if (floating) canvas.drawColor(palette.colorAt(palette.size - 1))

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val here = cells[row * cols + col]
                val left = col * cell
                val top = row * cell + overhang
                if (!floating) {
                    paint.color = palette.colorAt(here.ground)
                    canvas.drawRect(left, top, left + cell, top + cell, paint)
                }
                paint.color = palette.colorAt(here.shape)
                drawTile(canvas, paint, here, left, top, cell)
            }
        }
        return bitmap
    }

    /** How many columns [density] asks for — a couple of billboard-sized tiles up to a fine poster grid. */
    internal fun columnCount(density: Float): Int = Amount.at(density)

    /**
     * The roll for every tile, row-major.
     *
     * **[variety] is spent per property, not once per tile**, so the two loosen independently: at `0.5` a tile is as
     * likely to keep the base shape and turn its own way as the reverse, which is what keeps a half-varied field
     * reading as one motif with exceptions rather than as two interleaved patterns.
     *
     * **Every roll is drawn whether or not it is used**, so sliding [variety] re-dresses the same lattice instead of
     * re-rolling it — the discipline the jittered designs keep, for the same reason: a knob that also reshuffles is a
     * knob whose effect cannot be seen.
     *
     * @param floating excludes the frame's ground stop from every shape, since that is what the arcs are drawn *on*.
     */
    internal fun plan(
        cols: Int,
        rows: Int,
        variety: Float,
        paletteSize: Int,
        floating: Boolean,
        seed: Long,
    ): List<Cell> {
        val random = Random(seed)
        val mix = variety.coerceIn(0f, 1f)
        // What a tile falls back to where variety does not reach: the quarter disc, unturned. It is the shape the
        // whole look is built on, so a rigid field is a wall of it rather than a wall of nothing.
        val stops = if (floating) (paletteSize - 1).coerceAtLeast(1) else paletteSize
        val contrasts = List(stops) { StopContrast.readableAgainst(it, stops) }

        return List(cols * rows) {
            val shapeVaries = random.nextFloat() < mix
            val turnVaries = random.nextFloat() < mix
            val shapeRoll = Tile.entries[random.nextInt(Tile.entries.size)]
            val turnRoll = random.nextInt(Turns)
            val ground = random.nextInt(stops)
            val far = contrasts[ground]
            val shape = far[random.nextInt(far.size)]
            Cell(
                tile = if (shapeVaries) shapeRoll else Tile.QUARTER,
                turn = if (turnVaries) turnRoll else 0,
                ground = ground,
                shape = shape,
            )
        }
    }

    /**
     * One tile's arc: a single circle, placed and clipped to the cell.
     *
     * **Every shape here is the same circle at a different center and radius**, which is the whole reason they line up.
     * A quarter disc is a circle of the cell's own width centered on a corner; a half disc one of half that centered on
     * an edge's midpoint; a disc one in the middle. Stated that way the arcs meet the cell's edges exactly — a
     * quarter's arc crosses the two edges away from its corner, a half's meets the edge it sits on — which is what lets
     * two neighbours read as one larger circle. Building the shapes as paths instead would put that agreement in three
     * places to get wrong independently.
     */
    private fun drawTile(canvas: Canvas, paint: Paint, cell: Cell, left: Float, top: Float, side: Float) {
        val (center, radius) = when (cell.tile) {
            Tile.PLAIN -> return
            Tile.DISC -> DiscCenter to side / 2f
            Tile.QUARTER -> QuarterCorners[cell.turn] to side
            Tile.HALF -> HalfEdges[cell.turn] to side / 2f
        }

        canvas.save()
        canvas.clipRect(left, top, left + side, top + side)
        canvas.drawCircle(left + center[0] * side, top + center[1] * side, radius, paint)
        canvas.restore()
    }

    /** Where a quarter disc's circle is centered, in cell fractions — the four corners, clockwise from the top-left. */
    private val QuarterCorners = arrayOf(
        floatArrayOf(0f, 0f),
        floatArrayOf(1f, 0f),
        floatArrayOf(1f, 1f),
        floatArrayOf(0f, 1f),
    )

    /** Where a half disc's circle is centered — the four edge midpoints, clockwise from the top. */
    private val HalfEdges = arrayOf(
        floatArrayOf(0.5f, 0f),
        floatArrayOf(1f, 0.5f),
        floatArrayOf(0.5f, 1f),
        floatArrayOf(0f, 0.5f),
    )

    /** Where a whole disc's circle is centered — the middle, at every turn. */
    private val DiscCenter = floatArrayOf(0.5f, 0.5f)

    /** [DesignParams.variant] selecting the shapes floating on one ground over the default per-tile grounds. */
    private const val VariantFloating = 1

    /** Quarter-turns a shape can take — which corner it is anchored to, or which edge it sits on. */
    private const val Turns = 4

}
