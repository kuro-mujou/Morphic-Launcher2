package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.FlowFieldGenerator.Item
import inkspire.morphic.core.graphics.wallpaper.FlowFieldGenerator.Mark
import inkspire.morphic.core.graphics.wallpaper.FlowFieldGenerator.Orb
import inkspire.morphic.core.graphics.wallpaper.FlowFieldGenerator.Plan
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Two flow fields and every moment between them — [FlowFieldGenerator]'s scrub.
 *
 * **The trails have no identity to carry, so the marks are paired by nearness.** A flow field's trails are grown one
 * at a time off each other's flanks, in an order the seed's stream picks, so two seeds' trails share nothing — and
 * re-growing them through a turned field would boil, and could not land on the other end without a pop. What the
 * picture *is* is its marks, so each mark of one end is paired with the nearest unpaired mark of the other within
 * [Reach] lanes, and bent into it; a mark left without a partner fades out, or in. Two evenly spaced sets bent into
 * each other are not evenly spaced on the way, so marks can cross mid-scrub: the author chose that over no scrub.
 *
 * **A pair is resampled to one point count and made to run one way**, once per scrub. A mark's points are already a
 * hop apart, so resampling one to more points lays them on the line it already was — which is why a moment just after
 * the start draws the mark the bake drew. Whether one of the two is reversed is settled by which way round their ends
 * lie nearer, since a streamline's direction says nothing about which end of a dash is which.
 *
 * **Depth interpolates**, like everything else, and a moment is drawn in depth order — so a mark passing an orb
 * changes sides at its own moment, and the order the scrub hands the bake is the other end's exactly.
 */
internal class FlowFieldMorph(private val a: Plan, private val b: Plan) {

    /** A pair of marks, one from each end, resampled to one point count and running the same way. */
    private class MarkPair(val from: Mark, val to: Mark, val fromPoints: FloatArray, val toPoints: FloatArray)

    private val pairs = ArrayList<MarkPair>()
    private val leaving = ArrayList<Mark>()
    private val arriving = ArrayList<Mark>()
    private val orbPairs = ArrayList<Pair<Orb, Orb>>()
    private val orbsLeaving = ArrayList<Orb>()
    private val orbsArriving = ArrayList<Orb>()

    init {
        val marksA = a.items.filterIsInstance<Mark>()
        val marksB = b.items.filterIsInstance<Mark>()
        val partner = nearest(marksA.map { middle(it.points) }, marksB.map { middle(it.points) }, a.separation * Reach)
        val taken = BooleanArray(marksB.size)
        for ((i, mark) in marksA.withIndex()) {
            val j = partner[i]
            if (j < 0) {
                leaving += mark
            } else {
                taken[j] = true
                pairs += paired(mark, marksB[j])
            }
        }
        for ((j, mark) in marksB.withIndex()) if (!taken[j]) arriving += mark

        val orbsA = a.items.filterIsInstance<Orb>()
        val orbsB = b.items.filterIsInstance<Orb>()
        val orbPartner = nearest(
            orbsA.map { floatArrayOf(it.x, it.y) },
            orbsB.map { floatArrayOf(it.x, it.y) },
            Float.MAX_VALUE,
        )
        val orbTaken = BooleanArray(orbsB.size)
        for ((i, orb) in orbsA.withIndex()) {
            val j = orbPartner[i]
            if (j < 0) {
                orbsLeaving += orb
            } else {
                orbTaken[j] = true
                orbPairs += orb to orbsB[j]
            }
        }
        for ((j, orb) in orbsB.withIndex()) if (!orbTaken[j]) orbsArriving += orb
    }

    /** Every mark and orb of the moment [t], in the order it is drawn. */
    fun at(t: Float): List<Item> {
        val items = ArrayList<Item>(pairs.size + leaving.size + arriving.size + orbPairs.size + orbsLeaving.size)
        for (pair in pairs) {
            val from = pair.from
            val to = pair.to
            items += Mark(
                points = FloatArray(pair.fromPoints.size) { lerp(pair.fromPoints[it], pair.toPoints[it], t) },
                width = lerp(from.width, to.width, t),
                color = mix(from.color, to.color, t),
                beads = lerp(from.beads, to.beads, t),
                opacity = 1f,
                depth = lerp(from.depth, to.depth, t),
            )
        }
        for (mark in leaving) items += Mark(mark.points, mark.width, mark.color, mark.beads, 1f - t, mark.depth)
        for (mark in arriving) items += Mark(mark.points, mark.width, mark.color, mark.beads, t, mark.depth)
        for ((from, to) in orbPairs) {
            items += Orb(
                x = lerp(from.x, to.x, t),
                y = lerp(from.y, to.y, t),
                radius = lerp(from.radius, to.radius, t),
                color = mix(from.color, to.color, t),
                opacity = 1f,
                depth = lerp(from.depth, to.depth, t),
            )
        }
        for (orb in orbsLeaving) items += Orb(orb.x, orb.y, orb.radius, orb.color, 1f - t, orb.depth)
        for (orb in orbsArriving) items += Orb(orb.x, orb.y, orb.radius, orb.color, t, orb.depth)
        return items.sortedBy { it.depth }
    }

    /** How many marks found a partner, and how many of each end's did not — for a test to hold the pairing to. */
    val counts: Triple<Int, Int, Int> get() = Triple(pairs.size, leaving.size, arriving.size)

    private companion object {

        /** How far a mark may look for its partner, in lanes — past a lane or two, two marks are not the same mark. */
        const val Reach = 3f

        /** [from]'s and [to]'s marks resampled to one point count, the second reversed where that lines their ends up. */
        fun paired(from: Mark, to: Mark): MarkPair {
            val count = maxOf(from.points.size, to.points.size) / 2
            val fromPoints = resampled(from.points, count)
            var toPoints = resampled(to.points, count)
            val last = count - 1
            val straight = distance(fromPoints, 0, toPoints, 0) + distance(fromPoints, last, toPoints, last)
            val crossed = distance(fromPoints, 0, toPoints, last) + distance(fromPoints, last, toPoints, 0)
            if (crossed < straight) toPoints = reversed(toPoints)
            return MarkPair(from, to, fromPoints, toPoints)
        }

        /**
         * For each of [from]'s points, the nearest of [to]'s not yet taken within [reach], or `-1` — greedily, in
         * [from]'s order, through a grid a [reach] across so each asks only the cells around it.
         */
        fun nearest(from: List<FloatArray>, to: List<FloatArray>, reach: Float): IntArray {
            val cell = reach
            val grid = HashMap<Long, MutableList<Int>>()
            fun key(x: Int, y: Int) = (x.toLong() shl Half) xor (y.toLong() and Low)
            fun cellOf(v: Float) = if (cell == Float.MAX_VALUE) 0 else floor(v / cell).toInt()
            for ((j, p) in to.withIndex()) grid.getOrPut(key(cellOf(p[0]), cellOf(p[1]))) { ArrayList() }.add(j)
            val taken = BooleanArray(to.size)
            return IntArray(from.size) { i ->
                val p = from[i]
                val cx = cellOf(p[0])
                val cy = cellOf(p[1])
                var best = -1
                var bestDistance = reach
                for (dx in -1..1) for (dy in -1..1) {
                    for (j in grid[key(cx + dx, cy + dy)].orEmpty()) {
                        if (taken[j]) continue
                        val d = hypot(to[j][0] - p[0], to[j][1] - p[1])
                        if (d <= bestDistance) {
                            bestDistance = d
                            best = j
                        }
                    }
                }
                if (best >= 0) taken[best] = true
                best
            }
        }

        /** [points] resampled to [count] points evenly along their own length. */
        fun resampled(points: FloatArray, count: Int): FloatArray {
            val have = points.size / 2
            if (have == count) return points
            val along = FloatArray(have)
            for (i in 1 until have) {
                along[i] = along[i - 1] + hypot(points[i * 2] - points[i * 2 - 2], points[i * 2 + 1] - points[i * 2 - 1])
            }
            val total = along[have - 1]
            val out = FloatArray(count * 2)
            var segment = 0
            for (k in 0 until count) {
                val target = if (count <= 1) 0f else total * k / (count - 1)
                while (segment < have - 2 && along[segment + 1] < target) segment++
                val span = along[segment + 1] - along[segment]
                val f = if (span > 0f) ((target - along[segment]) / span).coerceIn(0f, 1f) else 0f
                val next = (segment + 1) * 2
                out[k * 2] = lerp(points[segment * 2], points[next], f)
                out[k * 2 + 1] = lerp(points[segment * 2 + 1], points[next + 1], f)
            }
            return out
        }

        fun reversed(points: FloatArray): FloatArray {
            val count = points.size / 2
            return FloatArray(points.size) { i -> points[(count - 1 - i / 2) * 2 + i % 2] }
        }

        fun middle(points: FloatArray): FloatArray {
            val m = points.size / 2 / 2
            return floatArrayOf(points[m * 2], points[m * 2 + 1])
        }

        fun distance(p: FloatArray, i: Int, q: FloatArray, j: Int): Float =
            hypot(p[i * 2] - q[j * 2], p[i * 2 + 1] - q[j * 2 + 1])

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

        fun mix(from: Int, to: Int, t: Float): Int = if (from == to) from else LinearGradientGenerator.lerpArgb(from, to, t)

        /** Half a `Long` and its low mask, so a grid cell's two coordinates cannot collide in its key. */
        const val Half = 32
        const val Low = 0xFFFFFFFFL
    }
}
