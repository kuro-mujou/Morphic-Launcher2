package inkspire.morphic.core.widgetscript

/** One run of a script: text to copy, or a `$…$` to evaluate. */
internal sealed interface ScriptPart {
    data class Text(val text: String) : ScriptPart

    /** @property range the whole `$…$`, delimiters included — what is shown when it fails. */
    data class Formula(val node: Node, val range: IntRange) : ScriptPart
}

/**
 * Splits a script into its [ScriptPart]s, parsing each formula as it is found. A formula that does not parse is
 * folded into the text around it, exactly as typed, and its reason is kept in [problems].
 */
internal class TemplateScanner(private val source: String) {
    val parts = mutableListOf<ScriptPart>()
    val problems = mutableListOf<ScriptProblem>()
    private val literal = StringBuilder()

    fun scan(): TemplateScanner = apply {
        var at = 0
        while (at < source.length) {
            val open = source.indexOf('$', at).takeIf { it >= 0 } ?: source.length
            literal.append(source, at, open)
            at = if (open < source.length) dollar(open) else open
        }
        flushText()
    }

    /** Handles the `$` at [open]; returns where scanning resumes. */
    private fun dollar(open: Int): Int {
        if (source.startsWith("$$", open)) {
            literal.append('$')
            return open + 2
        }
        val close = closingDollar(open)
        if (close < 0) {
            problems += ScriptProblem(open..open, "This \$ is never closed")
            literal.append(source, open, source.length)
            return source.length
        }
        val formula = formula(open, close)
        if (formula == null) {
            literal.append(source, open, close + 1)
        } else {
            flushText()
            parts += formula
        }
        return close + 1
    }

    /** The formula between [open] and [close], or null — with the reason recorded — when it does not parse. */
    private fun formula(open: Int, close: Int): ScriptPart.Formula? {
        val lexer = Lexer(source, open + 1, close)
        val tokens = lexer.tokens()
        if (lexer.problems.isNotEmpty()) {
            problems += lexer.problems
            return null
        }
        return try {
            ScriptPart.Formula(Parser(tokens).parse(), open..close)
        } catch (e: ParseException) {
            problems += ScriptProblem(e.range, e.message.orEmpty())
            null
        }
    }

    /**
     * The `$` that closes the one at [open], stepping over quoted strings. A quote left open would swallow the rest
     * of the text, so then the first `$` after [open] closes it instead and the lexer reports the quote.
     */
    private fun closingDollar(open: Int): Int {
        var at = open + 1
        while (at < source.length) {
            when (source[at]) {
                '$' -> return at
                '"' -> at = source.indexOf('"', at + 1).takeIf { it >= 0 } ?: return source.indexOf('$', open + 1)
            }
            at++
        }
        return -1
    }

    private fun flushText() {
        if (literal.isEmpty()) return
        parts += ScriptPart.Text(literal.toString())
        literal.clear()
    }
}
