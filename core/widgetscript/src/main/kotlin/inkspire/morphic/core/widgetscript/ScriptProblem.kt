package inkspire.morphic.core.widgetscript

/**
 * Something wrong with a script, located in its source so an editor can underline it.
 *
 * A problem never stops a widget drawing: the part of the text it covers is shown **as written**, so a half-typed
 * formula reads as the formula rather than as a blank or a crash.
 *
 * @property range character offsets into [WidgetExpression.source], end inclusive.
 */
data class ScriptProblem(val range: IntRange, val message: String)

/** An evaluated script: the text to draw, and whatever went wrong producing it. */
data class ScriptResult(val text: String, val problems: List<ScriptProblem>)
