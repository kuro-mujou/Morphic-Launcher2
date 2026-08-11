package inkspire.morphic.core.icon.layer

import inkspire.morphic.core.icon.IconShape
import kotlinx.serialization.Serializable

/**
 * One layer in an [IconLayerSet]: its [role], where its content comes from ([source]), and how it is drawn.
 *
 * Transform values live in the icon's normalized square box: [offsetX]/[offsetY] are fractions of that box
 * (0 = centered), [zoom] is a scale (1 = the default fit, >1 zooms in), [rotation] is in degrees clockwise.
 * [shape] masks fg/bg layers to a silhouette and is ignored for custom layers (they keep their own alpha), so
 * it defaults to `null` (unshaped). [effects] is the extensible, defaulted-empty effect bag (see
 * [LayerEffect]).
 *
 * @property visible When false the layer is skipped in the composite but kept in the set (an editor hide
 *   toggle). *(Assumption — a standard layer-editor affordance; flag if not wanted for v1.)*
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
    val effects: List<LayerEffect> = emptyList(),
)
