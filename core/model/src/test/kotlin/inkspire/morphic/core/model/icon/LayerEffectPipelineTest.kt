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
    fun `the whole icon filters its effects by the same rule a layer does`() {
        // One rule for both, which is why `activeEffects` is on the list rather than on either holder: a set whose
        // switched-off effects were filtered differently from a layer's would be a difference nobody thinks to look
        // for, and it would show up as one renderer drawing an effect the other skipped.
        val set = IconLayerSet.Base.copy(effects = listOf(tint.copy(enabled = false), bloom))

        assertEquals(listOf(bloom), set.activeEffects)
        assertEquals(2, set.effects.size)
    }

    @Test
    fun `reordering layers keeps the icon's own effects`() {
        // The trap this field brought with it: everything that rebuilds the stack must `copy` rather than call the
        // constructor, or a whole-icon effect silently disappears the moment a layer is moved — a loss with no error
        // and no obvious cause.
        val set = IconLayerSet(
            layers = listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.AppDefault),
                IconLayerSpec(role = LayerRole.FOREGROUND, source = LayerSource.AppDefault),
                IconLayerSpec(role = LayerRole.CUSTOM, source = LayerSource.Empty),
            ),
            effects = listOf(bloom),
        )

        assertEquals(listOf(bloom), set.moveUp(1).effects)
        assertEquals(listOf(bloom), set.moveDown(2).effects)
        // And a refused move returns the set itself, so there is nothing to lose on that path either.
        assertEquals(listOf(bloom), set.moveDown(0).effects)
    }

    /**
     * That an ordinary icon draws **live**, which is the regression that would cost the most: if this came back
     * false, every preview in the studio would silently take the baked path — slower, for nothing, on every icon
     * nobody has edited.
     */
    @Test
    fun `an ordinary icon draws live, so the preview stays on the fast path`() {
        assertTrue(IconLayerSet.Base.drawsLive)
        assertTrue(IconLayerSet.Base.copy(effects = listOf(bloom)).drawsLive)
        assertTrue(spec(tint, bloom).drawsLive)
        // Empty is the case that matters most, being every icon nobody has edited.
        assertTrue(emptyList<LayerEffect>().drawLive)
    }

    /**
     * And that a glow or a shadow sends the **whole icon** to the bake, from wherever it sits.
     *
     * These are the first two effects that answer `drawsLive` false, so this is the first test of the fallback at
     * all — it could not be written before them, `LayerEffect` being sealed and a test module unable to stub a
     * variant. Which is also why it is worth having now rather than being taken as obvious.
     */
    @Test
    fun `a blurred effect anywhere sends the whole icon to the bake`() {
        val glow = LayerEffect.Glow()
        val shadow = LayerEffect.Shadow()

        // On one layer. **Whole-icon rather than per-layer** — see `IconLayerSet.drawsLive` for why a hybrid stack
        // is the worst version of the two-renderer problem rather than the cheapest version of this one.
        val onLayer = IconLayerSet.Base.let {
            it.copy(layers = it.layers.mapIndexed { i, l -> if (i == 0) l.copy(effects = listOf(glow)) else l })
        }
        assertFalse(onLayer.drawsLive)

        // And on the composite, which has no layer to belong to.
        assertFalse(IconLayerSet.Base.copy(effects = listOf(shadow)).drawsLive)
    }

    @Test
    fun `a switched-off glow leaves the icon on the fast path`() {
        // `activeEffects` filters before anything asks, so an effect nobody can see must not drag the preview onto
        // the slow path — which would be a cost with nothing on screen to show for it.
        assertTrue(IconLayerSet.Base.copy(effects = listOf(LayerEffect.Glow(enabled = false))).drawsLive)
        // Same for one turned down to nothing, which is the *effect's* own answer rather than the user's switch.
        assertTrue(IconLayerSet.Base.copy(effects = listOf(LayerEffect.Glow(strength = 0f))).drawsLive)
    }

    @Test
    fun `an icon with no effects of its own costs nothing on disk`() {
        // Additive, like every field before it: `encodeDefaults = false` plus an empty default means the recipes
        // written before whole-icon effects existed read back byte-identical.
        val json = Json { encodeDefaults = false }
        val encoded = json.encodeToString(IconLayerSet.serializer(), IconLayerSet.Base)

        assertFalse(encoded.contains("effects"))
        assertEquals(IconLayerSet.Base, json.decodeFromString(IconLayerSet.serializer(), encoded))
    }

    @Test
    fun `a radial bloom that reaches nowhere paints nothing`() {
        // Not cosmetic: `RadialGradient` rejects a non-positive radius outright, so this is what keeps the one value
        // a slider can always be dragged to from reaching either renderer.
        val nowhere = LayerEffect.Bloom(falloff = Falloff.RADIAL, radius = 0f)

        assertTrue(nowhere.isIdentity)
        // The same radius is meaningless to a linear ramp, which spans the box whatever it says.
        assertFalse(nowhere.copy(falloff = Falloff.LINEAR).isIdentity)
    }

    @Test
    fun `a recipe that switched an effect off does say so`() {
        val json = Json { encodeDefaults = false }
        val off = bloom.copy(enabled = false)

        assertTrue(json.encodeToString(LayerEffect.serializer(), off).contains("\"enabled\":false"))
        assertEquals(off, json.decodeFromString(LayerEffect.serializer(), json.encodeToString(LayerEffect.serializer(), off)))
    }
}
