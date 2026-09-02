package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The four knob mappings, and the two of them whose `0.5` has to land on a number taken from somewhere else — gart's
 * three-radian sweep, and the reference's four moons.
 *
 * The trail growth itself is not tested here: its rule is a *spatial* one over hundreds of interacting trails, and
 * what it produces is a picture rather than a number. The render harness is where it is judged.
 */
class FlowFieldGeneratorTest {

    @Test
    fun `density maps to the trails grown per tone`() {
        assertEquals(30, FlowFieldGenerator.strokeCount(0f))
        assertEquals(300, FlowFieldGenerator.strokeCount(1f))
        assertEquals(300, FlowFieldGenerator.strokeCount(2f)) // clamped
    }

    @Test
    fun `curl sweeps from a drift to a whole turn, and the default is gart's`() {
        assertEquals(0.6f, FlowFieldGenerator.angleSpan(0f), 1e-6f)
        assertEquals(3f, FlowFieldGenerator.angleSpan(0.5f), 1e-6f) // gart's eclectic, exactly
        assertEquals(5.4f, FlowFieldGenerator.angleSpan(1f), 1e-6f)
        assertEquals(5.4f, FlowFieldGenerator.angleSpan(2f), 1e-6f) // clamped
    }

    @Test
    fun `thickness leaves gart's own widths alone at the default`() {
        assertEquals(0.25f, FlowFieldGenerator.thicknessScale(0f), 1e-6f)
        assertEquals(1f, FlowFieldGenerator.thicknessScale(0.5f), 1e-6f)
        assertEquals(1.75f, FlowFieldGenerator.thicknessScale(1f), 1e-6f)
    }

    @Test
    fun `depth zero is a sky with no moons, and the default is the reference's four`() {
        assertEquals(0, FlowFieldGenerator.moonCount(0f))
        assertEquals(4, FlowFieldGenerator.moonCount(0.5f))
        assertEquals(8, FlowFieldGenerator.moonCount(1f))
        assertEquals(8, FlowFieldGenerator.moonCount(2f)) // clamped
    }
}
