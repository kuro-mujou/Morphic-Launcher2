package inkspire.morphic.core.widgetscript

/**
 * One worked example of the language: what it is for, and the formula. The studio shows it evaluated live on the
 * device — "92%" under `$bi(level)$%` — which is how the language is learned without a manual.
 *
 * @property family the function it shows, as a script calls it (`df`, `bi`), or `if` for the one piece of grammar.
 */
data class ScriptExample(val family: String, val title: String, val formula: String)

/**
 * The examples the formula editor offers, grouped by [family][ScriptExample.family]. A test holds every function the
 * language has to at least one example here, and every example to parsing cleanly, so the browser cannot fall behind
 * the language or teach something it no longer accepts.
 */
object ScriptExamples {

    /** What each family is called in the browser, in the order it shows them. */
    val families: Map<String, String> = linkedMapOf(
        "df" to "Time & date",
        "bi" to "Battery",
        "si" to "Device",
        "tc" to "Text",
        "mu" to "Math",
        "if" to "Conditions",
        "gv" to "Settings",
    )

    val all: List<ScriptExample> = listOf(
        ScriptExample("df", "Time, 24-hour", "\$df(HH:mm)\$"),
        ScriptExample("df", "Time, 12-hour", "\$df(h:mm a)\$"),
        ScriptExample("df", "Day of the week", "\$df(EEEE)\$"),
        ScriptExample("df", "Short date", "\$df(\"d MMM\")\$"),
        ScriptExample("df", "Long date", "\$df(\"EEEE, d MMMM yyyy\")\$"),
        ScriptExample("df", "Day of the year", "\$df(D)\$"),
        ScriptExample("bi", "Battery level", "\$bi(level)\$%"),
        ScriptExample("bi", "Charging or not", "\$if(bi(charging), Charging, \"On battery\")\$"),
        ScriptExample("bi", "Battery temperature", "\$bi(temp)\$°C"),
        ScriptExample("si", "Phone model", "\$si(model)\$"),
        ScriptExample("si", "Android version", "Android \$si(aver)\$"),
        ScriptExample("tc", "Upper case", "\$tc(up, df(EEEE))\$"),
        ScriptExample("tc", "First three letters", "\$tc(cut, df(MMMM), 0, 3)\$"),
        ScriptExample("tc", "Capitalized words", "\$tc(cap, \"good morning\")\$"),
        ScriptExample("mu", "Minutes into the day", "\$df(H) * 60 + df(m)\$"),
        ScriptExample("mu", "Share of the year gone", "\$mu(round, df(D) / 3.65)\$%"),
        ScriptExample("mu", "Hours left today", "\$mu(floor, (1440 - df(H) * 60 - df(m)) / 60)\$h"),
        ScriptExample("if", "Morning or evening", "\$if(df(H) < 12, Morning, Evening)\$"),
        ScriptExample("if", "Low battery warning", "\$if(bi(level) < 20, \"Charge me\", \"\")\$"),
        ScriptExample("if", "Night or day", "\$if(df(H) >= 20 | df(H) < 6, Night, Day)\$"),
        ScriptExample("gv", "One of this widget's settings", "\$gv(text)\$"),
    )
}
