package inkspire.morphic.core.model.widget

import kotlinx.serialization.Serializable

/**
 * Which point of its parent a layer is pinned to — the same point of the layer lands on it, and
 * [WidgetLayerSpec.offsetX]/[WidgetLayerSpec.offsetY] move it from there.
 *
 * Anchoring is what lets one recipe draw at any cell size: resizing a widget moves its layers with the corners and
 * edges they belong to rather than scaling them. **Left and right are absolute, not start and end** — a widget is a
 * picture someone drew, and a right-to-left locale does not mirror a picture.
 *
 * Stored by name.
 */
@Serializable
enum class WidgetAnchor {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, CENTER, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT,
}
