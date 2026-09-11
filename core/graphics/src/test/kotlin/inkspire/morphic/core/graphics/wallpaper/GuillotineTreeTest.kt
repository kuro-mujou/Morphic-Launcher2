package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.GuillotineTree.Node
import inkspire.morphic.core.graphics.wallpaper.GuillotineTree.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge rule, on trees small enough to read: which cuts pair and slide, and which leave. Its failure is a scrub
 * that still partitions the frame and still moves smoothly while taking the wrong route — a cut turning, or the wrong
 * side of a cut disappearing — which no partition check can tell from the right one.
 */
class GuillotineTreeTest {

    private val frame = Rect(0f, 0f, 1f, 1f)

    @Test
    fun `two cuts along one axis pair, and slide from one share to the other`() {
        val from = Node(frame).also { indexed(it.cut(vertical = true, at = 0.25f)) }
        val to = Node(frame).also { indexed(it.cut(vertical = true, at = 0.75f)) }
        val pieces = moment(GuillotineTree.merge(from, to), 0.5f)
        assertEquals(listOf(Rect(0f, 0f, 0.5f, 1f) to (0 to 0), Rect(0.5f, 0f, 1f, 1f) to (1 to 1)), pieces)
    }

    /**
     * **Two cuts across each other never pair**: the starting cut leaves toward the edge, its second side going
     * where both sides hold as many pieces, and the finishing cut arrives across what stays.
     */
    @Test
    fun `two cuts across each other do not pair — one leaves, the other arrives`() {
        val from = Node(frame).also { indexed(it.cut(vertical = true, at = 0.5f)) }
        val to = Node(frame).also { indexed(it.cut(vertical = false, at = 0.5f)) }
        val pieces = moment(GuillotineTree.merge(from, to), 0.5f)
        assertEquals(
            listOf(
                // The first side stays and widens, cut across by the arriving horizontal, now halfway in.
                Rect(0f, 0f, 0.75f, 0.75f) to (0 to 0),
                Rect(0f, 0.75f, 0.75f, 1f) to (-1 to 1),
                // The second side narrows away into the right edge.
                Rect(0.75f, 0f, 1f, 1f) to (1 to -1),
            ),
            pieces,
        )
    }

    @Test
    fun `a cut only one tree makes leaves by its side with fewer pieces`() {
        val from = Node(frame).also { root ->
            val (first, _) = root.cut(vertical = true, at = 0.5f)
            first.cut(vertical = false, at = 0.5f)
            indexed(root)
        }
        val to = Node(frame).also { it.index = 0 }
        // The first side holds two pieces and the second one, so the second is what goes: the cut runs to the right,
        // and halfway through the lone second side has half its width left.
        val lone = moment(GuillotineTree.merge(from, to), 0.5f).single { (_, ends) -> ends.first == 2 }
        assertEquals(Rect(0.75f, 0f, 1f, 1f) to (2 to -1), lone)
    }

    private fun indexed(root: Pair<Node, Node>) {
        root.first.index = 0
        root.second.index = 1
    }

    private fun indexed(root: Node) = GuillotineTree.pieces(root).forEachIndexed { i, node -> node.index = i }

    private fun moment(blend: GuillotineTree.Blend, t: Float): List<Pair<Rect, Pair<Int, Int>>> {
        val out = ArrayList<Pair<Rect, Pair<Int, Int>>>()
        GuillotineTree.pieces(blend, frame, t) { rect, fromIndex, toIndex -> out.add(rect to (fromIndex to toIndex)) }
        return out
    }
}
