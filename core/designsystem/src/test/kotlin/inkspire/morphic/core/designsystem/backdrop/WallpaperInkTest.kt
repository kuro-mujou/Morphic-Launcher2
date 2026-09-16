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
    fun `a bright spot takes dark ink`() {
        assertFalse(inkOver(FloatArray(12) { 0.6f }))
    }

    @Test
    fun `a dark spot takes light ink`() {
        assertTrue(inkOver(FloatArray(12) { 0.04f }))
    }

    @Test
    fun `one bright speck does not flip a dark spot`() {
        val samples = FloatArray(20) { 0.03f }.also { it[7] = 0.95f }

        assertTrue(inkOver(samples))
    }

    @Test
    fun `a spot on the boundary keeps the ink it already shows, and a clear winner still flips it`() {
        // Scores close enough that a fresh reading picks one ink and the incumbent keeps the other.
        val boundary = FloatArray(20) { 0.18f }
        val fresh = inkOver(boundary.copyOf())

        assertEquals(!fresh, inkOver(boundary.copyOf(), current = !fresh))
        assertTrue(inkOver(FloatArray(12) { 0.04f }, current = false))
        assertFalse(inkOver(FloatArray(12) { 0.6f }, current = true))
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
