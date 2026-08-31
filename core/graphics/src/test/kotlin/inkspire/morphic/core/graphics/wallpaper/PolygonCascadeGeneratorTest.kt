package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The cascade's counts and its polygon geometry — a polygon whose vertices do not close, sit off their radius, or come
 * out the wrong count is silently wrong geometry a bitmap only confirms after the fact.
 */
class PolygonCascadeGeneratorTest {

    @Test
    fun `density maps to the iteration count range`() {
        assertEquals(16, PolygonCascadeGenerator.iterationCount(0f))
        assertEquals(60, PolygonCascadeGenerator.iterationCount(1f))
        assertEquals(16, PolygonCascadeGenerator.iterationCount(-1f)) // clamped
        assertEquals(60, PolygonCascadeGenerator.iterationCount(2f)) // clamped
    }

    @Test
    fun `variant picks the side count, clamped to the supported range`() {
        assertEquals(3, PolygonCascadeGenerator.sides(0)) // triangle
        assertEquals(6, PolygonCascadeGenerator.sides(3))
        assertEquals(8, PolygonCascadeGenerator.sides(20)) // clamped at the cap
        assertEquals(3, PolygonCascadeGenerator.sides(-5)) // negative clamps to the floor
    }

    @Test
    fun `a polygon has one vertex per side plus a repeated first, closing the ring`() {
        val verts = PolygonCascadeGenerator.polygon(
            sides = 5, cx = 100f, cy = 100f, radius = 40f, rotation = 0f, jitterPx = 0f, random = Random(1),
        )
        assertEquals((5 + 1) * 2, verts.size)
        // The closing vertex repeats the first, so the stroked ring has no gap.
        assertEquals(verts[0], verts[10], 1e-4f)
        assertEquals(verts[1], verts[11], 1e-4f)
    }

    @Test
    fun `with no jitter every vertex sits on the radius`() {
        val cx = 100f
        val cy = 100f
        val radius = 40f
        val verts = PolygonCascadeGenerator.polygon(
            sides = 6, cx = cx, cy = cy, radius = radius, rotation = 0.3f, jitterPx = 0f, random = Random(2),
        )
        var i = 0
        while (i < verts.size) {
            assertEquals("vertex left its radius", radius, hypot(verts[i] - cx, verts[i + 1] - cy), 1e-3f)
            i += 2
        }
    }
}
