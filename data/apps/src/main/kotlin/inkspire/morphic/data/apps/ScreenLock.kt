package inkspire.morphic.data.apps

import timber.log.Timber

/**
 * Turns the screen off and locks it — what `GestureAction.LockScreen` performs.
 *
 * A type of its own, beside [SystemShade] and for its reason: a fire-and-forget side effect on the platform.
 */
interface ScreenLock {

    /** Whether this device can lock from an app at all. False below API 28, where the action does not exist. */
    val isSupported: Boolean

    /**
     * Locks, through [MorphicGestureService]. A no-op, logged, while the service is off — reachable only by a race,
     * since [GestureActionRunner] asks for the service before it calls this.
     */
    fun lock()
}

/** Default [ScreenLock]. `internal` so only Koin constructs it. */
internal class PlatformScreenLock : ScreenLock {

    override val isSupported: Boolean get() = MorphicGestureService.canLockScreen

    override fun lock() {
        val service = MorphicGestureService.connected
        when {
            service == null -> Timber.w("Morphic gestures is off, so the screen cannot be locked")
            !service.lockScreen() -> Timber.w("The system refused to lock the screen")
        }
    }
}
