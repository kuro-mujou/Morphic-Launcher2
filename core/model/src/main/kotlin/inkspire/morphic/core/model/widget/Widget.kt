package inkspire.morphic.core.model.widget

/**
 * A widget on HOME: its own copy of a [recipe], under the [id] its placements refer to.
 *
 * **Each placed widget owns its recipe**, rather than pointing at a shared design. Placing one template twice gives
 * two widgets that can be restyled apart, and editing one never reaches into the other — the way a preset is copied
 * into each widget that uses it. What that gives up is a design edited once updating everywhere, which nothing
 * offers yet.
 */
data class Widget(val id: Long, val recipe: WidgetRecipe)
