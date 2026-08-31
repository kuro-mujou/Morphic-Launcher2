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
 * A few thick ribbons traced through a flow field, each a palette color outlined against a dark ground — the *Neon
 * Ribbons* (gart's `arts/flowforce/glst`).
 *
 * **The same flow field as [FlowFieldGenerator], drawn as fat ribbons instead of fine streaks.** Both drop particles
 * into a noise field and follow it; the difference is entirely in the drawing — Flow lays down hundreds of hairlines
 * for a woven texture, Ribbons lays down a *few* broad strokes, each outlined in the ground color so where two cross
 * one reads as passing over the other. That shared stepping is literally [FlowFieldGenerator.trace], reused here rather
 * than re-derived, so the two designs bend through the field identically — a streamline that is silently wrong is wrong
 * in one place, not two.
 *
 * **Each ribbon is outlined then filled**: a wider dark stroke laid first, the color stroke over it, so abutting or
 * crossing ribbons stay separate rather than merging into a blob. Colors cycle the palette's lighter stops over its
 * darkest as the ground. [DesignParams.density] sets how many ribbons, and [DesignParams.irregularity] how much the
 * shared field curls them — long sweeping ribbons at `0`, coiling ones at `1`. Deterministic in [seed].
 */
object RibbonsGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground
        val ribbonColors = if (palette.size > 1) palette.colors.dropLast(1) else palette.colors

        val noise = PerlinNoise2d(seed)
        val span = BaseAngleSpan * (0.5f + params.irregularity.coerceIn(0f, 1f)) // 0.5 → the shipped sweep
        val angleAt = { nx: Float, ny: Float -> noise.at(nx * Frequency, ny * Frequency) * span }
        val shortSide = min(width, height)
        val random = Random(seed)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = palette.colorAt(palette.size - 1)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        repeat(ribbonCount(params.density)) { i ->
            // FlowFieldGenerator.trace: the shared stepping, so Ribbons and Flow bend through the field identically.
            val points = FlowFieldGenerator.trace(random.nextFloat(), random.nextFloat(), Steps, StepLength, angleAt)
            if (points.size < MinPointsToDraw) return@repeat

            val width0 = RibbonFraction * shortSide
            val path = Streamlines.pathOf(points, width, height)
            outline.strokeWidth = width0 + OutlineGap * shortSide
            fill.strokeWidth = width0
            fill.color = ribbonColors[i % ribbonColors.size]
            canvas.drawPath(path, outline)
            canvas.drawPath(path, fill)
        }
        return bitmap
    }

    /** How many ribbons [density] asks for — [MinRibbons] sparse up to [MaxRibbons] a full weave. */
    internal fun ribbonCount(density: Float): Int =
        MinRibbons + (density.coerceIn(0f, 1f) * (MaxRibbons - MinRibbons)).roundToInt()

    private const val MinRibbons = 8
    private const val MaxRibbons = 26

    /** Two interleaved values is one point; a ribbon needs at least two points to be a stroke rather than a dot. */
    private const val MinPointsToDraw = 4

    /** A ribbon is long and smooth — more, shorter steps than Flow's streaks, so it curves rather than kinks. */
    private const val Steps = 70
    private const val StepLength = 0.006f

    /** How many noise cycles span the frame — broader than Flow's, so a ribbon sweeps rather than swirls tightly. */
    private const val Frequency = 1.8f

    /** The angle range the field sweeps at the default irregularity, in radians. */
    private const val BaseAngleSpan = 6f

    /** Ribbon thickness and its dark outline, each a fraction of the short side. */
    private const val RibbonFraction = 0.05f
    private const val OutlineGap = 0.012f
}
