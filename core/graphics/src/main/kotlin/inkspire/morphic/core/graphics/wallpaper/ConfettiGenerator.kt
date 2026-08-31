package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Discs strewn across the frame in palette colors — *Confetti Dots*.
 *
 * **A tilted lattice pushed around, not a random scatter.** The dots start on a square grid turned [TiltDegrees] off
 * the frame, and each is nudged off its cell. The tilt is the reason it reads as *strewn* rather than as a grid at all:
 * an axis-aligned lattice announces itself through any amount of jitter, because the eye finds the horizontals; turned
 * a little, the same lattice reads as an even sprinkle. This replaces a Poisson-disk sampler, which solved the harder
 * problem (guaranteed minimum spacing) to reach a look a jittered grid gets to for nothing — and which had no rigid end
 * to its knob, since uniformly-random points cannot be made *more* even.
 *
 * **The palette is spent unevenly, and that is where the restraint comes from.** Each disc's color is drawn with a
 * weight that falls off geometrically down the stops ([WeightFalloff]), so the first ink is the field, the second is
 * common, and the last is a rare accent — a handful of red dots in a bed of sand. Cycling the stops evenly, as the
 * first cut did, makes every color equally loud and the frame equally busy; this is a fixed property rather than a
 * knob because the color modes already reduce the palette, and a weighting over the one ink a two-stop palette leaves
 * would be a control that does nothing.
 *
 * **[DesignParams.variant] is a depth of field, which is the only real *depth* in the catalog.** Every disc has a
 * distance from the eye, and its size is that distance — a near one is big, a far one small, exactly as a lens sees it.
 * *Near* holds the big discs sharp and softens the small, *Far* does the reverse, and *Flat* is the plain graphic
 * frame. The blur is `BlurMaskFilter` on the disc itself, quantized into [BlurLevels] cached filters so a few hundred
 * discs do not each allocate one.
 *
 * **[DesignParams.irregularity] is *scatter*, and it moves two things at once** — how far each disc is pushed off its
 * cell *and* how much the radii vary. Both are the same question asked twice (the reference splits them into *Offset
 * distortion* and *Radius variation*), and joining them is what gives the knob a real rigid end: at `0` this is an even
 * lattice of identical discs, a polka-dot pattern, and at `1` a chaotic sprinkle of specks and boulders.
 * [DesignParams.density] is the lattice pitch and [DesignParams.scale] the disc size within it.
 *
 * [dots] is pure and tested — the lattice must cover a *rotated* frame (a corner left bare is a bald patch nothing else
 * explains), the radii must stay inside the cell, and the color weighting must actually favour the early stops.
 */
object ConfettiGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Resolution* slider's own range. */
    private val Amount = AmountKnob.Count("Resolution", 5..24)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Size",
        irregularity = "Scatter",
        variant = VariantKnob("Focus", listOf("Flat", "Near", "Far")),
    )

    /**
     * One placed disc, in pixels.
     *
     * @property depth how near the disc is, `0..1` — `1` is the largest disc this frame holds and `0` the smallest.
     *   It ranks the discs by size rather than measuring it, and is **stretched over whatever spread exists**: with the
     *   scatter low the discs differ by little, and a depth read off their radii directly would then leave the focus
     *   knob with a fraction of its range and almost nothing to show. Every disc the same size is the one case with no
     *   depth at all, and there it is `1` for all of them — see [Dot.depth]'s use in the focus blur.
     * @property ink which palette stop above the ground the disc is painted in, `1..n-1`.
     */
    internal data class Dot(val x: Float, val y: Float, val radius: Float, val depth: Float, val ink: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // stop 0 is the ground, light or dark as the palette itself decides
        if (palette.size < 2) return bitmap

        val dots = dots(
            width = width,
            height = height,
            resolution = Amount.at(params.density),
            scatter = params.irregularity,
            size = params.scale,
            inks = palette.size - 1,
            seed = seed,
        )
        val focus = params.variant.coerceIn(0, 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val blurs = arrayOfNulls<BlurMaskFilter>(BlurLevels + 1)

        // The blur is measured against the frame's largest disc, taken from the discs themselves rather than
        // re-derived from the params — a second copy of that arithmetic is exactly the kind that drifts.
        val nearest = dots.maxOfOrNull { it.radius } ?: 0f
        // And *Far*'s focal plane is the farthest disc there is, which is the nearest one when they are all the same
        // size: that is what makes both focus variants render sharp when the scatter leaves no depth to work with.
        val farthest = dots.minOfOrNull { it.depth } ?: 0f

        // Far discs first, so a near one laps over what is behind it — the ordering a depth of field needs to read.
        for (dot in dots.sortedBy { it.depth }) {
            paint.color = palette.colorAt(dot.ink)
            paint.maskFilter = blurFor(dot, focus, nearest, farthest, blurs)
            canvas.drawCircle(dot.x, dot.y, dot.radius, paint)
        }
        return bitmap
    }

    /**
     * The discs for a `[width] × [height]` frame: a square lattice of pitch `longSide / [resolution]`, turned
     * [TiltDegrees], with each point nudged and each radius thinned by [scatter], sized within its cell by [size].
     *
     * **The lattice is generated over the frame's bounding circle, not its rectangle.** Turned, a grid laid out to the
     * frame's own width and height leaves two corners bare — and a bald corner reads as a bug, not as composition. So
     * the index range is taken from the half-diagonal, and everything that lands outside is dropped.
     *
     * @param inks how many palette stops sit above the ground; a disc's stop is drawn from `1..inks` with the
     *   geometric weighting described on the class.
     */
    internal fun dots(
        width: Int,
        height: Int,
        resolution: Int,
        scatter: Float,
        size: Float,
        inks: Int,
        seed: Long,
    ): List<Dot> {
        val pitch = max(width, height).toFloat() / resolution.coerceAtLeast(1)
        val spread = scatter.coerceIn(0f, 1f)
        val maxRadius = pitch * (MinRadius + size.coerceIn(0f, 1f) * (MaxRadius - MinRadius))
        val tilt = TiltDegrees * PI.toFloat() / 180f
        val cos = cos(tilt)
        val sin = sin(tilt)
        val cx = width / 2f
        val cy = height / 2f
        val reach = ceil(hypot(width.toFloat(), height.toFloat()) / 2f / pitch).toInt() + 1
        val random = Random(seed)
        val weights = inkWeights(inks)

        val out = ArrayList<Dot>((2 * reach + 1) * (2 * reach + 1))
        for (row in -reach..reach) {
            for (col in -reach..reach) {
                // Every disc draws the same four values whatever the knobs say, so sliding one does not reshuffle the
                // rest — the discipline the jittered designs keep, and the reason a slider's effect is legible.
                val jx = (random.nextFloat() * 2f - 1f) * spread * MaxOffset
                val jy = (random.nextFloat() * 2f - 1f) * spread * MaxOffset
                val thin = random.nextFloat()
                val pick = random.nextFloat()

                // Culled on the *lattice* position at the *largest* radius a disc could take, not on where this one
                // ended up — so which discs exist is decided by the lattice alone and no knob can add or drop one at
                // the frame edge. The price is a ring of discs drawn just outside the frame; the alternative is a
                // count that changes as a slider moves, which is the kind of thing nobody notices until it matters.
                val bx = cx + col * pitch * cos - row * pitch * sin
                val by = cy + col * pitch * sin + row * pitch * cos
                if (offFrame(bx, by, maxRadius + MaxOffset * pitch, width, height)) continue

                val lx = (col + jx) * pitch
                val ly = (row + jy) * pitch
                val x = cx + lx * cos - ly * sin
                val y = cy + lx * sin + ly * cos
                val radius = maxRadius * (1f - spread * MaxThinning * thin)
                // Ranked, not measured — and pinned to 1 where every disc is the same size, which is the one case
                // where there is no depth to have a field over.
                val depth = if (spread <= 0f) 1f else 1f - thin
                out.add(Dot(x, y, radius, depth, inkFor(pick, weights)))
            }
        }
        return out
    }

    /** Whether a disc centred at ([x], [y]) is beyond a `[width]` × `[height]` frame even allowing it [margin] of reach. */
    private fun offFrame(x: Float, y: Float, margin: Float, width: Int, height: Int): Boolean =
        x + margin < 0f || x - margin > width || y + margin < 0f || y - margin > height

    /**
     * The cumulative pick thresholds for [inks] stops, each stop [WeightFalloff] as likely as the one before it,
     * normalized to `1`. A uniform `0..1` draw indexed through this lands on stop `1` most of the time and on the last
     * stop rarely, which is the whole of the design's restraint.
     */
    internal fun inkWeights(inks: Int): FloatArray {
        val n = inks.coerceAtLeast(1)
        val cumulative = FloatArray(n)
        var weight = 1f
        var total = 0f
        for (i in 0 until n) {
            total += weight
            cumulative[i] = total
            weight *= WeightFalloff
        }
        for (i in 0 until n) cumulative[i] /= total
        return cumulative
    }

    /** The palette stop a `0..1` draw of [pick] selects through [weights] — `1`-based, the ground being stop `0`. */
    private fun inkFor(pick: Float, weights: FloatArray): Int {
        for (i in weights.indices) if (pick <= weights[i]) return i + 1
        return weights.size
    }

    /**
     * The blur for [dot] under [focus] (`0` flat, `1` near-sharp, `2` far-sharp), from [cache] — `null` where the disc
     * is sharp enough that a mask filter would only cost time.
     *
     * **Measured against [nearest], the frame's largest disc, and never against the disc's own radius.** A lens's
     * circle of confusion is a property of how far out of focus a thing is, not of how big it is — so scaling the blur
     * by each disc's own size makes the small ones, which are exactly the ones *Near* is supposed to soften, come out
     * blurred by a fraction of a pixel and therefore not at all. That is a knob that renders identically to *Flat*.
     *
     * Quantized to [BlurLevels] steps because a `BlurMaskFilter` is an allocation with a native peer, and a frame holds
     * hundreds of discs whose blurs differ by fractions of a pixel nobody can see.
     */
    private fun blurFor(
        dot: Dot,
        focus: Int,
        nearest: Float,
        farthest: Float,
        cache: Array<BlurMaskFilter?>,
    ): BlurMaskFilter? {
        if (focus == 0) return null
        val defocus = if (focus == 1) 1f - dot.depth else dot.depth - farthest
        val level = (defocus * BlurLevels).roundToInt().coerceIn(0, BlurLevels)
        if (level == 0) return null
        val radius = nearest * MaxBlur * level / BlurLevels
        if (radius < 1f) return null
        return cache[level] ?: BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL).also { cache[level] = it }
    }

    /**
     * How far the lattice is turned off the frame, in degrees. Enough that the eye stops finding the horizontals,
     * little enough that the spacing still reads as even — an axis-aligned grid announces itself through any amount of
     * jitter, and a turn near 45° reads as a deliberate diamond pattern rather than as a scatter.
     */
    private const val TiltDegrees = 12f

    /** A disc's radius as a fraction of the pitch at the smallest *Size*, and at the largest (just clear of touching). */
    private const val MinRadius = 0.08f
    private const val MaxRadius = 0.45f

    /** The most a disc is pushed off its cell at full scatter, in pitches — half a cell, so it reaches its neighbour. */
    private const val MaxOffset = 0.5f

    /** How much of its radius a disc can lose at full scatter, so the field spans specks to boulders but never nothing. */
    private const val MaxThinning = 0.85f

    /** Each palette stop is this much as likely as the one before it — the reference's own default ratio, 100/50/25. */
    private const val WeightFalloff = 0.5f

    /** A fully defocused disc's blur, as a fraction of the frame's largest disc — a soft edge, not a smear. */
    private const val MaxBlur = 0.55f

    /** Distinct blurs cached; a disc's defocus is rounded to one of these rather than allocating its own filter. */
    private const val BlurLevels = 8
}
