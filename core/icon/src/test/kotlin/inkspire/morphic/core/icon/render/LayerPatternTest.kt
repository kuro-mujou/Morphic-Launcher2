package inkspire.morphic.core.icon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How large a pattern's tile is and when the tiling is turned.
 *
 * **The size is the assertion worth having**, because it is the one thing a tiled shader can get wrong invisibly:
 * a texture at half the intended scale is still a texture, and only a side-by-side against the bake would show it.
 * Both renderers ask this, so a difference here would be exactly the two-renderer bug the whole `render` package is
 * arranged to prevent.
 *
 * [LayerPattern.tile] and the non-null half of [LayerPattern.localMatrix] are absent for the reason
 * `LayerTransformTest` leaves `toMatrix` alone: they reach for `android.graphics`, which stubs to no-ops on the JVM,
 * so a test of them would assert nothing while looking like it asserted something.
 */
class LayerPatternTest {

    @Test
    fun `a tile is a fraction of the box, so one recipe textures the same at every bake size`() {
        // The property this exists for: a quarter-scale pattern must put four tiles across the icon whether it is
        // baked at 96px for a list row or 288px for a folder. A pixel size would texture them differently.
        assertEquals(24, LayerPattern.tileSizePx(scale = 0.25f, sizePx = 96))
        assertEquals(72, LayerPattern.tileSizePx(scale = 0.25f, sizePx = 288))
    }

    @Test
    fun `a full-scale tile fills the box exactly once`() {
        assertEquals(192, LayerPattern.tileSizePx(scale = 1f, sizePx = 192))
    }

    @Test
    fun `a tile never comes back smaller than a few pixels`() {
        // A shader repeating a one-pixel bitmap is a flat wash that costs a texture — the arithmetic allows it and a
        // slider at its floor on a small bake reaches it, so the result would read as the effect being broken.
        assertTrue(LayerPattern.tileSizePx(scale = 0.001f, sizePx = 96) >= 4)
        assertTrue(LayerPattern.tileSizePx(scale = 0f, sizePx = 96) >= 4)
    }

    @Test
    fun `a square-on tiling needs no matrix at all`() {
        // Nothing to do with patterns' own sizing, but the same "return null rather than an identity" bargain the
        // rest of this package makes — kept here because it is this file's own function.
        // The common case, and the reason this returns null rather than an identity: it lets both renderers skip
        // building and binding one for every unrotated pattern.
        assertNull(LayerPattern.localMatrix(angleDegrees = 0f, sizePx = 192))
        // A full turn is the same tiling, which the modulo is there to notice.
        assertNull(LayerPattern.localMatrix(angleDegrees = 360f, sizePx = 192))
        assertNull(LayerPattern.localMatrix(angleDegrees = -720f, sizePx = 192))
    }
}
