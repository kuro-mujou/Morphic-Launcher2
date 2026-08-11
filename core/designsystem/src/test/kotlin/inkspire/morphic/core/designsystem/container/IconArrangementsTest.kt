package inkspire.morphic.core.designsystem.container

import inkspire.morphic.core.model.IconArrangement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    /** Every arrangement, so a new enum value is covered by the shared invariants the moment it is added. */
    private val all = IconArrangement.entries

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
     * Degenerate input is answered identically by all seven, which is what hoisting the guard out of the seven
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
        val slots = IconArrangement.GRID.slots(6, width, height)
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
        val slots = IconArrangement.GRID.slots(2, 1000f, 100f)
        assertEquals(2, slots.size)
        assertEquals(1, slots.map { it.y }.distinct().size)
        assertNoOverlap(slots)
    }

    @Test
    fun `grid rows fill downward in reading order`() {
        val slots = IconArrangement.GRID.slots(6, width, height)
        // Item 3 opens the second row: back to the left edge, one row down.
        assertEquals(slots[0].x, slots[3].x, 0.01f)
        assertTrue(slots[3].y > slots[0].y)
    }

    // ── Circle ───────────────────────────────────────────────────────────────────────────────────────────

    /** The chord rule is what stops a crowded ring overlapping itself, so it is the property worth pinning. */
    @Test
    fun `circle shrinks its icons as the ring fills`() {
        val few = IconArrangement.CIRCLE.slots(3, width, height).first().width
        val many = IconArrangement.CIRCLE.slots(12, width, height).first().width
        assertTrue("12 icons ($many) should be smaller than 3 ($few)", many < few)
    }

    @Test
    fun `circle centres a lone icon rather than putting it on the ring`() {
        val slot = IconArrangement.CIRCLE.slots(1, width, height).single()
        assertEquals(width / 2f, slot.x + slot.width / 2f, 0.01f)
        assertEquals(height / 2f, slot.y + slot.height / 2f, 0.01f)
    }

    @Test
    fun `circle starts at twelve o'clock`() {
        val first = IconArrangement.CIRCLE.slots(4, width, height).first()
        assertEquals(width / 2f, first.x + first.width / 2f, 0.01f)
        assertTrue("the first icon should be above centre", first.y + first.height / 2f < height / 2f)
    }

    // ── Fans ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Each fan anchors its first icon in its own corner — the one thing that distinguishes the four values. */
    @Test
    fun `each fan pivots on its own corner`() {
        val pivots = mapOf(
            IconArrangement.FAN_TOP_LEFT to (true to true),
            IconArrangement.FAN_TOP_RIGHT to (false to true),
            IconArrangement.FAN_BOTTOM_LEFT to (true to false),
            IconArrangement.FAN_BOTTOM_RIGHT to (false to false),
        )
        for ((arrangement, corner) in pivots) {
            val (left, top) = corner
            val slot = arrangement.slots(6, width, height).first()
            if (left) assertEquals("$arrangement", 0f, slot.x, 0.01f)
            else assertEquals("$arrangement", width, slot.x + slot.width, 0.01f)
            if (top) assertEquals("$arrangement", 0f, slot.y, 0.01f)
            else assertEquals("$arrangement", height, slot.y + slot.height, 0.01f)
        }
    }

    /** Square cells in a non-square box — the fan shears rather than stays triangular if this is ever lost. */
    @Test
    fun `fan cells stay square`() {
        for (slot in IconArrangement.FAN_TOP_LEFT.slots(6, width, height)) {
            assertEquals(slot.width, slot.height, 0.01f)
        }
    }

    @Test
    fun `fan cascades along anti-diagonals without overlap`() {
        val slots = IconArrangement.FAN_TOP_LEFT.slots(6, width, height)
        // 6 icons → the smallest triangle holding them is g = 3, so cells are a third of the short side.
        assertEquals(height / 3f, slots[0].width, 0.01f)
        assertNoOverlap(slots)
    }

    // ── Beehive ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `beehive centres its first icon`() {
        val slot = IconArrangement.BEEHIVE.slots(7, width, height).first()
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
        val oneRing = IconArrangement.BEEHIVE.slots(7, width, height).first().width
        val twoRings = IconArrangement.BEEHIVE.slots(19, width, height).first().width
        assertTrue("19 icons ($twoRings) should be smaller than 7 ($oneRing)", twoRings < oneRing)
    }

    @Test
    fun `beehive icons are square and all one size`() {
        val slots = IconArrangement.BEEHIVE.slots(7, width, height)
        for (slot in slots) {
            assertEquals(slot.width, slot.height, 0.01f)
            assertEquals(slots[0].width, slot.width, 0.01f)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────────────

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
