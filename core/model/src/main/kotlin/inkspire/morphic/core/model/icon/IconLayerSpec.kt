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
 * @property normalize Whether this icon is resized so it covers about as much of its box as every other icon. Apps
 *   author at wildly different sizes — some fill their whole canvas, some leave a wide margin — and nothing in the
 *   platform enforces otherwise, so left alone a home screen is visibly uneven.
 *
 *   **Read from the foreground and applied to the whole icon.** It is the app's-own-artwork toggle, and an icon has
 *   one size rather than one per layer: `IconLayerResolver` derives a single factor from the icon's visible extent
 *   and applies it to every drawn layer together, so the composition is untouched and only the overall size changes.
 *   Scaling the foreground alone was the earlier version, and it was invisible on any icon with a plate — the glyph
 *   moved inside a background that did not.
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
    val normalize: Boolean = true,
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
