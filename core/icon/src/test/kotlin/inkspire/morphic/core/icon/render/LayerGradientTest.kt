package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which way a gradient angle runs.
 *
 * Pure convention, and therefore exactly the kind of thing two renderers come to disagree about if each inlines
 * its own two lines of trigonometry: nothing crashes, the gradient simply runs the other way in the editor than on
 * the home screen. These pin the convention so that cannot drift silently.
 */
class LayerGradientTest {

    private fun endpoints(angle: Float) = LayerGradient.endpoints(angle, sizePx = 100).toList()

    @Test
    fun `zero degrees runs top to bottom`() {
        val (x0, y0, x1, y1) = endpoints(0f)

        assertEquals(50f, x0, 0.01f)
        assertEquals(0f, y0, 0.01f)
        assertEquals(50f, x1, 0.01f)
        assertEquals(100f, y1, 0.01f)
    }

    @Test
    fun `ninety degrees runs left to right, so the angle turns clockwise`() {
        val (x0, y0, x1, y1) = endpoints(90f)

        assertEquals(0f, x0, 0.01f)
        assertEquals(50f, y0, 0.01f)
        assertEquals(100f, x1, 0.01f)
        assertEquals(50f, y1, 0.01f)
    }

    @Test
    fun `a half turn swaps the ends rather than changing the axis`() {
        val (x0, y0, x1, y1) = endpoints(180f)

        assertEquals(50f, x0, 0.01f)
        assertEquals(100f, y0, 0.01f)
        assertEquals(50f, x1, 0.01f)
        assertEquals(0f, y1, 0.01f)
    }

    @Test
    fun `the span is always the full box, so a diagonal is not foreshortened`() {
        // Both endpoints sit on the circle through the box's edge midpoints, so the gradient covers the same
        // distance at every angle. Otherwise turning the angle would visibly compress the gradient.
        val (x0, y0, x1, y1) = endpoints(45f)
        val span = kotlin.math.hypot(x1 - x0, y1 - y0)

        assertEquals(100f, span, 0.01f)
    }

    @Test
    fun `endpoints scale with the box, so one angle looks the same at every bake size`() {
        val small = LayerGradient.endpoints(90f, sizePx = 50).toList()
        val large = LayerGradient.endpoints(90f, sizePx = 200).toList()

        assertEquals(small.map { it * 4f }, large)
    }
}
