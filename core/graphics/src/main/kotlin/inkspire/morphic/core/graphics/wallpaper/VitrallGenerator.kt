package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The frame cut and re-cut by chords into leaded panes of tinted glass — *Vitrall*.
 *
 * **The cuts run edge to edge, which is what separates this from a mosaic.** A Voronoi breaks a frame into cells
 * *around points*, so every cell is a compact blob of roughly the same size; this cuts it *with lines*, so a single
 * chord can run the whole diagonal and the panes come out as long shards and slender wedges beside squat little
 * quadrilaterals. Both are "flat cells with leading between them" and they do not look remotely alike, which is why
 * [VoronoiGenerator] keeps its own name and this is a design of its own rather than a variant of it.
 *
 * **The subdivision is recursive and area-weighted.** One pane is picked — with probability rising as
 * [AreaBias] powers of its area, so the big ones go first but a small one is still cut now and then — and split by a
 * chord through a point near its centroid. That is what gives a spread of pane sizes; splitting the *largest* every
 * time gives a suspiciously even honeycomb, and splitting a *uniformly* chosen one leaves one huge untouched pane.
 *
 * **A cut is sometimes parallel to the pane's longest edge**, which is where the runs of parallel strips come from —
 * the detail that makes the reference read as leaded glass rather than as shattered glass. Always parallel would be a
 * brick wall; never, and the picture is all wedges.
 *
 * **[DesignParams.irregularity] bows the cuts, and it bows them by moving the *plane*, not the lines.** Each pane's
 * outline is subdivided and every point pushed through one smooth displacement field, so a straight chord becomes an
 * arc and the two panes either side of it stay welded along that arc — the leading has no gaps to open. Clipping
 * against curves directly would mean solving for the crossings, and getting one of them slightly wrong shows up as a
 * hairline of ground between two panes. The field is **pinned at the frame's border** so the outer edges stay
 * straight; a warp that moved them would pull the glass off its own frame.
 *
 * **[DesignParams.depth] is the glass, and without it this is just flat cells.** Each pane is filled with a gradient
 * rather than a color: its own tone at one edge, lifted and dropped along a fixed light direction across it. The
 * reference does this at every setting of every knob, and it is most of why its panes read as *material*.
 *
 * **[DesignParams.scale] is the leading**, drawn as a stroke over the fills — every pane stroked after every pane is
 * filled, so a shared edge takes both strokes and the line is the same weight everywhere. Filling and stroking pane by
 * pane would let each fill erase its neighbour's half.
 *
 * [panes] and [split] are pure and tested — a split that drops a vertex or keeps a degenerate sliver is a hole in the
 * glass, and neither needs a bitmap to catch.
 */
object VitrallGenerator : Generator {

    /** What [DesignParams.density] resolves to — the pane count, and the slider's own range. */
    private val Amount = AmountKnob.Count("Panes", 8..140)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Leading",
        irregularity = "Curves",
        depth = "Glass",
        variant = VariantKnob("Colors", Tint.entries.map { it.label }),
    )

    /**
     * How a pane's own tone is chosen — the reference's *Color distribution*.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     */
    internal enum class Tint(val label: String) {
        /** The ramp read at the pane's height, so the window is one progression up the frame — the reference's default. */
        VERTICAL("Vertical"),

        /** A ramp position of its own per pane, for a palette that is a set of accents rather than a progression. */
        SCATTERED("Scattered"),
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val panes = panes(Amount.at(params.density), seed)
        val bow = params.irregularity.coerceIn(0f, 1f) * MaxBow
        val glass = params.depth.coerceIn(0f, 1f) * MaxGlass
        val tint = Tint.entries[params.variant.coerceIn(0, Tint.entries.lastIndex)]
        val lead = params.scale.coerceIn(0f, 1f) * MaxLeading * min(width, height)
        val warp = PerlinNoise2d(seed xor BowSalt)
        val tone = Random(seed xor ToneSalt)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // the leading's own ground, under everything
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Both passes walk the same panes in the same order, so the outlines match exactly.
        val outlines = panes.map { outline(it, warp, bow, width, height) }
        outlines.forEachIndexed { i, path ->
            val at = if (tint == Tint.SCATTERED) tone.nextFloat() else centroidY(panes[i])
            fill.shader = glassShader(panes[i], LinearGradientGenerator.colorAt(at, palette), glass, width, height)
            canvas.drawPath(path, fill)
        }
        if (lead > 0f) {
            val leading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = lead
                strokeJoin = Paint.Join.ROUND
                color = palette.colorAt(palette.size - 1) // darkest stop by convention — the came between panes
            }
            outlines.forEach { canvas.drawPath(it, leading) }
        }
        return bitmap
    }

    /**
     * [count] panes tiling the unit square, each a convex polygon of interleaved `x, y`.
     *
     * A cut that would leave a pane under [MinArea] is thrown away and the pane tried again, which is what keeps the
     * window from filling up with needles — a chord through a point near a vertex is almost all sliver, and a few of
     * those per hundred panes is character while a field of them is noise.
     */
    internal fun panes(count: Int, seed: Long): List<FloatArray> {
        val random = Random(seed)
        val panes = ArrayList<FloatArray>(count)
        panes.add(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        var guard = count * MaxTries
        while (panes.size < count && guard-- > 0) {
            val index = pick(panes, random)
            val pane = panes[index]
            val (dx, dy) = if (random.nextFloat() < SliceBias) longestEdge(pane) else {
                val angle = random.nextFloat() * Math.PI.toFloat()
                cos(angle) to sin(angle)
            }
            val corner = random.nextInt(pane.size / 2)
            val toward = random.nextFloat() * CutDrift
            val cx = centroidX(pane) + (pane[corner * 2] - centroidX(pane)) * toward
            val cy = centroidY(pane) + (pane[corner * 2 + 1] - centroidY(pane)) * toward
            val halves = split(pane, cx, cy, dx, dy)
            if (halves.size == 2 && halves.all { area(it) >= MinArea }) {
                panes[index] = halves[0]
                panes.add(halves[1])
            }
        }
        return panes
    }

    /**
     * Which pane to cut next — roulette over `area^`[AreaBias], so a big pane is much likelier than a small one
     * without a small one being impossible.
     */
    private fun pick(panes: List<FloatArray>, random: Random): Int {
        var total = 0f
        val weights = FloatArray(panes.size) { i ->
            val w = Math.pow(area(panes[i]).toDouble(), AreaBias.toDouble()).toFloat()
            total += w
            w
        }
        var running = random.nextFloat() * total
        for (i in weights.indices) {
            running -= weights[i]
            if (running <= 0f) return i
        }
        return panes.lastIndex
    }

    /**
     * The two halves of convex [pane] cut by the line through (`px`, `py`) along (`dx`, `dy`), or the pane alone if
     * the line misses it.
     *
     * Every crossing point is computed once and appended to **both** halves, which is the whole reason the leading
     * never opens a gap: the two panes either side of a cut hold the *same* two vertices, not two roundings of them.
     */
    @Suppress("LongParameterList") // A line is four numbers; a struct for it would be read once and never again.
    internal fun split(pane: FloatArray, px: Float, py: Float, dx: Float, dy: Float): List<FloatArray> {
        val nx = -dy
        val ny = dx
        val count = pane.size / 2
        val near = ArrayList<Float>(pane.size + 4)
        val far = ArrayList<Float>(pane.size + 4)
        for (i in 0 until count) {
            val ax = pane[i * 2]
            val ay = pane[i * 2 + 1]
            val j = (i + 1) % count
            val bx = pane[j * 2]
            val by = pane[j * 2 + 1]
            val sa = (ax - px) * nx + (ay - py) * ny
            val sb = (bx - px) * nx + (by - py) * ny
            if (sa >= 0f) { near.add(ax); near.add(ay) } else { far.add(ax); far.add(ay) }
            if ((sa > 0f) != (sb > 0f)) {
                val t = sa / (sa - sb)
                val mx = ax + (bx - ax) * t
                val my = ay + (by - ay) * t
                near.add(mx); near.add(my)
                far.add(mx); far.add(my)
            }
        }
        val halves = listOf(near, far).filter { it.size >= MinVertices }.map { it.toFloatArray() }
        return halves.ifEmpty { listOf(pane) }
    }

    /** Twice the signed area of [pane], halved and made positive — the shoelace formula. */
    internal fun area(pane: FloatArray): Float {
        var sum = 0f
        val count = pane.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            sum += pane[i * 2] * pane[j * 2 + 1] - pane[j * 2] * pane[i * 2 + 1]
        }
        return abs(sum) / 2f
    }

    /** The direction of [pane]'s longest edge — the axis a strip-making cut runs along. */
    private fun longestEdge(pane: FloatArray): Pair<Float, Float> {
        var best = 1f to 0f
        var longest = -1f
        val count = pane.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            val dx = pane[j * 2] - pane[i * 2]
            val dy = pane[j * 2 + 1] - pane[i * 2 + 1]
            val length = dx * dx + dy * dy
            if (length > longest) {
                longest = length
                best = dx to dy
            }
        }
        return best
    }

    private fun centroidX(pane: FloatArray): Float {
        var sum = 0f
        for (i in pane.indices step 2) sum += pane[i]
        return sum / (pane.size / 2)
    }

    private fun centroidY(pane: FloatArray): Float {
        var sum = 0f
        for (i in 1 until pane.size step 2) sum += pane[i]
        return sum / (pane.size / 2)
    }

    /**
     * [pane]'s outline as a device-space [Path], each edge walked in [EdgeSteps] steps so the warp can bend it.
     *
     * The warp fades to nothing at the frame's border (`16uv(1-u)(1-v)`, which is `1` at the center and `0` on all
     * four edges), so the outermost panes keep the frame's own straight sides.
     */
    private fun outline(pane: FloatArray, warp: PerlinNoise2d, bow: Float, width: Int, height: Int): Path {
        val path = Path()
        val count = pane.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            for (step in 0 until EdgeSteps) {
                val t = step.toFloat() / EdgeSteps
                val u = pane[i * 2] + (pane[j * 2] - pane[i * 2]) * t
                val v = pane[i * 2 + 1] + (pane[j * 2 + 1] - pane[i * 2 + 1]) * t
                val fade = EdgeFade * u * (1f - u) * v * (1f - v)
                val x = (u + bow * fade * warp.at(u * BowFrequency, v * BowFrequency)) * width
                val y = (v + bow * fade * warp.at(v * BowFrequency + BowOffset, u * BowFrequency)) * height
                if (i == 0 && step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        return path
    }

    /**
     * The shader a pane is filled with: [base] lifted at one corner of the pane and dropped at the other, along a
     * fixed light direction, by [glass].
     *
     * The gradient spans the *pane's own* extent rather than the frame's, so a small pane gets the whole sweep too —
     * a frame-wide gradient would leave the small ones looking flat beside the large ones, which is the opposite of
     * what a window of hand-cut glass does.
     */
    private fun glassShader(pane: FloatArray, base: Int, glass: Float, width: Int, height: Int): Shader {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in pane.indices step 2) {
            minX = min(minX, pane[i]); maxX = maxOf(maxX, pane[i])
            minY = min(minY, pane[i + 1]); maxY = maxOf(maxY, pane[i + 1])
        }
        return LinearGradient(
            minX * width, maxY * height, maxX * width, minY * height,
            TriangularFacetsGenerator.shade(base, 1f + glass),
            TriangularFacetsGenerator.shade(base, 1f - glass),
            Shader.TileMode.CLAMP,
        )
    }

    /** How strongly the pane count is weighted by area when choosing what to cut next. */
    private const val AreaBias = 1.6f

    /** How often a cut runs parallel to the pane's longest edge rather than at a fresh angle. */
    private const val SliceBias = 0.4f

    /** How far the cut's point may drift from the pane's centroid toward one of its corners. */
    private const val CutDrift = 0.7f

    /** The smallest pane a cut may leave, as a fraction of the frame — below this it is all sliver. */
    private const val MinArea = 0.00035f

    /** A polygon needs three vertices, so six interleaved floats. */
    private const val MinVertices = 6

    /** Cut attempts allowed per pane before the subdivision gives up — a guard, not a budget. */
    private const val MaxTries = 6

    /** Points per pane edge — enough for a bowed edge to read as an arc rather than as a chain of chords. */
    private const val EdgeSteps = 8

    /** `16uv(1-u)(1-v)`'s coefficient: it makes the fade exactly `1` at the frame's center and `0` on its border. */
    private const val EdgeFade = 16f

    /**
     * How far the warp may push a point at full curves, as a fraction of the frame.
     *
     * It has to be this large because the *fade* spends most of it: `16uv(1-u)(1-v)` is `1` only at the frame's exact
     * center and is already down to `0.5` a fifth of the way in, so the push a typical point actually sees is a
     * fraction of this. The first attempt at a fifth of this number moved the glass by a pixel or two and the knob
     * read as dead.
     */
    private const val MaxBow = 0.16f

    /** Warp cells across the frame — a little over one, so a long cut bows once or twice rather than rippling. */
    private const val BowFrequency = 1.7f

    /** Reads the second warp axis from elsewhere in the same field, so the push is not along one diagonal. */
    private const val BowOffset = 31.7f

    /** How far a pane's own tone is lifted and dropped across it at full glass. */
    private const val MaxGlass = 0.35f

    /** The widest leading, as a fraction of the frame's short side. */
    private const val MaxLeading = 0.02f

    /** Keeps each seeded stream independent, so tuning one knob does not reshuffle what the others drew. */
    private const val BowSalt = 0x1D872B41L
    private const val ToneSalt = 0x6C078965L
}
