package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crest arithmetic and the two measurements the design is built on — the shadow's default and the palette turn.
 *
 * A crest decides which band a pixel is in and whether a band shows at all, and it is bounded arithmetic that needs no
 * canvas. The two measured constants are checked here because they are the kind of thing a later tidy-up rounds off
 * without noticing: `0.5` reproducing the reference's shading, and the `±20°/±20pp` turn.
 */
class WavesGeneratorTest {

    private val ripple = WavesGenerator.Lobe(
        listOf(
            WavesGenerator.Term(amplitude = 0.65f, frequency = 2f, phase = 0.4f),
            WavesGenerator.Term(amplitude = 0.35f, frequency = 3f, phase = 1.1f),
        ),
    )

    @Test
    fun `density maps to the crest count range`() {
        assertEquals(1, WavesGenerator.layerCount(0f))
        assertEquals(10, WavesGenerator.layerCount(1f))
        assertEquals(10, WavesGenerator.layerCount(2f)) // clamped
    }

    @Test
    fun `an undistorted crest is the smoothstep between its two edge heights`() {
        val left = 0.2f
        val right = 0.6f
        assertEquals(left, WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 0f), 1e-6f)
        assertEquals(right, WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 1f), 1e-6f)
        // Halfway is halfway for any S with this symmetry; the quarter point is what tells 3t²-2t³ from a half-cosine.
        assertEquals(0.4f, WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 0.5f), 1e-6f)
        assertEquals(
            left + (right - left) * (3f * 0.25f * 0.25f - 2f * 0.25f * 0.25f * 0.25f),
            WavesGenerator.crestAt(left, right, ripple, distortion = 0f, nx = 0.25f),
            1e-6f,
        )
    }

    @Test
    fun `equal edge heights leave the crest flat, however far distortion is pushed`() {
        var nx = 0f
        while (nx <= 1f) {
            val flat = WavesGenerator.crestAt(0.5f, 0.5f, ripple, distortion = 0f, nx = nx)
            assertEquals("a crest with equal edges strayed off them at $nx", 0.5f, flat, 1e-6f)
            nx += 0.05f
        }
    }

    @Test
    fun `distortion never moves a crest off its edge heights`() {
        val left = 0.2f
        val right = 0.6f
        assertEquals(left, WavesGenerator.crestAt(left, right, ripple, distortion = 1f, nx = 0f), 1e-6f)
        assertEquals(right, WavesGenerator.crestAt(left, right, ripple, distortion = 1f, nx = 1f), 1e-6f)
    }

    @Test
    fun `distortion moves the crest between its edges, and bounded`() {
        var moved = false
        var nx = 0f
        while (nx <= 1f) {
            val sweep = WavesGenerator.crestAt(0.3f, 0.7f, ripple, distortion = 0f, nx = nx)
            val warped = WavesGenerator.crestAt(0.3f, 0.7f, ripple, distortion = 1f, nx = nx)
            val off = warped - sweep
            assertTrue("distortion pushed the crest past its sweep at $nx", off in -0.3001f..0.3001f)
            if (off > 0.05f || off < -0.05f) moved = true
            nx += 0.02f
        }
        assertTrue("full distortion left the crest on its sweep", moved)
    }

    @Test
    fun `the default depth reproduces the reference's shading`() {
        assertEquals(0f, WavesGenerator.shadowDepth(0f), 1e-6f)
        // Measured off theirs: a band's top multiplies to x0.815 at the crest, which is a shadow of 0.185.
        assertEquals(0.185f, WavesGenerator.shadowDepth(0.5f), 1e-4f)
        assertEquals(0.37f, WavesGenerator.shadowDepth(1f), 1e-6f)
        assertEquals(0.37f, WavesGenerator.shadowDepth(2f), 1e-6f) // clamped
    }

    @Test
    fun `the gradient fill turns a color by twenty degrees and twenty points, both ways`() {
        // The reference's own blue, and one of the four pairs the transform was measured from: H 217.2 S 91.2 L 59.8.
        val blue = 0xFF3B82F6.toInt()

        // ... turning down lands on the measured (9, 141, 194): H 197.2, the same saturation, L 39.8.
        val down = WavesGenerator.turned(blue, up = false)
        assertChannels(expected = 0xFF098DC2.toInt(), actual = down)

        // Up is the same turn the other way, so a round trip through both is the color again.
        val back = WavesGenerator.turned(WavesGenerator.turned(blue, up = true), up = false)
        assertChannels(expected = blue, actual = back)
    }

    /** [actual]'s channels against [expected]'s, to within a byte or two of the rounding a color round trip costs. */
    private fun assertChannels(expected: Int, actual: Int) {
        assertEquals("alpha", expected ushr 24 and 0xFF, actual ushr 24 and 0xFF)
        assertEquals("red", (expected shr 16 and 0xFF).toDouble(), (actual shr 16 and 0xFF).toDouble(), 2.0)
        assertEquals("green", (expected shr 8 and 0xFF).toDouble(), (actual shr 8 and 0xFF).toDouble(), 2.0)
        assertEquals("blue", (expected and 0xFF).toDouble(), (actual and 0xFF).toDouble(), 2.0)
    }
}
