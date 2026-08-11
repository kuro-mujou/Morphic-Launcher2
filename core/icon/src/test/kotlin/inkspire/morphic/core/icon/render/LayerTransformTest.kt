package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conventions a layer's transform is read by — pinned because **two renderers read them**, and a disagreement
 * between the two would produce an icon that looks correct in the editor and wrong on every surface.
 *
 * Every assertion here is a *choice* rather than a fact: that an offset is a fraction and not a pixel count, which
 * way is positive, and what zoom and rotation pivot around. None of them would fail loudly if changed — the icon
 * would just quietly sit somewhere else.
 */
class LayerTransformTest {

    private fun spec(
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        zoom: Float = 1f,
        rotation: Float = 0f,
    ) = IconLayerSpec(
        role = LayerRole.FOREGROUND,
        source = LayerSource.AppDefault,
        offsetX = offsetX,
        offsetY = offsetY,
        zoom = zoom,
        rotation = rotation,
    )

    @Test
    fun `an untouched layer is identity`() {
        assertTrue(LayerTransform.of(spec(), sizePx = 192).isIdentity)
    }

    @Test
    fun `an offset is a fraction of the box, so it scales with the bake size`() {
        // The property this exists for: one stored set must land in the same *place* whether it is baked at 96px
        // for a list row or 288px for a folder. Storing pixels would make a set look different per surface.
        assertEquals(19.2f, LayerTransform.of(spec(offsetX = 0.1f), sizePx = 192).translateXPx, 0.001f)
        assertEquals(38.4f, LayerTransform.of(spec(offsetX = 0.1f), sizePx = 384).translateXPx, 0.001f)
    }

    @Test
    fun `a half-box offset moves content by half the box, which fixes the unit`() {
        val transform = LayerTransform.of(spec(offsetX = 0.5f, offsetY = -0.5f), sizePx = 100)

        assertEquals(50f, transform.translateXPx, 0.001f)
        // Negative Y is up: the fraction follows the canvas, not a maths-class axis.
        assertEquals(-50f, transform.translateYPx, 0.001f)
    }

    @Test
    fun `zoom and rotation pass through untouched, being unitless`() {
        val transform = LayerTransform.of(spec(zoom = 1.5f, rotation = 90f), sizePx = 192)

        assertEquals(1.5f, transform.zoom, 0.001f)
        assertEquals(90f, transform.rotationDegrees, 0.001f)
        assertFalse(transform.isIdentity)
    }

    @Test
    fun `a rotated layer is not identity even with no offset or zoom`() {
        // Guards the skip-the-work shortcut: treating "no translation" as "nothing to do" would silently drop
        // every rotation, which is the sort of thing an `isIdentity` gets wrong exactly once.
        assertFalse(LayerTransform.of(spec(rotation = 45f), sizePx = 192).isIdentity)
        assertFalse(LayerTransform.of(spec(zoom = 0.8f), sizePx = 192).isIdentity)
    }
}
