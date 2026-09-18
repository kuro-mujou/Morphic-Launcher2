package inkspire.morphic.core.widgetscript

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * A bound value as a widget stores it — literal text with `$…$` formulas in it, `$df(hh:mm)$ · $tc(up, df(EEE))$` —
 * parsed once and evaluated as often as its data changes.
 *
 * **[providers] is what this type is for.** It is read off the parse, before anything is evaluated, and it is
 * everything any evaluation could read in any branch; a widget's update cadence is derived from it and from nothing
 * else. An expression with no providers evaluates to the same text forever.
 *
 * `$$` is a literal dollar sign. A `$` inside a quoted string does not close the formula.
 *
 * Never throws. A formula that does not parse stays in the text exactly as typed and is listed in [problems]; one that
 * fails while running does the same in its [ScriptResult].
 *
 * @property source the stored text, which every [ScriptProblem.range] indexes into.
 * @property problems what is wrong with the text itself, known without evaluating it.
 */
class WidgetExpression private constructor(
    val source: String,
    private val parts: List<ScriptPart>,
    val problems: List<ScriptProblem>,
) {
    private val calls: List<Node.Call> = parts.filterIsInstance<ScriptPart.Formula>().flatMap { it.node.calls() }

    val providers: Set<ProviderId> = calls.flatMap { it.function.reads }.toSet()

    /**
     * How often this can show a new time, or null when it does not read the clock — the finest of its clock calls'.
     * A pattern built by another formula is not known until it runs, and counts as every second.
     */
    val clockTick: ClockTick? = calls
        .filter { ProviderId.CLOCK in it.function.reads }
        .minOfOrNull { call -> call.function.clockTick(call.args.map(::constantText)) }

    fun evaluate(data: ScriptData): ScriptResult {
        val evaluator = Evaluator(source, data)
        val runtime = mutableListOf<ScriptProblem>()
        val text = buildString {
            parts.forEach { part ->
                when (part) {
                    is ScriptPart.Text -> append(part.text)
                    is ScriptPart.Formula -> try {
                        append(evaluator.evaluate(part.node).text)
                    } catch (e: EvaluationException) {
                        runtime += ScriptProblem(e.range, e.message.orEmpty())
                        append(source, part.range.first, part.range.last + 1)
                    }
                }
            }
        }
        return ScriptResult(text, problems + runtime)
    }

    companion object {
        fun parse(source: String): WidgetExpression {
            val scanner = TemplateScanner(source).scan()
            return WidgetExpression(source, scanner.parts, scanner.problems)
        }
    }

    /**
     * What [node] evaluates to whatever the data, or null when it calls something — `dd-MM-yyyy` is three words and
     * two operators, and still a fixed pattern.
     */
    private fun constantText(node: Node): String? {
        if (node.calls().any()) return null
        return try {
            Evaluator(source, NoData).evaluate(node).text
        } catch (_: EvaluationException) {
            null
        }
    }
}

/** Handed to an evaluation that calls nothing, and so reads nothing; touching it is a bug in [WidgetExpression]. */
private object NoData : ScriptData {
    override val now: Instant get() = error("A constant read the clock")
    override val battery: BatteryReading get() = error("A constant read the battery")
    override val system: SystemReading get() = error("A constant read the system")
    override val zone: ZoneId get() = error("A constant read the zone")
    override val locale: Locale get() = error("A constant read the locale")
}
