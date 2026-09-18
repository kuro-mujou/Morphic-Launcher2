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
}
