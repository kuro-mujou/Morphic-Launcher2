package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue

/**
 * `tc(mode, text, …)` — the text converter. Mode names are the reference's, so a formula carried over from it reads
 * the same here.
 *
 * | mode | arguments | result |
 * |---|---|---|
 * | `low` / `up` | text | lower / upper case, in the script's locale |
 * | `cap` | text | each word's first letter upper-cased, the rest untouched |
 * | `len` | text | length in characters |
 * | `cut` | text, start, count? | a slice; a negative start counts from the end, a missing count runs to it |
 * | `split` | text, separator, index | the index-th piece, from 0; past the end is empty rather than an error |
 * | `reg` | text, pattern, replacement | every regex match replaced; `$1` refers to a group |
 * | `lpad` / `rpad` | text, length, pad? | padded to length with pad (a space by default); never shortened |
 * | `count` | text, search | non-overlapping occurrences |
 */
internal object TextFunction : ScriptFunction {
    override val name = "tc"
    override val reads = emptySet<ProviderId>()
    override val arity = 2..4

    /** @property after how many arguments follow the mode, text included. */
    private enum class Mode(val after: IntRange) {
        LOW(after = 1..1), UP(after = 1..1), CAP(after = 1..1), LEN(after = 1..1),
        CUT(after = 2..3), SPLIT(after = 3..3), REG(after = 3..3),
        LPAD(after = 2..3), RPAD(after = 2..3), COUNT(after = 2..2),
    }

    override fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue {
        val name = args[0].text.lowercase()
        val mode = Mode.entries.firstOrNull { it.name.lowercase() == name }
            ?: throw ScriptException("tc has no mode \"$name\"")
        val rest = args.drop(1)
        if (rest.size !in mode.after) throw ScriptException("tc($name, …) takes ${describe(mode.after)} after the mode")
        val text = rest[0].text
        return when (mode) {
            Mode.LOW -> ScriptValue.Text(text.lowercase(data.locale))
            Mode.UP -> ScriptValue.Text(text.uppercase(data.locale))
            Mode.CAP -> ScriptValue.Text(capitalizeWords(text, data))
            Mode.LEN -> ScriptValue.Num(text.length.toDouble())
            Mode.CUT -> ScriptValue.Text(cut(text, rest))
            Mode.SPLIT -> ScriptValue.Text(split(text, rest))
            Mode.REG -> ScriptValue.Text(replace(text, pattern(rest[1].text), rest[2].text))
            Mode.LPAD -> ScriptValue.Text(text.padStart(rest.whole(1, "Length").coerceAtLeast(0), padOf(rest)))
            Mode.RPAD -> ScriptValue.Text(text.padEnd(rest.whole(1, "Length").coerceAtLeast(0), padOf(rest)))
            Mode.COUNT -> ScriptValue.Num(count(text, rest[1].text).toDouble())
        }
    }

    private fun capitalizeWords(text: String, data: ScriptData): String =
        text.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(data.locale) } }

    private fun cut(text: String, rest: List<ScriptValue>): String {
        val raw = rest.whole(1, "Start")
        val start = (if (raw < 0) text.length + raw else raw).coerceIn(0, text.length)
        val count = if (rest.size > 2) rest.whole(2, "Count").coerceIn(0, text.length - start) else text.length - start
        return text.substring(start, start + count)
    }

    private fun split(text: String, rest: List<ScriptValue>): String {
        val separator = rest[1].text
        if (separator.isEmpty()) throw ScriptException("split needs a separator")
        return text.split(separator).getOrElse(rest.whole(2, "Index")) { "" }
    }

    private fun pattern(source: String): Regex = try {
        Regex(source)
    } catch (e: IllegalArgumentException) {
        throw ScriptException("Not a valid pattern: ${e.message?.lineSequence()?.first()}", e)
    }

    // `Matcher` reports a group the pattern does not have as an IndexOutOfBoundsException, which is the user's typo
    // rather than a bug here, so it is caught by name despite being a general type.
    @Suppress("TooGenericExceptionCaught")
    private fun replace(text: String, pattern: Regex, replacement: String): String = try {
        pattern.replace(text, replacement)
    } catch (e: IllegalArgumentException) {
        throw ScriptException("Not a valid replacement: ${e.message}", e)
    } catch (e: IndexOutOfBoundsException) {
        throw ScriptException("The replacement names a group the pattern does not have: ${e.message}", e)
    }

    private fun padOf(rest: List<ScriptValue>): Char {
        val pad = rest.getOrNull(2)?.text ?: " "
        return pad.singleOrNull() ?: throw ScriptException("Padding must be one character, not \"$pad\"")
    }

    private fun count(text: String, search: String): Int = if (search.isEmpty()) 0 else text.split(search).size - 1

    private fun describe(range: IntRange): String = when {
        range.first == range.last -> "${range.first} argument${if (range.first == 1) "" else "s"}"
        else -> "${range.first} or ${range.last} arguments"
    }
}
