package inkspire.morphic.core.designsystem.surface

import inkspire.morphic.core.model.HomeEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec for the two halves of the surface-swipe hand-off: [ScrollAxes] turning what a layout scrolls into a
 * [OneFingerSwipe] policy, and [ScrollEdges] answering where that content is resting.
 *
 * Worth pinning because the derivation is stated once and read from two opposite ends — HOME's axes give an edge's
 * *open* policy, the side surface's give its *close* — and because the edge each end must have reached differs by
 * exactly one `opposite`. Both are the kind of mapping that stays plausible-looking while being inverted.
 */
class SurfaceSwipePolicyTest {

    @Test
    fun `nothing scrolling on an axis leaves one finger free to cross it`() {
        val axes = ScrollAxes(horizontal = AxisScroll.NONE, vertical = AxisScroll.NONE)
        for (edge in HomeEdge.entries) {
            assertEquals(OneFingerSwipe.ALWAYS, axes.oneFingerSwipe(edge))
        }
    }

    @Test
    fun `a bounded scroller claims its own axis and leaves the other free`() {
        // A vertical list: a swipe up or down is the list's until it runs out; left and right are nobody's.
        val list = ScrollAxes(vertical = AxisScroll.BOUNDED)
        assertEquals(OneFingerSwipe.AT_EDGE, list.oneFingerSwipe(HomeEdge.TOP))
        assertEquals(OneFingerSwipe.AT_EDGE, list.oneFingerSwipe(HomeEdge.BOTTOM))
        assertEquals(OneFingerSwipe.ALWAYS, list.oneFingerSwipe(HomeEdge.LEFT))
        assertEquals(OneFingerSwipe.ALWAYS, list.oneFingerSwipe(HomeEdge.RIGHT))

        // A pager is the transpose, which is what makes the two HOME pairings behave oppositely.
        val pager = ScrollAxes(horizontal = AxisScroll.BOUNDED)
        assertEquals(OneFingerSwipe.AT_EDGE, pager.oneFingerSwipe(HomeEdge.LEFT))
        assertEquals(OneFingerSwipe.AT_EDGE, pager.oneFingerSwipe(HomeEdge.RIGHT))
        assertEquals(OneFingerSwipe.ALWAYS, pager.oneFingerSwipe(HomeEdge.TOP))
    }

    @Test
    fun `an infinite scroller has no edge to hand off from, so one finger never crosses`() {
        val wrapping = ScrollAxes(horizontal = AxisScroll.INFINITE)
        assertEquals(OneFingerSwipe.NEVER, wrapping.oneFingerSwipe(HomeEdge.LEFT))
        assertEquals(OneFingerSwipe.NEVER, wrapping.oneFingerSwipe(HomeEdge.RIGHT))
    }

    @Test
    fun `only AT_EDGE consults where the content is`() {
        assertTrue(OneFingerSwipe.ALWAYS.allows(atEdge = false))
        assertTrue(OneFingerSwipe.AT_EDGE.allows(atEdge = true))
        assertFalse(OneFingerSwipe.AT_EDGE.allows(atEdge = false))
        assertFalse(OneFingerSwipe.NEVER.allows(atEdge = true))
    }

    @Test
    fun `content that reports nothing is at every edge, so it behaves as it did before the hand-off existed`() {
        val unreported = ScrollEdges()
        for (edge in HomeEdge.entries) assertTrue(unreported[edge])
    }

    @Test
    fun `each edge reads its own field`() {
        val onlyLeft = ScrollEdges(atLeft = true, atRight = false, atTop = false, atBottom = false)
        assertTrue(onlyLeft[HomeEdge.LEFT])
        assertFalse(onlyLeft[HomeEdge.RIGHT])
        assertFalse(onlyLeft[HomeEdge.TOP])
        assertFalse(onlyLeft[HomeEdge.BOTTOM])
    }

    /**
     * The asymmetry the whole thing turns on: opening edge E asks about E, closing asks about `E.opposite`. A pager
     * resting on its first page can therefore *open* a LEFT surface and *close* a RIGHT one, and neither the other
     * way round.
     */
    @Test
    fun `opening reads the edge swiped toward and closing reads its opposite`() {
        val atFirstPage = ScrollEdges(atLeft = true, atRight = false)

        // Opening: the content must have reached the edge the surface is parked behind.
        assertTrue(atFirstPage[HomeEdge.LEFT])
        assertFalse(atFirstPage[HomeEdge.RIGHT])

        // Closing: the surface is dragged back the way it came, so the far edge is the one that matters.
        assertFalse(atFirstPage[HomeEdge.LEFT.opposite])
        assertTrue(atFirstPage[HomeEdge.RIGHT.opposite])
    }

    @Test
    fun `opposite stays on its own axis`() {
        assertEquals(HomeEdge.RIGHT, HomeEdge.LEFT.opposite)
        assertEquals(HomeEdge.LEFT, HomeEdge.RIGHT.opposite)
        assertEquals(HomeEdge.BOTTOM, HomeEdge.TOP.opposite)
        assertEquals(HomeEdge.TOP, HomeEdge.BOTTOM.opposite)
        for (edge in HomeEdge.entries) {
            assertEquals(edge, edge.opposite.opposite)
            assertEquals(edge.isHorizontal, edge.opposite.isHorizontal)
        }
    }
}
