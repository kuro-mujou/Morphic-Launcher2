package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.math.roundToInt
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
 * **[DesignParams.density] sets the grid size** — a few bold loops or a fine weave. Deterministic in [seed]: every
 * cell's orientation is drawn from it.
 *
 * [orientations] is pure and tested — the per-cell coin flips are what a recipe reproduces, and their count and
 * determinism need no canvas.
 */
object TruchetGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val cols = gridSize(params.density)
        val rows = (cols * height / width.coerceAtLeast(1)).coerceAtLeast(1) // roughly square cells for the frame's shape
        val flipped = orientations(cols, rows, seed)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // lightest stop — the ground the loops run over
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = min(cellW, cellH) * ArcWidthFraction
        }
        val radius = min(cellW, cellH) / 2f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * cellW
                val y0 = r * cellH
                // Arcs shift toward the palette's darker half going down the frame, so they stay legible on the ground.
                paint.color = LinearGradientGenerator.colorAt(ArcRampFloor + (1f - ArcRampFloor) * (r.toFloat() / rows), palette)
                if (flipped[r * cols + c]) {
                    arc(canvas, x0 + cellW, y0, radius, startAngle = 90f, paint) // top-right corner
                    arc(canvas, x0, y0 + cellH, radius, startAngle = 270f, paint) // bottom-left corner
                } else {
                    arc(canvas, x0, y0, radius, startAngle = 0f, paint) // top-left corner
                    arc(canvas, x0 + cellW, y0 + cellH, radius, startAngle = 180f, paint) // bottom-right corner
                }
            }
        }
        return bitmap
    }

    /** How many columns [density] asks for — [MinCols] bold loops up to [MaxCols] a fine weave. */
    internal fun gridSize(density: Float): Int =
        MinCols + (density.coerceIn(0f, 1f) * (MaxCols - MinCols)).roundToInt()

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
            startAngle, 90f, false, paint,
        )
    }

    private const val MinCols = 4
    private const val MaxCols = 14

    /** Arc stroke as a fraction of the cell — thick enough to read as ribbons of the maze, not hairlines. */
    private const val ArcWidthFraction = 0.34f

    /** The lowest point on the ramp an arc is colored from, so even the top row sits in the darker, legible half. */
    private const val ArcRampFloor = 0.45f
}
