package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Perceptual mixing, and the round trip that has to hold for it to mean anything.
 *
 * [LayerTritoneTest]'s reason: only the bake reaches this, and the failures are silent — a transposed matrix
 * constant shifts every color a little without ever throwing, so the check is that a color survives the trip to
 * OKLab and back, and that a mix lands where its ends say.
 */
class OklabTest {

    private fun assertChannels(expected: Int, actual: Int, tolerance: Int = 2) {
        for (shift in intArrayOf(16, 8, 0)) {
            val e = expected shr shift and 0xFF
            val a = actual shr shift and 0xFF
            assertTrue("channel at $shift: expected ~$e, was $a", kotlin.math.abs(e - a) <= tolerance)
        }
    }

    @Test
    fun `mixing a color with itself returns it`() {
        // The round trip: sRGB to OKLab and back has to be the identity, or every mix drifts. Saturated colors are
        // the ones a bad matrix constant bends most, so they are the ones tested.
        for (argb in intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF3366CC.toInt(), 0xFFB65A78.toInt())) {
            assertChannels(argb, Oklab.mix(argb, argb, 0.5f))
        }
    }

    @Test
    fun `the ends of a mix are its endpoints`() {
        val violet = 0xFF241B4E.toInt()
        val sand = 0xFFFFD9A0.toInt()

        assertChannels(violet, Oklab.mix(violet, sand, 0f))
        assertChannels(sand, Oklab.mix(violet, sand, 1f))
    }

    @Test
    fun `t is clamped, so a segment cannot read past its ends`() {
        val a = 0xFF241B4E.toInt()
        val b = 0xFFFFD9A0.toInt()

        assertChannels(a, Oklab.mix(a, b, -1f))
        assertChannels(b, Oklab.mix(a, b, 2f))
    }

    @Test
    fun `the perceptual midpoint of black and white is a neutral gray between them`() {
        val mid = Oklab.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        val r = mid shr 16 and 0xFF
        val g = mid shr 8 and 0xFF
        val b = mid and 0xFF

        // Neutral: the three channels agree.
        assertEquals(r, g)
        assertEquals(g, b)
        // And a real mid-gray, not either end.
        assertTrue("was $r", r in 1..254)
    }
}
