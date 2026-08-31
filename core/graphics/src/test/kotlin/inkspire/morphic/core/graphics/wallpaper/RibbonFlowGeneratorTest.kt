package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ribbon Flow's two pure mappings — the ribbon count and the field's angle span. The tracing and the looped ramp it
 * defers to are tested in their own homes; the per-segment gradient drawing is canvas work, judged in the render harness.
 */
class RibbonFlowGeneratorTest {

    @Test
    fun `density maps to the ribbon count range`() {
        assertEquals(10, RibbonFlowGenerator.ribbonCount(0f))
        assertEquals(34, RibbonFlowGenerator.ribbonCount(1f))
        assertEquals(10, RibbonFlowGenerator.ribbonCount(-1f)) // clamped
        assertEquals(34, RibbonFlowGenerator.ribbonCount(2f)) // clamped
    }

    @Test
    fun `irregularity scales the angle span, with the default landing mid-range`() {
        assertEquals(3f, RibbonFlowGenerator.angleSpan(0f), 1e-6f)
        assertEquals(6f, RibbonFlowGenerator.angleSpan(0.5f), 1e-6f) // BaseAngleSpan × 1.0
        assertEquals(9f, RibbonFlowGenerator.angleSpan(1f), 1e-6f)
        assertEquals(9f, RibbonFlowGenerator.angleSpan(2f), 1e-6f) // clamped
    }
}
