package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.BloomFalloff
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.ShapeAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which way a bloom's light runs, and where it sits.
 *
 * Pure convention, and therefore exactly the kind of thing two renderers come to disagree about if each inlines its
 * own two lines of trigonometry: nothing crashes, the light simply runs the other way in the editor than on the home
 * screen. These pin the convention so that cannot drift silently.
 *
 * **The anchored cases are the ones worth the most here.** `ShapeMask` had to split its pure decision from its
 * matrix assembly because `android.graphics.Matrix` stubs to a no-op in a JVM test; this file places a bloom without
 * a matrix at all, so the placement that would otherwise be untestable is the part being tested.
 */
class LayerGradientTest {

    private val box = LayerGradient.Frame.box(sizePx = 100)
    private val identity = LayerTransform(zoom = 1f, rotationDegrees = 0f, translateXPx = 0f, translateYPx = 0f)

    private fun endpoints(angle: Float) = LayerGradient.endpoints(box, angle).toList()

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
    fun `the span is always the full frame, so a diagonal is not foreshortened`() {
        // Both endpoints sit on the circle through the frame's edge midpoints, so the ramp covers the same distance
        // at every angle. Otherwise turning the angle would visibly compress it.
        val (x0, y0, x1, y1) = endpoints(45f)

        assertEquals(100f, kotlin.math.hypot(x1 - x0, y1 - y0), 0.01f)
    }

    @Test
    fun `endpoints scale with the box, so one angle looks the same at every bake size`() {
        val small = LayerGradient.endpoints(LayerGradient.Frame.box(50), 90f).toList()
        val large = LayerGradient.endpoints(LayerGradient.Frame.box(200), 90f).toList()

        assertEquals(small.map { it * 4f }, large)
    }

    @Test
    fun `a full radius reaches the corners rather than the edges`() {
        // The whole difference from the endpoints, which measure to the edge midpoints: a disc asked to cover its
        // frame has to reach the furthest point, or a radius of 1 would leave four unlit corners.
        val radial = LayerGradient.radial(box, radiusFraction = 1f)

        assertEquals(50f, radial.centerX, 0.01f)
        assertEquals(50f, radial.centerY, 0.01f)
        assertEquals(kotlin.math.hypot(50f, 50f), radial.radiusPx, 0.01f)
    }

    @Test
    fun `a radius of nothing still comes back positive`() {
        // `RadialGradient` throws on a non-positive radius, so this is the guard for a stored recipe that never went
        // through `Bloom.isIdentity` — invisible, but not a crash.
        assertTrue(LayerGradient.radial(box, radiusFraction = 0f).radiusPx > 0f)
        assertTrue(LayerGradient.radial(box, radiusFraction = -3f).radiusPx > 0f)
    }

    @Test
    fun `fading out keeps the color and drops only the alpha`() {
        // Not `Color.TRANSPARENT`: a ramp to transparent *black* drags a white bloom through gray on the way out,
        // which reads as a dirty edge rather than as light.
        assertEquals(0x00FFFFFF, LayerGradient.fadeOut(0xFFFFFFFF.toInt()))
        assertEquals(0x0033AA77, LayerGradient.fadeOut(0xFF33AA77.toInt()))
    }

    @Test
    fun `a box-anchored bloom ignores the layer's transform entirely`() {
        // The whole meaning of the anchor: the light stays put while the content slides under it.
        val moved = LayerTransform(zoom = 2f, rotationDegrees = 30f, translateXPx = 20f, translateYPx = -10f)
        val frame = LayerGradient.frameOf(bloom(), fit = smallInk, transform = moved, sizePx = 100)

        assertEquals(box, frame)
    }

    @Test
    fun `a content-anchored bloom sits on the ink rather than the box`() {
        val frame = LayerGradient.frameOf(
            bloom(anchor = ShapeAnchor.CONTENT),
            fit = smallInk,
            transform = identity,
            sizePx = 100,
        )

        // The ink is a quarter-box square centered at (0.25, 0.75), so the light is laid out there and nowhere else.
        assertEquals(25f, frame.centerX, 0.01f)
        assertEquals(75f, frame.centerY, 0.01f)
        assertEquals(25f, frame.sizePx, 0.01f)
    }

    @Test
    fun `content anchoring carries the layer's zoom, rotation and offset`() {
        // The load-bearing case. The frame has to go through the *same* movement the artwork does, or the light
        // drifts off the ink — and drifting is invisible in the editor, which is drawing it the same wrong way.
        val turned = LayerTransform(zoom = 2f, rotationDegrees = 90f, translateXPx = 10f, translateYPx = 0f)
        val frame = LayerGradient.frameOf(
            bloom(anchor = ShapeAnchor.CONTENT),
            fit = smallInk,
            transform = turned,
            sizePx = 100,
        )

        // Ink center is (-25, +25) from the box center; doubled it is (-50, +50), and a quarter turn clockwise
        // sends that to (-50, -50). Plus the box center and the offset: (10, 0).
        assertEquals(10f, frame.centerX, 0.01f)
        assertEquals(0f, frame.centerY, 0.01f)
        assertEquals(50f, frame.sizePx, 0.01f)
        assertEquals(90f, frame.rotationDegrees, 0.01f)
    }

    @Test
    fun `a ramp turns with the artwork it is anchored to`() {
        // The frame's rotation adds to the bloom's own angle, which is what makes "top to bottom" mean the
        // artwork's top once the layer has been turned.
        val turned = LayerTransform(zoom = 1f, rotationDegrees = 90f, translateXPx = 0f, translateYPx = 0f)
        val frame = LayerGradient.frameOf(
            bloom(anchor = ShapeAnchor.CONTENT),
            fit = ShapeMask.InkFit.Box,
            transform = turned,
            sizePx = 100,
        )

        // 0° in a frame turned 90° is the same picture as 90° in an unturned one: left to right.
        assertEquals(
            LayerGradient.endpoints(box, 90f).toList(),
            LayerGradient.endpoints(frame, 0f).toList(),
        )
    }

    @Test
    fun `the bloom's own offset is in frame units and turns with it`() {
        val turned = LayerTransform(zoom = 1f, rotationDegrees = 90f, translateXPx = 0f, translateYPx = 0f)
        val nudged = bloom(anchor = ShapeAnchor.CONTENT).copy(offsetX = 0.5f)
        val frame = LayerGradient.frameOf(nudged, ShapeMask.InkFit.Box, turned, sizePx = 100)

        // Half a frame "right" in a frame turned a quarter clockwise is half a box *down* on screen — which is what
        // makes a highlight placed on a corner of the artwork stay on that corner when the layer turns.
        assertEquals(50f, frame.centerX, 0.01f)
        assertEquals(100f, frame.centerY, 0.01f)
    }

    @Test
    fun `an offset alone still moves a box-anchored bloom`() {
        val frame = LayerGradient.frameOf(bloom().copy(offsetY = -0.25f), smallInk, identity, sizePx = 100)

        assertEquals(50f, frame.centerX, 0.01f)
        assertEquals(25f, frame.centerY, 0.01f)
    }

    @Test
    fun `a sheen at zero degrees is lit from the top`() {
        // The same convention `endpoints` runs on, and the one that would be silently inverted if each renderer had
        // placed the disc itself: the light sits on the side the angle names, which at 0° is the top.
        val sweep = LayerGradient.sweep(box, angleDegrees = 0f, curve = 0.5f)

        assertEquals(50f, sweep.centerX, 0.01f)
        assertTrue("the disc sits above the frame", sweep.centerY < 0f)
        assertTrue(sweep.litInside)
    }

    @Test
    fun `a quarter turn lights the left, matching the ramp's own direction`() {
        val sweep = LayerGradient.sweep(box, angleDegrees = 90f, curve = 0.5f)

        assertTrue("the disc sits left of the frame", sweep.centerX < 0f)
        assertEquals(50f, sweep.centerY, 0.01f)
    }

    @Test
    fun `flattening the curve pushes the disc away rather than changing which side is lit`() {
        // What the magnitude means: a bigger circle crosses the frame as a straighter edge. If this ever came out
        // *smaller* at zero the control would read as inverted, which is a look you would tune around rather than
        // report.
        val bowed = LayerGradient.sweep(box, angleDegrees = 0f, curve = 1f)
        val flat = LayerGradient.sweep(box, angleDegrees = 0f, curve = 0f)

        assertTrue(flat.radiusPx > bowed.radiusPx)
        assertTrue(flat.litInside)
    }

    @Test
    fun `the sign flips which side of the arc is lit, not where the light comes from`() {
        // The whole of what makes one signed slider legitimate: both keep the light on the angle's side, and only
        // the way the boundary bows changes. If the sign were a half turn instead, the control would duplicate the
        // angle and there would be two ways to say one thing.
        val outward = LayerGradient.sweep(box, angleDegrees = 0f, curve = 0.5f)
        val inward = LayerGradient.sweep(box, angleDegrees = 0f, curve = -0.5f)

        assertTrue(outward.litInside)
        assertTrue(!inward.litInside)
        // Mirrored about the frame, which is what "bows the other way" is: one disc above, one below.
        assertTrue(outward.centerY < 50f)
        assertTrue(inward.centerY > 50f)
    }

    @Test
    fun `the boundary lands on the frame's center at every curve`() {
        // The reason there are four stops rather than two: the transition has to stay the same visual width whatever
        // the disc's size, or flattening the curve would fade the sheen out — a control undoing itself.
        listOf(0f, 0.25f, 0.5f, 1f).forEach { curve ->
            val sweep = LayerGradient.sweep(box, angleDegrees = 0f, curve = curve)
            val distanceToCenter = kotlin.math.hypot(50f - sweep.centerX, 50f - sweep.centerY)
            val boundary = (sweep.stops[1] + sweep.stops[2]) / 2f

            assertEquals("curve $curve", distanceToCenter / sweep.radiusPx, boundary, 0.01f)
        }
    }

    @Test
    fun `stops ascend, which every gradient constructor requires`() {
        listOf(-1f, -0.3f, 0f, 0.3f, 1f).forEach { curve ->
            val stops = LayerGradient.sweep(box, angleDegrees = 37f, curve = curve).stops

            assertEquals("curve $curve", stops.sorted(), stops)
        }
    }

    @Test
    fun `the lit color is on the inside or the outside, and never both`() {
        val lit = 0xFF102030.toInt()
        val outward = LayerGradient.sweep(box, angleDegrees = 0f, curve = 0.5f).colorsOf(lit)
        val inward = LayerGradient.sweep(box, angleDegrees = 0f, curve = -0.5f).colorsOf(lit)

        assertEquals(lit, outward.first())
        assertEquals(LayerGradient.fadeOut(lit), outward.last())
        // Reversed, which is the one thing a renderer assembling its own colors would eventually get wrong.
        assertEquals(LayerGradient.fadeOut(lit), inward.first())
        assertEquals(lit, inward.last())
    }

    private fun bloom(anchor: ShapeAnchor = ShapeAnchor.BOX) =
        LayerEffect.Bloom(falloff = BloomFalloff.LINEAR, anchor = anchor)

    /** A quarter-box square of ink, sitting low and to the left — deliberately not centered, so a bug shows. */
    private val smallInk = ShapeMask.InkFit(scale = 0.25f, centerX = 0.25f, centerY = 0.75f)
}
