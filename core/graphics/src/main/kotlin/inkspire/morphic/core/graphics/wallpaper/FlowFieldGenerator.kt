package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Streamlines traced through a noise flow field and drawn as strokes — the swirling *Flow Field*, and the first
 * generator built on the real generative engine ([PerlinNoise2d]).
 *
 * **A flow field is one idea: at every point the noise says which way to go, and a particle dropped in follows it.**
 * Hundreds of particles seeded across the frame, each stepped along the field for a while, trace the curved streaks
 * that make the swirl read. There is no simulation and no state — just the field, sampled — which is what keeps it
 * deterministic in [seed] (the field *and* the particle starts are seeded) and cheap.
 *
 * **The strokes are the lighter palette colors on the darkest as a ground**, so the streaks read against their
 * background. [DesignParams.density] sets how many particles there are — sparse streaks or a dense weave.
 *
 * **[trace] is pure and takes the field as a function**, so the stepping — the part that is silently wrong when a
 * sign is flipped or the bounds check runs a step late — is tested against a known field with no noise and no bitmap
 * in the way. Only the stroke drawing needs a canvas.
 */
object FlowFieldGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // Ordered light-to-dark by convention, so the last stop is the ground and the rest are the streaks over it.
        canvas.drawColor(palette.colorAt(palette.size - 1))
        val strokeColors = if (palette.size > 1) palette.colors.dropLast(1) else palette.colors

        val noise = PerlinNoise2d(seed)
        // Position in the unit square scaled to the noise's feature size, mapped to an angle with a few turns of range
        // so the field curls rather than merely leaning one way.
        val angleAt = { nx: Float, ny: Float -> noise.at(nx * Frequency, ny * Frequency) * AngleSpan }

        val shortSide = min(width, height)
        val random = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        repeat(particleCount(params.density)) { i ->
            val points = trace(random.nextFloat(), random.nextFloat(), Steps, StepLength, angleAt)
            if (points.size < MinPointsToDraw) return@repeat

            paint.color = strokeColors[i % strokeColors.size]
            paint.strokeWidth = (MinStrokeFraction + random.nextFloat() * StrokeRange) * shortSide
            canvas.drawPath(pathOf(points, width, height), paint)
        }
        return bitmap
    }

    /** How many particles [density] asks for — [MinParticles] when sparse up to [MaxParticles] when dense. */
    internal fun particleCount(density: Float): Int =
        MinParticles + (density.coerceIn(0f, 1f) * (MaxParticles - MinParticles)).roundToInt()

    /**
     * A streamline through [angleAt], from ([startX], [startY]) in the unit square — up to [steps] points, each a
     * [stepLength] hop in the field's direction, stopping the moment it leaves the frame.
     *
     * Returns interleaved `x, y` in unit-square coordinates. **The first point is recorded before the bounds check**,
     * so a particle that starts at the very edge still contributes a mark rather than vanishing; the check then ends
     * the line as soon as it wanders out, which is what keeps a streak from smearing along the frame's edge.
     */
    internal fun trace(
        startX: Float,
        startY: Float,
        steps: Int,
        stepLength: Float,
        angleAt: (Float, Float) -> Float,
    ): FloatArray {
        val points = ArrayList<Float>(steps * 2)
        var x = startX
        var y = startY
        var step = 0
        while (step < steps) {
            points.add(x)
            points.add(y)
            if (x !in 0f..1f || y !in 0f..1f) break

            val angle = angleAt(x, y)
            x += cos(angle) * stepLength
            y += sin(angle) * stepLength
            step++
        }
        return points.toFloatArray()
    }

    /** The interleaved unit-square points as a canvas [Path] scaled to `[width] × [height]`. */
    private fun pathOf(points: FloatArray, width: Int, height: Int): Path = Path().apply {
        moveTo(points[0] * width, points[1] * height)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] * width, points[i + 1] * height)
            i += 2
        }
    }

    private const val MinParticles = 300
    private const val MaxParticles = 1200

    /** Two interleaved values is one point; a streak needs at least two points to be a line rather than a dot. */
    private const val MinPointsToDraw = 4

    /** How many hops a streamline takes, and how far each is as a fraction of the frame — together, its length. */
    private const val Steps = 42
    private const val StepLength = 0.006f

    /** How many noise cycles span the frame — the size of the swirls. */
    private const val Frequency = 2.5f

    /** The angle range the field sweeps, in radians — a few turns, so it curls rather than leaning one way. */
    private const val AngleSpan = 7f

    /** Stroke width as a fraction of the short side: a floor plus a seeded spread, so the streaks vary in weight. */
    private const val MinStrokeFraction = 0.002f
    private const val StrokeRange = 0.004f
}
