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
 * icon and the difference would only show up as "the tint looks wrong on the bloom" on one surface.
 */
class LayerEffectPipelineTest {

    private val tint = LayerEffect.Color(tintArgb = 0xFFFF0000.toInt())
    private val bloom = LayerEffect.Bloom(strength = 0.5f)

    private fun spec(vararg effects: LayerEffect) = IconLayerSpec(
        role = LayerRole.FOREGROUND,
        source = LayerSource.AppDefault,
        effects = effects.toList(),
    )

    @Test
    fun `effects are applied in the order they are listed`() {
        assertEquals(listOf(tint, bloom), spec(tint, bloom).activeEffects)
        // The same two the other way round is a different pipeline, not the same one normalised.
        assertEquals(listOf(bloom, tint), spec(bloom, tint).activeEffects)
    }

    @Test
    fun `a disabled effect is kept in the list and left out of the pipeline`() {
        // The whole point of `enabled`: switching an effect off must not discard what was tuned into it.
        val off = tint.copy(enabled = false)
        val spec = spec(off, bloom)

        assertEquals(listOf(off, bloom), spec.effects)
        assertEquals(listOf(bloom), spec.activeEffects)
    }

    @Test
    fun `an effect that would paint nothing is left out too`() {
        // Two different reasons to skip — the user's switch, and the effect saying it is a no-op — and the pipeline
        // must not care which, or every renderer would have to ask both questions itself.
        assertEquals(emptyList<LayerEffect>(), spec(LayerEffect.Color(), LayerEffect.Bloom(strength = 0f)).activeEffects)
    }

    @Test
    fun `a layer draws live only while every one of its effects can`() {
        // An effect that cannot be drawn live cannot simply be skipped: a preview missing one effect is a preview
        // that lies, so the whole layer falls back to its bake.
        assertTrue(spec(tint, bloom).drawsLive)
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
    fun `a bloom still stores itself as a gradient, so old recipes keep loading`() {
        // The discriminator is deliberately stale — see `LayerEffect.Bloom`. An unknown *key* is skipped, but an
        // unknown polymorphic *type* throws, and `IconLayerSetCodec` drops the whole recipe on a throw. So renaming
        // it would cost a user every customized icon rather than one effect's colors. Silent and total, so pinned.
        val json = Json { encodeDefaults = false }
        val encoded = json.encodeToString(LayerEffect.serializer(), bloom)

        assertTrue(encoded.contains("\"gradient\""))
        // The geometry is all defaulted, so an untouched bloom costs nothing on disk.
        assertFalse(encoded.contains("falloff"))
        assertFalse(encoded.contains("radius"))
        assertFalse(encoded.contains("anchor"))
        assertFalse(encoded.contains("offset"))
        // And a recipe predating them comes back as the plain top-to-bottom ramp it was.
        assertEquals(bloom, json.decodeFromString(LayerEffect.serializer(), """{"type":"gradient","strength":0.5}"""))
    }

    @Test
    fun `a recipe written with two stops loses them and nothing else`() {
        // The accepted cost of one color replacing two: the old keys are unknown now, so `ignoreUnknownKeys` drops
        // them and the color defaults. Everything the effect still has a field for survives, which is what makes
        // this a downgrade rather than a corruption.
        val json = Json { ignoreUnknownKeys = true }
        val stored = """{"type":"gradient","startArgb":-16711936,"endArgb":-65536,"angleDegrees":90.0,"strength":0.5}"""

        val decoded = json.decodeFromString(LayerEffect.serializer(), stored) as LayerEffect.Bloom

        assertEquals(0xFFFFFFFF.toInt(), decoded.argb)
        assertEquals(90f, decoded.angleDegrees, 0.001f)
        assertEquals(0.5f, decoded.strength, 0.001f)
    }

    @Test
    fun `a radial bloom that reaches nowhere paints nothing`() {
        // Not cosmetic: `RadialGradient` rejects a non-positive radius outright, so this is what keeps the one value
        // a slider can always be dragged to from reaching either renderer.
        val nowhere = LayerEffect.Bloom(falloff = BloomFalloff.RADIAL, radius = 0f)

        assertTrue(nowhere.isIdentity)
        // The same radius is meaningless to a linear ramp, which spans the box whatever it says.
        assertFalse(nowhere.copy(falloff = BloomFalloff.LINEAR).isIdentity)
    }

    @Test
    fun `a recipe that switched an effect off does say so`() {
        val json = Json { encodeDefaults = false }
        val off = bloom.copy(enabled = false)

        assertTrue(json.encodeToString(LayerEffect.serializer(), off).contains("\"enabled\":false"))
        assertEquals(off, json.decodeFromString(LayerEffect.serializer(), json.encodeToString(LayerEffect.serializer(), off)))
    }
}
