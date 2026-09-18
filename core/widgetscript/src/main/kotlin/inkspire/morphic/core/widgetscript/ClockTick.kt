package inkspire.morphic.core.widgetscript

/**
 * How often a script that reads the clock can show something new — which is how often it has to be woken. A clock
 * showing `HH:mm` is redrawn on the minute and a date on the day, never on a fixed per-second timer.
 *
 * Ordered finest first, so the tick a whole script needs is the smallest of its parts'.
 */
enum class ClockTick { SECOND, MINUTE, HOUR, DAY }
