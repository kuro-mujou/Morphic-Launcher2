package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mesh's determinism and its blend — the parts that must be right for a recipe to reproduce and for the field to
 * read as color rather than as mud. `BitmapBlur`'s reason: checkable without a bitmap.
 */
class MeshGradientGeneratorTest {

    private val palette = Palette(listOf(0xFF241B4E.toInt(), 0xFFB65A78.toInt(), 0xFFFFD9A0.toInt()))

    @Test
    fun `density maps to the point count range`() {
        assertEquals(4, MeshGradientGenerator.pointCount(0f))
        assertEquals(12, MeshGradientGenerator.pointCount(1f))
        // Out of range clamps rather than running off the ends.
        assertEquals(4, MeshGradientGenerator.pointCount(-1f))
        assertEquals(12, MeshGradientGenerator.pointCount(2f))
    }

    @Test
    fun `the same seed yields the same points, so a recipe reproduces`() {
        assertEquals(
            MeshGradientGenerator.points(count = 6, palette = palette, seed = 99L),
            MeshGradientGenerator.points(count = 6, palette = palette, seed = 99L),
        )
    }

    @Test
    fun `a different seed yields different points`() {
        assertTrue(
            MeshGradientGenerator.points(6, palette, seed = 1L) !=
                MeshGradientGenerator.points(6, palette, seed = 2L),
        )
    }

    @Test
    fun `points cycle through the palette, so every stop appears`() {
        val colors = MeshGradientGenerator.points(count = 6, palette = palette, seed = 0L).map { it.argb }.toSet()

        assertEquals(palette.colors.toSet(), colors)
    }

    @Test
    fun `a single point colors the whole field its own color`() {
        val one = listOf(MeshGradientGenerator.Point(x = 0.5f, y = 0.5f, argb = 0xFFB65A78.toInt()))

        assertEquals(0xFFB65A78.toInt(), MeshGradientGenerator.colorAt(0.5f, 0.5f, one))
        assertEquals(0xFFB65A78.toInt(), MeshGradientGenerator.colorAt(0f, 0f, one))
    }

    @Test
    fun `a pixel sitting on a point reads that point's color, not its neighbour's`() {
        val a = 0xFF241B4E.toInt()
        val points = listOf(
            MeshGradientGenerator.Point(x = 0.2f, y = 0.2f, argb = a),
            MeshGradientGenerator.Point(x = 0.8f, y = 0.8f, argb = 0xFFFFD9A0.toInt()),
        )

        // Exactly on the first point, its `1/ε` weight dwarfs the far one — the color is that point's within a hair.
        val onA = MeshGradientGenerator.colorAt(0.2f, 0.2f, points)
        for (shift in intArrayOf(16, 8, 0)) {
            assertTrue(kotlin.math.abs(((onA shr shift) and 0xFF) - ((a shr shift) and 0xFF)) <= 3)
        }
    }
}
