package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior spec for [GridReflow]. String keys stand in for whatever identity the caller keys by (`GridItem` in
 * production).
 */
class GridReflowTest {

    @Test
    fun `everything in bounds is left unchanged`() {
        val placements = mapOf("a" to GridPlacement(0, 0, 0), "b" to GridPlacement(0, 1, 1))
        val result = GridReflow.reflow(placements, GridConfig(rows = 4, cols = 4))
        assertFalse(result.changed)
        assertEquals(placements, result.placements)
    }

    @Test
    fun `an out-of-bounds item reflows into a free cell while in-bounds items stay put`() {
        val fixed = GridPlacement(0, 0, 0)
        val placements = mapOf("fixed" to fixed, "oob" to GridPlacement(0, 0, 9))
        val config = GridConfig(rows = 4, cols = 4)

        val result = GridReflow.reflow(placements, config)

        assertTrue(result.changed)
        assertEquals(fixed, result.placements.getValue("fixed"))
        val moved = result.placements.getValue("oob")
        assertTrue("reflowed item must fit the grid", moved.fitsIn(config))
        assertNotEquals(fixed, moved)
    }

    @Test
    fun `overflow past the last page appends a new page`() {
        // A full 2x2 page plus one out-of-bounds item: the stray must spill onto page 1.
        val placements = mapOf(
            "a" to GridPlacement(0, 0, 0),
            "b" to GridPlacement(0, 0, 1),
            "c" to GridPlacement(0, 1, 0),
            "d" to GridPlacement(0, 1, 1),
            "oob" to GridPlacement(0, 0, 5),
        )
        val result = GridReflow.reflow(placements, GridConfig(rows = 2, cols = 2))
        assertTrue(result.changed)
        assertEquals(1, result.placements.getValue("oob").page)
    }

    @Test
    fun `a shrunk single-page grid keeps what still fits beside the swallowed row`() {
        // The dock's shape: one page, and a shrink from two rows to one. "b" was on the row that went away, but
        // the remaining row has gaps, so it stays on this grid rather than being evicted.
        val placements = mapOf("a" to GridPlacement(0, 0, 0), "b" to GridPlacement(0, 1, 0))
        val config = GridConfig(rows = 1, cols = 4)

        val result = GridReflow.reflow(placements, config, GridReflow.Overflow.EVICT)

        assertTrue(result.changed)
        assertEquals(emptySet<String>(), result.evicted)
        assertEquals(GridPlacement(0, 0, 0), result.placements.getValue("a"))
        assertTrue(result.placements.getValue("b").fitsIn(config))
    }

    @Test
    fun `a single-page grid evicts what it cannot fit rather than inventing a page`() {
        // Same shrink onto a row that is already full. Under NEXT_PAGE "c" would land on page 1 — a page the dock
        // never draws, so the item would simply disappear. EVICT hands it back for the caller to re-home.
        val placements = mapOf(
            "a" to GridPlacement(0, 0, 0),
            "b" to GridPlacement(0, 0, 1),
            "c" to GridPlacement(0, 1, 0),
        )

        val result = GridReflow.reflow(placements, GridConfig(rows = 1, cols = 2), GridReflow.Overflow.EVICT)

        assertEquals(setOf("c"), result.evicted)
        assertFalse("an evicted item must carry no placement", result.placements.containsKey("c"))
        assertEquals(GridPlacement(0, 0, 0), result.placements.getValue("a"))
    }

    @Test
    fun `admit finds room for a homeless item whose old coordinate is taken`() {
        // The arrival's dock coordinate is a perfectly valid home coordinate too — and occupied. This is the case
        // reflow cannot express: it would judge the arrival as "still fits" and stack it on top of "sitting".
        val occupants = mapOf("sitting" to GridPlacement(0, 0, 0))
        val arrivals = mapOf("arriving" to GridPlacement(0, 0, 0))
        val config = GridConfig(rows = 4, cols = 4)

        val result = GridReflow.admit(arrivals, occupants, config)

        assertTrue(result.changed)
        val landed = result.placements.getValue("arriving")
        assertTrue(landed.fitsIn(config))
        assertNotEquals(GridPlacement(0, 0, 0), landed)
        assertFalse("occupants are the caller's already; admit returns arrivals only", result.placements.containsKey("sitting"))
    }

    @Test
    fun `admit keeps an arrival's coordinate when it happens to be free`() {
        val result = GridReflow.admit(
            arrivals = mapOf("arriving" to GridPlacement(0, 2, 3)),
            occupants = mapOf("sitting" to GridPlacement(0, 0, 0)),
            config = GridConfig(rows = 4, cols = 4),
        )

        assertEquals(GridPlacement(0, 2, 3), result.placements.getValue("arriving"))
    }

    @Test
    fun `admit has nothing to persist when nothing arrives`() {
        val result = GridReflow.admit(
            arrivals = emptyMap<String, GridPlacement>(),
            occupants = mapOf("sitting" to GridPlacement(0, 0, 0)),
            config = GridConfig(rows = 4, cols = 4),
        )

        assertFalse(result.changed)
        assertTrue(result.placements.isEmpty())
    }

    @Test
    fun `removing the left column shifts everything left, and the leftmost occupant is re-homed`() {
        // The whole reason an editor names an edge: "one fewer column" is ambiguous until you say which one goes.
        val placements = mapOf(
            "left" to GridPlacement(0, 0, 0),
            "right" to GridPlacement(0, 0, 1),
            "below" to GridPlacement(0, 1, 2),
        )

        val result = GridReflow.edit(placements, GridEditorEdge.LEFT, add = false, GridConfig(rows = 4, cols = 3))

        assertTrue(result.changed)
        assertEquals("the survivors slide into the space", GridPlacement(0, 0, 0), result.placements.getValue("right"))
        assertEquals(GridPlacement(0, 1, 1), result.placements.getValue("below"))
        // Column 0's occupant shifted to -1, so it has no cell of its own any more and is placed in the first free one.
        assertTrue(result.placements.getValue("left").fitsIn(GridConfig(rows = 4, cols = 3)))
    }

    @Test
    fun `removing the right column leaves everyone else exactly where they were`() {
        val placements = mapOf("left" to GridPlacement(0, 0, 0), "right" to GridPlacement(0, 0, 2))

        val result = GridReflow.edit(placements, GridEditorEdge.RIGHT, add = false, GridConfig(rows = 4, cols = 2))

        assertEquals("no shift on the far edge", GridPlacement(0, 0, 0), result.placements.getValue("left"))
        assertTrue(result.placements.getValue("right").fitsIn(GridConfig(rows = 4, cols = 2)))
    }

    @Test
    fun `adding a row on top pushes everything down and re-homes nobody`() {
        // Grow-first for adds: items shifted into the *larger* grid always fit, so a growth never displaces.
        val placements = mapOf("a" to GridPlacement(0, 0, 0), "b" to GridPlacement(0, 3, 1))

        val result = GridReflow.edit(placements, GridEditorEdge.TOP, add = true, GridConfig(rows = 5, cols = 4))

        assertEquals(GridPlacement(0, 1, 0), result.placements.getValue("a"))
        assertEquals(GridPlacement(0, 4, 1), result.placements.getValue("b"))
        assertEquals(emptySet<String>(), result.evicted)
    }

    @Test
    fun `an edit shifts by a whole visual cell on a sub-cell grid`() {
        // Home's multiplier is 2, so one row a user can see is two logical rows. Shifting by one would leave items
        // straddling the visual lattice, in half-cells nothing can be dropped into.
        val placements = mapOf("a" to GridPlacement(0, 0, 0, rowSpan = 2, colSpan = 2))

        val result = GridReflow.edit(
            placements,
            GridEditorEdge.TOP,
            add = true,
            GridConfig(rows = 12, cols = 8, cellMultiplier = 2),
        )

        assertEquals(2, result.placements.getValue("a").row)
    }

    @Test
    fun `an edit that moves nothing asks for no write`() {
        // A far-edge growth: no shift, and nothing displaced. `changed` is compared against the input rather than
        // inferred from the edit, so this correctly reports there is nothing to persist.
        val placements = mapOf("a" to GridPlacement(0, 0, 0))

        val result = GridReflow.edit(placements, GridEditorEdge.BOTTOM, add = true, GridConfig(rows = 5, cols = 4))

        assertFalse(result.changed)
        assertEquals(placements, result.placements)
    }

    @Test
    fun `a single-page grid evicts what an edit pushes out`() {
        // The dock's editor: the same op, with `Overflow` the only thing that differs — which is what lets one
        // function serve both.
        val placements = mapOf(
            "a" to GridPlacement(0, 0, 0),
            "b" to GridPlacement(0, 0, 1),
        )

        val result = GridReflow.edit(
            placements,
            GridEditorEdge.LEFT,
            add = false,
            GridConfig(rows = 1, cols = 1),
            GridReflow.Overflow.EVICT,
        )

        assertEquals(GridPlacement(0, 0, 0), result.placements.getValue("b"))
        assertEquals(setOf("a"), result.evicted)
    }

    @Test
    fun `admit spills onto a new page when the receiving grid is full`() {
        val full = mapOf(
            "a" to GridPlacement(0, 0, 0),
            "b" to GridPlacement(0, 0, 1),
            "c" to GridPlacement(0, 1, 0),
            "d" to GridPlacement(0, 1, 1),
        )

        val result = GridReflow.admit(
            arrivals = mapOf("arriving" to GridPlacement(0, 0, 0)),
            occupants = full,
            config = GridConfig(rows = 2, cols = 2),
        )

        assertEquals(1, result.placements.getValue("arriving").page)
        assertEquals(emptySet<String>(), result.evicted)
    }
}
