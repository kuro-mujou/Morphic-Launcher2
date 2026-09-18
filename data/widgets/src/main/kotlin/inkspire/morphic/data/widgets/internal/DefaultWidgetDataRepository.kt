package inkspire.morphic.data.widgets.internal

import android.content.Context
import inkspire.morphic.core.widgetscript.BatteryReading
import inkspire.morphic.core.widgetscript.ProviderId
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.SystemReading
import inkspire.morphic.data.widgets.WidgetCadence
import inkspire.morphic.data.widgets.WidgetDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * [WidgetDataRepository] over the platform's own signals. A provider the cadence does not name is not subscribed to
 * at all; its slot in the snapshot holds a placeholder that the cadence guarantees nothing reads.
 *
 * `internal` so only Koin constructs it.
 *
 * @param context the application context — it registers receivers that outlive any one screen's.
 */
internal class DefaultWidgetDataRepository(private val context: Context) : WidgetDataRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun data(cadence: WidgetCadence): Flow<ScriptData> =
        clockEnvironment(context).flatMapLatest { environment ->
            combine(
                cadence.clockTick?.let { clockTicks(it, environment.zone, Instant::now) } ?: flowOf(Instant.now()),
                if (ProviderId.BATTERY in cadence.providers) batteryReadings(context) else flowOf(BatteryReading.Unknown),
                if (ProviderId.SYSTEM in cadence.providers) systemReadings(context) else flowOf(SystemReading.Unknown),
            ) { now, battery, system ->
                LiveScriptData(now, battery, system, environment.zone, environment.locale)
            }
        }.distinctUntilChanged()
}

/** One moment's readings, compared by value so an unchanged moment is not redrawn. */
private data class LiveScriptData(
    override val now: Instant,
    override val battery: BatteryReading,
    override val system: SystemReading,
    override val zone: ZoneId,
    override val locale: Locale,
) : ScriptData
