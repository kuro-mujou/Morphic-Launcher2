package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The ring count, the pitch, and the distance-to-loop mapping — the fraction must stay in `0..1` (it indexes the
 * looped palette), must repeat once per ring, and must measure a distance that is round **on the screen**.
 */
class RingsGeneratorTest {

    /** A tall phone: 1080×2400. Every aspect-sensitive case is measured on one, since a square frame hides the bug. */
    private val phone = 2400f / 1080f

    /** The pitch is now a count over a span, so a test picks its own span rather than deriving one from the frame. */
    private val span = hypot(1f, phone)

    @Test
    fun `density maps to the ring count range`() {
        assertEquals(4, RingsGenerator.ringCount(0f))
        assertEquals(18, RingsGenerator.ringCount(1f))
        assertEquals(4, RingsGenerator.ringCount(-1f)) // clamped
    }

    @Test
    fun `the origin reads as the start of the cycle`() {
        val perUnit = RingsGenerator.ringsPerUnit(10, span)
        assertEquals(0f, RingsGenerator.ringFraction(0.5f, 0.5f, 0.5f, 0.5f, phone, perUnit), 1e-6f)
    }

    /**
     * The bug this design carried until the quality pass: a `hypot` of two shares-of-their-own-side draws **ellipses**
     * on any frame that is not square. Two points the same number of *pixels* from the centre — one across, one down —
     * must land on the same ring; before the fix the one below it was more than two rings out.
     */
    @Test
    fun `a ring is round on the screen, not in the unit square`() {
        val perUnit = RingsGenerator.ringsPerUnit(10, span)
        val across = RingsGenerator.ringFraction(0.5f + 0.2f, 0.5f, 0.5f, 0.5f, phone, perUnit)
        // The same pixel distance downward is a smaller share of the taller side, by exactly the aspect.
        val down = RingsGenerator.ringFraction(0.5f, 0.5f + 0.2f / phone, 0.5f, 0.5f, phone, perUnit)
        assertEquals(across, down, 1e-5f)
    }

    /**
     * The pitch has to put exactly the asked-for number of cycles across whatever span it is given — which is what
     * makes the *Rings* slider's number the number of bands a person can count, however far out the origin is placed.
     */
    @Test
    fun `the count is rings across the span it is given`() {
        for (span in listOf(0.4f, 1f, hypot(1f, phone), 3f)) {
            val perUnit = RingsGenerator.ringsPerUnit(10, span)
            assertEquals("span $span", 10f, span * perUnit, 1e-4f)
        }
    }

    @Test
    fun `the fraction stays in the unit range`() {
        val perUnit = RingsGenerator.ringsPerUnit(14, span)
        var nx = 0f
        while (nx <= 1f) {
            var ny = 0f
            while (ny <= 1f) {
                val f = RingsGenerator.ringFraction(nx, ny, 0.3f, 0.7f, phone, perUnit)
                assertTrue("fraction left the unit range: $f", f in 0f..1f)
                ny += 0.05f
            }
            nx += 0.05f
        }
    }

    @Test
    fun `distances a whole ring apart map to the same phase`() {
        val perUnit = RingsGenerator.ringsPerUnit(10, span)
        val oneRing = RingsGenerator.ringFraction(1f / perUnit, 0f, 0f, 0f, phone, perUnit)
        val twoRings = RingsGenerator.ringFraction(2f / perUnit, 0f, 0f, 0f, phone, perUnit)
        assertEquals(oneRing, twoRings, 1e-5f)
    }
}
