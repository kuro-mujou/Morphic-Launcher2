package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rib count, the field's lattice size, and the lens — which bends the wrong way without looking wrong, and so
 * cannot be judged from a render.
 */
class RibbedGlassGeneratorTest {

    @Test
    fun `density maps to the reference's own rib range`() {
        assertEquals(1, RibbedGlassGenerator.ribCount(0f))
        assertEquals(30, RibbedGlassGenerator.ribCount(1f))
        assertEquals(1, RibbedGlassGenerator.ribCount(-1f)) // clamped
        assertEquals(30, RibbedGlassGenerator.ribCount(2f)) // clamped
    }

    @Test
    fun `complexity maps to the reference's own blob counts`() {
        assertEquals(2, RibbedGlassGenerator.latticeAcross(0f))
        assertEquals(6, RibbedGlassGenerator.latticeAcross(1f))
        assertEquals(2, RibbedGlassGenerator.latticeAcross(-1f)) // clamped
    }

    @Test
    fun `no refraction is no lens at all — the field, undistorted`() {
        // The rigid end the old mapping refused to have: every point across the rib samples exactly where it sits.
        for (step in 0..10) {
            assertEquals(1f, RibbedGlassGenerator.lensFactor(step / 10f, thickness = 0f), 1e-6f)
        }
    }

    @Test
    fun `the lens magnifies, and hardest at a rib's edges`() {
        val middle = RibbedGlassGenerator.lensFactor(0f, thickness = 1f)
        val edge = RibbedGlassGenerator.lensFactor(1f, thickness = 1f)
        // Below 1 is magnification: the rib shows a narrower window of the field than it occupies.
        assertTrue("the middle magnifies", middle < 1f)
        assertTrue("the edge magnifies harder than the middle", edge < middle)
        // And it is monotone across the rib, so nothing folds back on itself part way.
        var previous = middle
        for (step in 1..10) {
            val here = RibbedGlassGenerator.lensFactor(step / 10f, thickness = 1f)
            assertTrue("monotone at $step", here <= previous)
            previous = here
        }
    }

    @Test
    fun `the factor never goes negative, however thick the glass`() {
        // Past a thickness the raw formula turns negative, which would mirror the field through the rib's own centre.
        for (step in 0..20) {
            assertTrue(
                "negative at $step",
                RibbedGlassGenerator.lensFactor(1f, thickness = step / 5f) >= 0f,
            )
        }
    }
}
