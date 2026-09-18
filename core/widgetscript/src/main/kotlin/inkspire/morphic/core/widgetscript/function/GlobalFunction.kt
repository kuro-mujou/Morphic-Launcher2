package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue

/** `gv(name)` — one of the widget's own settings, as `ScriptData.globals` holds it. */
internal object GlobalFunction : ScriptFunction {
    override val name = "gv"
    override val reads = emptySet<ProviderId>()
    override val arity = 1..1

    override fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue {
        val key = args[0].text
        return ScriptValue.Text(data.globals[key] ?: throw ScriptException("No setting called \"$key\""))
    }
}
