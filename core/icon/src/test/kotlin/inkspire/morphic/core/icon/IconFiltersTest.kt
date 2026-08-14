package inkspire.morphic.core.icon

import inkspire.morphic.core.model.icon.IconFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter table's contract, which is the part of an authored list that a reviewer cannot check by reading.
 *
 * Nothing here asserts that a look is *nice* — that is taste, and it belongs on a device. What it pins is the
 * structure the renderers depend on: an id resolves to exactly one matrix, a matrix is the right shape, and an id
 * nobody knows degrades instead of failing. Those are the ways this file can be wrong silently.
 */
class IconFiltersTest {

    @Test
    fun `every id is unique, because a duplicate would shadow a look with no error`() {
        val ids = IconFilters.All.map { it.filter.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every matrix is a 4x5 with alpha left alone`() {
        IconFilters.All.forEach { entry ->
            assertEquals("${entry.filter.id} is not 4x5", 20, entry.matrix.size)
            // The alpha row: a filter changes colour, never coverage. A stray value here would make a look eat the
            // icon's edges — which reads as a rendering bug, not as the filter's doing.
            assertEquals("${entry.filter.id} scales alpha", 1f, entry.matrix[18], 0.0001f)
            assertEquals("${entry.filter.id} offsets alpha", 0f, entry.matrix[19], 0.0001f)
            assertEquals("${entry.filter.id} leaks colour into alpha", 0f, entry.matrix[15], 0.0001f)
            assertEquals("${entry.filter.id} leaks colour into alpha", 0f, entry.matrix[16], 0.0001f)
            assertEquals("${entry.filter.id} leaks colour into alpha", 0f, entry.matrix[17], 0.0001f)
        }
    }

    @Test
    fun `no filter is an accidental identity`() {
        // A look that changes nothing is a tile that does nothing, and composing matrices makes that easy to reach
        // by cancelling — a scale of 1 with an offset of 0, say. Cheaper to catch here than on a device.
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        IconFilters.All.forEach { entry ->
            assertTrue("${entry.filter.id} does nothing", !entry.matrix.contentEquals(identity))
        }
    }

    @Test
    fun `every entry is reachable from its own category`() {
        // The picker only ever lists by category, so an entry filed under one it is not returned for would exist
        // in the table and nowhere in the UI.
        IconFilters.All.forEach { entry ->
            assertTrue(
                "${entry.filter.id} missing from ${entry.category}",
                IconFilters.inCategory(entry.category).contains(entry),
            )
        }
        assertEquals(IconFilters.All.size, IconFilters.Category.entries.sumOf { IconFilters.inCategory(it).size })
    }

    @Test
    fun `an id this build does not know resolves to nothing rather than failing`() {
        // The degrade a recipe written by a later build depends on — the same one `IconShapes` makes for a shape.
        assertNull(IconFilters.matrixOrNull(IconFilter("no_such_filter")))
        assertNull(IconFilters.entryOrNull(IconFilter("")))
    }

    @Test
    fun `a known id resolves to its own matrix`() {
        val entry = IconFilters.All.first()

        assertNotNull(IconFilters.entryOrNull(entry.filter))
        assertTrue(IconFilters.matrixOrNull(entry.filter).contentEquals(entry.matrix))
    }

    @Test
    fun `classic mono drains colour, which is the one look with an arithmetic answer`() {
        // Saturation 0 means each output row is the luminance weights, so all three rows are identical. Worth
        // pinning one entry's actual numbers so the composition helpers cannot silently stop composing.
        val mono = IconFilters.matrixOrNull(IconFilter("classic_mono"))!!

        for (channel in 0..2) {
            assertEquals(0.213f, mono[channel * 5 + 0], 0.001f)
            assertEquals(0.715f, mono[channel * 5 + 1], 0.001f)
            assertEquals(0.072f, mono[channel * 5 + 2], 0.001f)
        }
    }
}
