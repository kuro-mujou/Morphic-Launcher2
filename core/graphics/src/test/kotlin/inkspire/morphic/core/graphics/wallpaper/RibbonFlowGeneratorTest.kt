package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ribbon Flow's pure mappings — how many lanes the rank holds, how far apart they sit, how fine the field is, how
 * wide a stroke is, and the amplitude bound that is the only reason the lines cannot cross. Every one of them is
 * silently wrong when it is wrong: a rank at the wrong pitch is still a plausible rank, and lines that cross read as
 * a busier design rather than as a broken one.
 */
class RibbonFlowGeneratorTest {

    @Test
    fun `density maps to the lane count range`() {
        assertEquals(10, RibbonFlowGenerator.lineCount(0f))
        assertEquals(50, RibbonFlowGenerator.lineCount(1f))
        assertEquals(10, RibbonFlowGenerator.lineCount(-1f)) // clamped
        assertEquals(50, RibbonFlowGenerator.lineCount(2f)) // clamped
    }

    @Test
    fun `the rank is ruled wider than the diagonal, so a wandering line still covers the edges`() {
        val diagonal = 2632f
        val count = 30
        val spacing = RibbonFlowGenerator.spacingPx(diagonal, count)
        val margin = (spacing * (count - 1) - diagonal) / 2f
        assertEquals("each side is ruled a full wander wide", diagonal * 0.15f, margin, 1e-2f)
    }

    @Test
    fun `a single lane has nothing to space`() {
        assertEquals(2400f, RibbonFlowGenerator.spacingPx(2400f, 1), 1e-3f)
    }

    /**
     * The smoothest field would otherwise buy a deflection near the frame's own size — the ordering bound scales as
     * `1 / frequency` — and that is more than the rank's margin covers.
     */
    @Test
    fun `the wander is capped at the share the rank leaves room for`() {
        val diagonal = 2632f
        assertEquals(diagonal * 0.15f, RibbonFlowGenerator.amplitudeFor(1f / 2400f, diagonal), 1e-2f)
        assertTrue(
            "a fine field is bounded by the ordering rule, not the cap",
            RibbonFlowGenerator.amplitudeFor(20f / 2400f, diagonal) < diagonal * 0.15f,
        )
    }

    @Test
    fun `smoothness runs from the sharpest field to one swell, with the default near the reference's eight`() {
        assertEquals(20f, RibbonFlowGenerator.detailFor(0f), 1e-3f)
        assertEquals(1f, RibbonFlowGenerator.detailFor(1f), 1e-3f)
        assertEquals(7.7f, RibbonFlowGenerator.detailFor(0.5f), 0.1f)
        assertEquals(20f, RibbonFlowGenerator.detailFor(-1f), 1e-3f) // clamped
    }

    @Test
    fun `thickness is a share of the lane, touching at the top and shipped at the default`() {
        assertEquals(0f, RibbonFlowGenerator.thicknessFraction(0f), 1e-6f)
        assertEquals(1f, RibbonFlowGenerator.thicknessFraction(1f), 1e-6f)
        assertEquals(0.125f, RibbonFlowGenerator.thicknessFraction(0.5f), 1e-6f) // the reference's 12%
    }

    /**
     * The bound the whole construction rests on: lane `i` drawn at `vᵢ + amplitude · noise(u, vᵢ)` keeps its order
     * against `i + 1` while `amplitude · ∂noise/∂v > −1`. Checked as the arithmetic rather than through a render,
     * because a crossing at one seed and one size proves nothing about the next.
     */
    @Test
    fun `the amplitude ceiling stays inside the ordering bound at every frequency`() {
        for (frequency in listOf(1e-4f, 1e-3f, 5e-3f, 0.05f)) {
            val worstSlope = RibbonFlowGenerator.amplitudeCeiling(frequency) * frequency * 2f
            assertTrue("frequency $frequency crosses at $worstSlope", worstSlope < 1f)
        }
    }

    @Test
    fun `a degenerate frequency still answers a finite amplitude`() {
        assertTrue(RibbonFlowGenerator.amplitudeCeiling(0f).isFinite())
    }
}
