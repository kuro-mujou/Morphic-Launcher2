package inkspire.morphic.core.widgetscript

/**
 * A source of live data a script can read, named so a parsed expression can **declare** what it reads before anything
 * is evaluated. That declaration is what a widget's update cadence is derived from: a recipe whose expressions name no
 * provider is drawn once, and one naming only [CLOCK] wakes on the clock and nothing else. There is no cadence setting
 * for the user to arbitrate, and so this set has to be complete — a function that reads a provider without declaring
 * it here draws stale, silently, and only on the device.
 */
enum class ProviderId {
    /** The current instant, [ScriptData.now] — and how often it matters is a [ClockTick], not a fixed rate. */
    CLOCK,

    /** [ScriptData.battery]. */
    BATTERY,

    /** [ScriptData.system]. */
    SYSTEM,
}
