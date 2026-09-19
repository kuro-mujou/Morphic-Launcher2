package inkspire.morphic.data.widgets

import inkspire.morphic.core.widgetscript.ScriptData
import kotlinx.coroutines.flow.Flow

/**
 * The device's live data, as a placed widget reads it.
 *
 * **It listens to exactly what [WidgetCadence] names and nothing else.** A widget reading only the date gets one
 * emission a day and no battery receiver at all; one reading nothing gets a single emission and then silence. That
 * is the whole of this launcher's answer to a widget's update rate.
 *
 * The flow is cold and holds its listeners only while collected, so a surface that stops collecting when it is not
 * on screen — `collectAsStateWithLifecycle` — stops every wake-up with it.
 */
interface WidgetDataRepository {
    fun data(cadence: WidgetCadence): Flow<ScriptData>

    /**
     * The last reading any collector of [cadence] received, or null if none has run yet — **what a newly composed
     * widget draws on its first frame**, before its own subscription has emitted.
     *
     * [data] is cold and its first value arrives a frame or more after collection starts, so a widget composed
     * beside one already on screen — the floating copy lifted out of it, or its cell recreated on another page by a
     * drop — would otherwise draw empty in between and flash. A reading taken moments ago for the same cadence is
     * exactly what the widget already on screen is showing.
     */
    fun latest(cadence: WidgetCadence): ScriptData?
}
