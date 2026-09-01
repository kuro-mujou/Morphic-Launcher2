package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cutting flat polygons: a pane in, two panes out — by a straight line or by an arc.
 *
 * The flat-polygon arithmetic behind [VitrallGenerator] and [ModernMosaicGenerator], kept beside them rather than
 * inside either because it is a different concern from a design. Where those files decide which way a cut runs and
 * what color a piece is, this one only answers "cut this shape in two" and "pull it back from its own edges" — and it
 * is gart's own split: its `arts/lines/vitrali` puts the same toolkit in a `glasscut.kt` next to the art, and its
 * header names exactly these three jobs ("half-plane clipping, chord splits, grout insets").
 *
 * A polygon is interleaved `x, y` floats in whatever frame the caller is working in; nothing here knows about pixels.
 *
 * **Everything here has one rule behind it: the two pieces of a cut must share their boundary exactly.** A crossing
 * point computed twice, once per piece, comes out a rounding apart, and the ground shows through between the two
 * panes as a hairline — which reads as a rendering artifact rather than as a bug, so it is the failure worth building
 * against. [split] computes each crossing once and hands it to both halves; [bowAbout] gets there by symmetry
 * instead, sampling the same arc from the same crossings in opposite directions.
 */
internal object GlassCut {

    /**
     * A pane cut in two.
     *
     * @property panes the two pieces, or the pane alone where the cut was refused.
     * @property arc the boundary the cut ran along, as a chain of points — two of them for a straight cut, the
     *   sampled arc for a bowed one, null where there was no cut to describe. It is the full bar across whatever was
     *   cut, not one piece's share of it, which is what makes it drawable as a heavier lead.
     */
    class Cut(val panes: List<FloatArray>, val arc: FloatArray?)

    /**
     * The two halves of [pane] cut by the line through (`px`, `py`) along (`dx`, `dy`), or the pane alone if the cut
     * is not a clean two-way one.
     *
     * Every crossing point is computed once and appended to **both** halves, which is the whole reason the leading
     * never opens a gap.
     *
     * **A line crossing the pane more than twice is refused, and that guard is what makes bowed cuts survivable.**
     * A bowed pane is not convex, so a line can enter and leave it twice and carve *three* pieces; this walk would
     * hand back the two outer ones welded into a single self-intersecting loop. Nothing about that reads as an error
     * — [area] returns a plausible number for it, smaller than the shape it covers — so a subdivision recursing on
     * area stops terminating, and what comes out is thousands of panes of thousands of vertices instead of a window.
     * The caller reads a refusal as "try another cut", which is the same answer it gets for a line that misses.
     */
    @Suppress("LongParameterList") // A line is four numbers; a struct for it would be read once and never again.
    fun split(pane: FloatArray, px: Float, py: Float, dx: Float, dy: Float): List<FloatArray> {
        val nx = -dy
        val ny = dx
        val count = pane.size / 2
        val near = ArrayList<Float>(pane.size + 4)
        val far = ArrayList<Float>(pane.size + 4)
        var crossings = 0
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
                crossings++
            }
        }
        if (crossings > CleanCrossings) return listOf(pane)
        val halves = listOf(near, far).filter { it.size >= MinVertices }.map { it.toFloatArray() }
        return halves.ifEmpty { listOf(pane) }
    }

    /**
     * [pane] cut by an arc of signed radius [reach] bowing off the line through (`px`, `py`) at [angle].
     *
     * The circle is struck from a center `|reach|` away perpendicular to the cut, so it passes through the cut's own
     * point and bows to one side or the other of the straight cut that would otherwise have been made there. **The
     * sign of [reach] is that side** — a bow that always fell the same way would give a whole window one curl.
     */
    @Suppress("LongParameterList") // As [split]: a bowed cut is a line plus a radius, all read once.
    fun bow(pane: FloatArray, angle: Float, px: Float, py: Float, reach: Float): Cut {
        val side = if (reach >= 0f) 1f else -1f
        val r = abs(reach)
        return bowAbout(pane, px + cos(angle + Quarter) * r * side, py + sin(angle + Quarter) * r * side, r)
    }

    /**
     * [pane] cut by the circle of radius [r] about (`cx`, `cy`).
     *
     * **Both sides come from one function called twice, and that is the correctness argument.** Each call samples the
     * boundary arc from the crossings it found, over a sweep whose magnitude is the same either way and whose
     * direction is the side's own — so the two pieces trace the same arc in opposite orders and weld along it. The
     * two endpoints are shared exactly; only the interior samples are a rounding apart, the sweep having been
     * reconstructed from the other end, and that rounding is a fraction of a thousandth of a pixel. Solving for the
     * crossings independently per pane is what would leave a hairline of ground showing.
     *
     * Meant for gentle cuts, as the reference's are: the center sits outside the pane and the radius is comfortably
     * larger than it, so the bitten piece is only mildly non-convex.
     */
    fun bowAbout(pane: FloatArray, cx: Float, cy: Float, r: Float): Cut {
        val (inside, arc) = arcSide(pane, cx, cy, r, keepInside = true)
        val (outside, _) = arcSide(pane, cx, cy, r, keepInside = false)
        if (inside == null || outside == null) return Cut(listOf(pane), null)
        return Cut(listOf(inside, outside), arc)
    }

    /**
     * One side of a circular cut: [pane] clipped to the inside or the outside of the circle of radius [r] about
     * (`cx`, `cy`), with the boundary arc sampled into segments of about [ArcStep] so the result is a plain polygon.
     *
     * Returns the piece and the sampled arc, or nulls where the circle does not cut the pane cleanly in two.
     */
    @Suppress("LongParameterList") // A circle plus a side is five numbers, all of them read once.
    private fun arcSide(
        pane: FloatArray,
        cx: Float,
        cy: Float,
        r: Float,
        keepInside: Boolean,
    ): Pair<FloatArray?, FloatArray?> {
        val count = pane.size / 2
        val sign = if (keepInside) 1f else -1f
        // Signed depth into the kept side: negative is kept, so one sign flip covers both sides of the cut.
        fun into(x: Float, y: Float) = (hypot(x - cx, y - cy) - r) * sign

        val walk = ArrayList<Float>(pane.size + ChainRoom)
        val marks = ArrayList<Int>(count + MarkRoom)
        var px = pane[(count - 1) * 2]
        var py = pane[(count - 1) * 2 + 1]
        var pd = into(px, py)
        var crossings = 0
        for (i in 0 until count) {
            val vx = pane[i * 2]
            val vy = pane[i * 2 + 1]
            val vd = into(vx, vy)
            crossings += crossEdge(walk, marks, px, py, vx - px, vy - py, cx, cy, r, pd <= 0f)
            if (vd <= 0f) {
                walk.add(vx)
                walk.add(vy)
                marks.add(Kept)
            }
            px = vx
            py = vy
            pd = vd
        }
        // As [split]: more than two crossings means three pieces, and welding would join two of them.
        if (marks.size < MinCorners || crossings != CleanCrossings) return null to null
        return weld(walk, marks, cx, cy, r, keepInside)
    }

    /**
     * Appends whatever of the edge from (`px`, `py`) along (`ex`, `ey`) crosses the circle to [walk] and [marks], in
     * walk order, and answers how many crossings it found.
     *
     * [startsKept] is whether the edge's own start is on the kept side; each crossing flips that, which is what tells
     * an exit from an entry without a second distance test.
     */
    @Suppress("LongParameterList") // An edge, a circle and a starting side; splitting them would only hide the maths.
    private fun crossEdge(
        walk: ArrayList<Float>,
        marks: ArrayList<Int>,
        px: Float,
        py: Float,
        ex: Float,
        ey: Float,
        cx: Float,
        cy: Float,
        r: Float,
        startsKept: Boolean,
    ): Int {
        // The quadratic in the edge's own parameter — where |start + t·edge - center| is exactly the radius.
        val a = ex * ex + ey * ey
        val fx = px - cx
        val fy = py - cy
        val b = 2f * (fx * ex + fy * ey)
        val c = fx * fx + fy * fy - r * r
        val disc = b * b - 4f * a * c
        if (disc <= 0f || a <= Tiny) return 0
        val root = sqrt(disc)
        var kept = startsKept
        var found = 0
        for (t in floatArrayOf((-b - root) / (2f * a), (-b + root) / (2f * a))) {
            if (t <= Grazing || t >= 1f - Grazing) continue
            walk.add(px + ex * t)
            walk.add(py + ey * t)
            marks.add(if (kept) Leaving else Entering)
            kept = !kept
            found++
        }
        return found
    }

    /**
     * The walked boundary closed up: where an exit meets the next entry the boundary follows the circle, so the arc
     * between them is sampled in.
     *
     * The sweep's direction comes from **which side is kept**, not from the shorter way round — that stays right even
     * when a deep bite wraps past half the circle, and it is what makes the two sides' arcs each other's reverse.
     */
    @Suppress("LongParameterList") // The walk, the circle, and the side — one call, all read once.
    private fun weld(
        walk: ArrayList<Float>,
        marks: ArrayList<Int>,
        cx: Float,
        cy: Float,
        r: Float,
        keepInside: Boolean,
    ): Pair<FloatArray?, FloatArray?> {
        val out = ArrayList<Float>(walk.size * 2)
        val chain = ArrayList<Float>()
        val marked = marks.size
        for (i in 0 until marked) {
            val x = walk[i * 2]
            val y = walk[i * 2 + 1]
            out.add(x)
            out.add(y)
            val j = (i + 1) % marked
            if (marks[i] != Leaving || marks[j] != Entering) continue
            val jx = walk[j * 2]
            val jy = walk[j * 2 + 1]
            val from = atan2(y - cy, x - cx)
            var sweep = (atan2(jy - cy, jx - cx) - from) % Turn
            if (keepInside) {
                if (sweep < 0f) sweep += Turn
            } else {
                if (sweep > 0f) sweep -= Turn
            }
            val steps = (abs(sweep) * r / ArcStep).toInt().coerceIn(1, MaxArcSteps)
            chain.add(x)
            chain.add(y)
            for (s in 1 until steps) {
                val at = from + sweep * s / steps
                val ax = cx + cos(at) * r
                val ay = cy + sin(at) * r
                out.add(ax)
                out.add(ay)
                chain.add(ax)
                chain.add(ay)
            }
            chain.add(jx)
            chain.add(jy)
        }
        if (out.size < MinVertices) return null to null
        val piece = out.toFloatArray()
        if (area(piece) < Tiny) return null to null
        return piece to chain.toFloatArray().takeIf { chain.isNotEmpty() }
    }

    /**
     * [pane] pulled back from every one of its edges by [by] — the grout inset. Null where the inset consumes it.
     *
     * **A true uniform inset: every edge is shifted inward along its own normal and the edges re-intersected**, so the
     * band left around the piece is the same width all the way round. Scaling the polygon about its centroid instead
     * is the cheap version and it is visibly wrong on anything but a regular shape — each edge moves in by a distance
     * proportional to how far it started from the centroid, so the grout thickens around a piece's long side. (The
     * triangle case has a shortcut, scaling about the *incenter*, which [TriangularFacetsGenerator] uses; it does not
     * generalize past three edges, which is why this exists.)
     *
     * **The everted-sliver guards are the part that is not obvious, and there are two because there are two ways to
     * evert.** Inset far enough and a convex polygon does not shrink to nothing tidily: the offset edges cross past
     * each other and re-intersect into a small polygon that draws as a real shape of about the right color in about
     * the right place. Where it flips across *one* axis its winding reverses, which the signed area catches. Where it
     * flips across *both* — inset a square by more than half its side and every edge crosses its opposite — the two
     * reversals cancel and the winding is **unchanged**, so no area test can see it. (gart's `inset` keeps only the
     * winding check and has this hole.) That case is caught before any work, by the plain precondition that nothing
     * can be pulled back from its own edges by more than half its own extent.
     */
    fun inset(pane: FloatArray, by: Float): FloatArray? {
        val count = pane.size / 2
        if (count < MinCorners || by <= 0f) return pane.takeIf { count >= MinCorners }
        val box = bounds(pane)
        val narrowest = min(box[2] - box[0], box[3] - box[1])
        val source = signedArea(pane)
        // The both-axes eversion, refused up front — see the note above; and a zero-area pane has no inside to keep.
        if (by + by >= narrowest || source == 0f) return null
        // Wind consistently first: the inward normal is the edge turned one way or the other depending on the winding,
        // and a piece that arrived reversed would be inset *outward* — which is the everted sliver, arriving early.
        val poly = if (source < 0f) reversed(pane) else pane

        val ax = FloatArray(count)
        val ay = FloatArray(count)
        val ex = FloatArray(count)
        val ey = FloatArray(count)
        for (i in 0 until count) {
            val j = (i + 1) % count
            val dx = poly[j * 2] - poly[i * 2]
            val dy = poly[j * 2 + 1] - poly[i * 2 + 1]
            val len = hypot(dx, dy)
            if (len < Tiny) return null
            // A point on the shifted edge, and the edge's own direction — the line is all that is needed to re-intersect.
            ax[i] = poly[i * 2] - dy / len * by
            ay[i] = poly[i * 2 + 1] + dx / len * by
            ex[i] = dx
            ey[i] = dy
        }
        val out = ArrayList<Float>(pane.size)
        for (i in 0 until count) {
            val h = (i + count - 1) % count
            val cross = ex[h] * ey[i] - ey[h] * ex[i]
            if (abs(cross) < Tiny) continue // this edge is parallel to the last — no corner between them
            val t = ((ax[i] - ax[h]) * ey[i] - (ay[i] - ay[h]) * ex[i]) / cross
            out.add(ax[h] + ex[h] * t)
            out.add(ay[h] + ey[h] * t)
        }
        val result = out.toFloatArray()
        // The **signed** area, which is the one-axis guard: an everted result still has a perfectly respectable
        // unsigned area, and only its reversed winding says it is inside out. The pane was wound positive above, so a
        // negative answer here is exactly that flip.
        val shrunk = if (out.size >= MinVertices) signedArea(result) else 0f
        return result.takeIf { shrunk >= Tiny && shrunk <= abs(source) }
    }

    /** The signed area of [pane] — positive or negative by winding, which [inset] needs and [area] throws away. */
    private fun signedArea(pane: FloatArray): Float {
        var sum = 0f
        val count = pane.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            sum += pane[i * 2] * pane[j * 2 + 1] - pane[j * 2] * pane[i * 2 + 1]
        }
        return sum / 2f
    }

    /** [pane]'s vertices in the opposite order, so its winding flips. */
    private fun reversed(pane: FloatArray): FloatArray {
        val count = pane.size / 2
        return FloatArray(pane.size) { i ->
            val v = count - 1 - i / 2
            if (i % 2 == 0) pane[v * 2] else pane[v * 2 + 1]
        }
    }

    /**
     * Where the infinite line through (`px`, `py`) along (`dx`, `dy`) enters and leaves [pane] — the full chord
     * rather than one pane's share of it. Null if the line misses.
     */
    @Suppress("LongParameterList") // As [split]: a line is four numbers.
    fun chord(pane: FloatArray, px: Float, py: Float, dx: Float, dy: Float): FloatArray? {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        val count = pane.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            val ax = pane[i * 2]
            val ay = pane[i * 2 + 1]
            val ex = pane[j * 2] - ax
            val ey = pane[j * 2 + 1] - ay
            val den = dx * ey - dy * ex
            if (abs(den) < Tiny) continue // parallel to this edge — no crossing to find
            val along = ((ax - px) * ey - (ay - py) * ex) / den
            val across = ((ax - px) * dy - (ay - py) * dx) / den
            if (across in 0f..1f) {
                lo = min(lo, along)
                hi = max(hi, along)
            }
        }
        if (hi <= lo) return null
        return floatArrayOf(px + dx * lo, py + dy * lo, px + dx * hi, py + dy * hi)
    }

    /** The area of [pane] — the shoelace formula, made positive. */
    fun area(pane: FloatArray): Float {
        var sum = 0f
        val count = pane.size / 2
        for (i in 0 until count) {
            val j = (i + 1) % count
            sum += pane[i * 2] * pane[j * 2 + 1] - pane[j * 2] * pane[i * 2 + 1]
        }
        return abs(sum) / 2f
    }

    /** [pane]'s bounding box as `minX, minY, maxX, maxY`. */
    fun bounds(pane: FloatArray): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in pane.indices step 2) {
            minX = min(minX, pane[i]); maxX = max(maxX, pane[i])
            minY = min(minY, pane[i + 1]); maxY = max(maxY, pane[i + 1])
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /**
     * The mean of [pane]'s vertices — not the area centroid.
     *
     * The difference matters for where it is used and not for what it is called: a cut is placed near here and then
     * drifted at random anyway, so the vertex mean is the cheaper answer to the same question. It does pull toward
     * whichever side of a pane carries the sampled arc, which is a bias toward cutting the curved end — harmless,
     * and worth knowing before anything measures against it.
     */
    fun centroidX(pane: FloatArray): Float {
        var sum = 0f
        for (i in pane.indices step 2) sum += pane[i]
        return sum / (pane.size / 2)
    }

    /** The mean of [pane]'s vertices on the other axis — see [centroidX]. */
    fun centroidY(pane: FloatArray): Float {
        var sum = 0f
        for (i in 1 until pane.size step 2) sum += pane[i]
        return sum / (pane.size / 2)
    }

    /** The angle of [pane]'s longest diagonal. */
    fun longestDiagonal(pane: FloatArray): Float {
        val count = pane.size / 2
        var best = 0f
        var angle = 0f
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val dx = pane[j * 2] - pane[i * 2]
                val dy = pane[j * 2 + 1] - pane[i * 2 + 1]
                val span = dx * dx + dy * dy
                if (span > best) {
                    best = span
                    angle = atan2(dy, dx)
                }
            }
        }
        return angle
    }

    /** A right angle and a full turn, in radians. */
    const val Quarter = (PI / 2).toFloat()
    const val Turn = (2 * PI).toFloat()

    /** Below this a determinant is a line parallel to an edge, and an area is nothing at all. */
    const val Tiny = 1e-6f

    /**
     * How long an arc's segments are, as a fraction of the frame the caller is cutting in, and the most segments an
     * arc may ever be cut into.
     *
     * **The cap is not the reference model's** — gart is a desktop art tool with a desktop heap and samples a
     * sweeping arc into thousands of segments. Here every bow adds its samples to a pane that may be cut again, and
     * a pane repeatedly shaved into slivers accumulates them: a full-frame arc came to about 760 points, so a dozen
     * shaves put six figures of vertices through a clip that is `O(n)` per cut. At 48 the worst arc's sagitta error
     * is about a pixel and a half on a tall phone, which is below the antialiasing.
     */
    private const val ArcStep = 0.0025f
    private const val MaxArcSteps = 48

    /** How many times a cut may cross a pane and still leave two pieces — see [split]. */
    private const val CleanCrossings = 2

    /** A polygon needs three corners, so six interleaved floats. */
    private const val MinCorners = 3
    private const val MinVertices = 6

    /** How a walked vertex relates to the kept side of a circular cut. */
    private const val Kept = 0
    private const val Leaving = 1
    private const val Entering = 2

    /** Room set aside for the arc's own points, so a bowed clip does not grow its lists an element at a time. */
    private const val ChainRoom = 32
    private const val MarkRoom = 16

    /** A crossing this close to a vertex is that vertex, and counting it twice inverts the kept side from there on. */
    private const val Grazing = 1e-5f
}
