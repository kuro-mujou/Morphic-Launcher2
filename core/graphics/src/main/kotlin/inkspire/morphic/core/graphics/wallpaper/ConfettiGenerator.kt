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
 * Discs scattered evenly across the frame in palette colors on a dark ground — *Confetti Dots* (gart's Poisson-disk
 * sampler, `stipple/util/PoissonDisk`).
 *
 * **Poisson-disk sampling, not a uniform random scatter — the difference is the whole look.** Throwing points
 * uniformly gives clumps and bare patches; Poisson-disk sampling keeps every point at least a shrinking minimum
 * distance from the others, so the discs read as *evenly strewn* — the confetti look rather than a stain. The sampler
 * throws darts at a radius, shrinks it when a batch of darts all land too close, and stops once it has enough points;
 * a point's radius when it was placed sizes its disc, so the early, widely-spaced ones are large and the later infill
 * is small.
 *
 * **The ground is the palette's darkest stop; the discs cycle the rest.** So every dot pops against the back, and no
 * dot is the same color as the ground. [DesignParams.density] sets how many discs, and [DesignParams.irregularity] the
 * *offset distortion* — how far each disc is nudged off its even Poisson position, from a pristine grid-like spread at
 * `0` to a loose sprinkle at `1`. Deterministic in [seed].
 *
 * [samples] and [distort] are pure and tested — the min-distance dart-throwing is where a wrong wrap or comparison
 * silently collapses the even spread back into clumps, and the offset is where a runaway nudge piles the discs up; both
 * need no bitmap to check.
 */
object ConfettiGenerator : Generator {

    /** One placed disc: where it sits in the unit square, and the min-distance radius it was placed at. */
    internal data class Sample(val x: Float, val y: Float, val radius: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val samples = distort(samples(sampleCount(params.density), seed), params.irregularity, seed)
        val shortSide = min(width, height)
        val dotColors = if (palette.size > 1) palette.colors.dropLast(1) else palette.colors

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground the dots pop against
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        samples.forEachIndexed { i, sample ->
            paint.color = dotColors[i % dotColors.size]
            // A disc filling most of its clearance, so evenly-spaced samples give evenly-sized dots that do not touch.
            canvas.drawCircle(sample.x * width, sample.y * height, sample.radius * DiscFraction * shortSide, paint)
        }
        return bitmap
    }

    /** How many discs [density] asks for — [MinSamples] sparse up to [MaxSamples] a dense sprinkle. */
    internal fun sampleCount(density: Float): Int =
        MinSamples + (density.coerceIn(0f, 1f) * (MaxSamples - MinSamples)).roundToInt()

    /**
     * Up to [count] evenly-spaced points for [seed] by toroidal dart-throwing — throw a candidate, keep it only if it
     * clears every existing point (and their wraps across the edges) by the current radius, and shrink the radius when
     * a full batch of darts fails. **Toroidal**, so the spacing wraps at the frame edges and the field could tile.
     *
     * May return fewer than [count] if the radius shrinks to nothing first; the caller draws however many it gets.
     */
    internal fun samples(count: Int, seed: Long): List<Sample> {
        val random = Random(seed)
        val placed = ArrayList<Sample>(count)
        var radius = InitialRadius
        while (placed.size < count && radius > 0f) {
            var attempts = 0
            var added = false
            while (attempts < AttemptsPerRadius) {
                attempts++
                val cx = random.nextFloat()
                val cy = random.nextFloat()
                if (clears(cx, cy, radius, placed)) {
                    placed.add(Sample(cx, cy, radius))
                    added = true
                    break
                }
            }
            if (!added) radius *= RadiusDecay
        }
        return placed
    }

    /**
     * [samples] with each disc nudged off its Poisson position by up to its own placement radius times [OffsetMax] and
     * [irregularity] — the *offset distortion* knob. At `irregularity = 0` the samples are returned untouched (the even
     * spread); climbing it loosens them toward a sprinkle. Scaling the nudge by each disc's radius keeps a large,
     * widely-spaced disc free to move while a small infill one barely does, so the discs stay clear of each other.
     *
     * A **salted** stream, independent of the dart-throwing in [samples], so sliding irregularity moves the discs
     * without re-rolling which points were placed. Positions are clamped to the unit square rather than wrapped, since a
     * disc pushed off an edge should sit at the edge, not reappear opposite.
     */
    internal fun distort(samples: List<Sample>, irregularity: Float, seed: Long): List<Sample> {
        val amount = irregularity.coerceIn(0f, 1f)
        if (amount <= 0f) return samples
        val random = Random(seed xor OffsetSalt)
        return samples.map { sample ->
            val reach = sample.radius * OffsetMax * amount
            val dx = (random.nextFloat() * 2f - 1f) * reach
            val dy = (random.nextFloat() * 2f - 1f) * reach
            sample.copy(x = (sample.x + dx).coerceIn(0f, 1f), y = (sample.y + dy).coerceIn(0f, 1f))
        }
    }

    /** Whether ([cx], [cy]) is at least [radius] from every placed sample, counting the toroidal wraps across each edge. */
    private fun clears(cx: Float, cy: Float, radius: Float, placed: List<Sample>): Boolean {
        val min2 = radius * radius
        for (sample in placed) {
            for (wx in Wraps) {
                for (wy in Wraps) {
                    val dx = cx + wx - sample.x
                    val dy = cy + wy - sample.y
                    if (dx * dx + dy * dy < min2) return false
                }
            }
        }
        return true
    }

    private const val MinSamples = 40
    private const val MaxSamples = 220

    /** The disc radius the first, widest-spaced darts are thrown at — the largest confetti. */
    private const val InitialRadius = 0.14f

    /** Darts tried at a radius before it shrinks — enough to fill the radius before giving up on it. */
    private const val AttemptsPerRadius = 800

    /** How much the radius shrinks when a batch of darts all fail — a gentle decay so sizes grade smoothly. */
    private const val RadiusDecay = 0.98f

    /** A disc fills this fraction of its placement radius, so neighbours stay just clear of each other. */
    private const val DiscFraction = 0.5f

    /** The most a disc is nudged off its Poisson position at full irregularity, as a fraction of its placement radius. */
    private const val OffsetMax = 0.9f

    /** Keeps the offset stream independent of the dart-throwing, so irregularity moves discs without re-rolling them. */
    private const val OffsetSalt = 0x632BE5ABL

    /** The three toroidal offsets per axis — the tile itself and its two edge wraps. */
    private val Wraps = floatArrayOf(-1f, 0f, 1f)
}
