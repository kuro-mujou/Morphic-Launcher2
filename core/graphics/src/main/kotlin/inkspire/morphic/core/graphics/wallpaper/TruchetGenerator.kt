package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * A grid of Truchet tiles — each a pair of quarter-circle arcs turned one of two ways at random — that join across the
 * grid into a maze of flowing loops (gart's `arts/ticktiletock`).
 *
 * **Two quarter-arcs per cell, one of two orientations — the whole Truchet trick.** A cell draws arcs either through
 * its top-left and bottom-right corners or through its top-right and bottom-left corners. Because every arc meets an
 * edge at the *midpoint*, a cell's arcs always line up with its neighbours' whichever way each is turned, so the loops
 * run on across the grid with no breaks — the pattern is emergent, not authored. The arc color climbs the palette down
 * the frame so the maze shifts hue top to bottom, drawn over the lightest stop as a ground.
 *
 * **[DesignParams.density] sets the grid size** — a few bold loops or a fine weave — and [DesignParams.scale] the
 * weight of the line that draws them, which is a different picture at the same grid: a fine tracery, the ribbon this
 * design shipped with, or a maze whose arcs swell until the ground between them is the pattern instead. Deterministic
 * in [seed]: every cell's orientation is drawn from it.
 *
 * [orientations] and [arcWidthFraction] are pure and tested — the per-cell coin flips are what a recipe reproduces,
 * and a line weight that quietly closed the maze would be a solid frame with a plausible reason.
 *
 * **It plans and then paints, and a scrub is a pair of plans and a moment between them** — Bauhaus's shape, for
 * Bauhaus's reason: a flip is a choice, so a moment is not a plan of cells but two of them and a `t`. The bake is the
 * moment `0` of a plan and itself. See docs/MORPH_ENGINE_PLAN.md.
 */
object TruchetGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Resolution* slider's own range. */
    private val Amount = AmountKnob.Count("Resolution", 4..14)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
    )

    /**
     * A maze planned but not painted — which way every cell turns its arcs, at no particular size and in no particular
     * palette.
     *
     * **In columns and rows, which is all the frame's shape decides**, so a plan is one picture at every size of one
     * shape.
     *
     * @property weight an arc's width as a share of its cell — [arcWidthFraction] of the thickness knob.
     * @property flipped every cell's orientation, row-major — [orientations].
     */
    internal class Plan(val cols: Int, val rows: Int, val weight: Float, val flipped: BooleanArray)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val plan = plan(width, height, params, seed)
        draw(Canvas(bitmap), plan, plan, 0f, palette, width, height)
        return bitmap
    }

    /** The maze [params] and [seed] describe, in a frame shaped like `[width]` × `[height]`. */
    internal fun plan(width: Int, height: Int, params: DesignParams, seed: Long): Plan {
        val cols = gridSize(params.density)
        val rows = (cols * height / width.coerceAtLeast(1)).coerceAtLeast(1) // roughly square cells for the frame's shape
        return Plan(cols, rows, arcWidthFraction(params.scale), orientations(cols, rows, seed))
    }

    /**
     * Paints the moment [t] of the way from [from] to [to] into [canvas] at `[width]` × `[height]`, in [palette] — the
     * bake being the moment `0` of a plan and itself, and every scrub frame some other moment.
     *
     * **A cell that flips turns**, a quarter turn clockwise about its center with both arcs carried round ([turnAt]).
     * The two orientations are one tile turned, so turning is the one motion that passes between them without drawing
     * a third tile; mid-turn the arcs leave the edge midpoints and the loops through that cell break, and they join
     * again as it lands. A cell that keeps its orientation does not move, and an arc's color is its row's, so nothing
     * else changes.
     */
    @Suppress("LongParameterList") // A moment is two plans and a t, and a frame is a canvas and a size.
    internal fun draw(canvas: Canvas, from: Plan, to: Plan, t: Float, palette: Palette, width: Int, height: Int) {
        val cols = from.cols
        val rows = from.rows
        canvas.drawColor(palette.colorAt(0)) // lightest stop — the ground the loops run over
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = min(cellW, cellH) * from.weight
        }
        val radius = min(cellW, cellH) / 2f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * cellW
                val y0 = r * cellH
                // Arcs shift toward the palette's darker half going down the frame, so they stay legible on the ground.
                val down = ArcRampFloor + (1f - ArcRampFloor) * (r.toFloat() / rows)
                paint.color = LinearGradientGenerator.colorAt(down, palette)
                val turn = turnAt(from.flipped[r * cols + c], to.flipped[r * cols + c], t)
                // The two arcs sit at opposite corners, so the second is the first turned half round. At a whole turn
                // the corner is read off a table, which keeps the bake the four arcs it always drew.
                for (half in 0..1) {
                    val at = turn + half * 2
                    val corner = tileCornerAt(at)
                    arc(canvas, x0 + corner[0] * cellW, y0 + corner[1] * cellH, radius, at * QuarterTurn, paint)
                }
            }
        }
    }

    /**
     * The quarter turns a cell stands at, [t] of the way from orientation [a] to orientation [b] — `0` unflipped, `1`
     * flipped, and a flip either way taken clockwise, since both ways are a quarter turn and neither is shorter.
     */
    internal fun turnAt(a: Boolean, b: Boolean, t: Float): Float {
        val start = if (a) 1f else 0f
        return if (a == b) start else start + t
    }

    /**
     * A scrub between two mazes: the cells that flip turn, and nothing else moves — see [draw].
     *
     * **Nothing to pair**: the grid is the knobs' and the frame's, so two seeds hold the same cells and a cell's partner
     * is the cell in its own place.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(width, height, params, from), plan(width, height, params, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> morph.draw(canvas, t, palette, w, h) }
    }

    /** Two mazes prepared to interpolate, cell for cell — or **null where they are not the same grid**. */
    internal fun morph(from: Plan, to: Plan): Morph? {
        val same = from.cols == to.cols && from.rows == to.rows && from.weight == to.weight
        return if (same) Morph(from, to) else null
    }

    /** Two mazes and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /** Paints the moment [t]; the ends are the plans themselves, each drawn as its own bake. */
        fun draw(canvas: Canvas, t: Float, palette: Palette, width: Int, height: Int) = when {
            t <= 0f -> draw(canvas, from, from, 0f, palette, width, height)
            t >= 1f -> draw(canvas, to, to, 0f, palette, width, height)
            else -> draw(canvas, from, to, t, palette, width, height)
        }
    }

    /** How many columns [density] asks for — bold loops up to a fine weave. */
    internal fun gridSize(density: Float): Int = Amount.at(density)

    /**
     * A `[cols] × [rows]` grid of orientation flips for [seed], row-major — `true` where a cell turns its arcs the
     * top-right / bottom-left way, `false` the other. This is the whole of what a Truchet seed decides.
     */
    internal fun orientations(cols: Int, rows: Int, seed: Long): BooleanArray {
        val random = Random(seed)
        return BooleanArray(cols * rows) { random.nextBoolean() }
    }

    /**
     * A quarter-circle arc of [radius] centered on the cell corner at ([cornerX], [cornerY]), swept 90° from
     * [startAngle] — the quarter that lies *inside* the cell, which is set by which corner it is.
     */
    private fun arc(canvas: Canvas, cornerX: Float, cornerY: Float, radius: Float, startAngle: Float, paint: Paint) {
        canvas.drawArc(
            cornerX - radius, cornerY - radius, cornerX + radius, cornerY + radius,
            startAngle, QuarterTurn, false, paint,
        )
    }

    /**
     * How wide an arc is drawn at [scale], as a share of its cell — a tracery at `0`, and at `1` wide enough that the
     * arcs meet across the cell and the ground reads as the pattern.
     *
     * Curved so the field's `0.5` lands on the weight this design shipped with, and floored so the thinnest setting is
     * still a line rather than nothing — a Truchet with no ink is a flat frame, and the knob that produced it would
     * look like a broken design rather than an empty one.
     */
    internal fun arcWidthFraction(scale: Float): Float =
        MinArcWidth + (MaxArcWidth - MinArcWidth) * scale.coerceIn(0f, 1f).pow(ArcWidthCurve)

    /**
     * A quarter turn, in degrees — the sweep of every arc, and how far a corner's arc starts from the last corner's,
     * since a Truchet tile's arc runs edge midpoint to edge midpoint around one corner.
     */
    private const val QuarterTurn = 90f

    /** The arc's width as a share of its cell at [DesignParams.scale]'s two ends, and the exponent centring it. */
    private const val MinArcWidth = 0.04f
    private const val MaxArcWidth = 0.9f

    /** Chosen so the field's `0.5` resolves to `0.34` of a cell, the weight this design shipped with. */
    private const val ArcWidthCurve = 1.52f

    /** The lowest point on the ramp an arc is colored from, so even the top row sits in the darker, legible half. */
    private const val ArcRampFloor = 0.45f
}
