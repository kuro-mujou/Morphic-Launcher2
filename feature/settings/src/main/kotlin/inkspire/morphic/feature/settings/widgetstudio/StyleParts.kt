package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toRect
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource

/**
 * One thing the Style tab can select: a block of the widget, by its index among the recipe's layers.
 *
 * The widget itself — its background and whatever settings the recipe holds outside any block — is not a part; it is
 * what is selected when nothing is, written as a null index throughout.
 */
data class StylePart(val index: Int, val name: String)

/** The recipe's blocks, in draw order: its named top-level groups. */
internal fun WidgetRecipe.parts(): List<StylePart> = layers.mapIndexedNotNull { index, layer ->
    layer.name?.takeIf { layer.source is WidgetSource.Overlap }?.let { StylePart(index, it) }
}

/** The settings [part] owns — the block's, or the widget's own for null. */
internal fun WidgetRecipe.globalsOf(part: Int?): List<WidgetGlobal> = when (part) {
    null -> globals
    else -> (layers.getOrNull(part)?.source as? WidgetSource.Overlap)?.globals.orEmpty()
}

/**
 * This recipe with [global] replacing the one of its name in [part]'s scope. A part that is not a block, or a name the
 * scope does not hold, changes nothing — a control can only edit a setting it was shown.
 */
internal fun WidgetRecipe.withGlobal(part: Int?, global: WidgetGlobal): WidgetRecipe {
    fun List<WidgetGlobal>.replaced() = map { if (it.name == global.name) global else it }
    if (part == null) return copy(globals = globals.replaced())
    val layer = layers.getOrNull(part) ?: return this
    val group = layer.source as? WidgetSource.Overlap ?: return this
    val edited = layer.copy(source = group.copy(globals = group.globals.replaced()))
    return copy(layers = layers.toMutableList().apply { set(part, edited) })
}

/** This recipe with [part]'s layer changed by [change] — a move or a pinch. A part that does not exist changes nothing. */
internal fun WidgetRecipe.withLayer(part: Int, change: (WidgetLayerSpec) -> WidgetLayerSpec): WidgetRecipe {
    val layer = layers.getOrNull(part) ?: return this
    return copy(layers = layers.toMutableList().apply { set(part, change(layer)) })
}

/** This recipe without [part]'s layer. */
internal fun WidgetRecipe.without(part: Int): WidgetRecipe =
    if (part in layers.indices) copy(layers = layers.filterIndexed { index, _ -> index != part }) else this

/**
 * [part] moved one step up (+1) or down (-1) the draw order, trading places with the next **block** that way — never
 * with the background, which would hide the block behind it — or null when no block lies that way.
 *
 * @return the recipe and the index the part now has.
 */
internal fun WidgetRecipe.restacked(part: Int, step: Int): Pair<WidgetRecipe, Int>? {
    val blocks = parts().map { it.index }
    val other = blocks.getOrNull(blocks.indexOf(part) + step)?.takeIf { part in blocks } ?: return null
    val swapped = layers.toMutableList().apply {
        set(part, layers[other])
        set(other, layers[part])
    }
    return copy(layers = swapped) to other
}

/**
 * This recipe without the widget-level settings nothing reads any more — what a removed block leaves behind when the
 * design gave it a switch at the widget's level ("Show date" with no date). A control that changes nothing does not
 * stay on the Style tab.
 *
 * Conservative on purpose: a setting counts as read when any binding anywhere names it, or any formula mentions it in
 * a `gv` call, even inside a block whose own setting of that name would shadow it. Keeping one too many costs a row;
 * dropping one still in use breaks the widget.
 */
internal fun WidgetRecipe.withoutUnusedGlobals(): WidgetRecipe {
    val read = layers.flatMap { it.readNames() }.toSet()
    val formulas = layers.flatMap { it.formulas() }
    fun mentioned(name: String): Boolean {
        val call = Regex("""gv\(\s*"?${Regex.escape(name)}"?\s*\)""")
        return formulas.any(call::containsMatchIn)
    }
    return copy(globals = globals.filter { it.name in read || mentioned(it.name) })
}

/** Every global name this layer and everything inside it binds a property to. */
private fun WidgetLayerSpec.readNames(): List<String> = listOfNotNull(visibleGlobal) + when (val source = source) {
    is WidgetSource.Text -> listOfNotNull(source.colorGlobal, source.sizeGlobal, source.fontGlobal)
    is WidgetSource.Shape -> listOfNotNull(source.colorGlobal, source.cornerRadiusGlobal)
    is WidgetSource.Progress -> listOfNotNull(source.colorGlobal, source.trackColorGlobal)
    is WidgetSource.Overlap -> source.layers.flatMap { it.readNames() }
    is WidgetSource.Image -> emptyList()
}

/** Every formula this layer and everything inside it evaluates. */
private fun WidgetLayerSpec.formulas(): List<String> = when (val source = source) {
    is WidgetSource.Text -> listOf(source.text)
    is WidgetSource.Progress -> listOf(source.value)
    is WidgetSource.Overlap -> source.layers.flatMap { it.formulas() }
    is WidgetSource.Shape, is WidgetSource.Image -> emptyList()
}

/**
 * These per-part values after [part]'s layer is removed: its own entry gone, and every later part's moved down one
 * index with its layer — without which a later block's reset would return it to its neighbor's values.
 */
internal fun <T> Map<Int?, T>.afterRemoving(part: Int): Map<Int?, T> =
    filterKeys { it != part }.mapKeys { (key, _) -> if (key != null && key > part) key - 1 else key }

/**
 * The part a tap at [point] lands on: the topmost of [parts] whose drawn box holds it, or null — the widget itself —
 * when it lands on none. Topmost is last, since later layers draw over earlier ones.
 *
 * @param bounds where each layer was drawn, by index, as `WidgetRender` reports it.
 */
internal fun partAt(point: Offset, parts: List<StylePart>, bounds: Map<Int, IntRect>): Int? =
    parts.lastOrNull { part -> bounds[part.index]?.toRect()?.contains(point) == true }?.index
