package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.parse.ContentMetrics
import inkspire.morphic.core.icon.parse.ParsedLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame a content-anchored shape is fitted to.
 *
 * Only [ShapeMask.inkFit] is exercised, and that is the point of the split rather than a gap: the matrix half is
 * `android.graphics.Matrix`, which a JVM unit test can only stub into no-ops, so asserting against it would pin
 * nothing while looking like it pinned everything. `LayerTransformTest` leaves `toMatrix` alone for the same reason.
 *
 * What is left is the arithmetic that would be **silently** wrong: a silhouette fitted to the wrong square is still
 * a silhouette on an icon, so nothing throws and nothing looks broken until someone compares it to the artwork.
 */
class ShapeMaskTest {

    private fun image(metrics: ContentMetrics?) = ParsedLayer.Image(StubDrawable(), metrics)

    @Test
    fun `ink filling its canvas fits the whole box`() {
        val fit = ShapeMask.inkFit(image(ContentMetrics(left = 0f, top = 0f, right = 1f, bottom = 1f)))

        assertTrue(fit.isBox)
    }

    @Test
    fun `a centered blob fits a square of its own size, still centered`() {
        // Ink covering the middle half of the canvas: the shape shrinks onto it rather than staying at box size,
        // which is the whole of what "cut against the artwork" buys over "cut against the box".
        val fit = ShapeMask.inkFit(image(ContentMetrics(left = 0.25f, top = 0.25f, right = 0.75f, bottom = 0.75f)))

        assertEquals(0.5f, fit.scale, 0.001f)
        assertEquals(0.5f, fit.centerX, 0.001f)
        assertEquals(0.5f, fit.centerY, 0.001f)
        assertFalse(fit.isBox)
    }

    @Test
    fun `off-center ink moves the frame with it`() {
        // The case the box anchor cannot express at all: artwork sitting in a corner is cropped by a shape it does
        // not touch, so the frame has to follow the ink and not just its size.
        val fit = ShapeMask.inkFit(image(ContentMetrics(left = 0f, top = 0f, right = 0.4f, bottom = 0.4f)))

        assertEquals(0.4f, fit.scale, 0.001f)
        assertEquals(0.2f, fit.centerX, 0.001f)
        assertEquals(0.2f, fit.centerY, 0.001f)
    }

    @Test
    fun `a wide logo gets a square as wide as it is, not one squashed to its height`() {
        // Fitting the *rectangle* would make a circle an ellipse. The longest side is the side, always — so the
        // frame here is 0.8 on both axes even though the ink is only 0.2 tall.
        val fit = ShapeMask.inkFit(image(ContentMetrics(left = 0.1f, top = 0.4f, right = 0.9f, bottom = 0.6f)))

        assertEquals(0.8f, fit.scale, 0.001f)
        // Centered on the ink on both axes, which is what keeps the square over the logo rather than beside it.
        assertEquals(0.5f, fit.centerX, 0.001f)
        assertEquals(0.5f, fit.centerY, 0.001f)
    }

    @Test
    fun `unmeasured content falls back to the box rather than to nothing`() {
        // A pack drawable, an imported image and a flat fill are never measured. Fitting them to a zero-size frame
        // would erase the layer; refusing the anchor outright would make the control inert on them. Both are worse
        // than the box, which still follows the transform.
        assertTrue(ShapeMask.inkFit(image(metrics = null)).isBox)
        assertTrue(ShapeMask.inkFit(ParsedLayer.Color(argb = 0xFF00FF00.toInt())).isBox)
    }

    @Test
    fun `a degenerate measurement falls back to the box, since it would divide the shape away`() {
        assertTrue(ShapeMask.inkFit(image(ContentMetrics(left = 0.5f, top = 0.5f, right = 0.5f, bottom = 0.5f))).isBox)
    }
}
