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
 * An even lattice of square tiles, each either carrying one quarter disc or left flat — the *Bauhaus blocks* poster
 * look.
 *
 * **One shape, and everything else is emergent.** A decorated tile draws a quarter of a circle whose radius is the
 * cell's *full width*, anchored at one of its corners; that is the entire vocabulary. Half circles, whole circles and
 * lozenges all appear anyway, wherever neighbouring tiles anchor their quarters at a shared corner or edge — and
 * because every one of them is built from the same motif, the frame reads as one system rather than as an assortment
 * of shapes. Drawing halves and circles as tiles of their own instead is the tempting mistake: it produces shapes that
 * sit *inside* a cell relating to nothing beside them, which reads as busier while actually being less varied.
 *
 * **Every edge is flush, and there is no ink line anywhere.** Flat color meeting flat color is the whole graphic
 * language here; a stroke between tiles would turn a poster into a grid. [MondrianGenerator] is the one that rules its
 * pieces, and that difference is how the two are told apart.
 *
 * **[DesignParams.scale] is coverage** — what fraction of the tiles carry a shape at all, and so how much air the
 * pattern has. It earns its own knob rather than falling out of the shape vocabulary, because "a strict repeat with a
 * few tiles left blank" and "a loose field with none blank" are both looks, and neither is reachable while the two are
 * tangled together.
 *
 * **[DesignParams.irregularity] is variety, which here can only mean the turns**: at `0` every quarter is anchored at
 * the same corner and the frame is a strict wallpaper repeat; climbing it lets more tiles face their own way. Colors
 * are drawn per tile at *every* setting — rigid describes the geometry, and a one-color grid would be a different and
 * duller claim.
 *
 * **[DesignParams.variant] is what the shapes sit on.** `0` gives every tile its own ground, so the frame is packed
 * edge to edge with color — the loud poster. `1` floats the shapes on a single dark ground and draws nothing at all
 * for an undecorated tile, which turns the same lattice into something mostly negative space. The second is the
 * restrained one and the pair is the design's real range.
 *
 * [plan] is pure and tested: which tiles are decorated, which way each faces, and above all **which two palette
 * stops** — a tile that drew its arc in its own ground color would be an invisible shape, and no bitmap is needed to
 * catch that.
 */
object BauhausGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 2..9)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Coverage",
        irregularity = "Variety",
        variant = VariantKnob("Ground", listOf("Tiles", "Floating")),
    )

    /**
     * One tile's roll: whether it is decorated, which way its quarter faces, and the two stops to draw it with.
     *
     * @property turn quarter-turns clockwise, `0..3` — which corner the quarter disc is anchored to.
     * @property ground the palette index the tile is filled with. Unused by the floating variant, which has one ground
     *   for the whole frame.
     * @property shape the palette index the arc is drawn in — **never equal to [ground]**, or the shape would be
     *   invisible.
     */
    internal data class Cell(val decorated: Boolean, val turn: Int, val ground: Int, val shape: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val cols = columnCount(params.density)
        // Square cells sized to fit the columns exactly; the rows are however many reach the bottom.
        val cell = width.toFloat() / cols
        val rows = ceil(height / cell).toInt().coerceAtLeast(1)
        // Square cells cannot also divide the height, so the lattice always overhangs — and the overhang is split
        // across both edges rather than left at the bottom. A single leftover strip reads as a mis-measured grid; the
        // same pixels taken off the top and bottom read as the pattern carrying on past the frame.
        val overhang = (height - rows * cell) / 2f
        val floating = params.variant == VariantFloating
        val cells = plan(cols, rows, params.scale, params.irregularity, palette.size, floating, seed)

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
                if (here.decorated) {
                    paint.color = palette.colorAt(here.shape)
                    drawQuarter(canvas, paint, here.turn, left, top, cell)
                }
            }
        }
        return bitmap
    }

    /** How many columns [density] asks for — a couple of billboard-sized tiles up to a fine poster grid. */
    internal fun columnCount(density: Float): Int = Amount.at(density)

    /**
     * The roll for every tile, row-major.
     *
     * **[coverage] and [variety] are spent independently**, so a sparse field can still be a strict repeat and a dense
     * one can still face every way — they are separate questions and the knobs keep them separate.
     *
     * **Every roll is drawn whether or not it is used**, so sliding either knob re-dresses the same lattice instead of
     * re-rolling it — the discipline the jittered designs keep, for the same reason: a knob that also reshuffles is a
     * knob whose effect cannot be seen.
     *
     * @param coverage how many tiles carry a shape, mapped onto [MinCoverage]`..`[MaxCoverage] so that even the
     *   emptiest setting keeps some — a frame of bare squares is not this design.
     * @param floating excludes the frame's ground stop from every shape, since that is what the arcs are drawn *on*.
     */
    internal fun plan(
        cols: Int,
        rows: Int,
        coverage: Float,
        variety: Float,
        paletteSize: Int,
        floating: Boolean,
        seed: Long,
    ): List<Cell> {
        val random = Random(seed)
        val decorated = MinCoverage + coverage.coerceIn(0f, 1f) * (MaxCoverage - MinCoverage)
        val mix = variety.coerceIn(0f, 1f)
        val stops = if (floating) (paletteSize - 1).coerceAtLeast(1) else paletteSize
        val contrasts = List(stops) { StopContrast.readableAgainst(it, stops) }

        return List(cols * rows) {
            val carries = random.nextFloat() < decorated
            val turnVaries = random.nextFloat() < mix
            val turnRoll = random.nextInt(Turns)
            val ground = random.nextInt(stops)
            val far = contrasts[ground]
            val shape = far[random.nextInt(far.size)]
            Cell(decorated = carries, turn = if (turnVaries) turnRoll else 0, ground = ground, shape = shape)
        }
    }

    /**
     * One tile's quarter disc, clipped to its cell.
     *
     * **A circle of the cell's own width, centered on a corner and clipped** — not a built path. Stated that way the
     * arc meets the two edges away from its corner *exactly*, which is the whole reason two neighbours can read as one
     * larger circle; a path approximating the same curve would line up with its neighbour only by luck.
     */
    private fun drawQuarter(canvas: Canvas, paint: Paint, turn: Int, left: Float, top: Float, side: Float) {
        val corner = QuarterCorners[turn]
        canvas.save()
        canvas.clipRect(left, top, left + side, top + side)
        canvas.drawCircle(left + corner[0] * side, top + corner[1] * side, side, paint)
        canvas.restore()
    }

    /** Where a quarter disc's circle is centered, in cell fractions — the four corners, clockwise from the top-left. */
    private val QuarterCorners = arrayOf(
        floatArrayOf(0f, 0f),
        floatArrayOf(1f, 0f),
        floatArrayOf(1f, 1f),
        floatArrayOf(0f, 1f),
    )

    /** [DesignParams.variant] selecting the shapes floating on one ground over the default per-tile grounds. */
    private const val VariantFloating = 1

    /** Quarter-turns a shape can take — which corner it is anchored to. */
    private const val Turns = 4

    /**
     * What the coverage knob spans: a scattering of shapes on mostly bare tiles, up to one on every tile.
     *
     * It stops short of empty at the bottom because a frame of plain squares is a different and worse design, and
     * reaches *every* tile at the top, which is roughly where the reference's own default sits.
     */
    private const val MinCoverage = 0.2f
    private const val MaxCoverage = 1f
}
