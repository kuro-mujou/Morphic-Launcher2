package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A noise terrain read as a map — inked contour lines on bare ground by default, or an embossed relief of filled
 * bands — the *Topography* look.
 *
 * **The contours are traced and stroked, not detected pixel by pixel.** An earlier version banded the field and inked
 * every pixel whose band differed from a neighbour's, arguing that a wallpaper needs only the *look* of a survey map
 * and not the marching squares under a real one. Driving the reference is what refuted it: a contour there has a
 * **width** (its *Thickness* runs 2..100), one chosen level is drawn **thicker than the rest**, and each line is
 * anti-aliased. A one-pixel difference mask can do none of those — it is one pixel, one weight, one ink, forever — so
 * the field is sampled onto a lattice, each level is walked out of it with marching squares, and the resulting
 * polylines are stroked as real [android.graphics.Path]s. The *Embossed* look keeps the per-pixel path, because a
 * filled band is a region rather than a line.
 *
 * **Both looks read one terrain, so they cannot disagree about the picture.** [Terrain] is built once — the same
 * lattice, the same normalization, the same [levelHeights] — and the lines are traced through it while the relief
 * samples it. Two fields built from the same parameters by two code paths is the divergence that would be invisible:
 * flipping the *Style* knob would move the hills, and nothing would say why.
 *
 * **The lattice is square in *frame* terms, which is the bug the port inherited.** Both axes are scaled by the
 * frame's **width**, so one noise cycle is the same number of pixels across as it is down; dividing `y` by the height
 * instead — what the pixel version did — stretches every hill by the frame's aspect, which on a 1080×2400 phone is
 * more than double. The reference's hills are round.
 *
 * **The knobs, and the three of the reference's eleven that are not here.** *Levels* is [DesignParams.density] (its
 * `1..10`, exactly); *Coverage* is [DesignParams.scale] — the **width of the elevation window the levels are spread
 * across**, which is where the reference's negative space comes from and the knob whose absence made every earlier
 * render ink the whole frame; *Detail* is [DesignParams.irregularity] (the fBm octaves above the first); *Zoom* is
 * [DesignParams.roundness] (the feature size); *Highlight* is [DesignParams.depth] and *Thickness*
 * [DesignParams.depthScale]. Dropped: their *Variation* is the field's relief **amplitude**, which is the same axis as
 * *Coverage* read from the other end — a wider window over a flatter field is the same set of crossings — so it folds
 * in rather than taking a field; their *Smoothing* rounds a traced polyline's corners, and a lattice this fine traces
 * curves that are already smooth (driven end to end on the reference it moves lines by a pixel or two); their
 * *Thickness boost* is fixed here at the reference's own default, [HighlightBoost], because a boost with no
 * highlighted level does nothing and the pair is one idea.
 *
 * **The relief is lit and the map is inked, so the same ramp runs opposite ways in the two looks.** A survey map's
 * ground is the palette's first stop and its lines climb away from it, so a high contour is the strongest ink; a
 * relief has no exposed ground at all and its peaks are the sheets *catching the light*, so high is the palest. One
 * resolver ([Ink]) either way — the caller says where on the ramp it wants to be.
 *
 * **[DesignParams.colorLayout] is where the stops go, and it is why that field exists** — see [DesignParams]. Three
 * layouts, against the reference's five: *Islands* is not here because its render is not distinguishable from *Hills*
 * without knowing which loop belongs to which peak, and *First color* is not, because a single stop for every band
 * collapses the *Embossed* look to a solid frame — the restrained one-color map it offers is what
 * [inkspire.morphic.core.model.wallpaper.WallpaperColorMode.MONOCHROMATIC] is for.
 *
 * [levelHeights], [bandAt], [highlightedLevel], [strokeWidthPx] and [frequencyFor] are pure and tested: which
 * elevations carry a line, which band a height falls in, and how wide the ink is are all arithmetic that is silently
 * wrong — a map with its contours slightly beside where they belong is still a plausible map.
 */
object ContourGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Levels* slider's own range. */
    private val Amount = AmountKnob.Count("Levels", 1..10)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Coverage",
        irregularity = "Detail",
        roundness = "Zoom",
        depth = "Highlight",
        depthScale = "Thickness",
        variant = VariantKnob("Style", listOf("Lines", "Embossed")),
        colorLayout = VariantKnob("Colors", listOf("Hills", "Altitude", "Random")),
    )

    /**
     * The relief has no strokes, so it has no line weight and nothing to pick out with one — [DesignParams.depth]
     * becomes its paper-cut *Shadow* instead, which is the same family reading the same field, and
     * [DesignParams.depthScale] goes away entirely rather than sitting there moving nothing.
     */
    override fun styleFor(params: DesignParams): DesignStyle =
        if (params.variant == VariantEmbossed) style.copy(depth = "Shadow", depthScale = null) else style

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val ground = palette.colorAt(0)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ground)

        val levels = Amount.at(params.density)
        val heights = levelHeights(levels, params.scale)
        // The relief quantizes the whole field into one flat sheet per band, so it needs a rung for each of them or
        // neighbouring sheets share a color and merge; the map only draws `levels` lines over bare ground and is
        // happier landing on the palette's own stops. See RampTones.countFor.
        val rungs = if (params.variant == VariantEmbossed) {
            RampTones.countFor(palette.size, levels + 1)
        } else {
            RampTones.countFor(palette.size)
        }
        val tones = RampTones.aboveGround(palette, rungs)
        // A single-stop palette is all ground and has nothing to draw the map in — the bare ground is the honest
        // picture, and every path below would divide by a tone count of zero.
        if (tones.isEmpty()) return bitmap

        val terrain = terrainOf(width, height, params, seed)
        val ink = Ink(params, tones, terrain, seed, levels)

        if (params.variant == VariantEmbossed) {
            emboss(bitmap, width, height, terrain, heights, ink, params.depth)
        } else {
            strokeContours(canvas, terrain, heights, ink, params, min(width, height).toFloat())
        }
        return bitmap
    }

    // ---- the terrain -------------------------------------------------------------------------------------------

    /**
     * The height field, sampled onto a lattice and normalized to `0..1` over what it actually reached in this frame.
     *
     * **Normalizing over the observed range is what makes *Coverage* mean anything.** A level at height `0.8` has to
     * be a real fraction of *this* picture's relief; against the noise's nominal range it would land wherever the
     * seed happened to put the peaks, and the same Coverage would draw a full map on one seed and an empty one on the
     * next.
     */
    private fun terrainOf(width: Int, height: Int, params: DesignParams, seed: Long): Terrain {
        val noise = PerlinNoise2d(seed)
        val cell = max(1f, min(width, height) / LatticeAcross)
        val cols = max(1, ceil(width / cell).toInt())
        val rows = max(1, ceil(height / cell).toInt())
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        val frequency = frequencyFor(params.roundness)
        val detail = params.irregularity.coerceIn(0f, 1f)

        val z = FloatArray((cols + 1) * (rows + 1))
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        for (r in 0..rows) {
            for (c in 0..cols) {
                // Both axes over the frame's *width*, so a cycle is as long across as it is down and the hills stay
                // round on a tall frame. See the class note.
                val value = fbm(noise, c * cellW / width * frequency, r * cellH / width * frequency, detail)
                z[r * (cols + 1) + c] = value
                lowest = min(lowest, value)
                highest = max(highest, value)
            }
        }
        val span = highest - lowest
        if (span > 0f) {
            for (i in z.indices) z[i] = (z[i] - lowest) / span
        }
        return Terrain(cols, rows, cellW, cellH, z)
    }

    /**
     * Fractal noise — one broad octave plus three halving ones weighted by [detail], so `0` is a single smooth swell
     * and `1` a convoluted survey. Not normalized: [terrainOf] rescales whatever range this reaches.
     */
    private fun fbm(noise: PerlinNoise2d, x: Float, y: Float, detail: Float): Float {
        var value = noise.at(x, y)
        var amplitude = detail * DetailWeight
        var frequency = OctaveStep
        repeat(DetailOctaves) {
            value += amplitude * noise.at(x * frequency, y * frequency)
            amplitude *= DetailWeightFalloff
            frequency *= OctaveStep
        }
        return value
    }

    /** The lattice a [terrainOf] built, and the two ways the two looks read it. */
    private class Terrain(
        val cols: Int,
        val rows: Int,
        val cellW: Float,
        val cellH: Float,
        val z: FloatArray,
    ) {
        fun corner(c: Int, r: Int): Float = z[r * (cols + 1) + c]

        /** The height at a pixel, bilinear between the four lattice corners around it. */
        fun at(px: Float, py: Float): Float {
            val fx = (px / cellW).coerceIn(0f, cols.toFloat())
            val fy = (py / cellH).coerceIn(0f, rows.toFloat())
            val c = min(fx.toInt(), cols - 1)
            val r = min(fy.toInt(), rows - 1)
            val tx = fx - c
            val ty = fy - r
            val top = corner(c, r) + (corner(c + 1, r) - corner(c, r)) * tx
            val bottom = corner(c, r + 1) + (corner(c + 1, r + 1) - corner(c, r + 1)) * tx
            return top + (bottom - top) * ty
        }
    }

    // ---- the knobs, as arithmetic -----------------------------------------------------------------------------

    /**
     * The elevations that carry a contour, ascending — [levels] of them, spread evenly inside a window of the height
     * range whose width is set by [coverage].
     *
     * **The window is centered and only its width moves**, which is what gives the knob a rigid end at each side: at
     * `0` the levels crowd into [MinWindow] of the relief, so only the ground that happens to cross that slice is
     * drawn and the rest of the frame is bare; at `1` they span the whole range and the map covers everything. The
     * `+ 1` in the divisor keeps every level strictly inside the window rather than one landing on each edge, where
     * the outermost would trace the frame's own extremes and read as a rim.
     */
    internal fun levelHeights(levels: Int, coverage: Float): FloatArray {
        val window = MinWindow + (1f - MinWindow) * coverage.coerceIn(0f, 1f).pow(CoverageEase)
        val low = 0.5f - window * 0.5f
        return FloatArray(levels) { low + window * (it + 1f) / (levels + 1f) }
    }

    /** Which band of the relief a height falls in — `0` below every level, [FloatArray.size] above them all. */
    internal fun bandAt(height: Float, heights: FloatArray): Int {
        var band = 0
        while (band < heights.size && height >= heights[band]) band++
        return band
    }

    /**
     * Which level is drawn at [HighlightBoost] the others' weight, or `-1` for none — the reference's *Highlighted
     * level*, whose own range is `None` plus one entry per level and so moves with the *Levels* knob.
     *
     * `0` answering "none" is [DesignParams.depth]'s standing rule (`0` is flat) landing exactly on the reference's
     * own first option.
     */
    internal fun highlightedLevel(depth: Float, levels: Int): Int =
        (depth.coerceIn(0f, 1f) * levels).roundToInt() - 1

    /**
     * How wide a contour is drawn, in pixels on a frame whose short side is [shortSide].
     *
     * **Measured off the reference rather than chosen**: its *Thickness* `2` draws about `1.5px` on a 1080-wide
     * frame, `12` about `3px` and `100` about `17px` — a straight line in its own units, which is what
     * [ThicknessBase] and [ThicknessPerUnit] are. The **cubed** response is the departure, and it is the panel's
     * fault rather than this design's: every knob of every design still opens at `0.5`, and half of a `2..100` range
     * is a fat ribbon where the reference opens at `12`. Cubed, `0.5` lands on `14` — near enough their default that
     * the design opens looking like itself.
     */
    internal fun strokeWidthPx(thickness: Float, shortSide: Float): Float {
        val units = MinThickness + (MaxThickness - MinThickness) * thickness.coerceIn(0f, 1f).pow(ThicknessEase)
        return shortSide / ReferenceShortSide * (ThicknessBase + ThicknessPerUnit * units)
    }

    /**
     * How many noise cycles span the frame's width at [zoom] — the reference's *Zoom*, running the other way, since
     * zooming **in** is fewer and larger hills.
     *
     * Eased so that the panel's universal `0.5` lands near the reference's own default of `77`, for [strokeWidthPx]'s
     * reason.
     */
    internal fun frequencyFor(zoom: Float): Float =
        MaxCycles - (MaxCycles - MinCycles) * zoom.coerceIn(0f, 1f).pow(ZoomEase)

    // ---- the lines --------------------------------------------------------------------------------------------

    /** Walks every level out of the terrain and strokes it, the highlighted one last so it sits over its neighbours. */
    private fun strokeContours(
        canvas: Canvas,
        terrain: Terrain,
        heights: FloatArray,
        ink: Ink,
        params: DesignParams,
        shortSide: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val base = strokeWidthPx(params.depthScale, shortSide)
        val highlighted = highlightedLevel(params.depth, heights.size)
        var ordinal = 0
        for (level in heights.indices) {
            paint.strokeWidth = if (level == highlighted) base * HighlightBoost else base
            val altitude = (level + 0.5f) / heights.size
            for (line in trace(terrain, heights[level])) {
                paint.color = ink.toneAt(line[0], line[1], altitude, ordinal++)
                canvas.drawPath(Streamlines.pathOfPixels(line), paint)
            }
        }
    }

    /**
     * The polylines of one iso-level — marching squares over the lattice, the crossings interpolated along each cell
     * edge so the line lands where the height actually reaches [level] rather than on the lattice.
     */
    private fun trace(terrain: Terrain, level: Float): List<FloatArray> {
        val segments = FloatList()
        for (r in 0 until terrain.rows) {
            for (c in 0 until terrain.cols) {
                cellSegments(terrain, c, r, level, segments)
            }
        }
        return chain(segments)
    }

    /**
     * The 0, 1 or 2 segments crossing one lattice cell, appended to [into] as `ax, ay, bx, by`.
     *
     * The two **saddle** cases — opposite corners above the level and the other two below — are resolved by the
     * cell's own average, which is the standard disambiguation and the only one that makes neighbouring cells agree:
     * decided per cell by anything else, two cells sharing an edge can join their lines the two different ways and
     * leave a hairline gap between them.
     */
    // MagicNumber: every number here *is* the marching-squares corner bitmask — a name per case would be sixteen
    // names for the numbers 0..15, and the bit weights are what make the mask readable at all.
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "MagicNumber")
    private fun cellSegments(terrain: Terrain, c: Int, r: Int, level: Float, into: FloatList) {
        val topLeft = terrain.corner(c, r)
        val topRight = terrain.corner(c + 1, r)
        val bottomRight = terrain.corner(c + 1, r + 1)
        val bottomLeft = terrain.corner(c, r + 1)
        var code = 0
        if (topLeft >= level) code = code or 1
        if (topRight >= level) code = code or 2
        if (bottomRight >= level) code = code or 4
        if (bottomLeft >= level) code = code or 8
        if (code == 0 || code == 15) return

        val x0 = c * terrain.cellW
        val y0 = r * terrain.cellH
        val topX = x0 + terrain.cellW * crossing(topLeft, topRight, level)
        val bottomX = x0 + terrain.cellW * crossing(bottomLeft, bottomRight, level)
        val leftY = y0 + terrain.cellH * crossing(topLeft, bottomLeft, level)
        val rightY = y0 + terrain.cellH * crossing(topRight, bottomRight, level)
        val x1 = x0 + terrain.cellW
        val y1 = y0 + terrain.cellH

        // The saddles pick between the same two pairings; the average says which corners the field joins.
        val saddleJoinsTopLeft = (topLeft + topRight + bottomRight + bottomLeft) * 0.25f >= level
        when (code) {
            1, 14 -> into.segment(x0, leftY, topX, y0)
            2, 13 -> into.segment(topX, y0, x1, rightY)
            3, 12 -> into.segment(x0, leftY, x1, rightY)
            4, 11 -> into.segment(x1, rightY, bottomX, y1)
            6, 9 -> into.segment(topX, y0, bottomX, y1)
            7, 8 -> into.segment(x0, leftY, bottomX, y1)
            5 -> if (saddleJoinsTopLeft) {
                into.segment(x0, leftY, topX, y0)
                into.segment(x1, rightY, bottomX, y1)
            } else {
                into.segment(topX, y0, x1, rightY)
                into.segment(x0, leftY, bottomX, y1)
            }

            else -> if (saddleJoinsTopLeft) {
                into.segment(topX, y0, x1, rightY)
                into.segment(x0, leftY, bottomX, y1)
            } else {
                into.segment(x0, leftY, topX, y0)
                into.segment(x1, rightY, bottomX, y1)
            }
        }
    }

    /** Where between two corner heights [level] falls, `0..1` — the midpoint where the two are equal. */
    private fun crossing(from: Float, to: Float, level: Float): Float =
        if (from == to) FlatCrossing else ((level - from) / (to - from)).coerceIn(0f, 1f)

    /**
     * The loose segments joined end to end into polylines.
     *
     * **Chained rather than stroked one at a time**, because a segment is a fragment of a cell: stroked separately
     * they meet at rounded caps that bulge, a dashed look at any real width, and there would be no *line* to give a
     * color to — which is what a layout that colors by hill needs. Endpoints are matched on a quantized key, since
     * two cells compute the same shared crossing from the same two corners and must land on the same key.
     */
    private fun chain(segments: FloatList): List<FloatArray> {
        val count = segments.segments
        val ends = HashMap<Long, MutableList<Int>>(count * FloatList.ENDS_PER_SEGMENT)
        for (i in 0 until count) {
            ends.getOrPut(segments.startKey(i)) { ArrayList(FloatList.ENDS_PER_SEGMENT) }.add(i)
            ends.getOrPut(segments.endKey(i)) { ArrayList(FloatList.ENDS_PER_SEGMENT) }.add(i)
        }
        val used = BooleanArray(count)
        val lines = ArrayList<FloatArray>()
        for (start in 0 until count) {
            if (used[start]) continue
            used[start] = true
            val forward = FloatList()
            forward.point(segments.startX(start), segments.startY(start))
            forward.point(segments.endX(start), segments.endY(start))
            extend(segments, ends, used, forward)
            val backward = FloatList()
            backward.point(segments.startX(start), segments.startY(start))
            extend(segments, ends, used, backward)
            lines += backward.reversedInto(forward)
        }
        return lines
    }

    /** Walks unused segments off the last point of [line], appending each one's far end, until none is left. */
    private fun extend(
        segments: FloatList,
        ends: HashMap<Long, MutableList<Int>>,
        used: BooleanArray,
        line: FloatList,
    ) {
        while (true) {
            val tip = line.lastKey()
            val next = ends[tip]?.firstOrNull { !used[it] } ?: return
            used[next] = true
            if (segments.startKey(next) == tip) {
                line.point(segments.endX(next), segments.endY(next))
            } else {
                line.point(segments.startX(next), segments.startY(next))
            }
        }
    }

    // ---- the relief -------------------------------------------------------------------------------------------

    /**
     * The *Embossed* look: every pixel filled by the band it falls in, over a drop shadow cast by the sheets above it.
     *
     * **The shadow is a real drop shadow — offset and blurred — not a darkening that rides each band's own edge.**
     * That is the correction: an earlier version darkened a pixel by how near it sat to the level above, which puts a
     * rim on the *inside* of every ring and gradients each band across its whole width, reading as an inner shadow on
     * every shape rather than as one sheet lying over another. Measured off the reference instead: the shadow is the
     * silhouette of everything **higher** than this pixel, shifted by [ShadowThrow] of the frame's short side **up and
     * to the right**, softened over [ShadowBlur], multiplying to `0.80` where it is solid. So it falls *outside* the
     * shape that casts it, onto the larger sheet around it, and each band stays flat everywhere else.
     *
     * **It is directional, unlike [MetaballsGenerator]'s.** That one was measured on a different design of theirs and
     * hugs a blob's edge on every side; this one plainly has a light, from the lower left. Two measurements of two
     * designs — deliberately not one shared derivation.
     *
     * **The bands are resolved into an array first**, because the shadow asks about a *different* pixel's band
     * thirteen times over: recomputing the field there would be fourteen bilinear samples a pixel instead of one.
     */
    private fun emboss(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        terrain: Terrain,
        heights: FloatArray,
        ink: Ink,
        depth: Float,
    ) {
        val bands = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bands[y * width + x] = bandAt(terrain.at(x + PixelCenter, y + PixelCenter), heights)
            }
        }

        val shadow = depth.coerceIn(0f, 1f) * MaxShadow
        val shortSide = min(width, height).toFloat()
        val throwPx = shortSide * ShadowThrow
        val blurPx = shortSide * ShadowBlur
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val band = bands[i]
                // Inverted against the lines look on purpose: a relief is lit, so its peaks are the *lightest* sheet
                // and the hollows the darkest, where a survey map's highest contour is its strongest ink.
                val altitude = 1f - (band + PixelCenter) / (heights.size + 1f)
                val color = ink.toneAt(x + PixelCenter, y + PixelCenter, altitude, band)
                pixels[i] = if (shadow > 0f) {
                    Shades.scale(color, 1f - shadow * cast(bands, width, height, x, y, throwPx, blurPx))
                } else {
                    color
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * How much of the blurred, shifted silhouette of the sheets above ([x], [y]) covers it — `0` in full light, `1`
     * in full shadow.
     *
     * The blur is [ShadowTaps] read around the shifted point rather than a separable pass over the frame, because the
     * thing being blurred is *per pixel*: what counts as "above" depends on which band the pixel receiving the shadow
     * is in, so there is no one mask to blur.
     */
    @Suppress("LongParameterList")
    private fun cast(
        bands: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        throwPx: Float,
        blurPx: Float,
    ): Float {
        val band = bands[y * width + x]
        // The caster sits down and to the left of its shadow, so that is where the silhouette is read from.
        val fromX = x - throwPx
        val fromY = y + throwPx
        var covered = 0
        var tap = 0
        while (tap < ShadowTaps.size) {
            val sx = (fromX + ShadowTaps[tap] * blurPx).toInt().coerceIn(0, width - 1)
            val sy = (fromY + ShadowTaps[tap + 1] * blurPx).toInt().coerceIn(0, height - 1)
            if (bands[sy * width + sx] > band) covered++
            tap += 2
        }
        return covered.toFloat() / (ShadowTaps.size / 2)
    }

    // ---- the color --------------------------------------------------------------------------------------------

    /**
     * Where a contour or a band takes its color from — [DesignParams.colorLayout], resolved against the palette's
     * tones above the ground.
     *
     * **One resolver for both looks, mixing a *place* with an *altitude*.** *Hills* leans on the place, so every
     * contour around one peak lands in the same part of the ramp and its rings climb it — which is what makes the
     * reference read as regions rather than as a heap of unrelated lines — and *Altitude* ignores the place entirely,
     * so a level is the same color wherever it appears. Keeping them one function is what stops the relief and the
     * lines drifting into two different pictures of the same knob.
     */
    private class Ink(
        params: DesignParams,
        private val tones: IntArray,
        private val terrain: Terrain,
        private val seed: Long,
        levels: Int,
    ) {
        private val layout = params.colorLayout.coerceIn(0, LayoutRandom)
        private val regions = PerlinNoise2d(seed + RegionSeedOffset)
        private val regionFrequency = frequencyFor(params.roundness) * RegionScale
        private val across = max(1f, terrain.cols * terrain.cellW)
        private val steps = max(1, levels)

        /**
         * The *Random* layout's draws, taken once rather than per call — the relief asks this per **pixel**, and a
         * fresh [Random] there would be a few million allocations for a handful of distinct answers.
         */
        private val scatter = FloatArray(ScatterSize) { Random(seed + it).nextFloat() }

        /**
         * The tone for something drawn at ([px], [py]) at [altitude] (`0..1` up the levels), the [ordinal]th thing
         * drawn.
         */
        fun toneAt(px: Float, py: Float, altitude: Float, ordinal: Int): Int {
            val position = when (layout) {
                LayoutAltitude -> altitude
                LayoutRandom -> scatter[ordinal.mod(ScatterSize)]
                else -> region(px, py) * RegionShare + altitude * (1f - RegionShare)
            }
            return tones[(position.coerceIn(0f, 1f) * tones.size).toInt().coerceAtMost(tones.size - 1)]
        }

        /**
         * Which part of the ramp this place belongs to — a second noise field read at a fraction of the terrain's own
         * frequency, so a region is larger than the hill inside it and every contour of that hill samples it the same.
         */
        private fun region(px: Float, py: Float): Float {
            val value = regions.at(px / across * regionFrequency, py / across * regionFrequency)
            // Quantized to the levels the design already has, so a region is a flat block of the ramp rather than a
            // gradient that would put every contour of a hill on a slightly different tone.
            val stepped = ((value * 0.5f + 0.5f) * steps).toInt().coerceIn(0, steps - 1)
            return stepped.toFloat() / steps
        }
    }

    /** [DesignParams.variant] selecting the filled relief over the default lines on bare ground. */
    private const val VariantEmbossed = 1

    /** [DesignParams.colorLayout] selecting one tone per elevation, wherever that elevation appears. */
    private const val LayoutAltitude = 1

    /** [DesignParams.colorLayout] selecting a tone per contour, unrelated to its neighbours'. */
    private const val LayoutRandom = 2

    /** How much of a *Hills* tone comes from the place rather than the altitude — the rest is the climb up a hill. */
    private const val RegionShare = 0.55f

    /** The region field's frequency as a fraction of the terrain's, so a region holds a whole hill. */
    private const val RegionScale = 0.45f

    /** Keeps the region field from being the terrain itself, which would put every region boundary on a contour. */
    private const val RegionSeedOffset = 7919L

    /** How many draws the *Random* layout cycles through — enough that a frame's contours read as unrelated. */
    private const val ScatterSize = 64

    /** Lattice samples across the frame's short side — fine enough that a traced contour needs no smoothing. */
    private const val LatticeAcross = 360f

    /** How many halving octaves ride above the broad one at full *Detail*. */
    private const val DetailOctaves = 3

    /** Each detail octave's frequency against the one below it, and its share of that one's amplitude. */
    private const val OctaveStep = 2f
    private const val DetailWeightFalloff = 0.5f

    /** Where a contour crosses a cell edge whose two corners are the same height — nowhere in particular. */
    private const val FlatCrossing = 0.5f

    /** The middle of a pixel, which is where the relief samples the terrain rather than at its corner. */
    private const val PixelCenter = 0.5f

    /**
     * The first detail octave's share of the broad one at full *Detail*; each further one halves again.
     *
     * **Low, because these octaves ride a field that is then normalized.** A heavy detail octave does not merely
     * roughen the coastline — it takes over the extremes, so the broad swells the design is actually made of get
     * squeezed into the middle of the range and the levels all fall on the crinkle. Rendered at `0.5` it drew the
     * reference's *Detail* `100`.
     */
    private const val DetailWeight = 0.3f

    /** The narrowest slice of the relief the levels are packed into, at *Coverage* `0`. */
    private const val MinWindow = 0.12f

    /** Shapes *Coverage* so the panel's universal `0.5` lands near the reference's own default of `79`. */
    private const val CoverageEase = 0.6f

    /** Noise cycles across the frame's width at *Zoom* `0` — many small hills. */
    private const val MaxCycles = 4f

    /** ... and at *Zoom* `1`, where barely one hill spans the frame. */
    private const val MinCycles = 0.5f

    /** Shapes *Zoom* so the panel's universal `0.5` lands near the reference's own default of `77`. */
    private const val ZoomEase = 0.6f

    /** The reference's *Thickness* range, in its own units — see [strokeWidthPx]. */
    private const val MinThickness = 2f
    private const val MaxThickness = 100f

    /** Shapes *Thickness* so the panel's universal `0.5` lands near the reference's own default of `12`. */
    private const val ThicknessEase = 3f

    /** The width, in pixels on a 1080-wide frame, of a contour at *Thickness* `0` and per unit above it — measured. */
    private const val ThicknessBase = 1.2f
    private const val ThicknessPerUnit = 0.157f

    /** The frame width the two thickness constants were measured on. */
    private const val ReferenceShortSide = 1080f

    /** How much wider the highlighted level is drawn — the reference's *Thickness boost* at its own default. */
    private const val HighlightBoost = 2.4f

    /** How dark a sheet goes under a solid shadow, at full *Shadow* — measured off the reference (a floor of ×0.80). */
    private const val MaxShadow = 0.4f

    /**
     * How far the shadow is thrown along each axis, as a share of the frame's short side — measured at 20px on a
     * 1080-wide frame, up and to the right, so the light is from the lower left.
     */
    private const val ShadowThrow = 0.0185f

    /** How far the thrown silhouette is softened over — measured: solid to ~28px, gone by ~60px. */
    private const val ShadowBlur = 0.0148f

    /**
     * Where the blurred silhouette is read, in units of [ShadowBlur] — the centre plus two hexagonal rings, which is
     * fourteen steps of softness across the falloff. Fewer taps band the edge visibly at this shadow depth.
     */
    private val ShadowTaps = floatArrayOf(
        0f, 0f,
        0.5f, 0f, 0.25f, 0.433f, -0.25f, 0.433f, -0.5f, 0f, -0.25f, -0.433f, 0.25f, -0.433f,
        1f, 0f, 0.5f, 0.866f, -0.5f, 0.866f, -1f, 0f, -0.5f, -0.866f, 0.5f, -0.866f,
    )
}

/**
 * A growable `Float` array — the segments marching squares emits, and the polylines they are chained into.
 *
 * **Here rather than an `ArrayList<Float>` because the counts are the whole frame's**: a level of a 360×800 lattice
 * emits tens of thousands of segments, and ten levels of boxed floats is a quarter of a million allocations per
 * render for nothing.
 */
private class FloatList {

    private var data = FloatArray(INITIAL)
    var size = 0
        private set

    /** How many whole segments this list holds, when it is holding segments rather than a polyline. */
    val segments: Int get() = size / FLOATS_PER_SEGMENT

    fun startX(segment: Int): Float = data[segment * FLOATS_PER_SEGMENT]
    fun startY(segment: Int): Float = data[segment * FLOATS_PER_SEGMENT + 1]
    fun endX(segment: Int): Float = data[segment * FLOATS_PER_SEGMENT + 2]
    fun endY(segment: Int): Float = data[segment * FLOATS_PER_SEGMENT + END_Y]

    /** [segment]'s two endpoints as chain keys — see [keyOf]. */
    fun startKey(segment: Int): Long = keyOf(startX(segment), startY(segment))
    fun endKey(segment: Int): Long = keyOf(endX(segment), endY(segment))

    /** The key of the point this list currently ends on — the tip a chain is being walked from. */
    fun lastKey(): Long = keyOf(data[size - 2], data[size - 1])

    /** Appends one `x, y` point. */
    fun point(x: Float, y: Float) {
        ensure(2)
        data[size++] = x
        data[size++] = y
    }

    /** Appends one segment as its two endpoints. */
    fun segment(ax: Float, ay: Float, bx: Float, by: Float) {
        point(ax, ay)
        point(bx, by)
    }

    /**
     * This list's points in reverse, followed by [tail]'s — the two halves of a chain walked out from its middle.
     *
     * **The first point is dropped**, because both halves were seeded with the same point: the segment they were
     * walked away from is one segment, and its start is where the two lists meet.
     */
    fun reversedInto(tail: FloatList): FloatArray {
        val out = FloatArray(size - 2 + tail.size)
        var at = 0
        var i = size - 2
        while (i >= 2) {
            out[at++] = data[i]
            out[at++] = data[i + 1]
            i -= 2
        }
        tail.data.copyInto(out, at, 0, tail.size)
        return out
    }

    private fun ensure(more: Int) {
        if (size + more <= data.size) return
        data = data.copyOf(max(data.size * 2, size + more))
    }

    companion object {

        /** A segment's two endpoints — how many buckets a chain's endpoint index needs per segment. */
        const val ENDS_PER_SEGMENT = 2

        /**
         * A point as one `Long`, quantized to [QUANTUM] of a pixel.
         *
         * **Quantized because the match has to be exact and the arithmetic is not.** Two cells sharing an edge each
         * compute that edge's crossing from the same two corner heights, so the result agrees to the last bit today —
         * but a chain that silently stops matching draws a stroke as a row of separate dashes, which reads as a
         * *style* rather than as a bug, so the key does not depend on that holding.
         */
        fun keyOf(x: Float, y: Float): Long {
            val qx = (x * QUANTUM).roundToInt().toLong()
            val qy = (y * QUANTUM).roundToInt().toLong()
            return (qx shl KEY_SHIFT) xor (qy and KEY_MASK)
        }

        private const val INITIAL = 256
        private const val QUANTUM = 16f
        private const val FLOATS_PER_SEGMENT = 4

        /** The last of a segment's four floats — the only one detekt cannot read as an index. */
        private const val END_Y = 3

        /** Half a `Long`, so the two coordinates of a key cannot collide across the join. */
        private const val KEY_SHIFT = 32
        private const val KEY_MASK = 0xFFFFFFFFL
    }
}
