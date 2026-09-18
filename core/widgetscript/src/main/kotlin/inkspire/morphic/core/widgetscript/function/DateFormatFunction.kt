package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue
import java.time.DateTimeException
import java.time.format.DateTimeFormatter

/**
 * `df(pattern)` — the current time, in the widget's zone and locale, through a `java.time` pattern: `hh:mm`,
 * `EEEE`, `d MMMM`. The reference's patterns are `SimpleDateFormat`'s, and the two agree on every letter a widget
 * uses; where they part (`u`, `Y`), this is `java.time`'s reading.
 *
 * Letters that are not pattern letters must be quoted — `'at' hh:mm` — or the pattern is rejected, rather than a
 * stray letter silently printing as a date field.
 */
internal object DateFormatFunction : ScriptFunction {
    override val name = "df"
    override val reads = setOf(ProviderId.CLOCK)
    override val arity = 1..1

    override fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue {
        val formatter = try {
            DateTimeFormatter.ofPattern(args[0].text, data.locale)
        } catch (e: IllegalArgumentException) {
            throw ScriptException("Not a date pattern: ${e.message}", e)
        }
        return try {
            ScriptValue.Text(formatter.format(data.now.atZone(data.zone)))
        } catch (e: DateTimeException) {
            throw ScriptException("Not a date pattern: ${e.message}", e)
        }
    }
}
