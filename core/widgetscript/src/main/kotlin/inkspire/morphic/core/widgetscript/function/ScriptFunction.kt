package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue

/**
 * One `name(args)` a script can call. A function is **strict** — its arguments arrive evaluated — and anything it
 * needs from outside comes through [ScriptData], never from a platform call inside [call].
 *
 * @property name lower case; calls are matched without case, as the reference does.
 * @property reads the providers [call] touches. This is a promise the cadence is built on: a function that reads the
 *   clock and declares nothing is redrawn never, and nothing fails to say so.
 * @property arity how many arguments a call may pass, checked when the script is parsed so a wrong count is reported
 *   before anything runs.
 */
internal interface ScriptFunction {
    val name: String
    val reads: Set<ProviderId>
    val arity: IntRange

    /** @throws ScriptException for anything the user should see — a bad mode, a number that is not one. */
    fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue
}

/** A failure the user caused and can fix, carrying the message they are shown. */
internal class ScriptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** [index]'s argument as a number, or a message naming what was expected there. */
internal fun List<ScriptValue>.number(index: Int, what: String): Double =
    this[index].numberOrNull() ?: throw ScriptException("$what must be a number, not \"${this[index].text}\"")

/** [index]'s argument as a whole number, for counts and positions. */
internal fun List<ScriptValue>.whole(index: Int, what: String): Int = number(index, what).toInt()
