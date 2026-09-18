package inkspire.morphic.data.widgets.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.ZoneId
import java.util.Locale

/**
 * The zone and locale every widget reads in — not a provider, since nothing ticks, but it can still change under a
 * running widget.
 */
internal data class ClockEnvironment(val zone: ZoneId, val locale: Locale)

/**
 * The current [ClockEnvironment], and again whenever the zone, the locale or the **time itself** is changed.
 *
 * The last is why this is not distinct: setting the clock by hand leaves the zone and locale as they were, but every
 * sleeping tick is now aimed at the wrong boundary. Emitting an equal value is what restarts them.
 */
internal fun clockEnvironment(context: Context): Flow<ClockEnvironment> = callbackFlow {
    fun send() {
        trySend(ClockEnvironment(ZoneId.systemDefault(), Locale.getDefault()))
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = send()
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_LOCALE_CHANGED)
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    send()
    awaitClose { context.unregisterReceiver(receiver) }
}
