package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The slope of the layer's height field — the derivation a bevel and a glass share.
 *
 * [LayerBevelTest]'s reason, one step earlier: this is the Sobel both of them read, and every failure it has is
 * silent. A transposed axis lights a bevel along the wrong edge and shears a glass the wrong way; a flipped sign
 * points both the opposite direction. Neither throws. The check is that the two renderers cannot each hold their
 * own copy to be wrong in — there is one, and this pins its sign.
 */
class LayerSurfaceTest {

    private val out = FloatArray(2)

    /** A `size`×`size` field whose height is [height] at each pixel. */
    private fun field(size: Int, height: (x: Int, y: Int) -> Float) =
        FloatArray(size * size) { height(it % size, it / size) }

    @Test
    fun `a flat field slopes nowhere`() {
        val heights = field(3) { _, _ -> 0.5f }
        LayerSurface.slope(heights, sizePx = 3, x = 1, y = 1, scale = 1f, out = out)

        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0f, out[1], 0.0001f)
    }

    @Test
    fun `a field rising to the right has a positive x-slope and no y-slope`() {
        // Height climbs with x at half a unit per pixel; the Sobel reads exactly that, and nothing sideways.
        val heights = field(3) { x, _ -> x / 2f }
        LayerSurface.slope(heights, sizePx = 3, x = 1, y = 1, scale = 1f, out = out)

        assertEquals(0.5f, out[0], 0.0001f)
        assertEquals(0f, out[1], 0.0001f)
    }

    @Test
    fun `a field rising downward has a positive y-slope and no x-slope`() {
        // The axis that a transpose would swap — pinned so the bevel and the glass cannot disagree about which way
        // is down.
        val heights = field(3) { _, y -> y / 2f }
        LayerSurface.slope(heights, sizePx = 3, x = 1, y = 1, scale = 1f, out = out)

        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
    }

    @Test
    fun `the scale multiplies the slope`() {
        val heights = field(3) { x, _ -> x / 2f }
        LayerSurface.slope(heights, sizePx = 3, x = 1, y = 1, scale = 4f, out = out)

        assertEquals(2f, out[0], 0.0001f)
    }

    @Test
    fun `the border reads as a continuation, so a uniform field is flat even at its edge`() {
        // Clamping is what stops the box's own edge reading as a cliff — an edge treated as a drop would slope the
        // whole rim of a full-bleed layer, which a glass would refract as a fringe around nothing.
        val heights = field(3) { _, _ -> 0.5f }
        LayerSurface.slope(heights, sizePx = 3, x = 0, y = 0, scale = 1f, out = out)

        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0f, out[1], 0.0001f)
    }
}
