package inkspire.morphic.core.designsystem.container

import inkspire.morphic.core.model.IconArrangement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where one icon sits inside an icon container — a box in **px, relative to the container's top-left**.
 *
 * **It is the box the icon fills, with the gap already taken out of it**, and that is worth stating because the
 * arrangements did not agree on it. The tiling shapes — grid and fan — returned *cells* that met edge to edge, so
 * their icons touched; the circle returned the icon's own size, spaced by construction. One meaning now, applied in
 * one place: see [slots]'s `gap`.
 *
 * Px rather than dp because the caller measures its own cell in px (`BoxWithConstraints`) and every value here is a
 * fraction of that measurement; converting to dp and back would only add two rounding steps to arithmetic that is
 * already density-independent by construction.
 */
data class ArrangementSlot(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Lays [count] icons out inside a [width] × [height] px box, the way this [IconArrangement] says to — the whole of
 * an icon container's inner layout, and **pure**, so it is unit-tested rather than eyeballed on a device.
 *
 * **One exhaustive function, not an interface and a registry.** An `Arrangement` interface with seven
 * implementations and an `arrangementFor(type)` mapping is one implementation per enum value, with nothing outside
 * that file ever implementing it. That is the wrong-interface-abstraction smell
 * `GridBlueprint` already had removed: it buys extensibility nobody can use (the enum is closed, so a new
 * arrangement is a new enum value either way) and costs a registry that can fall out of step with the enum it maps.
 * A `when` cannot: a new [IconArrangement] value **fails to compile** here until it says how it lays out.
 *
 * The shapes are the feature. What is *not* repeated is the degenerate-input guard, hoisted here so every
 * arrangement answers an
 * empty container, a zero-width cell and a negative count the same way rather than each remembering to.
 *
 * @param count how many icons to place — every one gets a slot, however cramped; a container's *footprint* is what
 *   bounds how many are legible, and that is the grid's business rather than this function's.
 * @param gap px between neighbouring icons. **Taken off here rather than by each shape**, so a new arrangement
 *   cannot forget it and the four that exist did not each have to learn it: every slot is inset by half of this, so
 *   two that met edge to edge now sit exactly [gap] apart. Zero — the default — is the old behavior, and is what the
 *   shape tests assert against.
 */
fun IconArrangement.slots(
    count: Int,
    width: Float,
    height: Float,
    gap: Float = 0f,
): List<ArrangementSlot> {
    if (count <= 0 || width <= 0f || height <= 0f) return emptyList()
    val placed = when (this) {
        IconArrangement.GRID -> gridSlots(count, width, height)
        IconArrangement.CIRCLE -> circleSlots(count, width, height)
        IconArrangement.FAN_TOP_LEFT -> fanSlots(count, width, height, fromLeft = true, fromTop = true)
        IconArrangement.FAN_TOP_RIGHT -> fanSlots(count, width, height, fromLeft = false, fromTop = true)
        IconArrangement.FAN_BOTTOM_LEFT -> fanSlots(count, width, height, fromLeft = true, fromTop = false)
        IconArrangement.FAN_BOTTOM_RIGHT -> fanSlots(count, width, height, fromLeft = false, fromTop = false)
        IconArrangement.BEEHIVE -> beehiveSlots(count, width, height)
    }
    return if (gap > 0f) placed.map { it.deflated(gap / 2f) } else placed
}

/**
 * This slot inset by [by] on every side.
 *
 * **Capped at a quarter of the slot**, so a fixed gap in a container packed with icons shrinks them rather than
 * consuming them: without it a slot narrower than the gap would come out at zero and draw nothing at all, which is a
 * worse answer than drawing them small. The same instinct as `resolveIconSize`'s lower guardrail being coerced back
 * to the space that exists.
 */
private fun ArrangementSlot.deflated(by: Float): ArrangementSlot {
    val inset = by.coerceAtMost(minOf(width, height) / 4f)
    return ArrangementSlot(
        x = x + inset,
        y = y + inset,
        width = width - inset * 2f,
        height = height - inset * 2f,
    )
}

/**
 * Rows and columns filling the box completely, with the column count chosen so the cells come out roughly square:
 * `sqrt(count × aspect)` is the column count at which `cols / rows` matches the box's own ratio.
 *
 * Capped at [count] so a wide box holding two icons is two columns rather than three with a hole in it.
 */
private fun gridSlots(count: Int, width: Float, height: Float): List<ArrangementSlot> {
    val aspect = width / height
    val cols = max(1, sqrt(count * aspect).roundToInt()).coerceAtMost(count)
    val rows = ceil(count.toFloat() / cols).toInt()
    val cellW = width / cols
    val cellH = height / rows
    return List(count) { i ->
        ArrangementSlot(x = (i % cols) * cellW, y = (i / cols) * cellH, width = cellW, height = cellH)
    }
}

/**
 * Icons evenly spaced round a ring, starting at twelve o'clock and going clockwise.
 *
 * The icon size is the smaller of a fixed share of the radius and 90% of the **chord** between neighbors, which is
 * what keeps a crowded ring from overlapping itself: the chord shrinks as the count grows, so the icons do too. A
 * single icon has no neighbor and therefore no chord, so it is centered at its own size instead.
 */
private fun circleSlots(count: Int, width: Float, height: Float): List<ArrangementSlot> {
    val cx = width / 2f
    val cy = height / 2f
    val maxR = minOf(width, height) / 2f
    if (count == 1) {
        val size = maxR * 1.1f
        return listOf(ArrangementSlot(cx - size / 2f, cy - size / 2f, size, size))
    }
    val ringR = maxR * 0.62f
    val chord = 2f * ringR * sin(PI.toFloat() / count)
    val size = minOf(maxR * 0.7f, chord * 0.9f)
    return List(count) { i ->
        val angle = -PI.toFloat() / 2f + 2f * PI.toFloat() * i / count
        ArrangementSlot(
            x = cx + ringR * cos(angle) - size / 2f,
            y = cy + ringR * sin(angle) - size / 2f,
            width = size,
            height = size,
        )
    }
}

/**
 * A corner-anchored diagonal cascade: one icon sits in the chosen corner and the rest fan inward along successive
 * anti-diagonals, making a triangular spread.
 *
 * `g` is the smallest triangle that holds them all — the least `g` with `g(g+1)/2 ≥ count` — and the cell is a
 * square of `min(width, height) / g`, so the fan stays square in a non-square box rather than shearing.
 *
 * [fromLeft] and [fromTop] are the chosen corner, decomposed. They are two booleans rather than a `FanCorner` enum
 * because [IconArrangement]'s own four `FAN_*` values already *are* that enum — a second one parallel
 * to it would be a taxonomy to keep in step, and only the `when` above can reach this.
 */
private fun fanSlots(
    count: Int,
    width: Float,
    height: Float,
    fromLeft: Boolean,
    fromTop: Boolean,
): List<ArrangementSlot> {
    var g = 1
    while (g * (g + 1) / 2 < count) g++
    val cell = minOf(width, height) / g

    val cells = ArrayList<Pair<Int, Int>>(count)
    var diagonal = 0
    while (cells.size < count) {
        var r = 0
        while (r <= diagonal && cells.size < count) {
            val c = diagonal - r
            if (r < g && c < g) cells.add(r to c)
            r++
        }
        diagonal++
    }

    return cells.map { (r, c) ->
        ArrangementSlot(
            x = if (fromLeft) c * cell else width - (c + 1) * cell,
            y = if (fromTop) r * cell else height - (r + 1) * cell,
            width = cell,
            height = cell,
        )
    }
}

/** The six directions round a hex ring, in axial (q, r) coordinates — one step each, in order. */
private val BeehiveDirections = arrayOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

/**
 * Icons packed as a honeycomb: one in the middle, then complete hexagonal rings outward.
 *
 * Coordinates are generated in axial (q, r) form — walk ring `k` by starting at `(-k, k)` and taking `k` steps in
 * each of the six [BeehiveDirections] — then converted to pixel centers by the standard pointy-top hex projection.
 * The whole cloud is then scaled to fit the box, which is what makes the result independent of how many rings it
 * took: eight icons and eighty both fill the container, at different icon sizes.
 */
private fun beehiveSlots(count: Int, width: Float, height: Float): List<ArrangementSlot> {
    val coords = ArrayList<Pair<Int, Int>>(count)
    coords.add(0 to 0)
    var k = 1
    while (coords.size < count) {
        var q = -k
        var r = k
        for (direction in BeehiveDirections) {
            repeat(k) {
                if (coords.size < count) {
                    coords.add(q to r)
                    q += direction.first
                    r += direction.second
                }
            }
        }
        k++
    }

    val sqrt3 = sqrt(3f)
    val centers = coords.map { (q, r) -> (1.5f * q) to (sqrt3 * r + sqrt3 / 2f * q) }
    var maxAbsX = 0f
    var maxAbsY = 0f
    centers.forEach { (x, y) ->
        maxAbsX = maxOf(maxAbsX, abs(x))
        maxAbsY = maxOf(maxAbsY, abs(y))
    }

    // Scale so the outermost center plus half an icon lands exactly on the box edge, on whichever axis binds first.
    val iconUnit = sqrt3 * 0.8f
    val scale = minOf(
        (width / 2f) / (maxAbsX + iconUnit / 2f),
        (height / 2f) / (maxAbsY + iconUnit / 2f),
    )
    val iconPx = iconUnit * scale
    val cx = width / 2f
    val cy = height / 2f
    return centers.map { (x, y) ->
        ArrangementSlot(
            x = cx + x * scale - iconPx / 2f,
            y = cy + y * scale - iconPx / 2f,
            width = iconPx,
            height = iconPx,
        )
    }
}
