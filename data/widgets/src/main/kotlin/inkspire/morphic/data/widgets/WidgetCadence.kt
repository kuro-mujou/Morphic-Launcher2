package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.drawn
import inkspire.morphic.core.model.widget.resolvedGlobals
import inkspire.morphic.core.widgetscript.ClockTick
import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.WidgetExpression

/**
 * What a widget has to be woken for: which providers to listen to, and how often the clock matters.
 *
 * **Derived, never set.** It is read off the recipe's own formulas, so a widget showing a date wakes once a day and
 * one showing only static text never wakes at all — and there is no setting for a user to get wrong.
 *
 * @property clockTick null when nothing reads the clock.
 */
data class WidgetCadence(val providers: Set<ProviderId>, val clockTick: ClockTick?) {

    companion object {
        /**
         * The cadence of every formula [recipe] draws — a text's and a progress's value alike. Hidden layers draw
         * nothing and so ask for nothing — including one the Style tab switched off.
         */
        fun of(recipe: WidgetRecipe): WidgetCadence {
            val expressions = formulas(recipe.layers, recipe.resolvedGlobals).map(WidgetExpression::parse).toList()
            return WidgetCadence(
                providers = expressions.flatMap { it.providers }.toSet(),
                clockTick = expressions.mapNotNull { it.clockTick }.minOrNull(),
            )
        }

        private fun formulas(layers: List<WidgetLayerSpec>, globals: WidgetGlobals): Sequence<String> =
            layers.drawn(globals).asSequence().flatMap {
                when (val source = it.source) {
                    is WidgetSource.Text -> sequenceOf(source.text)
                    is WidgetSource.Progress -> sequenceOf(source.value)
                    is WidgetSource.Overlap -> formulas(source.layers, globals)
                    is WidgetSource.Shape, is WidgetSource.Image -> emptySequence()
                }
            }
    }
}
