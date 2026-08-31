package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Only the ribbon count is Ribbons' own arithmetic — the streamline stepping it draws is [FlowFieldGenerator.trace],
 * already covered by that generator's tests, and reused rather than re-derived.
 */
class RibbonsGeneratorTest {

    @Test
    fun `density maps to the ribbon count range`() {
        assertEquals(8, RibbonsGenerator.ribbonCount(0f))
        assertEquals(26, RibbonsGenerator.ribbonCount(1f))
        assertEquals(8, RibbonsGenerator.ribbonCount(-1f)) // clamped
    }
}
