package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetRecipe

/**
 * A finished design offered to be placed — what a user picks, where the recipe is what they get a copy of.
 *
 * @property id stable across builds, so a later slice can recognize which template a widget started from.
 */
data class WidgetTemplate(val id: String, val name: String, val recipe: WidgetRecipe)
