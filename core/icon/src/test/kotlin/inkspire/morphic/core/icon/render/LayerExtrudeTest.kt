package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * How far an extrusion reaches, which way, and how many copies it is made of.
 *
 * **The reach is what must not drift**, and it is the assertion the cap makes non-obvious: the count is bounded, so
 * the per-step offset has to grow to compensate, and getting that wrong shortens the slab instead of coarsening it —
 * a difference nobody would attribute to a step limit.
 */
class LayerExtrudeTest {

    private fun extrude(depth: Float = 0.15f, angleDegrees: Float = 0f) =
        LayerEffect.Extrude(depth = depth, angleDegrees = angleDegrees)

    private fun reach(steps: LayerExtrude.Steps) =
        hypot(steps.dxPx * steps.count, steps.dyPx * steps.count)

    @Test
    fun `zero degrees extrudes straight down, matching every other angle in the studio`() {
        val steps = LayerExtrude.steps(extrude(), sizePx = 100)

        assertEquals(0f, steps.dxPx, 0.001f)
        assertTrue("positive y is down", steps.dyPx > 0f)
    }

    @Test
    fun `ninety degrees extrudes to the right, so the angle turns clockwise`() {
        val steps = LayerExtrude.steps(extrude(angleDegrees = 90f), sizePx = 100)

        assertTrue(steps.dxPx > 0f)
        assertEquals(0f, steps.dyPx, 0.001f)
    }

    @Test
    fun `the furthest copy lands at the depth asked for, whatever the count`() {
        // The property the cap makes worth pinning. At a depth this shallow the count is uncapped; the next test is
        // the same assertion where it is not.
        assertEquals(15f, reach(LayerExtrude.steps(extrude(depth = 0.15f), sizePx = 100)), 0.01f)
    }

    @Test
    fun `a capped count spreads its copies rather than falling short`() {
        // 0.25 of a 512px bake is 128px of depth against a cap of 48 copies, so each step has to be more than a
        // pixel. What must not happen is the slab reaching only 48px — which is what a fixed per-step offset would
        // give, and which would read as the depth slider quietly stopping partway.
        val steps = LayerExtrude.steps(extrude(depth = 0.25f), sizePx = 512)

        assertTrue("the count is capped", steps.count < 128)
        assertEquals(128f, reach(steps), 0.01f)
    }

    @Test
    fun `depth is a fraction of the box, so one recipe reads the same at every bake size`() {
        val small = LayerExtrude.steps(extrude(depth = 0.1f), sizePx = 96)
        val large = LayerExtrude.steps(extrude(depth = 0.1f), sizePx = 288)

        assertEquals(9.6f, reach(small), 0.01f)
        assertEquals(28.8f, reach(large), 0.01f)
    }

    @Test
    fun `no depth is no copies`() {
        // `isIdentity` already filters this out before either renderer is reached; this is the guard for a stored
        // recipe that never went through it, and it must not come back with a copy at zero offset.
        assertEquals(0, LayerExtrude.steps(extrude(depth = 0f), sizePx = 192).count)
        assertEquals(0, LayerExtrude.steps(extrude(depth = -1f), sizePx = 192).count)
    }
}
