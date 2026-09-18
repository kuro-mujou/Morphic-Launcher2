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

/**
 * Every imported picture this recipe draws or holds — hidden layers included, since hiding is not removing, and a
 * shape's own picture even while a setting overrides it, since unbinding brings it back. What a sweep of stored
 * pictures keeps, so a place a picture can live that is missing here is a picture deleted from under its widget.
 */
val WidgetRecipe.imagePaths: Set<String>
    get() = (globals.picturePaths() + layers.flatMap { it.imagePaths() }).toSet()

private fun WidgetLayerSpec.imagePaths(): List<String> = when (val source = source) {
    is WidgetSource.Image -> listOf(source.path)
    is WidgetSource.Shape -> listOfNotNull(source.picture?.path)
    is WidgetSource.Overlap -> source.globals.picturePaths() + source.layers.flatMap { it.imagePaths() }
    is WidgetSource.Stack -> source.layers.flatMap { it.imagePaths() }
    is WidgetSource.Text, is WidgetSource.Progress -> emptyList()
}

private fun List<WidgetGlobal>.picturePaths(): List<String> =
    mapNotNull { (it as? WidgetGlobal.Picture)?.value?.path }
