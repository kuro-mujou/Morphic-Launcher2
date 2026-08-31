package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette reduction — the one place a color mode becomes real colors, and silently wrong (a hue that drifts, a
 * two-color pick that lands on one color) with no bitmap needed to see it.
 */
class PaletteColorModeTest {

    // Light-to-dark, a real hue in the middle so monochromatic has chroma to work with.
    private val palette = Palette(
        listOf(0xFFF2E2C4.toInt(), 0xFFE6A15C.toInt(), 0xFFC9603E.toInt(), 0xFF2C6E6B.toInt(), 0xFF121E2B.toInt()),
    )

    @Test
    fun `colorful leaves the palette untouched`() {
        assertEquals(palette, PaletteColorMode.resolve(palette, WallpaperColorMode.COLORFUL))
    }

    @Test
    fun `bichromatic keeps exactly the two ends, in order`() {
        val bi = PaletteColorMode.bichromatic(palette)
        assertEquals(2, bi.size)
        assertEquals(palette.colorAt(0), bi.colorAt(0))          // lightest first
        assertEquals(palette.colorAt(palette.size - 1), bi.colorAt(1)) // darkest last
    }

    @Test
    fun `a one-color palette has no second end for bichromatic but still expands into shades`() {
        val one = Palette(listOf(0xFF808080.toInt()))
        // Bichromatic needs two ends and a single color has one — returned unchanged rather than doubling it.
        assertEquals(one, PaletteColorMode.bichromatic(one))
        // Monochromatic is exactly the "one color, many shades" case, so it expands even a single color.
        assertTrue(PaletteColorMode.monochromatic(one).size >= 3)
    }

    @Test
    fun `monochromatic runs light to dark`() {
        val mono = PaletteColorMode.monochromatic(palette)
        assertTrue("mono ramp needs several shades", mono.size >= 3)
        // Luminance must fall monotonically from the first (tint) to the last (shade).
        val lums = mono.colors.map { luminance(it) }
        for (i in 1 until lums.size) {
            assertTrue("shade $i is not darker than shade ${i - 1}", lums[i] < lums[i - 1])
        }
    }

    @Test
    fun `monochromatic holds one hue, not several`() {
        val mono = PaletteColorMode.monochromatic(palette)
        // Every shade is a mix of the same base toward white/black, so their hues stay close — unlike the source palette,
        // whose ends are different hues. A cheap proxy: the ratio between the two most-saturated channels stays stable.
        val base = palette.colorAt(palette.size / 2)
        val baseHue = hueProxy(base)
        // The middle shade IS the base, and the tint/shade should not swing to a different hue family.
        for (c in mono.colors) {
            assertTrue("a shade left the base hue", kotlin.math.abs(hueProxy(c) - baseHue) < 0.34f)
        }
    }

    @Test
    fun `the two ends of bichromatic are actually different colors for a real palette`() {
        val bi = PaletteColorMode.bichromatic(palette)
        assertNotEquals(bi.colorAt(0), bi.colorAt(1))
    }

    private fun luminance(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    // A crude hue proxy in [0,1): where the max channel sits. Enough to tell "same hue family" from "different".
    private fun hueProxy(argb: Int): Float {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == min) return 0f
        val d = (max - min).toFloat()
        return when (max) {
            r -> ((g - b) / d / 6f + 1f) % 1f
            g -> (b - r) / d / 6f + 1f / 3f
            else -> (r - g) / d / 6f + 2f / 3f
        }
    }
}
