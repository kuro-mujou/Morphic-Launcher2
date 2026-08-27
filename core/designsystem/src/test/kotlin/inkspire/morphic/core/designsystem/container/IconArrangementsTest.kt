package inkspire.morphic.core.designsystem.container

import inkspire.morphic.core.model.FanAnchor
import inkspire.morphic.core.model.GridFill
import inkspire.morphic.core.model.IconArrangement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The icon-container layout maths, checked without a device — which is the point of it being a pure function over
 * numbers rather than a composable that measures itself.
 *
 * The tests are deliberately about the **properties** every arrangement must hold (one slot per icon, inside the box,
 * no overlap where the shape promises none) rather than about exact coordinates. Pinning the coordinates would pin
 * L1's constants, and those are taste — the shapes are allowed to be retuned, the invariants are not.
 */
class IconArrangementsTest {

    private val width = 300f
    private val height = 200f

    /**
     * Every arrangement, so the shared invariants cover each of them.
     *
     * **Spelled out, and that is the price of the shapes carrying their own parameters**: `entries` used to enroll a
     * new value here the moment it was declared, and a sealed type has no such list. A shape added without a line
     * here is checked by nothing, which is why the exhaustive `when`s in `slots` and `swatchCount` are what actually
     * force a new shape to be finished — this list is a reminder, not a guard.
     */
    private val all = listOf<IconArrangement>(
        IconArrangement.Grid(),
        IconArrangement.Grid(GridFill.Columns(3)),
        IconArrangement.Grid(GridFill.Rows(3)),
        IconArrangement.Circle,
        IconArrangement.Beehive,
        IconArrangement.Fan(FanAnchor.TOP_LEFT),
        IconArrangement.Fan(FanAnchor.TOP_RIGHT),
        IconArrangement.Fan(FanAnchor.BOTTOM_LEFT),
        IconArrangement.Fan(FanAnchor.BOTTOM_RIGHT),
    )

    // ── Shared invariants ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every arrangement gives one slot per icon`() {
        for (arrangement in all) {
            for (count in 1..12) {
                assertEquals(
                    "$arrangement with $count icons",
                    count,
                    arrangement.slots(count, width, height).size,
                )
            }
        }
    }

    @Test
    fun `every arrangement keeps its slots inside the container`() {
        for (arrangement in all) {
            for (count in 1..12) {
                for (slot in arrangement.slots(count, width, height)) {
                    val message = "$arrangement with $count icons: $slot"
                    // A hair of tolerance, because these are floats scaled to land *on* the edge.
                    assertTrue(message, slot.x >= -0.01f && slot.y >= -0.01f)
                    assertTrue(message, slot.x + slot.width <= width + 0.01f)
                    assertTrue(message, slot.y + slot.height <= height + 0.01f)
                }
            }
        }
    }

    @Test
    fun `every arrangement gives every icon a positive size`() {
        for (arrangement in all) {
            for (count in 1..12) {
                for (slot in arrangement.slots(count, width, height)) {
                    assertTrue("$arrangement with $count icons: $slot", slot.width > 0f && slot.height > 0f)
                }
            }
        }
    }

    /**
     * Degenerate input is answered identically by every shape, which is what hoisting the guard out of the four
     * bodies bought — L1 repeated it in each and so could have lost it from one.
     */
    @Test
    fun `every arrangement is empty for a degenerate box or count`() {
        for (arrangement in all) {
            assertTrue("$arrangement, zero icons", arrangement.slots(0, width, height).isEmpty())
            assertTrue("$arrangement, negative icons", arrangement.slots(-1, width, height).isEmpty())
            assertTrue("$arrangement, zero width", arrangement.slots(4, 0f, height).isEmpty())
            assertTrue("$arrangement, zero height", arrangement.slots(4, width, 0f).isEmpty())
        }
    }

    // ── Grid ─────────────────────────────────────────────────────────────────────────────────────────────

    /** The grid is the one arrangement that tiles: its slots must cover the box exactly and never overlap. */
    @Test
    fun `grid tiles the box without gaps or overlap`() {
        val slots = IconArrangement.Grid().slots(6, width, height)
        // 6 icons in a 3:2 box → 3 columns × 2 rows, each cell a third by a half.
        assertEquals(3, slots.map { it.x }.distinct().size)
        assertEquals(2, slots.map { it.y }.distinct().size)
        assertEquals(width / 3f, slots[0].width, 0.01f)
        assertEquals(height / 2f, slots[0].height, 0.01f)
        assertNoOverlap(slots)
    }

    /** A count below the natural column width must not leave holes: two icons in a wide box are two columns. */
    @Test
    fun `grid never has more columns than icons`() {
        val slots = IconArrangement.Grid().slots(2, 1000f, 100f)
        assertEquals(2, slots.size)
        assertEquals(1, slots.map { it.y }.distinct().size)
        assertNoOverlap(slots)
    }

    /**
     * The cell is square in a box that does *not* divide into square cells — which `width`/`height` at six icons
     * happens to do, so the rule needs a box that would expose a stretched cell. A shape whose horizontal and
     * vertical pitch differ reads as a mistake, and the block giving up the slack instead is the fix.
     */
    @Test
    fun `grid cells stay square in a box that does not divide evenly`() {
        for (slot in IconArrangement.Grid().slots(6, 400f, 150f)) {
            assertEquals(slot.width, slot.height, 0.01f)
        }
    }

    /** A short last row is centered under the full rows, not hung off the left edge. */
    @Test
    fun `grid centers a short last row`() {
        // 5 icons in a 3:2 box → 3 columns, so the last row holds 2 and must sit half a cell in.
        val slots = IconArrangement.Grid().slots(5, width, height)
        val topRow = slots.take(3)
        val lastRow = slots.drop(3)
        assertEquals(2, lastRow.size)
        val topCenter = (topRow.first().centerX + topRow.last().centerX) / 2f
        val lastCenter = (lastRow.first().centerX + lastRow.last().centerX) / 2f
        assertEquals("the short row should share the block's center line", topCenter, lastCenter, 0.01f)
    }

    /** The whole block is centered in the box, so the margin it leaves is even on both sides. */
    @Test
    fun `grid centers its block in the box`() {
        val slots = IconArrangement.Grid().slots(6, 400f, 150f)
        val left = slots.minOf { it.x }
        val right = 400f - slots.maxOf { it.x + it.width }
        assertEquals(left, right, 0.01f)
    }

    @Test
    fun `grid rows fill downward in reading order`() {
        val slots = IconArrangement.Grid().slots(6, width, height)
        // Item 3 opens the second row: back to the left edge, one row down.
        assertEquals(slots[0].x, slots[3].x, 0.01f)
        assertTrue(slots[3].y > slots[0].y)
    }

    // ── The grid's fill axis ─────────────────────────────────────────────────────────────────────────────

/**
     * A pinned column count is the wrap, whatever the box would have derived.
     *
     * Counted as **how many share the first row** rather than as distinct x values, which is not the same number: a
     * short last row is centered, so its icons sit half a cell off the columns above and count as their own. That
     * is the rule slice A put in, and it is worth stating here because the naive assertion passes by luck at some
     * counts and not others.
     */
    @Test
    fun `pinned columns wrap at exactly that many`() {
        val slots = IconArrangement.Grid(GridFill.Columns(3)).slots(7, width, height)
        assertEquals(3, slots.count { it.y == slots.first().y })
        // Seven icons at three columns is three rows: three, three, and a short one.
        assertEquals(3, slots.map { it.y }.distinct().size)
        assertNoOverlap(slots)
    }

    /** Pinned rows reach the same place from the other side: the columns are however many the list takes. */
    @Test
    fun `pinned rows wrap at the columns that count implies`() {
        val slots = IconArrangement.Grid(GridFill.Rows(2)).slots(7, width, height)
        assertEquals(2, slots.map { it.y }.distinct().size)
        // Four across the top, three centered under them.
        assertEquals(4, slots.count { it.y == slots.first().y })
        assertNoOverlap(slots)
    }

    /**
     * The acceptance test for the dock: **one row stays one row**, however many icons arrive.
     *
     * An unpinned grid answers a growing list by adding rows, which is exactly what a strip across the bottom of a
     * tablet must not do. Nothing else here says the icons may not shrink — they must, and do.
     */
    @Test
    fun `one pinned row stays one row as icons are added`() {
        for (count in 1..8) {
            val slots = IconArrangement.Grid(GridFill.Rows(1)).slots(count, 1000f, 120f)
            assertEquals("$count icons", 1, slots.map { it.y }.distinct().size)
            assertEquals("$count icons", count, slots.map { it.x }.distinct().size)
        }
    }

    /**
     * A pinned axis divides its side from the first icon, not from the current count.
     *
     * Sizing against the rows *in use* would shrink every icon on each add until the container filled up, which is
     * the opposite of what pinning is for: the point is that adding an icon does not resize the ones already there.
     */
    @Test
    fun `a pinned row count divides the height before the icons fill it`() {
        val three = IconArrangement.Grid(GridFill.Rows(3))
        val sizes = (1..3).map { three.slots(it, width, height).first().height }
        for (size in sizes) assertEquals("icons resized as the list grew: $sizes", sizes.first(), size, 0.01f)
        // And it is genuinely a third of the height rather than the whole of it.
        assertEquals(height / 3f, sizes.first(), 0.01f)
    }

    /** The same rule across: three columns asked for is three columns wide, with two icons centered in them. */
    @Test
    fun `a pinned column count is not capped by the icon count`() {
        val slots = IconArrangement.Grid(GridFill.Columns(4)).slots(2, width, height)
        assertEquals(width / 4f, slots.first().width, 0.01f)
        val blockCenter = (slots.first().centerX + slots.last().centerX) / 2f
        assertEquals("the short row should sit in the middle of the block", width / 2f, blockCenter, 0.01f)
    }

    /** Auto is what an unconfigured container has always had, so it must still derive from the box. */
    @Test
    fun `auto is the default and derives its columns from the box`() {
        val default = IconArrangement.Grid().slots(6, width, height)
        val auto = IconArrangement.Grid(GridFill.Auto).slots(6, width, height)
        assertEquals(auto, default)
        assertEquals(3, auto.map { it.x }.distinct().size)
    }

    // ── The gap ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The gap's whole job: two icons that met edge to edge must end up exactly that far apart.
     *
     * Asserted on the **grid** because it is the arrangement that tiles, so it is the one where "touching" was the
     * old behavior and where the distance between neighbours is a number rather than a chord.
     */
    @Test
    fun `gap separates neighbouring icons by exactly that much`() {
        val gap = 12f
        val plain = IconArrangement.Grid().slots(6, width, height)
        val spaced = IconArrangement.Grid().slots(6, width, height, gap = gap)

        // Columns 0 and 1 of the first row: touching before, `gap` apart after.
        assertEquals(plain[0].x + plain[0].width, plain[1].x, 0.01f)
        assertEquals(spaced[0].x + spaced[0].width + gap, spaced[1].x, 0.01f)
        // The slot loses the gap, and stays centered on the cell it came from.
        assertEquals(plain[0].width - gap, spaced[0].width, 0.01f)
        assertEquals(plain[0].x + plain[0].width / 2f, spaced[0].x + spaced[0].width / 2f, 0.01f)
    }

    /**
     * A gap wider than the slot must shrink the icons, never erase them — see `deflated`'s cap. Without it a packed
     * container would place slots of zero width and draw nothing at all.
     */
    @Test
    fun `a gap larger than the slot still leaves an icon`() {
        val slots = IconArrangement.Grid().slots(64, 100f, 100f, gap = 500f)
        assertEquals(64, slots.size)
        for (slot in slots) {
            assertTrue("width ${slot.width}", slot.width > 0f)
            assertTrue("height ${slot.height}", slot.height > 0f)
        }
    }

    /** Every arrangement honors it, because the inset is applied once rather than by each shape. */
    @Test
    fun `every arrangement shrinks its slots for a gap`() {
        for (arrangement in all) {
            val plain = arrangement.slots(6, width, height).first().width
            val spaced = arrangement.slots(6, width, height, gap = 10f).first().width
            assertTrue("$arrangement", spaced < plain)
        }
    }

    // ── Circle ───────────────────────────────────────────────────────────────────────────────────────────

    /** The chord rule is what stops a crowded ring overlapping itself, so it is the property worth pinning. */
    @Test
    fun `circle shrinks its icons as the ring fills`() {
        val few = IconArrangement.Circle.slots(3, width, height).first().width
        val many = IconArrangement.Circle.slots(12, width, height).first().width
        assertTrue("12 icons ($many) should be smaller than 3 ($few)", many < few)
    }

    /**
     * The counterpart of the test above, and the half that is new: the icons keep their **full size** while the
     * ring still has room to grow, and only start giving way once it is against the box. A fixed radius makes the
     * icons carry the whole adjustment from the second one onward, which is what this pins against.
     */
    @Test
    fun `circle keeps its icons full size until the ring is against the box`() {
        val three = IconArrangement.Circle.slots(3, width, height).first().width
        val four = IconArrangement.Circle.slots(4, width, height).first().width
        assertEquals("a fourth icon should widen the ring, not shrink the icons", three, four, 0.01f)
    }

    /** And the ring is what absorbs the count until then — three icons cluster, eight stand well out. */
    @Test
    fun `circle grows its ring with the count`() {
        fun ringRadius(count: Int): Float {
            val slot = IconArrangement.Circle.slots(count, width, height).first()
            return hypot(slot.centerX - width / 2f, slot.centerY - height / 2f)
        }
        assertTrue("3 (${ringRadius(3)}) should ring tighter than 8 (${ringRadius(8)})", ringRadius(3) < ringRadius(8))
    }

    @Test
    fun `circle centers a lone icon rather than putting it on the ring`() {
        val slot = IconArrangement.Circle.slots(1, width, height).single()
        assertEquals(width / 2f, slot.x + slot.width / 2f, 0.01f)
        assertEquals(height / 2f, slot.y + slot.height / 2f, 0.01f)
    }

    @Test
    fun `circle starts at twelve o'clock`() {
        val first = IconArrangement.Circle.slots(4, width, height).first()
        assertEquals(width / 2f, first.x + first.width / 2f, 0.01f)
        assertTrue("the first icon should be above center", first.y + first.height / 2f < height / 2f)
    }

    // ── Fans ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Each fan opens away from its own corner — the one thing that distinguishes the four values.
     *
     * The corner is the **pivot, not a slot**: the innermost arc stands half an icon clear of it, so this asks that
     * the first icon is nearer that corner than any of the other three rather than flush in it.
     */
    @Test
    fun `each fan opens away from its own corner`() {
        val pivots = mapOf(
            IconArrangement.Fan(FanAnchor.TOP_LEFT) to (0f to 0f),
            IconArrangement.Fan(FanAnchor.TOP_RIGHT) to (width to 0f),
            IconArrangement.Fan(FanAnchor.BOTTOM_LEFT) to (0f to height),
            IconArrangement.Fan(FanAnchor.BOTTOM_RIGHT) to (width to height),
        )
        val corners = pivots.values.toList()
        for ((arrangement, pivot) in pivots) {
            val slot = arrangement.slots(6, width, height).first()
            val own = hypot(slot.centerX - pivot.first, slot.centerY - pivot.second)
            for (other in corners - pivot) {
                val away = hypot(slot.centerX - other.first, slot.centerY - other.second)
                assertTrue("$arrangement: first icon is not nearest its own corner", own < away)
            }
        }
    }

    /** Square cells in a non-square box — the fan shears rather than keeps its shape if this is ever lost. */
    @Test
    fun `fan cells stay square`() {
        for (slot in IconArrangement.Fan(FanAnchor.TOP_LEFT).slots(6, width, height)) {
            assertEquals(slot.width, slot.height, 0.01f)
        }
    }

    /**
     * The fan's structure is **arcs at increasing radius**, so that is what is pinned rather than the coordinates.
     *
     * Deliberately not [assertNoOverlap]: that helper is for the arrangements that tile, and the fan clusters — its
     * icons are square boxes on a polar lattice, so two on neighbouring arcs can be a full pitch apart and still
     * share a corner's worth of area. `beehiveSlots` is tested the same way and for the same reason.
     */
    @Test
    fun `fan steps outward in arcs`() {
        val slots = IconArrangement.Fan(FanAnchor.TOP_LEFT).slots(9, width, height)
        // Measured from the **pivot**, which stands half an icon in from the corner. From the corner itself the
        // distance would also vary with the angle, and an icon at 45° would read as further out than one beside it
        // on the same arc.
        val pivot = slots[0].width / 2f
        val radii = slots.map { hypot(it.centerX - pivot, it.centerY - pivot) }
        for (i in 1 until radii.size) {
            assertTrue("radius fell at $i: $radii", radii[i] >= radii[i - 1] - 0.01f)
        }
        // Nine icons cannot fit on one arc, so the shape must actually be nested rather than a single sweep.
        assertTrue("expected more than one arc, got $radii", radii.distinctBy { (it * 100).toInt() }.size > 1)
    }

    /** Every icon one size, as the beehive does — the fan scales its whole cloud rather than each arc. */
    @Test
    fun `fan icons are all one size`() {
        val slots = IconArrangement.Fan(FanAnchor.TOP_LEFT).slots(9, width, height)
        for (slot in slots) assertEquals(slots[0].width, slot.width, 0.01f)
    }

    // ── Beehive ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `beehive centers its first icon`() {
        val slot = IconArrangement.Beehive.slots(7, width, height).first()
        assertEquals(width / 2f, slot.x + slot.width / 2f, 0.01f)
        assertEquals(height / 2f, slot.y + slot.height / 2f, 0.01f)
    }

    /**
     * The scale-to-fit is what makes the honeycomb independent of how many rings it took, so a bigger count must
     * come out smaller rather than spilling out of the box (which the containment test would also catch, less
     * informatively).
     */
    @Test
    fun `beehive shrinks its icons as rings are added`() {
        val oneRing = IconArrangement.Beehive.slots(7, width, height).first().width
        val twoRings = IconArrangement.Beehive.slots(19, width, height).first().width
        assertTrue("19 icons ($twoRings) should be smaller than 7 ($oneRing)", twoRings < oneRing)
    }

    @Test
    fun `beehive icons are square and all one size`() {
        val slots = IconArrangement.Beehive.slots(7, width, height)
        for (slot in slots) {
            assertEquals(slot.width, slot.height, 0.01f)
            assertEquals(slots[0].width, slot.width, 0.01f)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────────────

    private val ArrangementSlot.centerX: Float get() = x + width / 2f
    private val ArrangementSlot.centerY: Float get() = y + height / 2f

    /** Asserts no two slots share any area — for the arrangements that tile rather than deliberately cluster. */
    private fun assertNoOverlap(slots: List<ArrangementSlot>) {
        for (i in slots.indices) {
            for (j in i + 1 until slots.size) {
                val a = slots[i]
                val b = slots[j]
                val apart = a.x + a.width <= b.x + 0.01f || b.x + b.width <= a.x + 0.01f ||
                    a.y + a.height <= b.y + 0.01f || b.y + b.height <= a.y + 0.01f
                assertTrue("$a overlaps $b", apart)
            }
        }
    }
}
