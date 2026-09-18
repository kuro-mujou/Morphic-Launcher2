package inkspire.morphic.feature.settings.widgetstudio

import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.resolvedGlobals

/**
 * Where a layer sits in a recipe's tree: its index among the recipe's own layers, then among its group's, and so on
 * down. Empty is the widget itself. What the Advanced view navigates, where Style needs only the first step.
 */
internal typealias LayerPath = List<Int>

/** The layer at [path], or null for the widget itself or a path that no longer leads anywhere. */
internal fun WidgetRecipe.layerAt(path: LayerPath): WidgetLayerSpec? {
    var layers = layers
    var layer: WidgetLayerSpec? = null
    path.forEach { index ->
        layer = layers.getOrNull(index) ?: return null
        layers = (layer?.source as? WidgetSource.Overlap)?.layers.orEmpty()
    }
    return layer
}

/** What sits directly inside [path] — the recipe's layers for the widget, a group's for a group, nothing otherwise. */
internal fun WidgetRecipe.childrenAt(path: LayerPath): List<WidgetLayerSpec> =
    if (path.isEmpty()) layers else (layerAt(path)?.source as? WidgetSource.Overlap)?.layers.orEmpty()

/** Whether [path] can hold layers: the widget, or a group. */
internal fun WidgetRecipe.isContainer(path: LayerPath): Boolean =
    path.isEmpty() || layerAt(path)?.source is WidgetSource.Overlap

/** The recipe with [path]'s layer changed by [change]. A path that leads nowhere changes nothing. */
internal fun WidgetRecipe.updatedAt(path: LayerPath, change: (WidgetLayerSpec) -> WidgetLayerSpec): WidgetRecipe {
    if (path.isEmpty()) return this
    return copy(layers = layers.updatedAt(path, change) ?: return this)
}

/** The recipe with [layers] as what sits directly inside [path]. */
internal fun WidgetRecipe.withChildrenAt(path: LayerPath, layers: List<WidgetLayerSpec>): WidgetRecipe = when {
    path.isEmpty() -> copy(layers = layers)
    else -> updatedAt(path) { layer ->
        val group = layer.source as? WidgetSource.Overlap ?: return@updatedAt layer
        layer.copy(source = group.copy(layers = layers))
    }
}

/** The recipe without the layer at [path]. */
internal fun WidgetRecipe.removedAt(path: LayerPath): WidgetRecipe {
    if (path.isEmpty()) return this
    val parent = path.dropLast(1)
    return withChildrenAt(parent, childrenAt(parent).filterIndexed { index, _ -> index != path.last() })
}

/**
 * The settings in scope at [path] — the widget's, and those of every group on the way down, nearest winning — which
 * is what a binding on that layer reads.
 */
internal fun WidgetRecipe.globalsAt(path: LayerPath): WidgetGlobals {
    var scope = resolvedGlobals
    var layers = layers
    // Every group *above* the layer opens a scope; the layer's own, if it is a group, is for its children.
    path.dropLast(1).forEach { index ->
        val group = layers.getOrNull(index)?.source as? WidgetSource.Overlap ?: return scope
        scope = scope.inside(group)
        layers = group.layers
    }
    return scope
}

/** What the Advanced view calls a layer: its name when it has one, else what kind of thing it is. */
internal val WidgetLayerSpec.label: String
    get() = name ?: when (source) {
        is WidgetSource.Text -> "Text"
        is WidgetSource.Shape -> "Shape"
        is WidgetSource.Image -> "Image"
        is WidgetSource.Progress -> "Progress"
        is WidgetSource.Overlap -> "Group"
    }

/**
 * These layers' labels, a repeated one numbered from its second — two unnamed lines are "Text" and "Text 2", which
 * a row of chips can tell apart where "Text" and "Text" cannot.
 */
internal fun List<WidgetLayerSpec>.labels(): List<String> {
    val seen = mutableMapOf<String, Int>()
    return map { layer ->
        val count = seen.merge(layer.label, 1, Int::plus)!!
        if (count == 1) layer.label else "${layer.label} $count"
    }
}

private fun List<WidgetLayerSpec>.updatedAt(
    path: LayerPath,
    change: (WidgetLayerSpec) -> WidgetLayerSpec,
): List<WidgetLayerSpec>? {
    val layer = getOrNull(path.first()) ?: return null
    val next = if (path.size == 1) {
        change(layer)
    } else {
        val group = layer.source as? WidgetSource.Overlap ?: return null
        layer.copy(source = group.copy(layers = group.layers.updatedAt(path.drop(1), change) ?: return null))
    }
    return toMutableList().apply { set(path.first(), next) }
}
