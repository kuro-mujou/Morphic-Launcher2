package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rib count and refraction mappings. The gradient is [LinearGradientGenerator]'s and the lens shading is judged in
 * the render harness; here only the two knobs this design owns.
 */
class RibbedGlassGeneratorTest {

    @Test
    fun `density maps to the rib count range`() {
        assertEquals(8, RibbedGlassGenerator.ribCount(0f))
        assertEquals(28, RibbedGlassGenerator.ribCount(1f))
        assertEquals(8, RibbedGlassGenerator.ribCount(-1f)) // clamped
        assertEquals(28, RibbedGlassGenerator.ribCount(2f)) // clamped
    }

    @Test
    fun `refraction is never zero, so the ribs are always visible glass`() {
        assertEquals(0.05f, RibbedGlassGenerator.refraction(0f), 1e-6f)
        assertEquals(0.24f, RibbedGlassGenerator.refraction(1f), 1e-6f)
        assertTrue("refraction fell to nothing", RibbedGlassGenerator.refraction(0f) > 0f)
    }
}
