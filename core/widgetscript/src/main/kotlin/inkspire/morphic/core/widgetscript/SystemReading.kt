package inkspire.morphic.core.widgetscript

/**
 * The [ProviderId.SYSTEM] reading: facts about the device, of which only [darkMode] changes while the launcher runs.
 *
 * @property androidVersion the release name the user knows, `16` — not the API level.
 */
data class SystemReading(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val darkMode: Boolean,
) {
    companion object {
        /** What a script that did not declare the system is handed, for [BatteryReading.Unknown]'s reason. */
        val Unknown = SystemReading(manufacturer = "", model = "", androidVersion = "", darkMode = false)
    }
}
