package inkspire.morphic.core.widgetscript

import inkspire.morphic.core.widgetscript.function.ScriptException

/** An evaluation failure, located at the node that raised it. */
internal class EvaluationException(val range: IntRange, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Runs a [Node] tree against one [ScriptData] snapshot.
 *
 * **An operator whose operands are not numbers is not an operator — it is the text that was written.** `dd-MM-yyyy`
 * and `dd / MM` evaluate to themselves, spacing included, which is what lets a date pattern go unquoted. The
 * exceptions are `=` and `!=`, which compare text exactly when either side is not a number, and `&` / `|`, which are
 * always logical and yield 1 or 0 — so `R&B` has to be quoted. Anything else that fails — dividing by zero, a function
 * refusing its arguments — fails the whole `$…$` it is in.
 *
 * @param source the whole script, which the nodes' ranges index into.
 */
internal class Evaluator(private val source: String, private val data: ScriptData) {

    fun evaluate(node: Node): ScriptValue = when (node) {
        is Node.Literal -> ScriptValue.Text(node.text)
        is Node.Call -> call(node)
        is Node.If -> choose(node)
        is Node.Group -> evaluate(node.inner)
        is Node.Negate -> negate(node)
        is Node.Binary -> binary(node)
        is Node.Juxtaposed -> ScriptValue.Text(joinAsWritten(node.parts.map { it to evaluate(it) }))
    }

    private fun call(node: Node.Call): ScriptValue {
        val args = node.args.map(::evaluate)
        return try {
            node.function.call(args, data)
        } catch (e: ScriptException) {
            throw EvaluationException(node.range, e.message.orEmpty(), e)
        }
    }

    /** Pairs of (condition, result), then an optional else; nothing matching and no else is empty text. */
    private fun choose(node: Node.If): ScriptValue {
        node.args.chunked(2).forEach { pair ->
            if (pair.size == 1) return evaluate(pair[0])
            if (evaluate(pair[0]).isTruthy()) return evaluate(pair[1])
        }
        return ScriptValue.Text("")
    }

    private fun negate(node: Node.Negate): ScriptValue {
        val operand = evaluate(node.operand)
        val number = operand.numberOrNull()
            ?: return ScriptValue.Text(textBetween(node.range.first, node.operand) + operand.text)
        return ScriptValue.Num(-number)
    }

    private fun binary(node: Node.Binary): ScriptValue {
        val left = evaluate(node.left)
        val right = evaluate(node.right)
        when (node.operator) {
            "&" -> return ScriptValue.of(left.isTruthy() && right.isTruthy())
            "|" -> return ScriptValue.of(left.isTruthy() || right.isTruthy())
        }
        val a = left.numberOrNull()
        val b = right.numberOrNull()
        if (a == null || b == null) {
            return when (node.operator) {
                "=" -> ScriptValue.of(left.text == right.text)
                "!=" -> ScriptValue.of(left.text != right.text)
                else -> ScriptValue.Text(joinAsWritten(listOf(node.left to left, node.right to right)))
            }
        }
        return arithmetic(node, a, b)
    }

    private fun arithmetic(node: Node.Binary, a: Double, b: Double): ScriptValue = when (node.operator) {
        "+" -> ScriptValue.Num(a + b)
        "-" -> ScriptValue.Num(a - b)
        "*" -> ScriptValue.Num(a * b)
        "/" -> ScriptValue.Num(nonZero(node, b).let { a / it })
        "%" -> ScriptValue.Num(nonZero(node, b).let { a % it })
        "=" -> ScriptValue.of(a == b)
        "!=" -> ScriptValue.of(a != b)
        "<" -> ScriptValue.of(a < b)
        ">" -> ScriptValue.of(a > b)
        "<=" -> ScriptValue.of(a <= b)
        ">=" -> ScriptValue.of(a >= b)
        else -> error("The parser produced an operator the evaluator does not know: ${node.operator}")
    }

    private fun nonZero(node: Node.Binary, divisor: Double): Double =
        divisor.takeIf { it != 0.0 } ?: throw EvaluationException(node.range, "Division by zero")

    /** Evaluated parts, with whatever the source held between them — spaces, an operator — kept as it was typed. */
    private fun joinAsWritten(parts: List<Pair<Node, ScriptValue>>): String = buildString {
        parts.forEachIndexed { i, (node, value) ->
            if (i > 0) append(textBetween(parts[i - 1].first.range.last + 1, node))
            append(value.text)
        }
    }

    private fun textBetween(from: Int, next: Node): String = source.substring(from, next.range.first)
}
