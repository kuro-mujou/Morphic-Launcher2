package inkspire.morphic.core.icon.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a legacy icon's edge counts as a flat plate, and — more importantly — when it does not.
 *
 * The refusals are the interesting half. A fill is only safe to apply by default while the foreground already
 * covers it completely, so anything that would make it *visible* has to be declined: a rounded icon whose corners
 * are transparent, a drop shadow's soft edge, a gradient. Getting one of those wrong does not fail loudly — it
 * silently changes the look of an icon nobody asked to change.
 */
class LegacyBackgroundTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun ring(size: Int = 100, pixel: Int) = IntArray(size) { pixel }

    @Test
    fun `a flat opaque edge yields its own color`() {
        val blue = argb(255, 30, 120, 200)

        assertEquals(blue, LegacyBackground.detectFill(ring(pixel = blue)))
    }

    @Test
    fun `near-identical pixels average to one color, so antialiasing does not defeat it`() {
        val pixels = IntArray(100) { index ->
            // A plate that is one color give or take a couple of levels, as a real bitmap is after compression.
            argb(255, 100 + index % 3, 100 + index % 3, 100 + index % 3)
        }

        val fill = LegacyBackground.detectFill(pixels)!!

        assertEquals(255, fill ushr 24 and 0xFF)
        // 34 pixels at 100 and 33 each at 101 and 102 sum to 10099, which is 100 after integer division — the mean
        // is truncated, not rounded, and one level of drift on a plate color is not worth carrying a Float for.
        assertEquals(100, fill shr 16 and 0xFF)
    }

    @Test
    fun `a mostly transparent edge yields nothing, so a glyph on transparency is left alone`() {
        val pixels = IntArray(100) { index ->
            if (index < 40) argb(255, 200, 200, 200) else argb(0, 0, 0, 0)
        }

        assertNull(LegacyBackground.detectFill(pixels))
    }

    @Test
    fun `transparent corners alone are enough to decline, which is the rounded-icon case`() {
        // The reason the solid threshold is near-total rather than a simple majority. A rounded legacy icon has an
        // opaque edge everywhere but its corners — and filling it would square the icon off, which is precisely the
        // visible change this must not make.
        val pixels = IntArray(100) { index -> if (index < 92) argb(255, 40, 40, 40) else argb(0, 0, 0, 0) }

        assertNull(LegacyBackground.detectFill(pixels))
    }

    @Test
    fun `a semi-transparent edge yields nothing, which is the drop-shadow case`() {
        val pixels = IntArray(100) { argb(140, 40, 40, 40) }

        assertNull(LegacyBackground.detectFill(pixels))
    }

    @Test
    fun `a gradient edge yields nothing`() {
        val pixels = IntArray(100) { index -> argb(255, index * 2, index * 2, index * 2) }

        assertNull(LegacyBackground.detectFill(pixels))
    }

    @Test
    fun `a few stray pixels do not veto an obviously flat plate`() {
        // Why the test is a consensus rather than a variance: an antialiased logo edge that happens to reach the
        // border is a handful of wildly different pixels, and a standard deviation would let them win.
        val pixels = IntArray(100) { index ->
            if (index < 95) argb(255, 200, 30, 30) else argb(255, 255, 255, 255)
        }

        assertEquals(argb(255, 202, 41, 41), LegacyBackground.detectFill(pixels))
    }

    @Test
    fun `an empty ring yields nothing rather than throwing`() {
        assertNull(LegacyBackground.detectFill(IntArray(0)))
    }
}
