package inkspire.morphic.core.model.widget

import kotlinx.serialization.Serializable

/**
 * One item in a widget: what it draws ([source]) and where, placed inside its parent — the widget itself, or an
 * [WidgetSource.Overlap] group.
 *
 * **Placement is an anchor plus an offset, never a coordinate.** A coordinate is only right at the size it was
 * authored at; an anchored offset keeps a clock in its corner however the widget is resized. The editor resolves a
 * drag to the nearest anchor plus what remains, so nobody types either.
 *
 * @property offsetX in dp from the [anchor] point, positive to the right.
 * @property offsetY in dp from the [anchor] point, positive downward.
 * @property rotation degrees clockwise, about the layer's center.
 * @property visible false hides the layer and keeps it — the editor's eye toggle, not a deletion.
 */
@Serializable
data class WidgetLayerSpec(
    val source: WidgetSource,
    val anchor: WidgetAnchor = WidgetAnchor.CENTER,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val width: WidgetExtent = WidgetExtent.Content,
    val height: WidgetExtent = WidgetExtent.Content,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val visible: Boolean = true,
)
