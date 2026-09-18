package inkspire.morphic.core.model.widget

/**
 * Where a layer sits in a recipe's tree: its index among the recipe's own layers, then among its group's, and so on
 * down. Empty is the widget itself. The studio edits by it and HOME runs a layer's tap by it, so both find a layer the
 * same way.
 */
typealias LayerPath = List<Int>

/** The layer at [path], or null for the widget itself or a path that no longer leads anywhere. */
fun WidgetRecipe.layerAt(path: LayerPath): WidgetLayerSpec? {
    var layers = layers
    var layer: WidgetLayerSpec? = null
    path.forEach { index ->
        layer = layers.getOrNull(index) ?: return null
        layers = layer?.source?.children.orEmpty()
    }
    return layer
}

/** The recipe with [path]'s layer changed by [change]. A path that leads nowhere changes nothing. */
fun WidgetRecipe.updatedAt(path: LayerPath, change: (WidgetLayerSpec) -> WidgetLayerSpec): WidgetRecipe {
    if (path.isEmpty()) return this
    return copy(layers = layers.updatedAt(path, change) ?: return this)
}

/**
 * The settings a tap on the layer at [path] can flip — the widget's, then every group's on the way down **including the
 * tapped layer's own** when it is a group, nearest winning. Wider than what a binding on that layer reads, because a
 * tap on a block is how a block reaches its own settings: tap the clock, and its 24-hour switch turns over.
 */
fun WidgetRecipe.tapScope(path: LayerPath): WidgetGlobals =
    path.indices.fold(resolvedGlobals) { scope, depth ->
        (layerAt(path.take(depth + 1))?.source as? WidgetSource.Overlap)?.let(scope::inside) ?: scope
    }

/**
 * The recipe with the setting called [name] flipped, as a tap on the layer at [path] flips it: the nearest one in
 * [tapScope]. A switch turns over and a choice steps to its next option, wrapping; a setting of any other kind, or none
 * of that name, changes nothing.
 */
fun WidgetRecipe.withFlipped(path: LayerPath, name: String): WidgetRecipe {
    val owner = path.indices.map { path.take(it + 1) }.lastOrNull { group ->
        (layerAt(group)?.source as? WidgetSource.Overlap)?.globals?.any { it.name == name } == true
    }
    return if (owner == null) {
        copy(globals = globals.flipped(name))
    } else {
        updatedAt(owner) { layer ->
            val group = layer.source as WidgetSource.Overlap
            layer.copy(source = group.copy(globals = group.globals.flipped(name)))
        }
    }
}

private fun List<WidgetGlobal>.flipped(name: String): List<WidgetGlobal> = map { global ->
    if (global.name != name) return@map global
    when (global) {
        is WidgetGlobal.Switch -> global.copy(value = !global.value)
        is WidgetGlobal.Choice ->
            if (global.options.isEmpty()) global else global.copy(selected = (global.selected + 1) % global.options.size)
        is WidgetGlobal.Color, is WidgetGlobal.Number, is WidgetGlobal.Font, is WidgetGlobal.Text,
        is WidgetGlobal.Picture,
        -> global
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
        val children = layer.source.children ?: return null
        layer.copy(source = layer.source.withChildren(children.updatedAt(path.drop(1), change) ?: return null))
    }
    return toMutableList().apply { set(path.first(), next) }
}
