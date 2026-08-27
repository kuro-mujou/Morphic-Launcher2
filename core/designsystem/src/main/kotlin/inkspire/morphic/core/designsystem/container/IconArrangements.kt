package inkspire.morphic.core.designsystem.container

import inkspire.morphic.core.model.FanAnchor
import inkspire.morphic.core.model.GridFill
import inkspire.morphic.core.model.HexOrientation
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
 * **One exhaustive function, not an interface and a registry.** An `Arrangement` interface with four
 * implementations and an `arrangementFor(shape)` mapping is one implementation per shape, with nothing outside that
 * file ever implementing it. That is the wrong-interface-abstraction smell `GridBlueprint` already had removed: it
 * buys extensibility nobody can use (the type is sealed, so a new arrangement is a new subtype either way) and
 * costs a registry that can fall out of step with the type it maps. A `when` cannot: a new [IconArrangement]
 * **fails to compile** here until it says how it lays out.
 *
 * Each arm reads the parameters its own shape carries, which is the other half of what sealing bought — a fan's
 * anchor is reachable only in the arm that draws arcs.
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
        is IconArrangement.Grid -> gridSlots(count, width, height, fill)
        IconArrangement.Circle -> circleSlots(count, width, height)
        is IconArrangement.Beehive -> beehiveSlots(count, width, height, orientation)
        is IconArrangement.Fan -> fanSlots(count, width, height, anchor)
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
 * Rows and columns of **square** cells, the block centered in the box, filled the way [fill] says.
 *
 * **Square, which means the block does not fill the box** — the cell is the smaller of what each axis affords, and
 * the slack is split as a margin. A cell that took `width / cols` by `height / rows` instead would put the icons on
 * a lattice whose horizontal and vertical pitch differ, so a container that is not square would space its icons
 * further apart across than down. That reads as a mistake rather than as a choice, and it is a mistake the eye
 * catches before it can name.
 *
 * **The fill runs *along* the pinned axis**, which is the one thing to keep hold of here: pinned columns fill left
 * to right and wrap downward, pinned rows fill top to bottom and wrap to the right, and [GridFill.Auto] — which
 * pins nothing — reads across like a page. Said the other way: the list always advances along the axis the user
 * fixed, and the container grows along the axis they left free.
 *
 * That is not a matter of taste. With the fill along the pinned axis, an icon's cell is `(i % rows, i / rows)` or
 * `(i / cols, i % cols)` — a function of its index alone — so **adding an icon lands it at the growing edge and
 * moves nothing already placed**. Filling across a *pinned row count* instead makes the wrap depend on the total:
 * ten icons in three rows wrap at four, thirteen wrap at five, and the fifth icon jumps a whole row on the add.
 * On a surface that is reordered by dragging onto a position, an arrangement that re-flows under the finger is
 * worse than one that has to be read down a column.
 */
private fun gridSlots(count: Int, width: Float, height: Float, fill: GridFill): List<ArrangementSlot> = when (fill) {
    GridFill.Auto -> rowMajorSlots(count, width, height, autoColumns(count, width, height), centerShortRow = true)
    is GridFill.Columns -> rowMajorSlots(count, width, height, max(1, fill.count), centerShortRow = false)
    is GridFill.Rows -> columnMajorSlots(count, width, height, max(1, fill.count))
}

/**
 * The column count [GridFill.Auto] derives from the box.
 *
 * `sqrt(count × aspect)` is the column count at which `cols / rows` matches the box's own ratio, so a resized
 * container keeps its block roughly the box's shape. It is capped at [count], so a wide box holding two icons is
 * two columns rather than three with a hole in it — a cap a *pinned* count deliberately does not get, since three
 * columns asked for is three columns of width whatever is in them.
 */
private fun autoColumns(count: Int, width: Float, height: Float): Int =
    max(1, sqrt(count * (width / height)).roundToInt()).coerceAtMost(count)

/**
 * Left to right, then down — [cols] wide, with the rows following from the count.
 *
 * **[centerShortRow] is what tells a derived block from a pinned one.** A trailing row of two under a row of five
 * is a shape, and hanging it off the left edge makes it look like an unfinished row instead — so [GridFill.Auto]
 * centers it. A pinned column count is not a shape being composed, it is a frame being filled, and there the
 * centering is what makes the tail look wrong: it lands the last icons half a cell off the columns above, and it
 * *moves* them when the next icon arrives. Flush is what makes the next slot the one the eye expects.
 */
private fun rowMajorSlots(
    count: Int,
    width: Float,
    height: Float,
    cols: Int,
    centerShortRow: Boolean,
): List<ArrangementSlot> {
    val rows = ceil(count.toFloat() / cols).toInt()
    val cell = minOf(width / cols, height / rows)
    val originX = (width - cols * cell) / 2f
    val originY = (height - rows * cell) / 2f
    return List(count) { i ->
        val row = i / cols
        val col = i % cols
        // Only the final row can be short, and it holds whatever the full rows above it did not take.
        val inThisRow = if (row == rows - 1) count - row * cols else cols
        val indent = if (centerShortRow) (cols - inThisRow) * cell / 2f else 0f
        ArrangementSlot(
            x = originX + indent + col * cell,
            y = originY + row * cell,
            width = cell,
            height = cell,
        )
    }
}

/**
 * Top to bottom, then rightward — [rows] tall, with the columns following from the count. The pinned-rows fill.
 *
 * **The height is divided by the pinned count, not by the rows in use**, so a container told to keep three rows
 * divides by three from its first icon: sizing against what is filled would shrink every icon on each add until
 * the container filled up, which is the opposite of what pinning an axis is for. The icons that *do* exist are
 * still centered on what they occupy, which only differs while the first column is filling.
 *
 * The trailing column is flush at the top rather than centered, for [rowMajorSlots]'s reason one axis over: it is
 * the growing edge, and the next icon has to land where the eye is already looking.
 */
private fun columnMajorSlots(count: Int, width: Float, height: Float, rows: Int): List<ArrangementSlot> {
    val cols = ceil(count.toFloat() / rows).toInt()
    val usedRows = minOf(rows, count)
    val cell = minOf(width / cols, height / rows)
    val originX = (width - cols * cell) / 2f
    val originY = (height - usedRows * cell) / 2f
    return List(count) { i ->
        ArrangementSlot(
            x = originX + (i / rows) * cell,
            y = originY + (i % rows) * cell,
            width = cell,
            height = cell,
        )
    }
}

/**
 * Icons evenly spaced round a ring, starting at twelve o'clock and going clockwise.
 *
 * **The ring grows with the count; the icons only shrink once it cannot.** Neighbours sit one pitch apart along the
 * chord, so the radius that seats [count] of them is `pitch / (2·sin(π/count))` — small for three icons, which is
 * what makes three read as a tight cluster rather than as three lonely points on a large circle. A ring is worth
 * having at whatever size the contents ask for; a fixed radius makes the *icons* carry the whole adjustment, and
 * they are the part the user is trying to look at.
 *
 * Past some count that radius no longer fits, and then the two constraints have to be solved together: the ring
 * must reach `maxR` less half an icon, *and* the chord must still be a pitch. Substituting one into the other gives
 * the closed form below — there is no iteration and no arbitrary cap, which matters because this runs per frame
 * while a container is being resized.
 *
 * A single icon has no neighbour and therefore no chord, so it is centered at its own size instead.
 */
private fun circleSlots(count: Int, width: Float, height: Float): List<ArrangementSlot> {
    val cx = width / 2f
    val cy = height / 2f
    val maxR = minOf(width, height) / 2f
    if (count == 1) {
        val size = maxR * 1.1f
        return listOf(ArrangementSlot(cx - size / 2f, cy - size / 2f, size, size))
    }
    // An icon's share of the pitch: neighbours a pitch apart leave a tenth of it as the gap between them.
    val fill = 0.9f
    val sinStep = sin(PI.toFloat() / count)
    val idealSize = maxR * 0.7f
    // The ring that seats `count` icons of `idealSize`, and the largest ring that still keeps one inside the box.
    val idealR = idealSize / fill / (2f * sinStep)
    val outermostR = maxR - idealSize / 2f
    val ringR: Float
    val size: Float
    if (idealR <= outermostR) {
        ringR = idealR
        size = idealSize
    } else {
        // Solving `size / fill = 2·sin·(maxR − size/2)` for size — the ring is against the wall, so the icons give.
        size = 2f * sinStep * maxR / (1f / fill + sinStep)
        ringR = maxR - size / 2f
    }
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
 * A corner-anchored fan: icons on **concentric quarter-arcs** sweeping out of the chosen corner, each arc holding
 * as many as its length affords.
 *
 * **The arcs are what the name promises, and the spacing is what makes them read as arcs.** Ring `k` sits at radius
 * `k − 0.5` and takes as many icons as fit along its quarter-circumference at the same pitch that separates one
 * ring from the next, so the density is uniform in polar terms. Spacing the rings and the icons independently is
 * what turns a fan back into a grid that happens to be bent — the tangential step has to track the radial one.
 *
 * The last arc is usually partial and is **centered on its sweep**, for the reason [gridSlots] centers its last
 * row: a short arc hung off one end reads as unfinished rather than as the shape ending.
 *
 * **The pivot is half an icon in from the corner, and the scale accounts for that on both ends.** Icons at the two
 * extremes of a sweep have their centers on the box's own edges, so anchoring the pivot *on* the corner puts half
 * of every arc's first and last icon outside the container. Solving `short = icon + maxR × scale` places the
 * innermost and outermost icons flush instead. This is why the whole-cloud scale-to-fit [beehiveSlots] uses does
 * not transfer unchanged: that cloud is centered and symmetric, and this one is anchored in a corner.
 *
 * **[anchor] decides the sweep, and that is why there is no second setting for it.** A corner has a quarter circle
 * to fill and an edge has a half, so "a corner with a 180° sweep" cannot be expressed and there is no pair of
 * controls to keep in step. The arc arithmetic is the same either way — an edge fan simply seats about twice as
 * many icons per ring, because a ring twice as long holds twice as many at one tangential pitch.
 *
 * The resolution lives here rather than on [FanAnchor] because a pivot fraction and a signed angle are this
 * function's arithmetic, not something the model should know. Exhaustive, so a new anchor cannot be added without
 * saying where it sits and what it sweeps.
 */
private fun fanSlots(
    count: Int,
    width: Float,
    height: Float,
    anchor: FanAnchor,
): List<ArrangementSlot> {
    val pivot = anchor.pivot()
    val span = abs(pivot.sweep)
    // Radius and capacity of each arc, outward until they hold them all. Unit radial pitch, so a capacity is just
    // how many unit steps fit along the arc, and the whole cloud is scaled to the box at the end.
    val arcs = ArrayList<Pair<Float, Int>>()
    var seated = 0
    var k = 1
    while (seated < count) {
        val r = k - 0.5f
        val capacity = max(1, (span * r).toInt() + 1)
        arcs.add(r to capacity)
        seated += capacity
        k++
    }

    val middle = pivot.start + pivot.sweep / 2f
    val polar = ArrayList<Pair<Float, Float>>(count)
    var remaining = count
    for ((r, capacity) in arcs) {
        val here = minOf(capacity, remaining)
        if (capacity == 1) {
            polar.add(r to middle)
        } else {
            // The step a *full* arc would use, so a partial last arc keeps the spacing of the ones inside it
            // rather than spreading to fill a sweep it cannot. Signed, so it fills in the anchor's own direction.
            val step = pivot.sweep / (capacity - 1)
            val first = middle - (here - 1) * step / 2f
            repeat(here) { i -> polar.add(r to (first + i * step)) }
        }
        remaining -= here
        if (remaining == 0) break
    }

    val maxR = polar.maxOf { it.first }
    val iconUnit = 0.8f
    // Each axis has to hold the icon plus the cloud's reach along it, and the reach is *two* radii where the pivot
    // is centered on that axis — an edge fan spills both ways from its anchor where a corner fan spills one.
    val scale = minOf(
        width / (iconUnit + pivot.x.spread * maxR),
        height / (iconUnit + pivot.y.spread * maxR),
    )
    val icon = iconUnit * scale
    val originX = pivot.x.along(width, icon)
    val originY = pivot.y.along(height, icon)
    return polar.map { (r, angle) ->
        ArrangementSlot(
            x = originX + r * scale * cos(angle) - icon / 2f,
            y = originY + r * scale * sin(angle) - icon / 2f,
            width = icon,
            height = icon,
        )
    }
}

/** Where a fan's pivot sits along one axis. [FanPivot] pairs two of these with the arc swept from them. */
private enum class PivotAt {
    LOW,
    CENTER,
    HIGH,
    ;

    /** How many radii the cloud reaches along this axis — both ways from a centered pivot, one way from an edge. */
    val spread: Float get() = if (this == CENTER) 2f else 1f

    /** The pivot's own coordinate, half an icon in from the edge it sits on. */
    fun along(extent: Float, icon: Float): Float = when (this) {
        LOW -> icon / 2f
        CENTER -> extent / 2f
        HIGH -> extent - icon / 2f
    }
}

/**
 * An anchor as geometry: where it pivots, and the arc it sweeps from there.
 *
 * Angles are screen convention — `0` points right and they grow *clockwise*, because y grows downward. [sweep] is
 * **signed**, and its sign is the order the icons fill in rather than anything about the shape: the arc it covers
 * is the same either way.
 */
private data class FanPivot(val x: PivotAt, val y: PivotAt, val start: Float, val sweep: Float)

/**
 * The eight anchors, as the arcs they mean.
 *
 * Every sweep is **centered on the inward normal** — the direction that points into the container from where the
 * anchor sits — and is a quarter circle wide at a corner, a half circle at an edge. That is the whole difference
 * between the two families, and it is why the kind of anchor implies the sweep rather than a second control
 * offering "a corner with a half sweep", which is not a shape this makes.
 *
 * The **direction** each one fills in is the other half of the table, and it is a convention rather than a
 * consequence. A corner starts on its *horizontal* edge and sweeps round to its vertical one, which is the order
 * the four corners have always filled in; an edge starts at its top or left end, which is where the eye enters.
 */
private fun FanAnchor.pivot(): FanPivot {
    val quarter = PI.toFloat() / 2f
    return when (this) {
        FanAnchor.TOP_LEFT -> FanPivot(PivotAt.LOW, PivotAt.LOW, start = 0f, sweep = quarter)
        FanAnchor.TOP -> FanPivot(PivotAt.CENTER, PivotAt.LOW, start = 2f * quarter, sweep = -2f * quarter)
        FanAnchor.TOP_RIGHT -> FanPivot(PivotAt.HIGH, PivotAt.LOW, start = 2f * quarter, sweep = -quarter)
        FanAnchor.RIGHT -> FanPivot(PivotAt.HIGH, PivotAt.CENTER, start = 3f * quarter, sweep = -2f * quarter)
        FanAnchor.BOTTOM_RIGHT -> FanPivot(PivotAt.HIGH, PivotAt.HIGH, start = 2f * quarter, sweep = quarter)
        FanAnchor.BOTTOM -> FanPivot(PivotAt.CENTER, PivotAt.HIGH, start = 2f * quarter, sweep = 2f * quarter)
        FanAnchor.BOTTOM_LEFT -> FanPivot(PivotAt.LOW, PivotAt.HIGH, start = 0f, sweep = -quarter)
        FanAnchor.LEFT -> FanPivot(PivotAt.LOW, PivotAt.CENTER, start = 3f * quarter, sweep = 2f * quarter)
    }
}

/** The six directions round a hex ring, in axial (q, r) coordinates — one step each, in order. */
private val BeehiveDirections = arrayOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

/**
 * Icons packed as a honeycomb: one in the middle, then complete hexagonal rings outward.
 *
 * Coordinates are generated in axial (q, r) form — walk ring `k` by starting at `(-k, k)` and taking `k` steps in
 * each of the six [BeehiveDirections] — then converted to pixel centers by the standard hex projection for
 * [orientation]. The whole cloud is then scaled to fit the box, which is what makes the result independent of how
 * many rings it took: eight icons and eighty both fill the container, at different icon sizes.
 *
 * **The two projections differ by which axis carries the three-halves step**, which is the 30° turn expressed as
 * arithmetic rather than as a rotation: a flat-top cell has a neighbour directly above and below it, a pointy-top
 * one has a neighbour directly left and right. Everything after this — the pitch, the icon's share of it, the
 * scale-to-fit — is the same for both, since a hex lattice is a hex lattice whichever way it is turned.
 */
private fun beehiveSlots(
    count: Int,
    width: Float,
    height: Float,
    orientation: HexOrientation,
): List<ArrangementSlot> {
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
    val centers = coords.map { (q, r) ->
        when (orientation) {
            HexOrientation.FLAT_TOP -> (1.5f * q) to (sqrt3 * r + sqrt3 / 2f * q)
            HexOrientation.POINTY_TOP -> (sqrt3 * q + sqrt3 / 2f * r) to (1.5f * r)
        }
    }
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
