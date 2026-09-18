package inkspire.morphic.core.model.widget

/**
 * The globals in scope at one point of a recipe, looked up by name — the **one** place a binding is resolved, so the
 * renderer and the update cadence cannot disagree about whether a layer is visible or what color it is.
 *
 * **Scopes nest.** A recipe's own globals are the outermost; a group that declares globals ([inside]) opens a scope
 * whose names shadow the ones around it. That is what lets two Clock blocks each own a global called `text` without
 * either being renamed — a binding, or a formula's `gv`, reads the nearest one.
 *
 * Every read takes the value the property holds on its own as a fallback, used when the binding is absent, names
 * no global, or names one of another type. A broken binding therefore draws the design as authored rather than
 * failing, which is the right way round for something a user can break by editing a formula elsewhere. A mistyped
 * nearest global is a broken binding too — it does not fall through to an outer one of the right type.
 */
class WidgetGlobals(globals: List<WidgetGlobal>, private val outer: WidgetGlobals? = null) {
    private val byName = globals.associateBy { it.name }

    /** The nearest global called [name] in scope, of whatever type — what a binding naming it would read. */
    operator fun get(name: String?): WidgetGlobal? = name?.let { byName[it] ?: outer?.get(it) }

    fun color(name: String?, fallback: Int): Int = (get(name) as? WidgetGlobal.Color)?.value ?: fallback

    fun number(name: String?, fallback: Float): Float = (get(name) as? WidgetGlobal.Number)?.value ?: fallback

    fun switch(name: String?, fallback: Boolean): Boolean = (get(name) as? WidgetGlobal.Switch)?.value ?: fallback

    fun font(name: String?, fallback: WidgetSource.Text.Font): WidgetSource.Text.Font =
        (get(name) as? WidgetGlobal.Font)?.value ?: fallback

    /** Every setting in scope, the nearest of each name — what someone choosing one of them is offered. */
    val all: List<WidgetGlobal>
        get() = outer?.all.orEmpty().filter { it.name !in byName } + byName.values

    /** The scope inside [group]: its own globals over these, or these unchanged when it declares none. */
    fun inside(group: WidgetSource.Overlap): WidgetGlobals =
        if (group.globals.isEmpty()) this else WidgetGlobals(group.globals, this)

    /**
     * Every global in scope as the text a formula's `gv(name)` reads — a color as `#AARRGGBB`, a switch as 1 or 0, a
     * choice as its chosen option — the nearest of each name winning.
     */
    fun asScriptValues(): Map<String, String> = outer?.asScriptValues().orEmpty() + byName.mapValues { (_, global) ->
        when (global) {
            is WidgetGlobal.Color -> "#%08X".format(global.value)
            is WidgetGlobal.Number -> if (global.value % 1f == 0f) "${global.value.toInt()}" else "${global.value}"
            is WidgetGlobal.Switch -> if (global.value) "1" else "0"
            is WidgetGlobal.Choice -> global.options.getOrElse(global.selected) { "" }
            is WidgetGlobal.Font -> global.value.name.lowercase()
            is WidgetGlobal.Text -> global.value
        }
    }
}

/** This recipe's globals, the outermost scope its bindings resolve in. */
val WidgetRecipe.resolvedGlobals: WidgetGlobals get() = WidgetGlobals(globals)

/**
 * Whether anything in this recipe can be restyled — its own globals, or a block's. A design with none offers no Style
 * row, since a screen of no controls changes nothing.
 */
val WidgetRecipe.isStyleable: Boolean get() = globals.isNotEmpty() || layers.any { it.declaresGlobals() }

private fun WidgetLayerSpec.declaresGlobals(): Boolean =
    (source as? WidgetSource.Overlap)?.globals?.isNotEmpty() == true ||
        source.children.orEmpty().any { it.declaresGlobals() }
