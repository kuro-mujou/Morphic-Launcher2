package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A dense combing of fine lines through a flow field, evenly seeded and drawn as uniform hairlines on a ground — the
 * *Flow Lines* (gart's `arts/flowforce` perl / cyanowaves).
 *
 * **The same field as [FlowFieldGenerator], combed rather than woven — the difference is entirely in *how* the lines
 * are laid.** Flow drops particles at *random* starts and strokes them at *varied* widths for a loose, woven texture;
 * Flow Lines seeds its particles on an even lattice and strokes them all at one *fine, uniform* width, so the frame
 * fills edge to edge with a coherent, brushed grain — a topographic combing rather than scattered streaks. The stepping
 * is [FlowFieldGenerator.trace] and the drawing [Streamlines.pathOf], both shared, so a Flow Line bends through the field
 * exactly as a Flow streak does; only the seeding and the stroke differ.
 *
 * **Even starts come from [PointScatter], not the random stream — that is what keeps the comb uniform.** A lattice of
 * start points (nudged a little so the lines do not begin in visible rows) guarantees the whole frame is covered; random
 * starts would clump and leave bald patches at this line count. [DesignParams.density] sets how many lines, and
 * [DesignParams.irregularity] how hard the field curls them — a near-parallel drift at `0`, tight swirls at `1`.
 * Deterministic in [seed].
 *
 * [lineCount] and [angleSpan] are the pure mappings; the tracing and path they defer to are tested in their own homes.
 */
object FlowLinesGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground the fine lines comb over
        val strokeColors = if (palette.size > 1) palette.colors.dropLast(1) else palette.colors

        val noise = PerlinNoise2d(seed)
        val span = angleSpan(params.irregularity)
        val angleAt = { nx: Float, ny: Float -> noise.at(nx * Frequency, ny * Frequency) * span }

        val count = lineCount(params.density)
        // Even lattice starts (lightly jittered) so the comb covers the whole frame rather than clumping like a scatter.
        val starts = PointScatter.gridJitter(count, StartJitter, seed)
        val shortSide = min(width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = StrokeFraction * shortSide // fine and uniform — the brushed grain, not Flow's varied weight
        }

        for (i in 0 until count) {
            val points = FlowFieldGenerator.trace(starts[i * 2], starts[i * 2 + 1], Steps, StepLength, angleAt)
            if (points.size < MinPointsToDraw) continue
            paint.color = strokeColors[i % strokeColors.size]
            canvas.drawPath(Streamlines.pathOf(points, width, height), paint)
        }
        return bitmap
    }

    /** How many lines [density] asks for — [MinLines] a sparse comb up to [MaxLines] a dense grain. */
    internal fun lineCount(density: Float): Int =
        MinLines + (density.coerceIn(0f, 1f) * (MaxLines - MinLines)).roundToInt()

    /**
     * The angle range the field sweeps, in radians, for a given [irregularity] — a gentle drift when low, tight swirls
     * when high. [BaseAngleSpan] scaled over [MinSpanScale]..[MaxSpanScale], so the default `0.5` lands on `1.0×`.
     */
    internal fun angleSpan(irregularity: Float): Float =
        BaseAngleSpan * (MinSpanScale + irregularity.coerceIn(0f, 1f) * (MaxSpanScale - MinSpanScale))

    private const val MinLines = 500
    private const val MaxLines = 2000

    /** Two interleaved values is one point; a line needs at least two points to be a stroke rather than a dot. */
    private const val MinPointsToDraw = 4

    /** Long, fine steps, so a line combs a broad arc across the frame rather than a short streak. */
    private const val Steps = 80
    private const val StepLength = 0.006f

    /** How many noise cycles span the frame — finer than Flow's, so the comb swirls tightly rather than in broad sweeps. */
    private const val Frequency = 3f

    /** The angle range the field sweeps at the default irregularity, in radians. */
    private const val BaseAngleSpan = 6f

    /** The angle-span scale at the irregularity extremes — `0.5` lands between them at `1.0`. */
    private const val MinSpanScale = 0.5f
    private const val MaxSpanScale = 1.5f

    /** Stroke width as a fraction of the short side — a fine hairline, uniform across every line. */
    private const val StrokeFraction = 0.0013f

    /** How far the lattice of start points is nudged off its grid — enough to hide the rows, not enough to clump. */
    private const val StartJitter = 0.75f
}
