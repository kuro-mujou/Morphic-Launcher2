package inkspire.morphic.core.widgetscript

import java.math.BigDecimal
import java.math.MathContext

/**
 * A value mid-evaluation. Every literal is [Text], including `42` — a number exists only once arithmetic or a function
 * produced one — so `007` written into a script stays `007` until something does maths with it, and a result is
 * never re-read from its own formatting (`1/3*3` is 1, not 0.9999999999).
 */
internal sealed interface ScriptValue {
    val text: String

    /** This value as a number, or null when it does not read as one. */
    fun numberOrNull(): Double?

    data class Text(override val text: String) : ScriptValue {
        override fun numberOrNull(): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    data class Num(val value: Double) : ScriptValue {
        override val text: String get() = formatNumber(value)
        override fun numberOrNull(): Double = value
    }

    companion object {
        fun of(value: Boolean): ScriptValue = Num(if (value) 1.0 else 0.0)
    }
}

/** Zero, a non-number that is empty, and nothing else is false — the `if` condition and the logical operators agree. */
internal fun ScriptValue.isTruthy(): Boolean = numberOrNull()?.let { it != 0.0 } ?: text.isNotEmpty()

/** Ten significant digits: enough that nothing a widget shows is visibly rounded, few enough to hide binary noise. */
private val DisplayPrecision = MathContext(10)

/** Whole numbers print without a point, which is what `$bi(level)$%` needs to read `92%` rather than `92.0%`. */
private const val WholeLimit = 1e15

private fun formatNumber(value: Double): String =
    if (value == Math.rint(value) && kotlin.math.abs(value) < WholeLimit) {
        value.toLong().toString()
    } else {
        BigDecimal(value).round(DisplayPrecision).stripTrailingZeros().toPlainString()
    }
