package inkspire.morphic.core.designsystem.ordered

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.GridGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior spec for the MovingGap primitives — the reorder model every ordered surface shares.
 *
 * They went untested while they were `internal` helpers of one file; making them the shared, generic contract two
 * surfaces depend on is the point at which "the folder still feels right on device" stops being enough evidence.
 * Items are plain strings, which is also the cheapest proof that the generalization is real.
 */
class OrderedFlowTest {

    private val order = listOf("a", "b", "c", "d")

    /** A 4-column grid of 10×10 cells at the origin, so a finger at (x, y) maps to cell (y/10, x/10). */
    private val geometry = GridGeometry(
        originInRoot = Offset.Zero,
        cellW = 10f,
        cellH = 10f,
        cols = 4,
        rows = 4,
    )

    // ── The gap is an index into the list *without* the dragged item ──

    @Test
    fun `a negative gap seeds from the dragged item's own position`() {
        // Dragging "c" (index 2) and hovering its own slot: the gap starts where it already is, so nothing moves.
        assertEquals(2, movingGap(order, dragged = "c", currentGap = -1, flatSlot = 2, insertBefore = true))
    }

    @Test
    fun `hovering the left half of an item inserts before it`() {
        // Others are [a, b, d]; slot 1 is "b", left half → gap 1, i.e. between a and b.
        assertEquals(1, movingGap(order, dragged = "c", currentGap = 2, flatSlot = 1, insertBefore = true))
    }

    @Test
    fun `hovering the right half of an item inserts after it`() {
        assertEquals(2, movingGap(order, dragged = "c", currentGap = 2, flatSlot = 1, insertBefore = false))
    }

    @Test
    fun `hovering the gap itself changes nothing`() {
        assertEquals(2, movingGap(order, dragged = "c", currentGap = 2, flatSlot = 2, insertBefore = true))
    }

    @Test
    fun `hovering past the last item appends`() {
        // Others are [a, b, d] — three items, so any slot beyond 3 means "after everything".
        assertEquals(3, movingGap(order, dragged = "c", currentGap = 0, flatSlot = 9, insertBefore = true))
    }

    @Test
    fun `the gap is clamped into range`() {
        assertEquals(3, movingGap(order, dragged = "c", currentGap = 99, flatSlot = 3, insertBefore = false))
    }

    // ── Display order: what the user sees and what gets written are one function ──

    @Test
    fun `the dragged item is lifted to the gap and the rest densify around it`() {
        assertEquals(listOf("a", "c", "b", "d"), movingGapDisplayOrder(order, dragged = "c", gap = 1))
    }

    @Test
    fun `a gap at the end appends the dragged item`() {
        assertEquals(listOf("a", "b", "d", "c"), movingGapDisplayOrder(order, dragged = "c", gap = 3))
    }

    @Test
    fun `no drag leaves the order untouched`() {
        assertEquals(order, movingGapDisplayOrder(order, dragged = null, gap = 2))
    }

    @Test
    fun `an out-of-range gap is clamped rather than throwing`() {
        assertEquals(listOf("a", "b", "d", "c"), movingGapDisplayOrder(order, dragged = "c", gap = 99))
    }

    @Test
    fun `a drag that does not move reproduces the original order`() {
        // The invariant that keeps a no-op drop a no-op: seeding the gap from the item's own index round-trips.
        val gap = movingGap(order, dragged = "c", currentGap = -1, flatSlot = 2, insertBefore = true)
        assertEquals(order, movingGapDisplayOrder(order, dragged = "c", gap = gap))
    }

    // ── Display order with a key selector: for surfaces whose items are resolved, not identities ──

    /** An item whose identity is [id] but whose equality would also weigh [label] — an `AppInfo` in miniature. */
    private data class Resolved(val id: String, val label: String)

    @Test
    fun `items are matched by key, so a different-but-equal-keyed instance is still the dragged one`() {
        val resolved = listOf(Resolved("a", "A"), Resolved("b", "B"), Resolved("c", "C"))
        // The same item as far as the surface is concerned, but not `equals` — exactly what happens when a label or a
        // baked icon is refreshed underneath a drag. Without the selector this instance would not be found in the
        // list, so it would be *appended* and the surface would briefly draw the app twice.
        val dragged = Resolved("c", "C (renamed)")

        val display = movingGapDisplayOrder(resolved, dragged, gap = 0) { it.id }

        assertEquals(listOf(dragged, Resolved("a", "A"), Resolved("b", "B")), display)
    }

    @Test
    fun `the default key is plain equality, so identity lists are unaffected`() {
        assertEquals(
            movingGapDisplayOrder(order, dragged = "c", gap = 1),
            movingGapDisplayOrder(order, dragged = "c", gap = 1) { it },
        )
    }

    // ── Partitions ──

    @Test
    fun `a cell splits into thirds for a surface that can merge`() {
        assertEquals(Third.LEFT, geometry.thirdInCell(Offset(x = 1f, y = 5f)))
        assertEquals(Third.CENTER, geometry.thirdInCell(Offset(x = 5f, y = 5f)))
        assertEquals(Third.RIGHT, geometry.thirdInCell(Offset(x = 9f, y = 5f)))
    }

    @Test
    fun `thirds are read within the hovered cell, not across the grid`() {
        // x = 25f is the middle of column 2 — a center third, not a "right of the whole row".
        assertEquals(Third.CENTER, geometry.thirdInCell(Offset(x = 25f, y = 5f)))
    }

    @Test
    fun `the half split is the cell fraction`() {
        assertEquals(0.4f, geometry.cellFractionX(Offset(x = 24f, y = 0f)), 0.001f)
    }

    @Test
    fun `a flat slot counts from the first item of page zero`() {
        assertEquals(0, flatSlotOf(row = 0, col = 0, cols = 4, page = 0, perPage = 8))
        assertEquals(5, flatSlotOf(row = 1, col = 1, cols = 4, page = 0, perPage = 8))
        assertEquals(13, flatSlotOf(row = 1, col = 1, cols = 4, page = 1, perPage = 8))
    }
}
