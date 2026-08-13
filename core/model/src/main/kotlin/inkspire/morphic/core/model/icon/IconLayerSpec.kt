package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * One layer in an [IconLayerSet]: its [role], where its content comes from ([source]), and how it is drawn.
 *
 * Transform values live in the icon's normalized square box: [offsetX]/[offsetY] are fractions of that box
 * (0 = centered), [zoom] is a scale (1 = the default fit, >1 zooms in), [rotation] is in degrees clockwise.
 * [shape] masks a layer to a silhouette, so it defaults to `null` (unshaped). [effects] is the extensible,
 * defaulted-empty effect bag (see [LayerEffect]).
 *
 * [opacity] and [blend] are **compositing** properties rather than effects: every layer has both, always, with a
 * meaningful default, which is what makes them fields. See [LayerBlend].
 *
 * @property visible When false the layer is skipped in the composite but kept in the set (an editor hide
 *   toggle). *(Assumption — a standard layer-editor affordance; flag if not wanted for v1.)*
 * @property normalize Whether this layer's artwork is resized so it covers about as much of its box as every other
 *   icon's. Apps author at wildly different sizes — some fill their whole canvas, some leave a wide margin — and
 *   nothing in the platform enforces otherwise, so left alone a home screen is visibly uneven.
 *
 *   **Off by default.** It rewrites how every app's icon looks, which is a change to make because someone asked for
 *   it rather than one to arrive with: an unconfigured launcher draws each app's artwork the size its author drew
 *   it. Turning it on is one tap in the studio's Source panel, and one edit on the global default reaches every app.
 *   It defaulted *on* until this was reversed, so stored recipes that never mentioned it — the field is not written
 *   when it matches the default — read back as off. That is the intended effect and needs no migration; a recipe
 *   that had it explicitly off still does.
 *
 *   **Foreground only.** `IconLayerResolver` measures whatever *this* layer resolved to and rescales that layer
 *   alone; a background is never resized, because a plate changes what sits behind the artwork rather than how large
 *   the artwork should be — so an app comes out the same size whether its background is drawn or switched off. An
 *   intermediate version applied one factor to every layer at once and this note used to describe it.
 */
@Serializable
data class IconLayerSpec(
    val role: LayerRole,
    val source: LayerSource,
    val visible: Boolean = true,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f,
    val shape: IconShape? = null,
    val opacity: Float = 1f,
    val blend: LayerBlend = LayerBlend.NORMAL,
    val normalize: Boolean = false,
    val effects: List<LayerEffect> = emptyList(),
) {

    /** The layer's color effect, or null when it has none. At most one is meaningful — see [LayerEffect.Color]. */
    val color: LayerEffect.Color?
        get() = effects.filterIsInstance<LayerEffect.Color>().firstOrNull()?.takeIf { !it.isIdentity }

    /** Replaces (or clears) this layer's color effect, leaving every other effect in place and in order. */
    fun withColor(color: LayerEffect.Color?): IconLayerSpec {
        val rest = effects.filterNot { it is LayerEffect.Color }
        return copy(effects = if (color == null || color.isIdentity) rest else rest + color)
    }

    /** The layer's gradient overlay, or null when it has none. */
    val gradient: LayerEffect.Gradient?
        get() = effects.filterIsInstance<LayerEffect.Gradient>().firstOrNull()?.takeIf { !it.isIdentity }

    /** Replaces (or clears) this layer's gradient overlay, leaving every other effect in place and in order. */
    fun withGradient(gradient: LayerEffect.Gradient?): IconLayerSpec {
        val rest = effects.filterNot { it is LayerEffect.Gradient }
        return copy(effects = if (gradient == null || gradient.isIdentity) rest else rest + gradient)
    }
}
