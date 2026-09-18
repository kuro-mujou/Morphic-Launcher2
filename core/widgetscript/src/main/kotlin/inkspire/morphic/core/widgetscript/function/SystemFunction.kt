package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue

/** `si(key)` — the device: `man`ufacturer, `model`, `aver` (the Android version) and `darkmode` (1 or 0). */
internal object SystemFunction : ScriptFunction {
    override val name = "si"
    override val reads = setOf(ProviderId.SYSTEM)
    override val arity = 1..1

    override fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue {
        val system = data.system
        return when (val key = args[0].text.lowercase()) {
            "man" -> ScriptValue.Text(system.manufacturer)
            "model" -> ScriptValue.Text(system.model)
            "aver" -> ScriptValue.Text(system.androidVersion)
            "darkmode" -> ScriptValue.of(system.darkMode)
            else -> throw ScriptException("si has no \"$key\"")
        }
    }
}
