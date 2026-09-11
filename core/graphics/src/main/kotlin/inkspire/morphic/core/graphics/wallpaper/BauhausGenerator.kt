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
 * [cells] is pure and tested: which tiles are decorated, which way each faces, and above all **which two palette
 * stops** — a tile that drew its arc in its own ground color would be an invisible shape, and no bitmap is needed to
 * catch that.
 *
 * **It plans and then paints, and a scrub is a pair of plans and a moment between them.** Everything a seed decides
 * here is discrete — decorated or bare, one corner or another, one stop or another — so a moment is not a plan of
 * tiles at all; it is two of them and a `t`, and [draw] takes exactly that. The bake is the moment `0` of a plan and
 * itself, which is what keeps it the render it was. See docs/MORPH_ENGINE_PLAN.md.
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

    /**
     * A lattice of tiles planned but not painted — at no particular size and in no particular palette.
     *
     * **In columns and rows, which is all the frame's shape decides**: the columns are a knob and the rows are however
     * many square cells reach the bottom, so a plan is one picture at every size of one shape.
     *
     * @property cells every tile's roll, row-major — [cells].
     */
    internal class Plan(val cols: Int, val rows: Int, val floating: Boolean, val cells: List<Cell>)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val plan = plan(width, height, params, palette.size, seed)
        draw(Canvas(bitmap), plan, plan, 0f, palette, width, height)
        return bitmap
    }

    /** The tiles [params] and [seed] describe, in a frame shaped like `[width]` × `[height]`, for [paletteSize] stops. */
    internal fun plan(width: Int, height: Int, params: DesignParams, paletteSize: Int, seed: Long): Plan {
        val cols = columnCount(params.density)
        // Square cells sized to fit the columns exactly; the rows are however many reach the bottom.
        val rows = ceil(height / (width.toFloat() / cols)).toInt().coerceAtLeast(1)
        val floating = params.variant == VariantFloating
        return Plan(cols, rows, floating, cells(cols, rows, params.scale, params.irregularity, paletteSize, floating, seed))
    }

    /**
     * Paints the moment [t] of the way from [from] to [to] into [canvas] at `[width]` × `[height]`, in [palette] — the
     * bake being the moment `0` of a plan and itself, and every scrub frame some other moment.
     *
     * **Each tile moves the way its own two rolls ask**, which is what a moment of this design is:
     * - its ground blends from one stop to the other;
     * - a quarter decorated at both ends **turns** from one corner to the other, the short way about the tile's center
     *   ([turnAt]) — a quarter anchored at another corner is the same shape turned, which is what the eye tracks;
     * - a quarter at only one end **blooms** out of its corner or **shrinks** back into it, the disc's radius running
     *   between nothing and the cell;
     * - and its color blends as the ground's does.
     */
    @Suppress("LongParameterList") // A moment is two plans and a t, and a frame is a canvas and a size.
    internal fun draw(canvas: Canvas, from: Plan, to: Plan, t: Float, palette: Palette, width: Int, height: Int) {
        val cols = from.cols
        val rows = from.rows
        val cell = width.toFloat() / cols
        // Square cells cannot also divide the height, so the lattice always overhangs — and the overhang is split
        // across both edges rather than left at the bottom. A single leftover strip reads as a mis-measured grid; the
        // same pixels taken off the top and bottom read as the pattern carrying on past the frame.
        val overhang = (height - rows * cell) / 2f
        val floating = from.floating
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // The floating variant's single ground: the darkest stop, this palette's convention for a ground.
        if (floating) canvas.drawColor(palette.colorAt(palette.size - 1))

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val a = from.cells[row * cols + col]
                val b = to.cells[row * cols + col]
                val left = col * cell
                val top = row * cell + overhang
                if (!floating) {
                    paint.color = blend(palette, a.ground, b.ground, t)
                    canvas.drawRect(left, top, left + cell, top + cell, paint)
                }
                when {
                    a.decorated && b.decorated -> {
                        paint.color = blend(palette, a.shape, b.shape, t)
                        drawQuarter(canvas, paint, turnAt(a.turn, b.turn, t), 1f, left, top, cell)
                    }
                    a.decorated -> {
                        paint.color = palette.colorAt(a.shape)
                        drawQuarter(canvas, paint, a.turn.toFloat(), 1f - t, left, top, cell)
                    }
                    b.decorated -> {
                        paint.color = palette.colorAt(b.shape)
                        drawQuarter(canvas, paint, b.turn.toFloat(), t, left, top, cell)
                    }
                }
            }
        }
    }

    /** Stop [a] blended [t] of the way to stop [b] — stop [a] itself, exactly, where the two are one or [t] is `0`. */
    private fun blend(palette: Palette, a: Int, b: Int, t: Float): Int =
        if (a == b || t <= 0f) {
            palette.colorAt(a)
        } else {
            LinearGradientGenerator.lerpArgb(palette.colorAt(a), palette.colorAt(b), t)
        }

    /**
     * The turn, in quarter turns, a quarter disc stands at [t] of the way from turn [a] to turn [b] — the short way
     * round, and clockwise where the two are opposite and neither way is shorter.
     */
    internal fun turnAt(a: Int, b: Int, t: Float): Float {
        val ahead = (b - a).mod(Turns)
        val step = if (ahead > Turns / 2) ahead - Turns else ahead
        return a + step * t
    }

    /**
     * A scrub between two lattices: tiles turn, bloom, shrink and recolor — see [draw] for how each does which.
     *
     * **Nothing to pair**: the lattice is the knobs' and the frame's, so two seeds hold the same tiles and a tile's
     * partner is the tile in its own place.
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
        return WallpaperMorph { canvas, t, w, h -> morph.draw(canvas, t, palette, w, h) }
    }

    /** Two lattices prepared to interpolate, tile for tile — or **null where they are not the same lattice**. */
    internal fun morph(from: Plan, to: Plan): Morph? {
        val same = from.cols == to.cols && from.rows == to.rows && from.floating == to.floating
        return if (same) Morph(from, to) else null
    }

    /** Two lattices and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /** Paints the moment [t]; the ends are the plans themselves, each drawn as its own bake. */
        fun draw(canvas: Canvas, t: Float, palette: Palette, width: Int, height: Int) = when {
            t <= 0f -> draw(canvas, from, from, 0f, palette, width, height)
            t >= 1f -> draw(canvas, to, to, 0f, palette, width, height)
            else -> draw(canvas, from, to, t, palette, width, height)
        }
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
    internal fun cells(
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
     * One tile's quarter disc at [turn] quarter turns and [bloom] of its full radius, clipped to its cell.
     *
     * **A circle of the cell's own width, centered on a corner and clipped** — not a built path. Stated that way the
     * arc meets the two edges away from its corner *exactly*, which is the whole reason two neighbours can read as one
     * larger circle; a path approximating the same curve would line up with its neighbour only by luck.
     *
     * **Anchored by [tileCornerAt]**, which reads the corner off a table at a whole turn and works it out only between
     * two — so the bake draws exactly the circle it always drew, and a turning quarter passes through every angle
     * between two corners on its way.
     */
    @Suppress("LongParameterList") // A quarter's pose and its cell.
    private fun drawQuarter(
        canvas: Canvas,
        paint: Paint,
        turn: Float,
        bloom: Float,
        left: Float,
        top: Float,
        side: Float,
    ) {
        if (bloom <= 0f) return
        val anchor = tileCornerAt(turn)
        canvas.save()
        canvas.clipRect(left, top, left + side, top + side)
        canvas.drawCircle(left + anchor[0] * side, top + anchor[1] * side, side * bloom, paint)
        canvas.restore()
    }

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
