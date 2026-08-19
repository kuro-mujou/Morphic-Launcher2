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

    /** Pushes a color through a 4×5 matrix the way a graphics pipeline does — `LayerFilterTest`'s helper. */
    private fun apply(matrix: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun channel(row: Int): Int {
            val value = matrix[row * 5] * r + matrix[row * 5 + 1] * g +
                matrix[row * 5 + 2] * b + matrix[row * 5 + 4]
            return value.toInt().coerceIn(0, 255)
        }
        return Triple(channel(0), channel(1), channel(2))
    }

    /** Equal to within one level per channel — see the duotone test for why the slack is there. */
    private fun assertNear(expected: Triple<Int, Int, Int>, actual: Triple<Int, Int, Int>) {
        assertEquals("red", expected.first.toFloat(), actual.first.toFloat(), 1f)
        assertEquals("green", expected.second.toFloat(), actual.second.toFloat(), 1f)
        assertEquals("blue", expected.third.toFloat(), actual.third.toFloat(), 1f)
    }

    @Test
    fun `every id is unique, because a duplicate would shadow a look with no error`() {
        val ids = IconFilters.All.map { it.filter.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every matrix is a 4x5 with alpha left alone`() {
        IconFilters.All.forEach { entry ->
            assertEquals("${entry.filter.id} is not 4x5", 20, entry.matrix.size)
            // The alpha row: a filter changes color, never coverage. A stray value here would make a look eat the
            // icon's edges — which reads as a rendering bug, not as the filter's doing.
            assertEquals("${entry.filter.id} scales alpha", 1f, entry.matrix[18], 0.0001f)
            assertEquals("${entry.filter.id} offsets alpha", 0f, entry.matrix[19], 0.0001f)
            assertEquals("${entry.filter.id} leaks color into alpha", 0f, entry.matrix[15], 0.0001f)
            assertEquals("${entry.filter.id} leaks color into alpha", 0f, entry.matrix[16], 0.0001f)
            assertEquals("${entry.filter.id} leaks color into alpha", 0f, entry.matrix[17], 0.0001f)
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
    fun `no category is empty, because a chip with nothing behind it is a dead end`() {
        // The picker draws a chip per category whatever the table holds, so an empty one is a tap that clears the
        // row and offers only "None". Cheap to add a category and forget to file anything under it.
        IconFilters.Category.entries.forEach { category ->
            assertTrue("$category has no filters", IconFilters.inCategory(category).isNotEmpty())
        }
    }

    @Test
    fun `a duotone lands on its two ends and spans between them`() {
        // **The fifth column again, which is where this is silent when wrong**: the dark end is a translation on
        // 0..255, so a 0..1 value there gives a duotone whose shadows are simply black — plausible, and not what
        // was authored. Handheld Green is the sharpest case, both ends being colors nothing else here produces.
        val matrix = IconFilters.matrixOrNull(IconFilter("handheld_green"))!!

        // Within a point, because [apply] truncates as a pipeline does and the weights sum to one only to within
        // float error — the wrong-scale failure this guards against is out by fifteen to a hundred and fifty-five,
        // so a point of slack costs it nothing.
        assertNear(Triple(0x0F, 0x38, 0x0F), apply(matrix, 0, 0, 0))
        assertNear(Triple(0x9B, 0xBC, 0x0F), apply(matrix, 255, 255, 255))

        // And a mid-gray lands mid-ramp rather than at either end, which is what "spans" means and what a
        // `solid` — the other matrix with color in its fifth column — would fail.
        val (r, g, b) = apply(matrix, 128, 128, 128)
        assertTrue("red did not span: $r", r in 0x0F + 1..0x9B - 1)
        assertTrue("green did not span: $g", g in 0x38 + 1..0xBC - 1)
        // Both ends of blue are the same value here, so it is flat by construction — which is the degenerate case
        // of the same arithmetic and would break the moment the span were applied to the wrong end.
        assertEquals(0x0F, b)
    }

    @Test
    fun `a duotone discards hue, which is the whole difference from a tint`() {
        // Two colors of the same luminance must come out identical — a tint would keep them apart, and keeping
        // them apart is exactly what stops a set of icons reading as one set.
        val matrix = IconFilters.matrixOrNull(IconFilter("duo_indigo_peach"))!!

        // Rec. 709 puts pure green at 0.715 — so 182/255 is the gray of the same luminance, to within the point
        // that rounding leaves. A tint would hold these tens of levels apart on every channel.
        assertNear(apply(matrix, 0, 255, 0), apply(matrix, 182, 182, 182))
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
    fun `classic mono drains color, which is the one look with an arithmetic answer`() {
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
