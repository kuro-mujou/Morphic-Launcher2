package inkspire.morphic.core.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProgressTest {

    @Test
    fun `a value is placed within its range`() {
        assertEquals(0.92f, progressFraction("92", 0f, 100f), 1e-6f)
        assertEquals(0.5f, progressFraction("30", 10f, 50f), 1e-6f)
        assertEquals(0.25f, progressFraction(" 360 ", 0f, 1440f), 1e-6f)
    }

    @Test
    fun `a value outside its range is held to the ends`() {
        assertEquals(0f, progressFraction("-5", 0f, 100f))
        assertEquals(1f, progressFraction("140", 0f, 100f))
    }

    @Test
    fun `what is not a number draws an empty track`() {
        // A formula that fails shows its own source text, which is not a number — and must not read as full.
        assertEquals(0f, progressFraction("\$bi(levle)\$", 0f, 100f))
        assertEquals(0f, progressFraction("", 0f, 100f))
        assertEquals(0f, progressFraction("NaN", 0f, 100f))
    }

    @Test
    fun `a range with no width draws an empty track`() {
        assertEquals(0f, progressFraction("50", 50f, 50f))
        assertEquals(0f, progressFraction("50", 100f, 0f))
    }
}
