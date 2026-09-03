package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.PolygonCascadeGenerator.CascadeShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The cascade's counts and the proportions of its six shapes.
 *
 * **The two measured shapes are what this file is really for.** A regular polygon is right or obviously wrong, but
 * the star's inner radius and the rectangle's aspect are numbers read off the reference, and a wrong one draws a
 * shape that is perfectly plausible and simply is not theirs — which no render of ours can show. Both are pinned
 * here against the measurement rather than against the constant that holds them.
 */
class PolygonCascadeGeneratorTest {

    @Test
    fun `density maps to the copy count range`() {
        assertEquals(2, PolygonCascadeGenerator.copyCount(0f))
        assertEquals(24, PolygonCascadeGenerator.copyCount(1f))
        assertEquals(2, PolygonCascadeGenerator.copyCount(-1f)) // clamped
        assertEquals(24, PolygonCascadeGenerator.copyCount(2f)) // clamped
    }

    @Test
    fun `variant picks a shape from the vocabulary, clamped to it`() {
        // Index 0 is the design's default look, and theirs opens on a star.
        assertEquals(CascadeShape.STAR, PolygonCascadeGenerator.shapeOf(0))
        assertEquals(CascadeShape.CIRCLE, PolygonCascadeGenerator.shapeOf(1))
        assertEquals(CascadeShape.RECTANGLE, PolygonCascadeGenerator.shapeOf(20)) // clamped at the end
        assertEquals(CascadeShape.STAR, PolygonCascadeGenerator.shapeOf(-5)) // and at the start
    }

    @Test
    fun `every shape is a ring of angle-radius pairs whose greatest radius is the circumradius`() {
        for (shape in CascadeShape.entries) {
            val ring = PolygonCascadeGenerator.ring(shape)
            assertEquals("$shape: a ring is interleaved angle, radius", 0, ring.size % 2)
            assertTrue("$shape: a ring needs at least a triangle's worth", ring.size >= 3 * 2)
            val radii = radiiOf(ring)
            // The size knob is expressed as a circumradius, so a shape whose radii exceeded 1 would draw larger than
            // the knob says and one whose greatest fell short would draw smaller — silently, at every setting.
            assertEquals("$shape: greatest radius is not the circumradius", 1f, radii.max(), 1e-4f)
            assertTrue("$shape: a radius left the unit ring", radii.all { it > 0f && it <= 1f + 1e-4f })
        }
    }

    @Test
    fun `the regular shapes have their side count and sit on the circumradius`() {
        val sides = mapOf(CascadeShape.TRIANGLE to 3, CascadeShape.SQUARE to 4, CascadeShape.HEXAGON to 6)
        for ((shape, count) in sides) {
            val ring = PolygonCascadeGenerator.ring(shape)
            assertEquals("$shape: wrong vertex count", count, ring.size / 2)
            assertTrue("$shape: a vertex left the circumradius", radiiOf(ring).all { abs(it - 1f) < 1e-4f })
        }
    }

    @Test
    fun `the star is a five-pointer whose inner radius is the measured share of its outer`() {
        val ring = PolygonCascadeGenerator.ring(CascadeShape.STAR)
        assertEquals(10, ring.size / 2)
        val radii = radiiOf(ring)
        assertTrue("the outer points are not on the circumradius", radii.filterIndexed { i, _ -> i % 2 == 0 }
            .all { abs(it - 1f) < 1e-4f })
        // Measured off the reference, and pointedly not the canonical pentagram's 0.382.
        assertTrue("the inner points are not at the measured radius", radii.filterIndexed { i, _ -> i % 2 == 1 }
            .all { abs(it - 0.451f) < 1e-4f })

        // The proportion the reference was recognized by: an unrotated five-pointer measures 1.0515 wide per tall.
        val (width, height) = extentOf(ring)
        assertEquals(1.0515f, width / height, 1e-3f)
    }

    @Test
    fun `the rectangle is twice as wide as it is tall`() {
        val ring = PolygonCascadeGenerator.ring(CascadeShape.RECTANGLE)
        assertEquals(4, ring.size / 2)
        val (width, height) = extentOf(ring)
        assertEquals(2f, width / height, 1e-3f)
    }

    /** Every radius in an interleaved angle-radius [ring], in vertex order. */
    private fun radiiOf(ring: FloatArray): List<Float> = (1 until ring.size step 2).map { ring[it] }

    /** A ring's width and height once its polar pairs are laid out — what the shape actually measures on screen. */
    private fun extentOf(ring: FloatArray): Pair<Float, Float> {
        val xs = (0 until ring.size step 2).map { cos(ring[it]) * ring[it + 1] }
        val ys = (0 until ring.size step 2).map { sin(ring[it]) * ring[it + 1] }
        return (xs.max() - xs.min()) to (ys.max() - ys.min())
    }
}
