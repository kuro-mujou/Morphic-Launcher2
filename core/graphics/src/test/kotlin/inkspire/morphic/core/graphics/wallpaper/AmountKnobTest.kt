package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The count knob's two directions, which have to be exact inverses: the generator asks it what to draw at a density,
 * and the Style panel asks it what density lands on a count. A panel that derived the second itself would drop the
 * user a count either side of the one they dragged to, on some ranges only — silent, and a different set of ranges
 * each time a generator's bounds are tuned.
 */
class AmountKnobTest {

    @Test
    fun `density spans the range and clamps outside it`() {
        val knob = AmountKnob.Count("Bands", 4..22)
        assertEquals(4, knob.at(0f))
        assertEquals(13, knob.at(0.5f))
        assertEquals(22, knob.at(1f))
        assertEquals(4, knob.at(-1f))
        assertEquals(22, knob.at(2f))
    }

    @Test
    fun `every count in the range is reachable, and round-trips`() {
        // The wide one and a narrow one: rounding has the most room to disagree where the steps are coarse.
        for (knob in listOf(AmountKnob.Count("Strokes", 300..1200), AmountKnob.Count("Blobs", 3..9))) {
            for (count in knob.range) {
                assertEquals(count, knob.at(knob.densityFor(count)))
            }
        }
    }

    @Test
    fun `a count outside the range reads as the nearer end`() {
        val knob = AmountKnob.Count("Rings", 4..18)
        assertEquals(0f, knob.densityFor(1), 1e-6f)
        assertEquals(1f, knob.densityFor(99), 1e-6f)
    }

    @Test
    fun `a single-count range has nothing to interpolate`() {
        val knob = AmountKnob.Count("Only", 5..5)
        assertEquals(5, knob.at(0.7f))
        assertEquals(0f, knob.densityFor(5), 1e-6f)
    }
}
