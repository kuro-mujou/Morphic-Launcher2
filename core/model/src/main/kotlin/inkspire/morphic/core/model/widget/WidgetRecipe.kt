package inkspire.morphic.core.model.widget

import kotlinx.serialization.Serializable

/**
 * A widget design — the stored unit of the widget studio, one serialized blob rather than columns, and the thing
 * templates ship as.
 *
 * It describes **how to draw**, not what was drawn: every piece of live content is a formula inside a
 * [WidgetSource.Text], evaluated at render time against whatever data the renderer is handed. That is what lets the
 * studio's preview and a placed widget be one composable given two data sources, rather than two renderers.
 *
 * Its [layers] are the widget's own free-placement group: each is anchored to the widget's box, and later ones draw
 * over earlier ones. A background is simply the first layer.
 *
 * @property span the size it is placed at, which the user can then resize — the layers re-lay rather than scale.
 * @property globals what the design lets someone restyle, with this widget's current values.
 */
@Serializable
data class WidgetRecipe(
    val layers: List<WidgetLayerSpec> = emptyList(),
    val span: WidgetSpan = WidgetSpan(),
    val globals: List<WidgetGlobal> = emptyList(),
)

