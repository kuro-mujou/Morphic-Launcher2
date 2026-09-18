package inkspire.morphic.core.widgetscript

import inkspire.morphic.core.widgetscript.Token.Kind
import inkspire.morphic.core.widgetscript.function.ScriptFunctions

/** A script that cannot be parsed, located at the token that made it so. */
internal class ParseException(val range: IntRange, message: String) : Exception(message)

/**
 * Builds the [Node] tree for one `$…$` from its tokens. Precedence, loosest first:
 *
 * ```
 * sequence    := or or*                          juxtaposition — `hh:mm a`
 * or          := and ( "|" and )*
 * and         := compare ( "&" compare )*
 * compare     := additive ( ("=" | "!=" | "<" | ">" | "<=" | ">=") additive )*
 * additive    := product ( ("+" | "-") product )*
 * product     := unary ( ("*" | "/" | "%") unary )*
 * unary       := "-" unary | primary
 * primary     := WORD "(" args ")" | WORD | STRING | "(" sequence ")"
 * ```
 *
 * A call needs its `(` **touching** the name: `if(` is a call, `am (x)` is a word beside a group. Function names and
 * argument counts are checked here, so a misspelled `tc` is reported while it is being typed, not when it first runs.
 */
internal class Parser(private val tokens: List<Token>) {
    private var at = 0
    private val peek get() = tokens[at]

    fun parse(): Node {
        val node = sequence()
        if (peek.kind != Kind.END) fail(peek, "Unexpected \"${peek.text}\"")
        return node
    }

    private fun sequence(): Node {
        val parts = mutableListOf(or())
        while (peek.kind !in SequenceEnds) parts += or()
        return parts.singleOrNull() ?: Node.Juxtaposed(parts, parts.first().range.first..parts.last().range.last)
    }

    private fun or() = binary(setOf("|"), ::and)
    private fun and() = binary(setOf("&"), ::compare)
    private fun compare() = binary(setOf("=", "!=", "<", ">", "<=", ">="), ::additive)
    private fun additive() = binary(setOf("+", "-"), ::product)
    private fun product() = binary(setOf("*", "/", "%"), ::unary)

    private fun binary(operators: Set<String>, operand: () -> Node): Node {
        var left = operand()
        while (peek.kind == Kind.OPERATOR && peek.text in operators) {
            val operator = take().text
            val right = operand()
            left = Node.Binary(operator, left, right, left.range.first..right.range.last)
        }
        return left
    }

    private fun unary(): Node {
        if (peek.kind == Kind.OPERATOR && peek.text == "-") {
            val minus = take()
            val operand = unary()
            return Node.Negate(operand, minus.start..operand.range.last)
        }
        return primary()
    }

    private fun primary(): Node {
        val token = take()
        return when (token.kind) {
            Kind.WORD -> if (peek.kind == Kind.LPAREN && peek.start == token.end) call(token) else literal(token)
            Kind.STRING -> literal(token)
            Kind.LPAREN -> {
                val inner = sequence()
                val close = expect(Kind.RPAREN, "Missing \")\"")
                Node.Group(inner, token.start until close.end)
            }
            Kind.END -> fail(token, "Expression ends too soon")
            else -> fail(token, "Unexpected \"${token.text}\"")
        }
    }

    private fun call(name: Token): Node {
        take()
        val args = mutableListOf<Node>()
        if (peek.kind != Kind.RPAREN) {
            args += sequence()
            while (peek.kind == Kind.COMMA) {
                take()
                args += sequence()
            }
        }
        val close = expect(Kind.RPAREN, "Missing \")\" after ${name.text}(")
        val range = name.start until close.end
        if (name.text.equals("if", ignoreCase = true)) {
            if (args.size < 2) throw ParseException(range, "if needs a condition and a result")
            return Node.If(args, range)
        }
        val function = ScriptFunctions[name.text.lowercase()] ?: fail(name, "No function called \"${name.text}\"")
        if (args.size !in function.arity) throw ParseException(range, arityMessage(function.name, function.arity))
        return Node.Call(function, args, range)
    }

    private fun literal(token: Token) = Node.Literal(token.text, token.start until token.end)

    private fun take(): Token = tokens[at].also { if (it.kind != Kind.END) at++ }

    private fun expect(kind: Kind, message: String): Token = if (peek.kind == kind) take() else fail(peek, message)

    private fun fail(token: Token, message: String): Nothing =
        throw ParseException(token.start until maxOf(token.end, token.start + 1), message)

    private fun arityMessage(name: String, arity: IntRange): String = when {
        arity.first == arity.last -> "$name takes ${arity.first} argument${if (arity.first == 1) "" else "s"}"
        arity.last == Int.MAX_VALUE -> "$name takes at least ${arity.first} arguments"
        else -> "$name takes ${arity.first} to ${arity.last} arguments"
    }

    private companion object {
        val SequenceEnds = setOf(Kind.COMMA, Kind.RPAREN, Kind.END)
    }
}
