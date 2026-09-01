package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The frame subdivided into rounded tiles floating on a wide grout — *Modern Mosaic*.
 *
 * **It is a subdivision, not a packing.** The reference's own description reads as a packing of rounded rectangles,
 * and driving it says otherwise: at *Count* `1` the whole frame is one tile, and every boundary is a cut that runs the
 * full width of whatever it cuts. So the frame is halved, and halved again, until there are as many tiles as asked
 * for — which is [MondrianGenerator]'s construction, and the reason that design is the one this is measured against.
 * What separates them is everything after the cut: the tiles are pulled back from their own edges so the ground shows
 * between them as grout, their corners are rounded, and their shared corners are pushed off square.
 *
 * **[DesignParams.variant] is *Ratio*, and it is the least obvious knob and the most important one.** It is not the
 * split *position* but the **smallest share a cut may leave**: the cut falls anywhere in `ratio .. 1 - ratio`, so
 * *Even* (`0.5`) is a point and every tile is an exact half of its parent, while *Fifth* (`0.2`) admits a tile four
 * times its sibling. The reference's five options are `1/2 · Golden Ratio · 1/3 · 1/4 · 1/5` and it lists them in
 * exactly that order — a single axis from rigid to lopsided, which is what gives the reading away. Its default is the
 * **golden** minor, and that is most of why the tile sizes read as a harmonious set rather than as a random spread:
 * a fixed band of split fractions makes the sizes powers of two or three numbers, not a continuum. Measured:
 * three strips at `0.382 / 0.236 / 0.382` of the frame, which is one golden cut and then a golden cut of the larger
 * part, to the pixel.
 *
 * **[DesignParams.roundness] is what makes this design itself**, which is why it arrived with a field of its own — at
 * `0` this *is* a Mondrian in a light grout, and at `1` every narrow tile is a pill.
 *
 * **[DesignParams.irregularity] pushes the corners off square through one smooth displacement field, and here that is
 * the right answer where in [VitrallGenerator] it was the wrong one.** Mapping every corner through a shared field
 * means two tiles meeting at a corner move it together, so the grout stays the even band that is the whole character
 * of the design. It leaves a residue: a tile's corner that sits *mid-edge* on its neighbour (a T-junction, which any
 * binary subdivision makes) lands slightly off that neighbour's straightened edge, by however much the field curves
 * over the span. In the window that residue would be a hairline of ground between two panes and a bug; **here there is
 * already a wide band of ground between every pair of tiles, so anything smaller than the grout is invisible by
 * construction.** The deciding property is whether the design puts ground between its pieces, and it is worth stating
 * because the two designs otherwise look like they want the same solution.
 *
 * **The ground is stop 0, whatever stop 0 is** — [ConfettiGenerator]'s finding, and the reference does the same here.
 * The tiles take [RampTones] above it, picked evenly: measured over sixteen tiles the reference spends its tones at
 * about equal rates, so unlike Confetti there is no falloff to reproduce.
 *
 * [tiles] is pure and tested: the tiles must still partition the frame, and *that* is a claim a render cannot check
 * here — a gap or an overlap in the subdivision shows up as a grout line slightly the wrong width, which is precisely
 * the kind of wrong nobody can point at.
 */
object ModernMosaicGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — the tile count, and the slider's own range.
     *
     * **Theirs is `1..100` and this is `2..40`, which is a departure and the panel's fault rather than the design's.**
     * Their *Count* opens at **16**, and every knob in our panel opens at `0.5` — on their range that is 51 tiles, a
     * texture where the design's whole appeal is a composed handful. A range whose midpoint is near their default is
     * the only lever there is until a design can carry its own defaults, which is the fix W7 and W10 both deferred and
     * which this is now the third design to want. What it costs is the dense end: 40 tiles rather than 100.
     */
    private val Amount = AmountKnob.Count("Tiles", 2..40)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Grout",
        irregularity = "Skew",
        roundness = "Roundness",
        variant = VariantKnob("Ratio", Ratio.entries.map { it.label }),
    )

    /**
     * How lopsided a cut may be — the reference's *Ratio*.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property least the smallest share of a tile a cut may leave; the cut falls in `least .. 1 - least`, so
     *   [EVEN]'s `0.5` leaves no room at all and halves exactly.
     */
    internal enum class Ratio(val label: String, val least: Float) {
        /** Exact halves — the rigid end, a quilt of one tile size per depth. */
        EVEN("Even", least = 0.5f),

        /** The golden minor, `1 - 1/φ` — the reference's default, and the one that reads as composed. */
        GOLDEN("Golden", least = 0.381966f),

        /** A third at the least, so a tile may be twice its sibling. */
        THIRD("Third", least = 1f / 3f),

        /** A quarter at the least — three times its sibling. */
        QUARTER("Quarter", least = 0.25f),

        /** A fifth at the least, the lopsided end: long tiles beside squat ones. */
        FIFTH("Fifth", least = 0.2f),
    }

    /**
     * A finished mosaic.
     *
     * @property tiles each tile's four corners, interleaved `x, y`, in a frame [aspect] wide and one tall. Rectangles
     *   until the skew moves them, quadrilaterals after.
     * @property aspect how wide the cut frame was, in units of its own height.
     * @property cell the side of a notional square tile — `sqrt(area / count)`. The grout and the skew are both
     *   measured in these, so they mean the same thing at any count and any frame.
     */
    internal class Mosaic(val tiles: List<FloatArray>, val aspect: Float, val cell: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        // Subdivided in an aspect-true frame, not the unit square: a cut is taken across a tile's *longer* side, and
        // in the unit square "longer" is a lie on a phone — every tile would read as tall and every cut horizontal.
        val aspect = width.toFloat() / height
        val ratio = Ratio.entries[params.variant.coerceIn(0, Ratio.entries.lastIndex)]
        val mosaic = tiles(Amount.at(params.density), ratio, params.irregularity, seed, aspect)

        val scale = height.toFloat()
        val grout = params.scale.coerceIn(0f, 1f) * MaxGrout * mosaic.cell * scale
        val soften = params.roundness.coerceIn(0f, 1f)
        val tones = RampTones.aboveGround(palette)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0))
        if (tones.isEmpty()) return bitmap // an all-ground palette has nothing to lay on it

        val random = Random(seed xor ToneSalt)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (tile in mosaic.tiles) {
            // Half the grout per side, so the band *between* two tiles is one grout wide.
            val inset = GlassCut.inset(scaled(tile, scale), grout / 2f) ?: continue
            fill.color = tones[random.nextInt(tones.size)]
            canvas.drawPath(rounded(inset, soften), fill)
        }
        return bitmap
    }

    /**
     * A mosaic of exactly [count] tiles cut at [ratio], skewed by [skew], from [seed], in a frame [aspect] wide and
     * one tall.
     *
     * **The largest tile is the one cut**, which is what keeps the sizes in a band — the reference's are, measurably,
     * and it is the opposite of [VitrallGenerator]'s choice for the opposite reason: a window wants a few sweeping
     * shards and this wants a tiling that reads as considered. Variety comes from the cut's direction and from which
     * side of it takes the larger share, both of them the seed's.
     */
    internal fun tiles(count: Int, ratio: Ratio, skew: Float, seed: Long, aspect: Float = 1f): Mosaic {
        val random = Random(seed)
        val frame = aspect.coerceAtLeast(Tiny)
        val wanted = count.coerceAtLeast(1)
        // Rectangles as left, top, right, bottom while they are being cut — corners come later, once nothing else
        // will move them.
        val rects = ArrayList<FloatArray>(wanted)
        rects.add(floatArrayOf(0f, 0f, frame, 1f))
        while (rects.size < wanted) {
            val at = widest(rects)
            val cut = split(rects[at], ratio, random) ?: break
            rects[at] = cut.first
            rects.add(cut.second)
        }

        val cell = sqrt(frame / wanted)
        val field = PerlinNoise2d(seed xor SkewSalt)
        val reach = skew.coerceIn(0f, 1f) * MaxSkew * cell
        return Mosaic(rects.map { corners(it, field, reach, frame) }, frame, cell)
    }

    /** Which of [rects] to cut next — the one with the most area, so the tiles stay in a band of sizes. */
    private fun widest(rects: List<FloatArray>): Int {
        var best = 0
        var most = -1f
        rects.forEachIndexed { i, r ->
            val area = (r[2] - r[0]) * (r[3] - r[1])
            if (area > most) {
                most = area
                best = i
            }
        }
        return best
    }

    /**
     * [rect] cut in two across its longer side, at a fraction in `ratio.least .. 1 - ratio.least`, or null once it is
     * too small to cut usefully.
     *
     * Cutting the **longer** side is what keeps tiles from drifting into slivers as the count climbs: a tile that is
     * twice as wide as it is tall gets cut vertically, which brings it back toward square. Where the sides are within
     * [SquareBand] of each other there is no longer side worth the name, and the seed picks — which is where the
     * variety in the layout comes from at low counts, since the cut fractions themselves are nearly fixed.
     */
    private fun split(rect: FloatArray, ratio: Ratio, random: Random): Pair<FloatArray, FloatArray>? {
        val left = rect[0]
        val top = rect[1]
        val right = rect[2]
        val bottom = rect[3]
        val width = right - left
        val height = bottom - top
        if (min(width, height) < MinSide) return null
        val vertical = when {
            width > height * SquareBand -> true
            height > width * SquareBand -> false
            else -> random.nextBoolean()
        }
        val least = ratio.least
        val share = least + random.nextFloat() * (1f - least - least)
        // Which side of the cut takes the larger share is the seed's — otherwise every mosaic leans the same way.
        val at = if (random.nextBoolean()) share else 1f - share
        return if (vertical) {
            val x = left + width * at
            floatArrayOf(left, top, x, bottom) to floatArrayOf(x, top, right, bottom)
        } else {
            val y = top + height * at
            floatArrayOf(left, top, right, y) to floatArrayOf(left, y, right, bottom)
        }
    }

    /**
     * [rect]'s four corners, each pushed through the same displacement [field] by up to [reach].
     *
     * The field is read in frame-relative coordinates so its swells are the same size on the picture however fine the
     * mosaic is, and it is **pinned at the frame's border** — a corner on an outer edge keeps the component that would
     * carry it out of the frame, so the mosaic still fills its frame instead of pulling away from it in a ragged line.
     */
    private fun corners(rect: FloatArray, field: PerlinNoise2d, reach: Float, frame: Float): FloatArray {
        val left = rect[0]
        val top = rect[1]
        val right = rect[2]
        val bottom = rect[3]
        val out = FloatArray(Corners * 2)
        val xs = floatArrayOf(left, right, right, left)
        val ys = floatArrayOf(top, top, bottom, bottom)
        for (i in 0 until Corners) {
            val x = xs[i]
            val y = ys[i]
            val dx = field.at(x * SkewFrequency, y * SkewFrequency) * reach
            val dy = field.at(x * SkewFrequency + SkewPhase, y * SkewFrequency - SkewPhase) * reach
            val edgeX = x <= Tiny || x >= frame - Tiny
            val edgeY = y <= Tiny || y >= 1f - Tiny
            out[i * 2] = x + if (edgeX) 0f else dx
            out[i * 2 + 1] = y + if (edgeY) 0f else dy
        }
        return out
    }

    /** [tile] in device pixels — one uniform [scale], since the cut frame already carries the aspect. */
    private fun scaled(tile: FloatArray, scale: Float): FloatArray = FloatArray(tile.size) { tile[it] * scale }

    /**
     * [tile] as a path with its corners rounded by [soften], `0` sharp and `1` as round as the shape allows.
     *
     * **A quadratic through the corner, not a circular arc.** The corner is trimmed back along both of its edges and
     * the gap bridged with one Bézier whose control point is the corner itself, which at these radii is
     * indistinguishable from an arc and needs no angle bisector or tangent length — an arc's `r / tan(θ/2)` blows up
     * on the near-straight corners that a skewed tile produces. The trim is capped at half of the shorter edge, so a
     * long thin tile rounds to a pill rather than crossing over itself.
     */
    private fun rounded(tile: FloatArray, soften: Float): Path {
        val path = Path()
        val count = tile.size / 2
        if (soften <= 0f) {
            path.moveTo(tile[0], tile[1])
            for (i in 1 until count) path.lineTo(tile[i * 2], tile[i * 2 + 1])
            path.close()
            return path
        }
        var shortest = Float.MAX_VALUE
        for (i in 0 until count) {
            val j = (i + 1) % count
            shortest = min(shortest, hypot(tile[j * 2] - tile[i * 2], tile[j * 2 + 1] - tile[i * 2 + 1]))
        }
        val trim = soften * shortest / 2f
        for (i in 0 until count) {
            val h = (i + count - 1) % count
            val j = (i + 1) % count
            val from = along(tile, i, h, trim)
            val to = along(tile, i, j, trim)
            if (i == 0) path.moveTo(from[0], from[1]) else path.lineTo(from[0], from[1])
            path.quadTo(tile[i * 2], tile[i * 2 + 1], to[0], to[1])
        }
        path.close()
        return path
    }

    /** The point [by] along the edge from corner [i] of [tile] toward corner [j], never past its midpoint. */
    private fun along(tile: FloatArray, i: Int, j: Int, by: Float): FloatArray {
        val x = tile[i * 2]
        val y = tile[i * 2 + 1]
        val ex = tile[j * 2] - x
        val ey = tile[j * 2 + 1] - y
        val len = hypot(ex, ey)
        if (len < Tiny) return floatArrayOf(x, y)
        val t = min(by, len / 2f) / len
        return floatArrayOf(x + ex * t, y + ey * t)
    }

    /** The widest grout, as a multiple of the notional square tile's side. */
    private const val MaxGrout = 0.34f

    /**
     * How far a corner may be pushed at full skew, as a multiple of the notional square tile's side.
     *
     * Measured against theirs rather than guessed: at `0.3` ours at maximum read like theirs at about 40, and this is
     * where the two match. Note it is comfortably **larger than the grout** and still leaves no artifact at a
     * T-junction, which is the class note's claim made good — what shows there is not the amplitude but how much the
     * field *curves* across one tile's edge, and a field this smooth barely does.
     */
    private const val MaxSkew = 0.45f

    /** The skew field's feature size, and the phase that reads its second channel out of the same field. */
    private const val SkewFrequency = 2.4f
    private const val SkewPhase = 31.7f

    /**
     * How much longer one side must be than the other before it decides the cut's direction on its own.
     *
     * Tight, because the alternative shows: at `1.25` a tile could keep being cut the same way and the mosaic filled
     * with 1:3 uprights where theirs are nearly square. Variety does not need this to be loose — the cut fraction and
     * which side takes the larger share are both the seed's.
     */
    private const val SquareBand = 1.12f

    /** The shortest side a tile may still be cut across, as a fraction of the frame's height. */
    private const val MinSide = 0.02f

    /** A rectangle's corners, which is also how many a skewed tile keeps. */
    private const val Corners = 4

    /** Below this a length is nothing at all, and a coordinate is on the frame's border. */
    private const val Tiny = 1e-6f

    /** Keeps the two seeded streams independent, so changing the skew does not reshuffle the colors. */
    private const val SkewSalt = 0x4B1D93F7L
    private const val ToneSalt = 0x2E9C6A15L
}
