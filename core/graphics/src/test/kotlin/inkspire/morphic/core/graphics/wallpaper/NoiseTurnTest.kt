package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The turn between two seeds' noise. Its whole reason is one property — the two weights' squares sum to one at every
 * moment — and a straight blend would pass every other check here while flattening the middle of each scrub.
 */
class NoiseTurnTest {

    @Test
    fun `a turn starts on the first field and ends on the second`() {
        assertEquals(0.3f, turnNoise(0.3f, -0.8f, 0f), 1e-6f)
        assertEquals(-0.8f, turnNoise(0.3f, -0.8f, 1f), 1e-6f)
    }

    @Test
    fun `the two weights' squares sum to one at every moment, so the swing holds`() {
        for (step in 0..20) {
            val t = step / 20f
            val first = turnNoise(1f, 0f, t)
            val second = turnNoise(0f, 1f, t)
            assertEquals("at $t", 1f, first * first + second * second, 1e-5f)
        }
    }
}
