package inkspire.morphic.data.widgets.internal

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import inkspire.morphic.core.widgetscript.SystemReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The device's facts. Only dark mode changes while the launcher runs, and it arrives as a configuration change, which
 * the application context is told about through its component callbacks.
 */
internal fun systemReadings(context: Context): Flow<SystemReading> = callbackFlow {
    fun send(configuration: Configuration) {
        trySend(
            SystemReading(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                darkMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            ),
        )
    }
    val callbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = send(newConfig)

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }
    context.registerComponentCallbacks(callbacks)
    send(context.resources.configuration)
    awaitClose { context.unregisterComponentCallbacks(callbacks) }
}.distinctUntilChanged()
