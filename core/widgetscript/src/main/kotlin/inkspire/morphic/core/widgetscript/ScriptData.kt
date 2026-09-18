package inkspire.morphic.core.widgetscript

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Everything an evaluation may read from outside the script, passed in rather than reached for — this module has no
 * Android and no clock of its own, which is what forces every read through here and so through a declared
 * [ProviderId]. The studio preview and a placed widget evaluate the same expression against two implementations of
 * this, and that is the only way the two may differ.
 *
 * Only the members a provider stands behind are *live*; the rest are the widget's own context and change when the user
 * changes them, not on a tick.
 */
interface ScriptData {
    /** The [ProviderId.CLOCK] reading. Read only by a script that declares the clock. */
    val now: Instant

    /** The [ProviderId.BATTERY] reading. */
    val battery: BatteryReading

    /** The [ProviderId.SYSTEM] reading. */
    val system: SystemReading

    /** The zone [now] is shown in. The widget's, not necessarily the device's: a second clock is one field changed. */
    val zone: ZoneId

    /** Formats dates and changes case; a script evaluated under two locales is expected to read differently. */
    val locale: Locale
}
