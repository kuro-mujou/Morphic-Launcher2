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
 * @property visibleGlobal a [WidgetGlobal.Switch] that decides whether a [visible] layer draws — how a design offers
 *   "Show date" without the person placing it ever seeing a layer.
 * @property name what the studio calls this layer. **A named layer at the top of a recipe is a block**: something
 *   the person placing the widget can select and restyle on its own. An unnamed one — a background panel — is part
 *   of the widget itself.
 * @property scale how much bigger than authored the layer is drawn, everything in it alike — what pinching a block
 *   sets. Applied in layout, not only in drawing, so a scaled layer still sits where its anchor puts it.
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
    val visibleGlobal: String? = null,
    val name: String? = null,
    val scale: Float = 1f,
)

/**
 * The layers that draw, under [globals]. The renderer lays out exactly these and the update cadence reads exactly
 * these — a hidden seconds clock counted by one and not the other would wake a widget every second to show nothing.
 */
fun List<WidgetLayerSpec>.drawn(globals: WidgetGlobals): List<WidgetLayerSpec> =
    filter { it.visible && globals.switch(it.visibleGlobal, fallback = true) }
