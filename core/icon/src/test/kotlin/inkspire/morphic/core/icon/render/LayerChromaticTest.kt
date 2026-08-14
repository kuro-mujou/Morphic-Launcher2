package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which channel leads a chromatic split, and how far.
 *
 * **The direction is the assertion worth having, and it is pure convention** — a red fringe on the left and one on
 * the right both look like a lens. So it is exactly the kind of thing two renderers assembling their own copies would
 * come to disagree about, with nothing failing and nothing looking wrong until the editor and the home screen are
 * seen side by side.
 */
class LayerChromaticTest {

    private fun split(offsetX: Float = 0.02f, offsetY: Float = 0f) =
        LayerEffect.ChromaticSplit(offsetX = offsetX, offsetY = offsetY)

    /** The row-major 4×5 index of the coefficient taking [input] into [output]. */
    private fun coefficient(matrix: FloatArray, output: Int, input: Int) = matrix[output * 5 + input]

    @Test
    fun `red leads, blue trails, and green does not move`() {
        val (red, green, blue) = LayerChromatic.fringes(split(offsetX = 0.02f), sizePx = 100)

        assertEquals(2f, red.dxPx, 0.001f)
        assertEquals(0f, green.dxPx, 0.001f)
        assertEquals(-2f, blue.dxPx, 0.001f)
    }

    @Test
    fun `green holding still is what keeps the icon where it was`() {
        // Not an accident of ordering: the eye reads luminance mostly from green, so displacing it would shift the
        // whole icon rather than fringe it.
        val (_, green, _) = LayerChromatic.fringes(split(offsetX = 0.1f, offsetY = 0.1f), sizePx = 200)

        assertEquals(0f, green.dxPx, 0.001f)
        assertEquals(0f, green.dyPx, 0.001f)
    }

    @Test
    fun `the offset is a fraction of the box, so a fringe is the same width of the icon at every bake size`() {
        assertEquals(1.92f, LayerChromatic.fringes(split(), sizePx = 96).first().dxPx, 0.001f)
        assertEquals(5.76f, LayerChromatic.fringes(split(), sizePx = 288).first().dxPx, 0.001f)
    }

    @Test
    fun `both axes displace, so a fringe can run diagonally`() {
        val (red, _, blue) = LayerChromatic.fringes(split(offsetX = 0.05f, offsetY = -0.05f), sizePx = 100)

        assertEquals(5f, red.dxPx, 0.001f)
        assertEquals(-5f, red.dyPx, 0.001f)
        // Mirrored, which is what makes the two fringes opposite rather than merely different.
        assertEquals(-5f, blue.dxPx, 0.001f)
        assertEquals(5f, blue.dyPx, 0.001f)
    }

    @Test
    fun `each copy carries exactly one channel and the layer's alpha`() {
        // The alpha is what makes every copy keep the layer's silhouette; dropping it would fringe the *shape*
        // instead of the colour, which looks like three overlapping icons.
        LayerChromatic.fringes(split(), sizePx = 100).forEachIndexed { channel, fringe ->
            for (output in 0..2) {
                for (input in 0..2) {
                    val expected = if (output == channel && input == channel) 1f else 0f
                    assertEquals(
                        "row $output, column $input of channel $channel",
                        expected,
                        coefficient(fringe.matrix, output, input),
                        0.001f,
                    )
                }
            }
            assertEquals("alpha passes through", 1f, coefficient(fringe.matrix, output = 3, input = 3), 0.001f)
        }
    }

    @Test
    fun `nothing is translated, so no copy is shifted in brightness`() {
        // The fifth column is a 0..255 translation and every builder but a few leaves it alone. A stray value here
        // would lift one channel's black level, which reads as a colour cast rather than as a fringe.
        LayerChromatic.fringes(split(), sizePx = 100).forEach { fringe ->
            for (output in 0..3) {
                assertTrue(coefficient(fringe.matrix, output, input = 4) == 0f)
            }
        }
    }
}
