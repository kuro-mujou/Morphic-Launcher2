package inkspire.morphic.core.widgetscript.function

import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.ScriptValue
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * `mu(op, …)` — math utilities, by the reference's names.
 *
 * `round(x, digits?)` rounds half up, away from zero, to `digits` places (0 by default) — the rounding a person
 * expects of a displayed number, not the banker's rounding `Math.round` would give 2.5. `floor`, `ceil`, `abs` and
 * `sqrt` take one number; `pow` takes a base and an exponent; `min` and `max` take any number of them.
 *
 * There is deliberately no `rnd`: a random number changes on every evaluation, so it reads a provider no cadence can
 * be derived from.
 */
internal object MathFunction : ScriptFunction {
    override val name = "mu"
    override val reads = emptySet<ProviderId>()
    override val arity = 2..Int.MAX_VALUE

    override fun call(args: List<ScriptValue>, data: ScriptData): ScriptValue {
        val op = args[0].text.lowercase()
        val numbers = args.drop(1)
        val result = when (op) {
            "round" -> round(numbers)
            "floor" -> floor(numbers.single(op))
            "ceil" -> ceil(numbers.single(op))
            "abs" -> abs(numbers.single(op))
            "sqrt" -> numbers.single(op).also { if (it < 0) throw ScriptException("sqrt of a negative number") }.let(::sqrt)
            "pow" -> numbers.exactly(op, 2).let { it[0].pow(it[1]) }
            "min" -> numbers.indices.minOf { numbers.number(it, "min") }
            "max" -> numbers.indices.maxOf { numbers.number(it, "max") }
            else -> throw ScriptException("mu has no operation \"$op\"")
        }
        if (!result.isFinite()) throw ScriptException("mu($op, …) has no finite result")
        return ScriptValue.Num(result)
    }

    private fun round(numbers: List<ScriptValue>): Double {
        if (numbers.size !in 1..2) throw ScriptException("round takes a number and, optionally, how many places")
        val places = if (numbers.size == 2) numbers.whole(1, "Places").coerceIn(-PlacesLimit, PlacesLimit) else 0
        return BigDecimal(numbers.number(0, "round").toString()).setScale(places, RoundingMode.HALF_UP).toDouble()
    }

    /** A double carries about 17 significant digits, so rounding finer than this changes nothing it can hold. */
    private const val PlacesLimit = 20

    private fun List<ScriptValue>.single(op: String): Double = exactly(op, 1)[0]

    private fun List<ScriptValue>.exactly(op: String, count: Int): List<Double> {
        if (size != count) throw ScriptException("$op takes $count number${if (count == 1) "" else "s"}")
        return indices.map { number(it, op) }
    }
}
