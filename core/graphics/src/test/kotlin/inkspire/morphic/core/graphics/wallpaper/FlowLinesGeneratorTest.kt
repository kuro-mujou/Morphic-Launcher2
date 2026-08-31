package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Flow Lines' two pure mappings — the line count and the field's angle span. The tracing, the lattice seeding and the
 * path drawing it defers to are tested in their own homes (`FlowFieldGeneratorTest`, `PointScatterTest`, and the render
 * harness for the canvas path); here only the knobs this design owns.
 */
class FlowLinesGeneratorTest {

    @Test
    fun `density maps to the line count range`() {
        assertEquals(500, FlowLinesGenerator.lineCount(0f))
        assertEquals(2000, FlowLinesGenerator.lineCount(1f))
        assertEquals(500, FlowLinesGenerator.lineCount(-1f)) // clamped
        assertEquals(2000, FlowLinesGenerator.lineCount(2f)) // clamped
    }

    @Test
    fun `irregularity scales the angle span, with the default landing mid-range`() {
        assertEquals(3f, FlowLinesGenerator.angleSpan(0f), 1e-6f)
        assertEquals(6f, FlowLinesGenerator.angleSpan(0.5f), 1e-6f) // BaseAngleSpan × 1.0
        assertEquals(9f, FlowLinesGenerator.angleSpan(1f), 1e-6f)
        assertEquals(9f, FlowLinesGenerator.angleSpan(2f), 1e-6f) // clamped
    }
}
