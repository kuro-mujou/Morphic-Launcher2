package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.BatteryReading
import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue

/**
 * `bi(key)` — the battery. `level` is 0–100, `charging` is 1 or 0, `source` is `ac`, `usb`, `wireless`, `dock` or empty
 * when unplugged, and `temp` is in °C.
 */
internal object BatteryFunction : ScriptFunction {
    override val name = "bi"
    override val reads = setOf(ProviderId.BATTERY)
    override val arity = 1..1

    override fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue {
        val battery = data.battery
        return when (val key = args[0].text.lowercase()) {
            "level" -> ScriptValue.Num(battery.level.toDouble())
            "charging" -> ScriptValue.of(battery.charging)
            "source" -> ScriptValue.Text(
                if (battery.source == BatteryReading.PowerSource.NONE) "" else battery.source.name.lowercase(),
            )
            "temp" -> ScriptValue.Num(battery.temperature.toDouble())
            else -> throw ScriptException("bi has no \"$key\"")
        }
    }
}
