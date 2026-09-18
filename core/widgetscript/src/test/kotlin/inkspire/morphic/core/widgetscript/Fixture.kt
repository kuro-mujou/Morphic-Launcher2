package inkspire.morphic.core.widgetscript

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Friday 18 September 2026, 19:14:05 UTC, read in English, on a Pixel at 92% charging over USB. */
internal object FixtureData : ScriptData {
    override val now: Instant = Instant.parse("2026-09-18T19:14:05Z")
    override val battery = BatteryReading(92, charging = true, BatteryReading.PowerSource.USB, temperature = 31.5f)
    override val system = SystemReading("Google", "Pixel 9", "16", darkMode = true)
    override val zone: ZoneId = ZoneId.of("UTC")
    override val locale: Locale = Locale.US
}

/** [source] evaluated against [data], asserting nothing went wrong on the way. */
internal fun eval(source: String, data: ScriptData = FixtureData): String {
    val result = WidgetExpression.parse(source).evaluate(data)
    check(result.problems.isEmpty()) { "Expected no problems in <$source>, got ${result.problems}" }
    return result.text
}
