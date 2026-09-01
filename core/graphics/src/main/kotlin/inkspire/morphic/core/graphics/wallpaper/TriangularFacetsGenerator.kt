package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A lit sheet of triangles over a field of colored areas — the low-poly *Triangular Facets*.
 *
 * **The color is a two-dimensional field of areas, not a ramp down the frame.** This is the design's identity and it
 * is what the first version got wrong: reading the palette at a facet's *height* makes a striped gradient with corners
 * cut into it, where the reference paints soft regions — a warm lobe here, a cool one there, the blend of the two
 * everywhere between. Measured off theirs, a path from the middle of one region to the middle of another is a
 * **straight line in RGB between those two stops**; it does not visit the stops in between, which is exactly what
 * rules out a scalar field read through the ramp. So the field is a coarse lattice of nodes, each holding one stop,
 * blended bilinearly and sampled at each facet's centroid — [ColorLattice], shared with the mesh gradient, which
 * builds its picture the same way and is in a real sense this design unfaceted.
 *
 * **The lattice is fixed and coarse, deliberately independent of [DesignParams.density].** Tying it to the facet grid
 * would let the resolution knob quietly change the size of the color regions, so a finer mesh would come out not just
 * finer but *busier* — the mistake the mesh gradient's warp lattice records. Fixed, the resolution knob changes only
 * how finely the same picture is cut.
 *
 * **[DesignParams.depth] is the relief, and it is what makes a facet field read as faceted rather than as a blurred
 * gradient.** Every lattice point carries a height from a smooth noise, so each triangle sits at its own angle; a
 * facet's color is then scaled by how far it tilts toward the light. Flat shading over a smooth field is a picture of
 * nothing — at `0` this is a plain quantized gradient, which is the honest rigid end — and the reference's own
 * *Tridimensionality* is the knob that rescues it.
 *
 * **[DesignParams.variant] is one axis sampled at three useful points: how far a facet's color departs from the
 * field.** *Field* takes the sample as it is, *Speckled* pulls each facet part of the way toward a random stop, and
 * *Scattered* goes all the way, which throws the field away and leaves a quilt of flat stops. The reference splits
 * this across a *Distribution* toggle and a *Randomness* slider, and hides the slider under the toggle's second
 * option — which is the same statement that they are one axis, made twice.
 *
 * **[DesignParams.scale] is the leading: every facet shrinks away from its own edges, uncovering the ground between
 * them.** A true inset by a distance rather than a scaling about the centroid, so the line between two facets is the
 * same width everywhere — a proportional shrink would draw a heavier line around the bigger triangles. The ground is
 * the palette's first stop, which the field never paints with, so the leading always reads against the glass.
 *
 * Everything but the fill is pure and tested: the lattice fit, the triangulation, the inset and the shading all fail
 * *silently* when they are wrong — a mesh that is one cell short leaves a strip of ground down the edge, an inset past
 * the inradius turns a facet inside out, and a shading term with the wrong sign lights the picture from underneath.
 */
object TriangularFacetsGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — cells along the frame's **long** axis, and the slider's own range.
     *
     * Counting on the long axis is the reference's choice and it is the one that keeps a cell square: the short axis
     * then takes however many cells fit, so the same number means the same *facet size* in portrait and landscape.
     */
    private val Amount = AmountKnob.Count("Resolution", 3..20)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Leading",
        irregularity = "Distortion",
        depth = "Relief",
        variant = VariantKnob("Colors", Colors.entries.map { it.label }),
    )

    /**
     * How far a facet's color departs from the field — one axis, at the three points that are actually different
     * pictures.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property speckle how far toward a randomly chosen stop each facet is pulled, `0..1`. At `1` the field is gone
     *   entirely and every facet is a flat stop, which is the reference's *Random* distribution.
     */
    internal enum class Colors(val label: String, val speckle: Float) {
        FIELD("Field", 0f),
        SPECKLED("Speckled", SpeckleAmount),
        SCATTERED("Scattered", 1f),
    }

    /**
     * The facet lattice's shape.
     *
     * @property cols cells across the frame.
     * @property rows cells down it.
     */
    internal data class Cells(val cols: Int, val rows: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val cells = cells(width, height, params.density)
        val points = grid(cells, jitter(params.irregularity), seed)
        val heights = relief(cells, params.depth, seed)
        val triangles = triangles(points, cells)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        // The ground: what the leading is made of, and what a facet uncovers as it shrinks. Painted whether or not
        // any of it will show, so a fully-tiled render and a leaded one differ only in the leading.
        canvas.drawColor(palette.colorAt(0))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // A hair of stroke in each facet's own color closes the seams antialiasing leaves between abutting triangles.
        // Dropped once the leading opens a real gap, where it would only thicken every facet back over the ground.
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }

        val nodes = fieldNodes(cells)
        val field = field(cells, palette, seed)
        val stops = fieldStops(palette.size)
        val speckle = Colors.entries[params.variant.coerceIn(0, Colors.entries.lastIndex)].speckle
        val speckleRandom = Random(seed xor SpeckleSalt)
        val inset = leading(params.scale) * min(width.toFloat() / cells.cols, height.toFloat() / cells.rows)

        val path = Path()
        var t = 0
        while (t < triangles.size) {
            val a = triangles[t]
            val b = triangles[t + 1]
            val c = triangles[t + 2]
            t += IndicesPerTriangle

            val centroidU = (points[a * 2] + points[b * 2] + points[c * 2]) / 3f
            val centroidV = (points[a * 2 + 1] + points[b * 2 + 1] + points[c * 2 + 1]) / 3f
            val base = ColorLattice.sample(field, nodes.cols, nodes.rows, centroidU, centroidV)
            // Every facet draws from the stream whatever the variant, so the Colors chooser re-colors the same
            // facets instead of reshuffling all of them — the discipline every seeded knob here keeps.
            val accent = palette.colorAt(stops[speckleRandom.nextInt(stops.size)])
            val lit = shade(speckled(base, accent, speckle), lighting(points, heights, cells, a, b, c))

            path.rewind()
            if (inset > 0f) {
                insetTriangle(path, points, width, height, a, b, c, inset)
            } else {
                path.moveTo(points[a * 2] * width, points[a * 2 + 1] * height)
                path.lineTo(points[b * 2] * width, points[b * 2 + 1] * height)
                path.lineTo(points[c * 2] * width, points[c * 2 + 1] * height)
                path.close()
            }

            paint.color = lit
            canvas.drawPath(path, paint)
            if (inset <= 0f) {
                seam.color = lit
                canvas.drawPath(path, seam)
            }
        }
        return bitmap
    }

    /**
     * The lattice [density] asks for on a `[width] × [height]` frame — the count on the long axis, and whatever keeps
     * the cells square on the short one.
     */
    internal fun cells(width: Int, height: Int, density: Float): Cells {
        val long = max(width, height).coerceAtLeast(1)
        val short = min(width, height).coerceAtLeast(1)
        val alongLong = Amount.at(density)
        val alongShort = (alongLong.toFloat() * short / long).roundToInt().coerceAtLeast(1)
        return if (width >= height) Cells(alongLong, alongShort) else Cells(alongShort, alongLong)
    }

    /**
     * How far a point may wander off its cell, as a fraction of the cell, for a given [irregularity] — `0` a rigid
     * lattice, `1` the reference's full *Distortion*.
     */
    internal fun jitter(irregularity: Float): Float = irregularity.coerceIn(0f, 1f) * MaxJitter

    /**
     * A `([Cells.cols] + 1) × ([Cells.rows] + 1)` grid of points in the unit square, each nudged up to [jitter] of a cell
     * off its lattice position — interleaved `x, y`, row-major.
     *
     * **A border point slides *along* its edge rather than being pinned to the lattice.** Pinning both of its
     * coordinates, which this design did at first, leaves a visibly ruled frame around an otherwise organic field —
     * the edge cells alone keep their exact lattice width. Zeroing only the component that would leave the frame keeps
     * the tiling exact and lets the edge break up with everything else. The random stream is consumed for every point
     * regardless, so which points are on the border does not shift the seeded sequence.
     */
    internal fun grid(cells: Cells, jitter: Float, seed: Long): FloatArray {
        val random = Random(seed)
        val points = FloatArray((cells.cols + 1) * (cells.rows + 1) * 2)
        var i = 0
        for (r in 0..cells.rows) {
            for (c in 0..cells.cols) {
                val jx = (random.nextFloat() * 2f - 1f) * jitter / cells.cols
                val jy = (random.nextFloat() * 2f - 1f) * jitter / cells.rows
                val onSide = c == 0 || c == cells.cols
                val onCap = r == 0 || r == cells.rows
                points[i++] = (c.toFloat() / cells.cols) + if (onSide) 0f else jx
                points[i++] = (r.toFloat() / cells.rows) + if (onCap) 0f else jy
            }
        }
        return points
    }

    /**
     * A height per lattice point, in **cell units** — the relief the shading reads, from a smooth noise at
     * [depth]'s amplitude.
     *
     * **The noise is sampled in lattice coordinates, not frame coordinates, and that is what sets the character.**
     * One noise unit per cell puts a swell of the field across a couple of cells however fine the mesh is, so the
     * relief stays a crumple of *this* sheet rather than becoming a smooth landform the facets merely sample. Heights
     * in cell units rather than pixels is the other half: a slope is then a pure number, so the same [depth] lights
     * a coarse mesh and a fine one the same amount.
     */
    internal fun relief(cells: Cells, depth: Float, seed: Long): FloatArray {
        val noise = PerlinNoise2d(seed xor ReliefSalt)
        val amplitude = depth.coerceIn(0f, 1f) * MaxRelief
        val heights = FloatArray((cells.cols + 1) * (cells.rows + 1))
        var i = 0
        for (r in 0..cells.rows) {
            for (c in 0..cells.cols) {
                heights[i++] = noise.at(c * ReliefFrequency, r * ReliefFrequency) * amplitude
            }
        }
        return heights
    }

    /**
     * The triangle list for [cells] over [points] — index triples, two triangles per cell.
     *
     * **Each cell is split along its *shorter* diagonal.** A fixed diagonal is what turns a badly-stretched quad into
     * a needle: the split runs the long way and leaves two slivers, which at high distortion is most of what the eye
     * sees. Choosing the shorter one is the cheap local form of the flip a Delaunay triangulation would make, and it
     * costs a comparison. On a rigid lattice the two are equal and every cell takes the same diagonal, which is what
     * gives the `0` end its clean quilt — the alternation this design used to do instead broke that into pinwheels.
     */
    internal fun triangles(points: FloatArray, cells: Cells): IntArray {
        val span = cells.cols + 1
        val list = ArrayList<Int>(cells.cols * cells.rows * IndicesPerTriangle * 2)
        for (r in 0 until cells.rows) {
            for (c in 0 until cells.cols) {
                val topLeft = r * span + c
                val topRight = topLeft + 1
                val bottomLeft = topLeft + span
                val bottomRight = bottomLeft + 1
                if (span2(points, topLeft, bottomRight) <= span2(points, topRight, bottomLeft)) {
                    list.add(topLeft); list.add(topRight); list.add(bottomRight)
                    list.add(topLeft); list.add(bottomRight); list.add(bottomLeft)
                } else {
                    list.add(topLeft); list.add(topRight); list.add(bottomLeft)
                    list.add(topRight); list.add(bottomRight); list.add(bottomLeft)
                }
            }
        }
        return list.toIntArray()
    }

    /** The squared distance between two [points], in unit-square coordinates — a diagonal's length, uncompared. */
    private fun span2(points: FloatArray, a: Int, b: Int): Float {
        val dx = points[a * 2] - points[b * 2]
        val dy = points[a * 2 + 1] - points[b * 2 + 1]
        return dx * dx + dy * dy
    }

    /** The color-field lattice's shape — fixed, and oriented to the frame so its regions come out round. */
    internal fun fieldNodes(cells: Cells): Cells =
        if (cells.cols >= cells.rows) Cells(FieldNodesLong, FieldNodesShort) else Cells(FieldNodesShort, FieldNodesLong)

    /** A stop per node of the color-field lattice, drawn from the stops the field may paint with. */
    internal fun field(cells: Cells, palette: Palette, seed: Long): IntArray {
        val nodes = fieldNodes(cells)
        val stops = fieldStops(palette.size)
        val random = Random(seed xor FieldSalt)
        return IntArray(nodes.cols * nodes.rows) { palette.colorAt(stops[random.nextInt(stops.size)]) }
    }

    /**
     * Which stops the field may paint with, given a palette of [size] — everything but the ground.
     *
     * **Below three stops it keeps the ground too, or the design dies at its own default.** Holding stop `0` back
     * leaves a bichromatic palette — the *default* color mode — a single color, so the field would be flat and the
     * speckle would have nothing to trade. Two tones of glass over a ground that matches one of them is a lesser
     * picture than the five-stop case, but it is still the picture.
     */
    internal fun fieldStops(size: Int): IntArray {
        val first = if (size >= GroundNeeds) 1 else 0
        return IntArray(size - first) { it + first }
    }

    /** [base] pulled [speckle] of the way to [accent] — the per-facet departure from the field. */
    private fun speckled(base: Int, accent: Int, speckle: Float): Int =
        if (speckle <= 0f) base else LinearGradientGenerator.lerpArgb(base, accent, speckle)

    /**
     * How much brighter or darker a facet is than a flat one, as a factor on its channels — `1` is unlit.
     *
     * The three corners fix a plane; its slope *toward the light* is what decides the shading, squashed so the
     * factor saturates rather than running to black on a near-vertical facet. Squashing rather than clamping is what
     * keeps the relief knob alive at its top end: a clamp would make every steep facet the same black.
     */
    internal fun lighting(points: FloatArray, heights: FloatArray, cells: Cells, a: Int, b: Int, c: Int): Float {
        // Cell units on all three axes, so the slope is a pure number and the resolution cannot change it.
        val ax = points[a * 2] * cells.cols
        val ay = points[a * 2 + 1] * cells.rows
        val bx = points[b * 2] * cells.cols
        val by = points[b * 2 + 1] * cells.rows
        val cx = points[c * 2] * cells.cols
        val cy = points[c * 2 + 1] * cells.rows
        val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if (area == 0f) return 1f
        val dza = heights[b] - heights[a]
        val dzb = heights[c] - heights[a]
        val dzdx = (dza * (cy - ay) - dzb * (by - ay)) / area
        val dzdy = (dzb * (bx - ax) - dza * (cx - ax)) / area
        // Positive where the facet rises toward the light, which sits up and to the left of the frame.
        val slope = -(dzdx * LightX + dzdy * LightY)
        return 1f + ReliefGain * slope / sqrt(1f + slope * slope)
    }

    /** [color] with every channel scaled by [factor], alpha untouched — the relief's whole effect on a facet. */
    internal fun shade(color: Int, factor: Float): Int {
        if (factor == 1f) return color
        val a = color ushr AlphaShift and ChannelMask
        val r = ((color shr RedShift and ChannelMask) * factor).roundToInt().coerceIn(0, ChannelMask)
        val g = ((color shr GreenShift and ChannelMask) * factor).roundToInt().coerceIn(0, ChannelMask)
        val b = ((color and ChannelMask) * factor).roundToInt().coerceIn(0, ChannelMask)
        val packed = (a shl AlphaShift) or (r shl RedShift) or (g shl GreenShift) or b
        return packed
    }

    /** How far a facet pulls back from its own edges at [scale], as a fraction of a cell. */
    internal fun leading(scale: Float): Float {
        val t = scale.coerceIn(0f, 1f)
        // Cubed, so the lower half of the knob is all hairlines. That is where the design wants to live — the
        // reference opens this at zero, and until a design can carry its own defaults ours opens on the panel's 0.5,
        // which a linear track would spend on a cream web with a picture behind it.
        return t * t * t * MaxLeading
    }

    /**
     * Adds the triangle, inset by [inset] pixels on every side, to the (already rewound) [path].
     *
     * A uniform inset of a triangle is the triangle scaled about its **incenter** by `(r - inset) / r`, `r` being the
     * inradius — which is why the incenter is worth computing rather than using the centroid: scaling about the
     * centroid moves each edge in by a different amount, so the leading would be visibly uneven around a facet that
     * is not equilateral. A facet with nothing left contributes nothing rather than turning inside out.
     */
    @Suppress("LongParameterList") // Three point indices and a frame; the alternative is a struct per triangle.
    private fun insetTriangle(
        path: Path,
        points: FloatArray,
        width: Int,
        height: Int,
        a: Int,
        b: Int,
        c: Int,
        inset: Float,
    ) {
        val ax = points[a * 2] * width
        val ay = points[a * 2 + 1] * height
        val bx = points[b * 2] * width
        val by = points[b * 2 + 1] * height
        val cx = points[c * 2] * width
        val cy = points[c * 2 + 1] * height
        // Side lengths opposite each vertex, which is what weights the incenter.
        val la = sqrt((bx - cx) * (bx - cx) + (by - cy) * (by - cy))
        val lb = sqrt((ax - cx) * (ax - cx) + (ay - cy) * (ay - cy))
        val lc = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))
        val perimeter = la + lb + lc
        if (perimeter <= 0f) return
        val ix = (la * ax + lb * bx + lc * cx) / perimeter
        val iy = (la * ay + lb * by + lc * cy) / perimeter
        val twiceArea = abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay))
        val inradius = twiceArea / perimeter
        val k = (inradius - inset) / inradius
        if (k <= 0f) return
        path.moveTo(ix + (ax - ix) * k, iy + (ay - iy) * k)
        path.lineTo(ix + (bx - ix) * k, iy + (by - iy) * k)
        path.lineTo(ix + (cx - ix) * k, iy + (cy - iy) * k)
        path.close()
    }

    /**
     * How far *Speckled* pulls a facet off the field — enough to read as a scatter over the regions rather than as
     * noise on top of them. The reference's *Randomness* slider is continuous and opens near here.
     */
    private const val SpeckleAmount = 0.4f

    /** Three point indices make a triangle — the stride the triangle list is walked in. */
    private const val IndicesPerTriangle = 3

    /** Where each channel sits in a packed ARGB int, and the byte that reads it. */
    private const val AlphaShift = 24
    private const val RedShift = 16
    private const val GreenShift = 8
    private const val ChannelMask = 0xFF

    /**
     * The most a point may wander off its lattice cell, at full distortion. Past about this the shorter-diagonal split
     * stops being enough to keep the triangles honest and the sheet starts folding through itself.
     */
    private const val MaxJitter = 0.55f

    /** Nodes on the color-field lattice along the frame's long axis, and along its short one. Fixed — see the note. */
    private const val FieldNodesLong = 4
    private const val FieldNodesShort = 3

    /** How many stops a palette needs before the field can afford to hold one back as the ground. */
    private const val GroundNeeds = 3

    /** The height a lattice point reaches at full relief, in cell units. */
    private const val MaxRelief = 1f

    /** Noise units per cell for the relief — under one, so a swell spans a couple of facets rather than each one. */
    private const val ReliefFrequency = 0.85f

    /** How far the relief may push a facet's brightness, either way, as the squashed slope saturates. */
    private const val ReliefGain = 0.6f

    /** Where the light sits over the frame — `(-2, -3)` normalized, so up and to the left. */
    private const val LightX = -0.5547f
    private const val LightY = -0.8321f

    /**
     * The widest leading, as a fraction of a cell. Short of the inradius of a rigid cell's triangle, so the widest
     * setting is heavy leaded glass rather than a ground with specks floating on it — the reference's own top end,
     * which dissolves the design, is not a picture worth spending the knob's last third on.
     */
    private const val MaxLeading = 0.12f

    /** Keeps each seeded stream independent, so tuning one knob does not reshuffle what the others drew. */
    private const val ReliefSalt = 0x5BF03635L
    private const val FieldSalt = 0x27D4EB2FL
    private const val SpeckleSalt = 0x9E3779B9L
}
