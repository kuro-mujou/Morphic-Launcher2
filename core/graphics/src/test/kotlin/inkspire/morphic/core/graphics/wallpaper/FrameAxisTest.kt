package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shared projection, checked where it is shared rather than inside either design that uses it.
 *
 * **The axis has to span exactly `0..1` corner to corner, at every angle and every aspect**, because that is what
 * everything built on it is a fraction *of* — Diagonal Bands' coverage, Louvers' strip index and ramp position. Short
 * of it, a slab drifts off the frame at some angles and not others, which reads as the design being uneven rather
 * than as arithmetic being wrong, and is invisible in any single render.
 */
class FrameAxisTest {

    @Test
    fun `the axis spans exactly zero to one over the frame, at every angle`() {
        for (degrees in listOf(-90f, -20f, 0f, 20f, 45f, 90f, 135f, 160f, 250f)) {
            val axis = frameAxis(degrees, width = 1080, height = 2400)
            val corners = listOf(0f to 0f, 1079f to 0f, 1079f to 2399f, 0f to 2399f)
                .map { (x, y) -> axis.at(x, y) }
            assertEquals("$degrees°: some corner must sit at 0", 0f, corners.min(), 1e-5f)
            assertEquals("$degrees°: some corner must sit at 1", 1f, corners.max(), 1e-5f)
        }
    }

    @Test
    fun `zero degrees runs left to right and ninety runs top to bottom`() {
        // Screen coordinates, clockwise from the horizontal — the whole reason a direction can be given as one number.
        val across = frameAxis(0f, 1080, 2400)
        assertEquals(across.at(600f, 0f), across.at(600f, 2399f), 1e-6f)
        assertTrue(across.at(0f, 0f) < across.at(1079f, 0f))

        val down = frameAxis(90f, 1080, 2400)
        assertEquals(down.at(0f, 1200f), down.at(1079f, 1200f), 1e-6f)
        assertTrue(down.at(0f, 0f) < down.at(0f, 2399f))
    }

    @Test
    fun `a perpendicular pair moves independently`() {
        // What Louvers rests on: walking along the strips must not change which strip you are in, at any angle. The
        // two axes normalize by different spans, so this is the property to assert rather than a dot product of the
        // readings — those are not perpendicular once each has been divided by its own frame extent.
        for (degrees in listOf(-90f, -20f, 0f, 33f)) {
            val across = frameAxis(degrees, 1080, 2400)
            val alongRadians = Math.toRadians((degrees + 90f).toDouble())
            val stepX = cos(alongRadians).toFloat() * 300f
            val stepY = sin(alongRadians).toFloat() * 300f
            assertEquals(
                "$degrees°: a step along the strips must not move across them",
                across.at(540f, 1200f),
                across.at(540f + stepX, 1200f + stepY),
                1e-5f,
            )
        }
    }

    @Test
    fun `a shallow angle draws shallow on the screen, not on the unit square`() {
        // The point of projecting in pixels: a 20° axis must fall 20° across the *screen*. Walking the frame's full
        // width should therefore move it by width·cos(20°) in pixels, against height·sin(20°) down it.
        val axis = frameAxis(20f, 1080, 2400)
        val acrossWidth = axis.at(1079f, 0f) - axis.at(0f, 0f)
        val expected = 1079f * cos(Math.toRadians(20.0)).toFloat()
        val span = expected + 2399f * sin(Math.toRadians(20.0)).toFloat()
        assertEquals(expected / span, acrossWidth, 1e-4f)
    }

    @Test
    fun `a frame with no extent along the axis reads as its middle`() {
        // A one-pixel strip has nothing to divide by; the middle is the honest answer rather than a crash or a NaN.
        assertEquals(0.5f, frameAxis(0f, width = 1, height = 2400).at(0f, 0f), 1e-6f)
    }
}
