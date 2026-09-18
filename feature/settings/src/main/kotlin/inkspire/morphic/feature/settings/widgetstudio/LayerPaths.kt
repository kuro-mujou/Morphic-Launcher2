package inkspire.morphic.feature.settings.widgetstudio

import inkspire.morphic.core.model.widget.LayerPath
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.children
import inkspire.morphic.core.model.widget.layerAt
import inkspire.morphic.core.model.widget.resolvedGlobals
import inkspire.morphic.core.model.widget.updatedAt
import inkspire.morphic.core.model.widget.withChildren

/** What sits directly inside [path] — the recipe's layers for the widget, a group's for a group, nothing otherwise. */
internal fun WidgetRecipe.childrenAt(path: LayerPath): List<WidgetLayerSpec> =
    if (path.isEmpty()) layers else layerAt(path)?.source?.children.orEmpty()

/** Whether [path] can hold layers: the widget, or a group of either kind. */
internal fun WidgetRecipe.isContainer(path: LayerPath): Boolean =
    path.isEmpty() || layerAt(path)?.source?.children != null

/** The recipe with [layers] as what sits directly inside [path]. */
internal fun WidgetRecipe.withChildrenAt(path: LayerPath, layers: List<WidgetLayerSpec>): WidgetRecipe = when {
    path.isEmpty() -> copy(layers = layers)
    else -> updatedAt(path) { it.copy(source = it.source.withChildren(layers)) }
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
    // Every free group *above* the layer opens a scope; the layer's own, if it is a group, is for its children. A stack
    // declares no settings, so it is walked through without opening one.
    path.dropLast(1).forEach { index ->
        val source = layers.getOrNull(index)?.source ?: return scope
        if (source is WidgetSource.Overlap) scope = scope.inside(source)
        layers = source.children ?: return scope
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
        is WidgetSource.Stack -> "Stack"
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
