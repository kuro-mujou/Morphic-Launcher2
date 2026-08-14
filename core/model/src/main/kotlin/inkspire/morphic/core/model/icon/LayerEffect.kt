package inkspire.morphic.core.model.icon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A visual effect applied to a single layer while compositing — something that changes what the layer *is*.
 *
 * Deliberately an open sealed hierarchy held in a **list**, rather than one fixed nullable field per effect. That
 * is a direct lesson from L1, which hard-coded a column per effect and paid for it in repeated schema churn: here
 * a new effect is a new variant with `@SerialName`, and a stored recipe written by a build that has it still loads
 * on one that does not, because `ignoreUnknownKeys` drops what it cannot read.
 *
 * **Opacity and blend mode are deliberately *not* here.** They live on [IconLayerSpec] as fields, because they
 * describe how a layer joins the stack rather than what it looks like — see [LayerBlend].
 *
 * A **shadow** is the next variant, and the one that is not additive — see the plan's deferral note.
 */
/**
 * How a [LayerEffect.Color.tintArgb] is laid onto the layer it tints.
 *
 * **Two modes because a multiply cannot lift a color, and one real case needs lifting.** App-shipped themed-icon
 * layers are not consistent in the wild — some ship a black glyph, some white, some a colored one, some artwork that
 * is not a silhouette at all — and the platform's own contract is that *only their alpha is meaningful*, the consumer
 * tinting them to whatever it wants. A multiply cannot do that: black times any tint is still black, so a black glyph
 * can never be made white. [SOLID] is what makes the app's shipped color stop mattering.
 *
 * Persisted inside the layer set as part of [LayerEffect.Color], so the names are an on-disk contract.
 */
@Serializable
enum class TintMode {

    /**
     * Multiplies each channel by the tint's, pushing the layer toward that color **while keeping its own shading**.
     * The only mode there used to be, and still the right one for tinting artwork that has internal detail.
     */
    @SerialName("multiply")
    MULTIPLY,

    /**
     * Replaces the color outright and **keeps only the alpha**, so the layer becomes a flat silhouette of itself.
     * The platform's own treatment of a themed-icon layer, and the way to make apps that ship different colors agree.
     *
     * Everything that recolors is spent by this: [LayerEffect.Color.hueDegrees], `saturation` and `brightness` all
     * act on channels this then overwrites. That falls out of the matrix arithmetic rather than being special-cased,
     * and it is correct — a flat color has no shading left for them to act on.
     */
    @SerialName("solid")
    SOLID,
}

/**
 * How a [LayerEffect.Bloom]'s light falls off across the layer — which is the *only* thing separating its two forms.
 *
 * **Each form has exactly one geometric parameter, and it is not the same one**, which is why this is an enum rather
 * than a flag beside two always-visible sliders: a [LINEAR] bloom is decided by the direction it runs and spans its
 * frame whatever that direction is, where a [RADIAL] one is decided by how far out it reaches. Neither value can
 * answer the other's question, so the studio shows one slider or the other and this is what it asks. Where the light
 * *sits* is a question both can answer, so that is a field on the effect rather than part of this.
 *
 * Persisted inside the layer set, so the names are an on-disk contract. Defaults to [LINEAR], which is what this
 * effect was before it had a choice — so every stored recipe reads back rendering exactly as it did.
 */
@Serializable
enum class BloomFalloff {

    /** A ramp running across the whole frame along `angleDegrees`. The original, and still the common case. */
    @SerialName("linear")
    LINEAR,

    /** A disc reaching `radius` of the way to the frame's corners — a glow from a point, or a vignette. */
    @SerialName("radial")
    RADIAL,
}

@Serializable
sealed interface LayerEffect {

    /**
     * Whether this effect draws at all. **Off is not the same as absent**, which is the whole reason it exists: an
     * effect with five parameters and a color is something a user tunes and then wants to *compare against*, and
     * removing it from the list to switch it off would throw that tuning away. Absent from the list means never
     * configured; present and `false` means set up and silenced.
     *
     * Persisted, and defaulted true — with `encodeDefaults = false` on both icon stores, an effect nobody switched
     * off costs nothing on disk and every recipe written before this existed reads back unchanged.
     */
    val enabled: Boolean

    /**
     * True when this would not change a single pixel, so both renderers can skip it and the editor can tell a
     * configured effect from a default one.
     *
     * On the interface rather than per variant because [IconLayerSpec.activeEffects] filters the whole list at
     * once — before this it was two identically-named properties that no shared code could reach.
     */
    val isIdentity: Boolean

    /**
     * Whether the **live** render path can draw this effect, or whether the studio has to preview the layer from
     * its bake instead.
     *
     * **Not persisted** — it is a property of what we can implement, not of the icon — which is why it is a body
     * `get()` on each variant rather than a constructor parameter. The bake has no such flag because it has no
     * such limit: it owns a software bitmap, so a blur is a `BlurMaskFilter` and a displacement is arithmetic over
     * an `IntArray`, at every API level.
     *
     * Declared here so that adding an effect *forces* the question to be answered once, in the model, instead of
     * each renderer guessing. See `docs/ICON_EFFECTS_PLAN.md` §2.
     */
    val drawsLive: Boolean

    /**
     * Recoloring, as one color matrix: hue rotation, then saturation, then brightness, then an optional [tintArgb].
     *
     * One variant rather than four because they compose into a single matrix and are applied in one pass — splitting
     * them would mean four list entries whose *order* silently changed the result, which is a way to be wrong that
     * this shape simply does not have.
     *
     * **Monochrome is this, not a variant of its own**: `saturation = 0` with a [tintArgb] is a tinted grayscale,
     * which is what L1's monochrome fallback computed. Note that is a different thing from
     * [LayerSource.AppDefaultMonochrome], which swaps in artwork the *app* ships; this recolors whatever is there.
     *
     * @property tintArgb the color [tintMode] applies; null leaves the channels alone. The tint's own alpha is
     *   ignored — [IconLayerSpec] already has opacity, and two ways to set one thing is one too many.
     * @property tintMode how [tintArgb] is applied. Defaults to [TintMode.MULTIPLY], which is what a tint has
     *   always meant here, so every stored recipe reads back unchanged.
     * @property saturation 0 is grayscale, 1 unchanged, above 1 oversaturated.
     * @property brightness a plain multiplier on the color channels; 1 is unchanged.
     * @property hueDegrees rotation around the color wheel, 0 unchanged.
     */
    @Serializable
    @SerialName("color")
    data class Color(
        val tintArgb: Int? = null,
        val saturation: Float = 1f,
        val brightness: Float = 1f,
        val hueDegrees: Float = 0f,
        val tintMode: TintMode = TintMode.MULTIPLY,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        override val isIdentity: Boolean
            get() = tintArgb == null && saturation == 1f && brightness == 1f && hueDegrees == 0f

        /** A color matrix, which both paths already share through `LayerFilter`. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * Light spilling across the layer: [argb] fading out to nothing, painted **over it and clipped to it** —
     * source-atop, so it colors the artwork rather than covering the icon with a rectangle.
     *
     * **This is what used to be called `Gradient`, and it is one color now rather than two.** The rename is because
     * every other entry in this list names a *look* — a blend, a filter, a color — where "gradient" named the
     * mechanism. Fading to transparent rather than to a second chosen color is the bigger change and it is what
     * makes the effect usable: with two opaque stops, source-atop *replaces* every pixel it covers, so a
     * white-to-black bloom at full strength obliterated the artwork it was supposed to light. What is given up is the
     * two-arbitrary-stop duotone the general control also allowed; a tint plus a bloom reaches most of it.
     *
     * **The `@SerialName` stays `"gradient"` deliberately, even though the shape broke.** An unknown discriminator
     * is not skipped the way an unknown *key* is — it throws, and `IconLayerSetCodec` drops the **whole recipe** on
     * a throw, where an unreadable field costs one color. So the settings layer's rule that a key name is the seam
     * for a semantic break does not transfer here: the blast radius is a whole icon rather than one slice. Stored
     * recipes lose their two stops (the old keys are dropped, [argb] defaults to white) and keep everything else.
     *
     * @property falloff whether the light runs across the frame or out from a point in it. See [BloomFalloff] — it
     *   is what decides which of [angleDegrees] and [radius] means anything.
     * @property angleDegrees the direction it runs, clockwise from "straight down"; 0 is top-to-bottom.
     *   [BloomFalloff.LINEAR] only — a disc has no direction.
     * @property radius how far the light reaches, as a fraction of the way to the frame's corners; 1 covers it
     *   entirely. [BloomFalloff.RADIAL] only — a linear ramp always spans its frame.
     * @property offsetX where the light sits, as a fraction of the frame from its center. Positive is toward the
     *   frame's own right, which is the artwork's right under [ShapeAnchor.CONTENT] — so a bloom placed on a corner
     *   of the artwork stays on that corner when the layer turns.
     * @property offsetY the same, downward.
     * @property anchor what the light is placed against — the icon's box, or this layer's artwork carried by its
     *   transform. [ShapeAnchor.BOX] leaves it where it is put while the content slides underneath;
     *   [ShapeAnchor.CONTENT] sits it on the ink and moves, zooms and turns with it. The same question a shape mask
     *   asks, and answered by the same enum through the same derivation, which is what stops the two drifting apart.
     * @property strength how strongly it is laid on; 0 is invisible, 1 is the full color where the ramp starts. A
     *   separate knob from [argb]'s own alpha because the color picker has no alpha channel by design, and because
     *   this is the one a user reaches for.
     */
    @Serializable
    @SerialName("gradient")
    data class Bloom(
        val argb: Int = 0xFFFFFFFF.toInt(),
        val angleDegrees: Float = 0f,
        val strength: Float = 1f,
        val falloff: BloomFalloff = BloomFalloff.LINEAR,
        val radius: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val anchor: ShapeAnchor = ShapeAnchor.BOX,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /**
         * Painting nothing, either because it was turned down to nothing or because a radial one reaches nowhere.
         *
         * That second clause is **not** cosmetic: `RadialGradient` rejects a radius of zero or less outright, so
         * without it the one value a slider can always be dragged to would crash the bake. The renderers still
         * clamp, since a recipe is not obliged to be sensible — but an effect that would draw nothing should say so
         * here, where [IconLayerSpec.activeEffects] filters it out before either path is reached.
         */
        override val isIdentity: Boolean
            get() = strength <= 0f || (falloff == BloomFalloff.RADIAL && radius <= 0f)

        /** A shader drawn source-atop, which both paths can do at any API. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * One of the built-in colour looks, by id — see [IconFilter] for why the table lives in `core:icon` and why
     * this is a fixed vocabulary rather than curated content.
     *
     * **A whole matrix rather than the four numbers [Color] exposes**, which is the point of having both: the
     * sliders are for adjusting *this* icon, a filter is a look somebody authored that no combination of hue,
     * saturation, brightness and tint can reach — the channel mixing a sepia needs, or the lifted blacks of a matte
     * grade. They compose, and their order in the list decides which acts on which.
     *
     * @property filter the id. An unknown one resolves to no matrix and the effect draws nothing, so a recipe from
     *   a later build degrades rather than failing.
     */
    @Serializable
    @SerialName("filter")
    data class Filter(
        val filter: IconFilter,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** No id, nothing to look up. An id this build does not know is caught by the renderer, not here. */
        override val isIdentity: Boolean get() = filter.id.isBlank()

        /** A colour matrix, which both paths already share through `LayerFilter`'s own machinery. */
        override val drawsLive: Boolean get() = true
    }
}

/**
 * The effects that actually draw, **in the order they are applied** — which is the list's own order.
 *
 * **On the list rather than on [IconLayerSpec], because a layer is no longer the only thing that has effects.**
 * [IconLayerSet] carries its own, applied to the finished composite, and "which of these draw?" has to have one
 * answer for both — a set whose disabled effects were filtered by a different rule from a layer's would be a
 * difference nobody would think to look for.
 *
 * Two questions, deliberately answered together: [LayerEffect.enabled] is the user's switch,
 * [LayerEffect.isIdentity] is the effect saying it would paint nothing. No renderer should have to ask either.
 */
val List<LayerEffect>.activeEffects: List<LayerEffect>
    get() = filter { it.enabled && !it.isIdentity }

/**
 * Whether the **live** render path can draw every one of these, or whether the studio must preview from the bake.
 *
 * False if any single active effect says so — an effect that cannot be drawn cannot simply be skipped, since a
 * preview missing one effect is a preview that lies.
 */
val List<LayerEffect>.drawLive: Boolean
    get() = activeEffects.all { it.drawsLive }

/**
 * The one effect of type [T] that is doing something, or null.
 *
 * **This is the *editor's* view, where [activeEffects] is the *renderers'*.** It deliberately ignores
 * [LayerEffect.enabled], because a panel has to show the sliders of an effect you switched off — that is what
 * switching off rather than deleting is for. Anything deciding what to *draw* must read [activeEffects] instead.
 */
inline fun <reified T : LayerEffect> List<LayerEffect>.effectOrNull(): T? =
    filterIsInstance<T>().firstOrNull()?.takeIf { !it.isIdentity }

/**
 * These effects with the one of type [T] replaced, or removed when [effect] is null or would paint nothing.
 *
 * At most one of each type is meaningful, so this replaces rather than appends — and an effect at its defaults is
 * *dropped* rather than stored as a row of neutral numbers, which is what keeps an untouched recipe empty on disk.
 */
inline fun <reified T : LayerEffect> List<LayerEffect>.withEffect(effect: T?): List<LayerEffect> {
    val rest = filterNot { it is T }
    return if (effect == null || effect.isIdentity) rest else rest + effect
}
