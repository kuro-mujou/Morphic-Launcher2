package inkspire.morphic.feature.home.widgetpicker

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateShelvesTest {

    /** Each design as its width in visual cells; the letter is only there to tell equal widths apart. */
    private fun shelve(vararg spans: Pair<String, Int>, cols: Int = 4) =
        templateShelves(spans.toList(), cols) { it.second }.map { shelf -> shelf.joinToString("") { it.first } }

    @Test
    fun `designs share a shelf while they fit`() {
        assertEquals(listOf("a", "bc", "de"), shelve("a" to 4, "b" to 2, "c" to 2, "d" to 1, "e" to 2))
    }

    @Test
    fun `one that does not fit starts the next shelf, and nothing is reordered to fill the gap`() {
        // "c" would fit back beside "a", but it follows "b" — moving it there would shuffle the author's order.
        assertEquals(listOf("a", "bc"), shelve("a" to 3, "b" to 2, "c" to 1))
    }

    @Test
    fun `a design wider than the grid takes a shelf of its own`() {
        assertEquals(listOf("a", "b"), shelve("a" to 6, "b" to 1))
    }

    @Test
    fun `no designs are no shelves`() {
        assertEquals(emptyList<String>(), shelve())
    }
}
