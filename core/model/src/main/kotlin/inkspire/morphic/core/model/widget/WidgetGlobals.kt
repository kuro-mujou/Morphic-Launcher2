package inkspire.morphic.core.model.widget

/**
 * A recipe's globals, looked up by name — the **one** place a binding is resolved, so the renderer and the update
 * cadence cannot disagree about whether a layer is visible or what color it is.
 *
 * Every read takes the value the property holds on its own as a fallback, used when the binding is absent, names
 * no global, or names one of another type. A broken binding therefore draws the design as authored rather than
 * failing, which is the right way round for something a user can break by editing a formula elsewhere.
 */
class WidgetGlobals(globals: List<WidgetGlobal>) {
    private val byName = globals.associateBy { it.name }

    fun color(name: String?, fallback: Int): Int = (byName[name] as? WidgetGlobal.Color)?.value ?: fallback

    fun number(name: String?, fallback: Float): Float = (byName[name] as? WidgetGlobal.Number)?.value ?: fallback

    fun switch(name: String?, fallback: Boolean): Boolean = (byName[name] as? WidgetGlobal.Switch)?.value ?: fallback

    fun font(name: String?, fallback: WidgetSource.Text.Font): WidgetSource.Text.Font =
        (byName[name] as? WidgetGlobal.Font)?.value ?: fallback

    /**
     * Every global as the text a formula's `gv(name)` reads: a color as `#AARRGGBB`, a switch as 1 or 0, a choice as
     * its chosen option.
     */
    fun asScriptValues(): Map<String, String> = byName.mapValues { (_, global) ->
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

/** This recipe's globals, for resolving its bindings. */
val WidgetRecipe.resolvedGlobals: WidgetGlobals get() = WidgetGlobals(globals)
