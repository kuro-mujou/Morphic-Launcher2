package inkspire.morphic.core.widgetscript

import inkspire.morphic.core.widgetscript.function.ScriptFunction

/**
 * The parsed form of one `$…$`: what [WidgetExpression] walks to learn which providers a script reads, and what the
 * evaluator runs. Rebuilt from the stored string on every load and never persisted, so the grammar can change without
 * a migration.
 *
 * Every node keeps its [range] because the fallback for an operator that does not apply is **the source as written**
 * — `dd-MM-yyyy` is three words and two minus signs, and it has to come out as `dd-MM-yyyy`.
 *
 * @property range offsets into the whole script.
 */
internal sealed interface Node {
    val range: IntRange

    data class Literal(val text: String, override val range: IntRange) : Node

    data class Call(val function: ScriptFunction, val args: List<Node>, override val range: IntRange) : Node

    /**
     * `if(cond, then, cond, then, …, else)` — a language construct rather than a function because only the branch
     * taken is evaluated, so an error in the other one does not surface.
     */
    data class If(val args: List<Node>, override val range: IntRange) : Node

    /**
     * `( … )`. A node of its own only so its [range] covers the parentheses: they are grammar, and text rebuilt from
     * the source around a group must neither keep the `(` nor lose the `)`.
     */
    data class Group(val inner: Node, override val range: IntRange) : Node

    data class Negate(val operand: Node, override val range: IntRange) : Node

    data class Binary(val operator: String, val left: Node, val right: Node, override val range: IntRange) : Node

    /** Parts written side by side with no operator between — `hh:mm a` — joined by the whitespace that was there. */
    data class Juxtaposed(val parts: List<Node>, override val range: IntRange) : Node
}

/** Every call in this node or anything under it, in any branch. */
internal fun Node.calls(): Sequence<Node.Call> = when (this) {
    is Node.Literal -> emptySequence()
    is Node.Call -> sequenceOf(this) + args.asSequence().flatMap { it.calls() }
    is Node.If -> args.asSequence().flatMap { it.calls() }
    is Node.Group -> inner.calls()
    is Node.Negate -> operand.calls()
    is Node.Binary -> left.calls() + right.calls()
    is Node.Juxtaposed -> parts.asSequence().flatMap { it.calls() }
}
