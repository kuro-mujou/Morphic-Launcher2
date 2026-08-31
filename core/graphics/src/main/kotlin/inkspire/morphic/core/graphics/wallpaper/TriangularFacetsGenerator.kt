package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The frame tiled into flat-shaded triangles over a palette gradient — the low-poly *Triangular Facets*.
 *
 * **A jittered grid, not a Delaunay of random points — a deliberate choice, not a shortcut.** True Delaunay of
 * scattered points is more machinery, and it gives *irregular* facets with the occasional sliver; that irregular,
 * shattered look is what a Voronoi design (*Vitrall*) is for. A wallpaper facet field wants the opposite — even,
 * pleasing triangles with no degenerate cases — which a grid of points, each nudged a little off its lattice
 * position, produces directly and robustly. The grid's own topology *is* the triangulation, so there is no algorithm
 * to get wrong.
 *
 * **Each facet is one flat color: the palette gradient at the facet's height, jittered a shade per triangle** — which
 * is what makes a smooth gradient read as *faceted* rather than as a blur. Both the point jitter and the shade jitter
 * are seeded, so the recipe reproduces. [DesignParams.density] sets how fine the grid is, and
 * [DesignParams.irregularity] how far each point wanders off its lattice cell — `0` is near-regular triangles, `1` a
 * shattered field (Smart Launcher's *Distortion*).
 *
 * The point grid and the triangle list are pure and tested; only the fill needs a canvas.
 */
object TriangularFacetsGenerator : Generator {

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val cols = gridColumns(params.density)
        val rows = (cols * height) / width.coerceAtLeast(1) // keep facets roughly equilateral for the frame's shape
        val points = grid(cols, rows.coerceAtLeast(1), jitter(params.irregularity), seed)
        val triangles = triangles(cols, rows.coerceAtLeast(1))

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // A hair of stroke in each facet's own color closes the seams antialiasing leaves between abutting triangles.
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }

        val shade = Random(seed xor ShadeSalt)
        var t = 0
        while (t < triangles.size) {
            val a = triangles[t] * 2
            val b = triangles[t + 1] * 2
            val c = triangles[t + 2] * 2
            t += IndicesPerTriangle

            val centroidY = (points[a + 1] + points[b + 1] + points[c + 1]) / 3f
            val color = facetColor(centroidY, (shade.nextFloat() * 2f - 1f) * ShadeJitter, palette)
            val path = Path().apply {
                moveTo(points[a] * width, points[a + 1] * height)
                lineTo(points[b] * width, points[b + 1] * height)
                lineTo(points[c] * width, points[c + 1] * height)
                close()
            }
            paint.color = color
            seam.color = color
            canvas.drawPath(path, paint)
            canvas.drawPath(path, seam)
        }
        return bitmap
    }

    /** How many columns of facets [density] asks for — [MinColumns] when sparse up to [MaxColumns] when dense. */
    internal fun gridColumns(density: Float): Int =
        MinColumns + (density.coerceIn(0f, 1f) * (MaxColumns - MinColumns)).roundToInt()

    /**
     * How far a point may wander off its cell, as a fraction of the cell, for a given [irregularity] — `0` a rigid
     * grid, `1` the tangling limit. Scaled so the default `0.5` lands on [MaxJitter]`/2`, the value the design shipped
     * with, so an untouched Facets renders exactly as it did before the knob existed.
     */
    internal fun jitter(irregularity: Float): Float = irregularity.coerceIn(0f, 1f) * MaxJitter

    /**
     * A `([cols]+1) × ([rows]+1)` grid of points in the unit square, each interior point nudged up to [jitter] of a
     * cell off its lattice position — interleaved `x, y`, row-major.
     *
     * **The border points are pinned to the frame's edge** (their jitter zeroed), so the triangulation covers the
     * whole unit square exactly rather than leaving a ragged, un-tiled margin where the outermost facets pulled
     * inward. The random stream is consumed for every point regardless, so which points are interior does not shift
     * the seeded sequence.
     */
    internal fun grid(cols: Int, rows: Int, jitter: Float, seed: Long): FloatArray {
        val random = Random(seed)
        val points = FloatArray((cols + 1) * (rows + 1) * 2)
        var i = 0
        for (r in 0..rows) {
            for (c in 0..cols) {
                val jx = (random.nextFloat() * 2f - 1f) * jitter / cols
                val jy = (random.nextFloat() * 2f - 1f) * jitter / rows
                val border = c == 0 || c == cols || r == 0 || r == rows
                points[i++] = (c.toFloat() / cols) + if (border) 0f else jx
                points[i++] = (r.toFloat() / rows) + if (border) 0f else jy
            }
        }
        return points
    }

    /**
     * The triangle list for a `[cols] × [rows]` grid — index triples into [grid]'s points, two triangles per cell.
     *
     * **The diagonal alternates by cell**, which breaks the herringbone a fixed diagonal would draw and is what makes
     * the facets read as a field rather than as a woven texture.
     */
    internal fun triangles(cols: Int, rows: Int): IntArray {
        val list = ArrayList<Int>(cols * rows * 6)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val topLeft = r * (cols + 1) + c
                val topRight = topLeft + 1
                val bottomLeft = topLeft + (cols + 1)
                val bottomRight = bottomLeft + 1
                if ((r + c) % 2 == 0) {
                    list.add(topLeft); list.add(topRight); list.add(bottomLeft)
                    list.add(topRight); list.add(bottomRight); list.add(bottomLeft)
                } else {
                    list.add(topLeft); list.add(topRight); list.add(bottomRight)
                    list.add(topLeft); list.add(bottomRight); list.add(bottomLeft)
                }
            }
        }
        return list.toIntArray()
    }

    /**
     * A facet's flat color: the palette gradient at its height ([centroidY]), each channel scaled by `1 + [shade]` so
     * neighbouring facets sit a shade apart. Reuses [LinearGradientGenerator.colorAt] so a facet field and a plain
     * gradient of the same palette agree about the base ramp.
     */
    internal fun facetColor(centroidY: Float, shade: Float, palette: Palette): Int {
        val base = LinearGradientGenerator.colorAt(centroidY, palette)
        val factor = 1f + shade
        val a = base ushr 24 and 0xFF
        val r = ((base shr 16 and 0xFF) * factor).roundToInt().coerceIn(0, ChannelMax)
        val g = ((base shr 8 and 0xFF) * factor).roundToInt().coerceIn(0, ChannelMax)
        val b = ((base and 0xFF) * factor).roundToInt().coerceIn(0, ChannelMax)
        val packed = (a shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }

    private const val ChannelMax = 255
    private const val MinColumns = 5
    private const val MaxColumns = 16

    /** Three point indices make a triangle — the stride the triangle list is walked in. */
    private const val IndicesPerTriangle = 3

    /**
     * The most a point may wander off its lattice cell, at full irregularity — enough to shatter the grid without
     * tangling. The default irregularity (`0.5`) uses half of this, `0.55`, the value the design shipped with.
     */
    private const val MaxJitter = 1.1f

    /** How far a facet's shade may sit from the gradient, `±` — small, so the field reads as one ramp made of facets. */
    private const val ShadeJitter = 0.12f

    /** Keeps the shade jitter's stream independent of the point jitter's, so tuning one does not reshuffle the other. */
    private const val ShadeSalt = 0x9E3779B9L
}
