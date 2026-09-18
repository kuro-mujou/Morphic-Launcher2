package inkspire.morphic.core.widgetscript

/**
 * One lexeme of an expression, positioned in the **whole** script's source so a problem can point at it there.
 *
 * @property end exclusive.
 */
internal data class Token(val kind: Kind, val text: String, val start: Int, val end: Int) {
    enum class Kind { WORD, STRING, LPAREN, RPAREN, COMMA, OPERATOR, END }
}

/** The operators, longest first so `<=` is not read as `<` then `=`. */
private val Operators = listOf("!=", "<=", ">=", "+", "-", "*", "/", "%", "=", "<", ">", "&", "|")

private const val Delimiters = "(),\"$"

/**
 * Splits the inside of one `$…$` into tokens.
 *
 * **There are no number tokens.** A run of anything that is not whitespace, a delimiter or an operator is a [WORD]
 * — `hh:mm`, `up`, `3.5`, `10:30` — and whether a word is a number is decided when a value is needed. That is what
 * lets a date pattern be written bare, as the reference allows, without a second grammar for "text that happens to
 * look like arithmetic".
 *
 * An unterminated string runs to the end of the expression and is reported rather than guessed at.
 *
 * @param from offset of the expression's first character in [source].
 * @param to exclusive end of the expression in [source].
 */
internal class Lexer(private val source: String, from: Int, private val to: Int) {
    private var at = from
    val problems = mutableListOf<ScriptProblem>()

    fun tokens(): List<Token> = buildList {
        while (true) {
            val token = next()
            add(token)
            if (token.kind == Token.Kind.END) break
        }
    }

    private fun next(): Token {
        while (at < to && source[at].isWhitespace()) at++
        if (at >= to) return Token(Token.Kind.END, "", to, to)
        if (source[at] == '"') return string(at)
        return punctuation() ?: operator() ?: word()
    }

    private fun punctuation(): Token? {
        val kind = when (source[at]) {
            '(' -> Token.Kind.LPAREN
            ')' -> Token.Kind.RPAREN
            ',' -> Token.Kind.COMMA
            else -> return null
        }
        at++
        return Token(kind, source[at - 1].toString(), at - 1, at)
    }

    private fun operator(): Token? {
        val operator = operatorAt(at) ?: return null
        at += operator.length
        return Token(Token.Kind.OPERATOR, operator, at - operator.length, at)
    }

    private fun word(): Token {
        val start = at
        while (at < to && isWordChar(at)) at++
        return Token(Token.Kind.WORD, source.substring(start, at), start, at)
    }

    private fun string(start: Int): Token {
        val close = source.indexOf('"', start + 1).takeIf { it in 0 until to }
        at = (close ?: to) + if (close != null) 1 else 0
        if (close == null) problems += ScriptProblem(start until to, "Unclosed quote")
        return Token(Token.Kind.STRING, source.substring(start + 1, close ?: to), start, at)
    }

    private fun operatorAt(index: Int): String? =
        Operators.firstOrNull { source.startsWith(it, index) && index + it.length <= to }

    private fun isWordChar(index: Int): Boolean =
        !source[index].isWhitespace() && source[index] !in Delimiters && operatorAt(index) == null
}
