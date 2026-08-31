package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/**
 * Broad rounded ribbons flowing through the field, each carrying a gradient down its length — *Ribbon Flow* (gart's
 * `arts/flowforce` Eclectic).
 *
 * **The same flow field as [FlowFieldGenerator] and [RibbonsGenerator], but the color runs *along* the ribbon rather
 * than being flat — that gradient is the whole difference.** Neon Ribbons lays a few flat strokes, each one palette
 * color, outlined in the ground so crossings read as over/under. Ribbon Flow drops the outline and instead sweeps each
 * ribbon through the palette as it travels — a stroke that starts one hue and arrives at another — so the frame reads as
 * liquid rounded bands melting past each other rather than as ruled ribbons. Each ribbon starts the ramp at its own
 * seeded **gradient offset**, so no two are in step.
 *
 * **Drawn segment by segment, each its own looped-palette color.** A ribbon is a chain of short round-capped strokes;
 * the round caps overlap into one continuous band, and stepping the color per segment is what makes the gradient flow.
 * The ramp is the palette's lighter stops (the darkest is the ground, dropped from the ramp so a ribbon never vanishes
 * into the back) sampled as a **loop** via [LinearGradientGenerator.colorLooping], so the sweep has no seam.
 *
 * [DesignParams.density] sets how many ribbons, [DesignParams.irregularity] how hard the field curls them. Deterministic
 * in [seed]. [ribbonCount] and [angleSpan] are the pure mappings; the tracing and the ramp they defer to are tested in
 * their own homes.
 */
object RibbonFlowGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Ribbons* slider's own range. */
    private val Amount = AmountKnob.Count("Ribbons", 10..34)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Curl",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground the ribbons flow over
        // The ramp is the lighter stops only, so a ribbon's gradient never passes through the ground color and vanishes.
        val ramp = Palette(if (palette.size > 1) palette.colors.dropLast(1) else palette.colors)

        val noise = PerlinNoise2d(seed)
        val span = angleSpan(params.irregularity)
        val angleAt = { nx: Float, ny: Float -> noise.at(nx * Frequency, ny * Frequency) * span }
        val shortSide = min(width, height)
        val random = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = RibbonFraction * shortSide
        }

        repeat(ribbonCount(params.density)) {
            val points = FlowFieldGenerator.trace(random.nextFloat(), random.nextFloat(), Steps, StepLength, angleAt)
            val offset = random.nextFloat() // this ribbon's start on the ramp — the gradient offset
            if (points.size < MinPointsToDraw) return@repeat

            val segments = points.size / 2 - 1
            for (s in 0 until segments) {
                val t = s.toFloat() / segments
                val fraction = (t + offset).let { it - floor(it) } // wrap into 0..1 for the looped ramp
                paint.color = LinearGradientGenerator.colorLooping(fraction, ramp)
                val cur = s * 2
                val next = (s + 1) * 2
                canvas.drawLine(
                    points[cur] * width, points[cur + 1] * height,
                    points[next] * width, points[next + 1] * height,
                    paint,
                )
            }
        }
        return bitmap
    }

    /** How many ribbons [density] asks for — a few broad bands up to a full flow. */
    internal fun ribbonCount(density: Float): Int = Amount.at(density)

    /**
     * The angle range the field sweeps, in radians, for a given [irregularity] — a gentle drift when low, tight coils
     * when high. [BaseAngleSpan] scaled over [MinSpanScale]..[MaxSpanScale], so the default `0.5` lands on `1.0×`.
     */
    internal fun angleSpan(irregularity: Float): Float =
        BaseAngleSpan * (MinSpanScale + irregularity.coerceIn(0f, 1f) * (MaxSpanScale - MinSpanScale))

    /** Two interleaved values is one point; a ribbon needs at least two points to be a stroke rather than a dot. */
    private const val MinPointsToDraw = 4

    /** Long, smooth steps, so a ribbon sweeps a broad arc across the frame rather than kinking. */
    private const val Steps = 80
    private const val StepLength = 0.006f

    /** How many noise cycles span the frame — broad, so a ribbon sweeps rather than swirls tightly. */
    private const val Frequency = 1.8f

    /** The angle range the field sweeps at the default irregularity, in radians. */
    private const val BaseAngleSpan = 6f

    /** The angle-span scale at the irregularity extremes — `0.5` lands between them at `1.0`. */
    private const val MinSpanScale = 0.5f
    private const val MaxSpanScale = 1.5f

    /** Ribbon thickness as a fraction of the short side — broad and rounded, the liquid-band look. */
    private const val RibbonFraction = 0.05f
}
