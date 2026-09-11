package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The harmonics' turn into another seed's: it must land on the other seed's own bend, or a scrub jumps at its end. */
class SeededHarmonicsTest {

    private val weights = floatArrayOf(0.5f, 0.3f, 0.2f)
    private val harmonics = floatArrayOf(3f, 5f, 7f)

    @Test
    fun `a turn starts on one seed's bend and ends on the other's`() {
        val a = SeededHarmonics(weights, harmonics, Random(1))
        val b = SeededHarmonics(weights, harmonics, Random(2))
        for (x in listOf(0f, 0.7f, 2.1f, 4.4f)) {
            assertEquals(a.at(x), a.turnedTo(b, 0f).at(x), 1e-6f)
            assertEquals(b.at(x), a.turnedTo(b, 1f).at(x), 1e-5f)
        }
    }
}
