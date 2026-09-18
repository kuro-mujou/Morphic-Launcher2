package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ClockTick
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

    /**
     * The finest field the pattern prints, read off its letters with quoted text skipped — `'at' HH:mm` ticks on the
     * minute, not on the `a` inside the quotes. A pattern not fixed when the script is written ticks every second.
     */
    override fun clockTick(constantArgs: List<String?>): ClockTick {
        val pattern = constantArgs.firstOrNull() ?: return ClockTick.SECOND
        var quoted = false
        var finest = ClockTick.DAY
        pattern.forEach { c ->
            when {
                c == '\'' -> quoted = !quoted
                !quoted -> finest = minOf(finest, tickOf(c))
            }
        }
        return finest
    }

    /** Every letter that is not a time of day is a date, a zone or an era, and changes at most daily. */
    private fun tickOf(letter: Char): ClockTick = when (letter) {
        's', 'S', 'n', 'N', 'A' -> ClockTick.SECOND
        'm' -> ClockTick.MINUTE
        'H', 'h', 'k', 'K', 'a', 'B' -> ClockTick.HOUR
        else -> ClockTick.DAY
    }
}
