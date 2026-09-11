package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Iso-lines out of a lattice of heights — marching squares, with each crossing interpolated along its cell edge, and
 * the loose segments chained end to end into polylines.
 *
 * **Its own file because it is its own algorithm**: nothing in it knows it is drawing a map. [ContourGenerator] is its
 * one caller, tracing each level of its terrain for the bake and for every moment of a scrub.
 */
internal object Isolines {

    /**
     * The polylines of one iso-level — marching squares over the lattice, the crossings interpolated along each cell
     * edge so the line lands where the height actually reaches [level] rather than on the lattice.
     */
    @Suppress("LongParameterList") // A lattice is its shape, its cell and its heights; the level is the question.
    fun trace(cols: Int, rows: Int, cellW: Float, cellH: Float, z: FloatArray, level: Float): List<FloatArray> {
        val lattice = Lattice(cols, cellW, cellH, z)
        val segments = FloatList()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                cellSegments(lattice, c, r, level, segments)
            }
        }
        return chain(segments)
    }

    /** A lattice's shape and heights, read by corner — what one cell's segments are worked out from. */
    private class Lattice(private val cols: Int, val cellW: Float, val cellH: Float, private val z: FloatArray) {
        fun corner(c: Int, r: Int): Float = z[r * (cols + 1) + c]
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
    private fun cellSegments(terrain: Lattice, c: Int, r: Int, level: Float, into: FloatList) {
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

    /** Where a line crosses a cell edge whose two corners are the same height — nowhere in particular. */
    private const val FlatCrossing = 0.5f
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
