package inkspire.morphic.core.widgetscript

/**
 * The [ProviderId.BATTERY] reading.
 *
 * @property level 0–100.
 * @property temperature in °C.
 */
data class BatteryReading(
    val level: Int,
    val charging: Boolean,
    val source: PowerSource,
    val temperature: Float,
) {
    /** Where the charge is coming from, when anywhere. */
    enum class PowerSource { NONE, AC, USB, WIRELESS, DOCK }

    companion object {
        /**
         * What a script that did not declare the battery is handed. It is never read — the declaration is what says so —
         * and it is plainly not a real battery if it ever is.
         */
        val Unknown = BatteryReading(level = 0, charging = false, source = PowerSource.NONE, temperature = 0f)
    }
}
