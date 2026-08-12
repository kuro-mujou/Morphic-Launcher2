package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.TintMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the color matrices actually do to a pixel.
 *
 * Asserting on the matrix *entries* would pin the implementation rather than the behaviour, so these push colors
 * through instead: the question is whether "saturation 0" greys a pixel, not whether row 1 column 2 holds 0.715.
 * That also makes them readable as a description of the effects, which matters because **two renderers share this
 * arithmetic** and a change here moves both at once.
 */
class LayerFilterTest {

    /** Applies a 4×5 color matrix to an RGB triple the way a graphics pipeline does, and clamps as one would. */
    private fun apply(matrix: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun channel(row: Int): Int {
            val value = matrix[row * 5] * r + matrix[row * 5 + 1] * g +
                matrix[row * 5 + 2] * b + matrix[row * 5 + 4]
            return value.toInt().coerceIn(0, 255)
        }
        return Triple(channel(0), channel(1), channel(2))
    }

    @Test
    fun `an identity effect produces no matrix at all, so the renderers can skip the work`() {
        assertNull(LayerFilter.colorMatrixOf(null))
        assertNull(LayerFilter.colorMatrixOf(LayerEffect.Color()))
    }

    @Test
    fun `zero saturation greys a color toward its luminance`() {
        val matrix = LayerFilter.colorMatrixOf(LayerEffect.Color(saturation = 0f))!!

        val (r, g, b) = apply(matrix, r = 255, g = 0, b = 0)

        assertEquals(r, g)
        assertEquals(g, b)
        // Rec. 709 puts pure red at ~21% luminance, which is the weighting Android's own setSaturation uses.
        assertEquals(54, r)
    }

    @Test
    fun `brightness scales the channels`() {
        val matrix = LayerFilter.colorMatrixOf(LayerEffect.Color(brightness = 0.5f))!!

        assertEquals(Triple(50, 100, 20), apply(matrix, r = 100, g = 200, b = 40))
    }

    @Test
    fun `a tint pushes channels toward it while keeping the layer's own shading`() {
        // Pure red tint: green and blue are scaled to nothing, red is untouched — and a mid-grey stays mid, which
        // is the "keeps its shading" half. A flat silhouette would come out fully saturated instead.
        val matrix = LayerFilter.colorMatrixOf(LayerEffect.Color(tintArgb = 0xFFFF0000.toInt()))!!

        assertEquals(Triple(128, 0, 0), apply(matrix, r = 128, g = 128, b = 128))
    }

    @Test
    fun `monochrome is saturation zero plus a tint, not a variant of its own`() {
        val matrix = LayerFilter.colorMatrixOf(
            LayerEffect.Color(saturation = 0f, tintArgb = 0xFF0000FF.toInt()),
        )!!

        val (r, g, b) = apply(matrix, r = 255, g = 0, b = 0)

        // Greyed first, then cast entirely into the blue channel.
        assertEquals(0, r)
        assertEquals(0, g)
        assertEquals(54, b)
    }

    @Test
    fun `a solid tint replaces the color outright, which is the thing a multiply cannot do`() {
        val matrix = LayerFilter.colorMatrixOf(
            LayerEffect.Color(tintArgb = 0xFFFFFFFF.toInt(), tintMode = TintMode.SOLID),
        )!!

        // The case the whole mode exists for: a black themed glyph made white. Under MULTIPLY this is still black,
        // because black times anything is black — which is why app-shipped monochrome layers could not be made to
        // agree with each other.
        assertEquals(Triple(255, 255, 255), apply(matrix, r = 0, g = 0, b = 0))
        // And it is flat: two different inputs land on the same output, since only alpha survives.
        assertEquals(Triple(255, 255, 255), apply(matrix, r = 200, g = 40, b = 90))
    }

    @Test
    fun `a solid tint lands on the color asked for, not a near-black one`() {
        // Pins the 0..255 translation convention. The fifth column is added to the channel directly, so a tint
        // written on a 0..1 scale would come out as 0-or-1 of 255 — visually black, and silently so.
        val matrix = LayerFilter.colorMatrixOf(
            LayerEffect.Color(tintArgb = 0xFF3366CC.toInt(), tintMode = TintMode.SOLID),
        )!!

        assertEquals(Triple(0x33, 0x66, 0xCC), apply(matrix, r = 128, g = 128, b = 128))
    }

    @Test
    fun `a solid tint spends the recoloring before it, because a flat color has no shading left`() {
        val matrix = LayerFilter.colorMatrixOf(
            LayerEffect.Color(
                saturation = 0f,
                brightness = 2f,
                hueDegrees = 90f,
                tintArgb = 0xFF00FF00.toInt(),
                tintMode = TintMode.SOLID,
            ),
        )!!

        // Not a special case in the code — it falls out of the matrix having no color coefficients to carry them.
        assertEquals(Triple(0, 255, 0), apply(matrix, r = 10, g = 20, b = 30))
    }

    @Test
    fun `tint mode alone changes nothing without a tint to apply`() {
        assertNull(LayerFilter.colorMatrixOf(LayerEffect.Color(tintMode = TintMode.SOLID)))
    }

    @Test
    fun `a full turn of hue returns a color to itself`() {
        val matrix = LayerFilter.colorMatrixOf(LayerEffect.Color(hueDegrees = 360f))!!

        val (r, g, b) = apply(matrix, r = 200, g = 60, b = 30)

        // Within a level for floating-point drift; the point is that the rotation is a rotation.
        assertEquals(200f, r.toFloat(), 1.5f)
        assertEquals(60f, g.toFloat(), 1.5f)
        assertEquals(30f, b.toFloat(), 1.5f)
    }

    @Test
    fun `order is hue then saturation then brightness then tint, so each control means its name`() {
        // The observable consequence of the order: greying happens before the tint, so a tinted greyscale keeps its
        // luminance. Were the tint applied first, saturation would then flatten the tint back out and the control
        // would appear to do nothing.
        val matrix = LayerFilter.colorMatrixOf(
            LayerEffect.Color(saturation = 0f, brightness = 2f, tintArgb = 0xFF00FF00.toInt()),
        )!!

        val (r, g, b) = apply(matrix, r = 100, g = 100, b = 100)

        assertEquals(0, r)
        assertEquals(200, g)
        assertEquals(0, b)
    }

    @Test
    fun `combining effects still yields one matrix`() {
        assertNotNull(
            LayerFilter.colorMatrixOf(
                LayerEffect.Color(tintArgb = 0xFF112233.toInt(), saturation = 0.5f, brightness = 1.2f, hueDegrees = 45f),
            ),
        )
    }
}
