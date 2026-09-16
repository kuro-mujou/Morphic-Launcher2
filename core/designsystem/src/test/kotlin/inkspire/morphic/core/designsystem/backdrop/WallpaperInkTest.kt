package inkspire.morphic.core.designsystem.backdrop

import inkspire.morphic.core.model.wallpaper.LuminanceMap
import inkspire.morphic.core.model.wallpaper.WallpaperBrightness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule every piece of text on the wallpaper takes its color from.
 *
 * **These exist because the failure is a screen nobody can read, and it compiles.** The rule it replaced took one
 * color's luminance for the whole picture; the same wallpaper then gave white labels on one phone and black ones on
 * another, and both were wrong for half the screen.
 */
class WallpaperInkTest {

    @Test
    fun `a bright spot takes dark ink and no backing`() {
        val ink = inkOver(FloatArray(12) { 0.6f })

        assertFalse(ink.light)
        assertEquals(0f, ink.backingAlpha)
    }

    @Test
    fun `a dark spot takes light ink and no backing`() {
        val ink = inkOver(FloatArray(12) { 0.04f })

        assertTrue(ink.light)
        assertEquals(0f, ink.backingAlpha)
    }

    @Test
    fun `a spot straddling light and dark gets a backing, and the backing reaches the target`() {
        // Sky on one side, flowers on the other: neither ink reads everywhere.
        val samples = FloatArray(20) { if (it < 10) 0.02f else 0.45f }

        val ink = inkOver(samples.copyOf())

        assertTrue(ink.backingAlpha > 0f)
        assertTrue(ink.backingAlpha <= 0.6f)
    }

    @Test
    fun `one bright speck does not flip a dark spot`() {
        val samples = FloatArray(20) { 0.03f }.also { it[7] = 0.95f }

        val ink = inkOver(samples)

        assertTrue(ink.light)
        assertEquals(0f, ink.backingAlpha)
    }

    @Test
    fun `the backing is just enough - more contrast needed means more backing`() {
        val mild = inkOver(FloatArray(20) { if (it < 10) 0.02f else 0.25f })
        val harsh = inkOver(FloatArray(20) { if (it < 10) 0.02f else 0.4f })

        assertTrue(mild.light && harsh.light)
        assertTrue(harsh.backingAlpha > mild.backingAlpha)
    }

    @Test
    fun `a spot on the boundary keeps the ink it already shows, and a clear winner still flips it`() {
        // Scores close enough that a fresh reading picks one ink and the incumbent keeps the other.
        val boundary = FloatArray(20) { 0.18f }
        val fresh = inkOver(boundary.copyOf()).light

        assertEquals(!fresh, inkOver(boundary.copyOf(), current = !fresh).light)
        assertTrue(inkOver(FloatArray(12) { 0.04f }, current = false).light)
        assertFalse(inkOver(FloatArray(12) { 0.6f }, current = true).light)
    }

    @Test
    fun `a mid-gray picture that one color would call bright is not given dark ink by the system's verdict`() {
        assertTrue(WallpaperBrightness.Reported(supportsDarkText = false).wantsLightInk())
        assertFalse(WallpaperBrightness.Reported(supportsDarkText = true).wantsLightInk())
    }

    @Test
    fun `a measured half-dark picture is themed light as a whole`() {
        // Top half bright, bottom half dark.
        val map = LuminanceMap(columns = 2, rows = 4, values = FloatArray(8) { if (it < 4) 0.6f else 0.03f })

        assertTrue(WallpaperBrightness.Measured(map).wantsLightInk())
    }
}
