package inkspire.morphic.core.widgetscript.function

/** Every function a script can call, by [ScriptFunction.name]. `if` is not here: it is grammar, see `Node.If`. */
internal val ScriptFunctions: Map<String, ScriptFunction> =
    listOf(TextFunction, MathFunction, DateFormatFunction, BatteryFunction, SystemFunction, GlobalFunction)
        .associateBy { it.name }
