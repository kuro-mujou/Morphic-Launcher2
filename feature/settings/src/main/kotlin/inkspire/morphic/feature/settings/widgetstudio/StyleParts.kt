package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toRect
import inkspire.morphic.core.model.widget.WidgetGlobal
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

/**
 * The part a tap at [point] lands on: the topmost of [parts] whose drawn box holds it, or null — the widget itself —
 * when it lands on none. Topmost is last, since later layers draw over earlier ones.
 *
 * @param bounds where each layer was drawn, by index, as `WidgetRender` reports it.
 */
internal fun partAt(point: Offset, parts: List<StylePart>, bounds: Map<Int, IntRect>): Int? =
    parts.lastOrNull { part -> bounds[part.index]?.toRect()?.contains(point) == true }?.index
