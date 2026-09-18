package inkspire.morphic.data.widgets.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import inkspire.morphic.core.widgetscript.BatteryReading
import inkspire.morphic.core.widgetscript.BatteryReading.PowerSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The battery, from `ACTION_BATTERY_CHANGED`. The broadcast is sticky, so the current state arrives on registering
 * rather than at the next change.
 *
 * It fires on every voltage and temperature wobble as well as on the level, which is why this is distinct: a widget
 * showing the level is redrawn when the level moves.
 */
internal fun batteryReadings(context: Context): Flow<BatteryReading> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.toBatteryReading())
        }
    }
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()

private fun Intent.toBatteryReading(): BatteryReading {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, Percent).takeIf { it > 0 } ?: Percent
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    return BatteryReading(
        level = level * Percent / scale,
        // Full on the charger counts: "charging" is what a widget says while plugged in, not the current's direction.
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        source = powerSource(getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)),
        temperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / TenthsPerDegree,
    )
}

private fun powerSource(plugged: Int): PowerSource = when {
    plugged == BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
    plugged == BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && plugged == BatteryManager.BATTERY_PLUGGED_DOCK ->
        PowerSource.DOCK
    else -> PowerSource.NONE
}

private const val Percent = 100

/** The platform reports temperature in tenths of a degree. */
private const val TenthsPerDegree = 10f
