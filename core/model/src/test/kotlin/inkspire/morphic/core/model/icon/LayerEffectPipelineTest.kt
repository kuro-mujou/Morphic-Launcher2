package inkspire.morphic.core.model.icon

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a layer's effect pipeline is, and what it leaves out.
 *
 * **The order is the assertion worth having.** Both renderers walk `activeEffects` front to back — the bake as
 * statements, the live path by folding the reversed list into nested modifiers — and neither can check the other.
 * If this list ever came back reordered, or filtered into a different order, the two would still each draw *an*
 * icon and the difference would only show up as "the tint looks wrong on the gradient" on one surface.
 */
class LayerEffectPipelineTest {

    private val tint = LayerEffect.Color(tintArgb = 0xFFFF0000.toInt())
    private val gradient = LayerEffect.Gradient(strength = 0.5f)

    private fun spec(vararg effects: LayerEffect) = IconLayerSpec(
        role = LayerRole.FOREGROUND,
        source = LayerSource.AppDefault,
        effects = effects.toList(),
    )

    @Test
    fun `effects are applied in the order they are listed`() {
        assertEquals(listOf(tint, gradient), spec(tint, gradient).activeEffects)
        // The same two the other way round is a different pipeline, not the same one normalised.
        assertEquals(listOf(gradient, tint), spec(gradient, tint).activeEffects)
    }

    @Test
    fun `a disabled effect is kept in the list and left out of the pipeline`() {
        // The whole point of `enabled`: switching an effect off must not discard what was tuned into it.
        val off = tint.copy(enabled = false)
        val spec = spec(off, gradient)

        assertEquals(listOf(off, gradient), spec.effects)
        assertEquals(listOf(gradient), spec.activeEffects)
    }

    @Test
    fun `an effect that would paint nothing is left out too`() {
        // Two different reasons to skip — the user's switch, and the effect saying it is a no-op — and the pipeline
        // must not care which, or every renderer would have to ask both questions itself.
        assertEquals(emptyList<LayerEffect>(), spec(LayerEffect.Color(), LayerEffect.Gradient(strength = 0f)).activeEffects)
    }

    @Test
    fun `a layer draws live only while every one of its effects can`() {
        // An effect that cannot be drawn live cannot simply be skipped: a preview missing one effect is a preview
        // that lies, so the whole layer falls back to its bake.
        assertTrue(spec(tint, gradient).drawsLive)
        assertTrue(spec().drawsLive)
    }

    @Test
    fun `a disabled effect cannot force the fallback`() {
        // It draws nothing, so it has no opinion about how the layer is previewed. Guards the obvious mistake of
        // asking `effects` rather than `activeEffects`.
        assertTrue(spec(tint.copy(enabled = false)).drawsLive)
    }

    @Test
    fun `enabled is not written when it is true, so recipes predating it read back unchanged`() {
        // `encodeDefaults = false` is what both icon stores serialize with; this pins that the new field is free.
        val json = Json { encodeDefaults = false }
        val encoded = json.encodeToString(LayerEffect.serializer(), tint)

        assertFalse(encoded.contains("enabled"))
        assertEquals(tint, json.decodeFromString(LayerEffect.serializer(), encoded))
    }

    @Test
    fun `a recipe that switched an effect off does say so`() {
        val json = Json { encodeDefaults = false }
        val off = gradient.copy(enabled = false)

        assertTrue(json.encodeToString(LayerEffect.serializer(), off).contains("\"enabled\":false"))
        assertEquals(off, json.decodeFromString(LayerEffect.serializer(), json.encodeToString(LayerEffect.serializer(), off)))
    }
}
