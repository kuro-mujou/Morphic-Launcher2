package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The column count range — this design's only pure mapping of its own. The variable-width banding is tested in
 * [BandsTest], the ramp in [LinearGradientGeneratorTest], and the edge shading is judged in the render harness.
 */
class GradientColumnsGeneratorTest {

    @Test
    fun `density maps to the column count range`() {
        assertEquals(4, GradientColumnsGenerator.columnCount(0f))
        assertEquals(16, GradientColumnsGenerator.columnCount(1f))
        assertEquals(4, GradientColumnsGenerator.columnCount(-1f)) // clamped
        assertEquals(16, GradientColumnsGenerator.columnCount(2f)) // clamped
    }
}
