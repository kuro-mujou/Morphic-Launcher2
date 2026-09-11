package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

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
            val worstSlope = RibbonFlowGenerator.amplitudeCeiling(frequency) * frequency * RibbonFlowGenerator.PerlinMaxSlope
            assertTrue("frequency $frequency crosses at $worstSlope", worstSlope < 1f)
        }
    }

    /**
     * **The slope the bound divides by is the field's own**, sampled rather than assumed. The test above only holds
     * the ceiling against [RibbonFlowGenerator.PerlinMaxSlope]; this is what keeps that number honest, and it is the
     * one that would have caught the lines crossing.
     */
    @Test
    fun `the field never climbs across a lane faster than the slope the bound assumes`() {
        val random = Random(7)
        var steepest = 0f
        repeat(20) {
            val field = PerlinNoise2d(random.nextLong())
            repeat(40_000) {
                val x = random.nextFloat() * 20f
                val y = random.nextFloat() * 20f
                steepest = max(steepest, abs(field.at(x, y + Step) - field.at(x, y - Step)) / (2 * Step))
            }
        }
        assertTrue("the field climbs at $steepest", steepest < RibbonFlowGenerator.PerlinMaxSlope)
    }

    /**
     * **No two neighboring lines cross, at any detail or count, at full Distortion** — the promise itself, checked on
     * the offsets the render draws rather than on the bound that is meant to guarantee it.
     */
    @Test
    fun `no two neighboring lines cross at full distortion`() {
        val diagonal = hypot(1079f, 2399f)
        for (roundness in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) for (density in listOf(0f, 0.5f, 1f)) {
            val count = RibbonFlowGenerator.lineCount(density)
            val spacing = RibbonFlowGenerator.spacingPx(diagonal, count)
            val frequency = RibbonFlowGenerator.detailFor(roundness) / 2400f
            val amplitude = RibbonFlowGenerator.amplitudeFor(frequency, diagonal)
            for (seed in 1L..6L) assertLanesInOrder(PerlinNoise2d(seed)::at, count, spacing, amplitude, frequency)
        }
    }

    /** Every lane of [count] lies strictly before the next, all along a frame's length. */
    private fun assertLanesInOrder(
        field: (Float, Float) -> Float,
        count: Int,
        spacing: Float,
        amplitude: Float,
        frequency: Float,
    ) {
        for (lane in 0 until count - 1) {
            val across = (lane - (count - 1) / 2f) * spacing
            var along = -1500f
            while (along < 1500f) {
                val here = RibbonFlowGenerator.offsetAt(field, along, across, amplitude, frequency)
                val next = RibbonFlowGenerator.offsetAt(field, along, across + spacing, amplitude, frequency)
                assertTrue("lanes $lane and ${lane + 1} cross at $along", next > here)
                along += 4f
            }
        }
    }

    @Test
    fun `a degenerate frequency still answers a finite amplitude`() {
        assertTrue(RibbonFlowGenerator.amplitudeCeiling(0f).isFinite())
    }

    private companion object {
        /** Half the span a slope is measured over, in the field's own units. */
        const val Step = 1e-3f
    }
}
