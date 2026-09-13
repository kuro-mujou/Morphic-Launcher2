package inkspire.morphic.data.apps

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import inkspire.morphic.core.model.ShadePanel

/**
 * The launcher's accessibility service, shown as **"Morphic gestures"** — what every system-panel action and the screen
 * lock run through.
 *
 * **Global actions for a combined shade.** `GLOBAL_ACTION_NOTIFICATIONS` and `GLOBAL_ACTION_QUICK_SETTINGS` open either
 * panel directly, where a replayed swipe on a combined shade could only ever reach the first.
 *
 * **A replayed swipe for a separate shade.** RedMagic OS (and the ZTE SystemUI under it) answers every programmatic
 * expand — the global actions included — with its control center whenever the split is on; the only thing it routes to
 * notifications is a touch that comes down on the left half of the status bar. An accessibility gesture is injected as
 * exactly that touch, so it is decided the way the user's own finger is.
 *
 * **A global action for the screen.** `GLOBAL_ACTION_LOCK_SCREEN` locks the way the power button does and keeps
 * fingerprint and face unlock working, where device admin's `lockNow` forces the PIN at the next unlock and makes the
 * launcher a device administrator the user must revoke before they can uninstall it.
 *
 * **Optional, and nothing else.** It reads no window content and receives no events; the user switches it on in the
 * system's accessibility settings. Until then `GestureActionRunner` asks for it in place of running any action that
 * needs it.
 *
 * [connected] is the live instance while the system has the service bound, and null otherwise — which is also the
 * honest answer to "can this be done right now".
 */
class MorphicGestureService : AccessibilityService() {

    override fun onServiceConnected() {
        connected = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * Swipes down from the very top of the screen at [xFraction] of its width, 0 at the physical left. False when the
     * system will not take the gesture.
     *
     * It starts on the first pixel row so the touch lands in the status bar window, which is the only window SystemUI
     * reads a pull-down from.
     */
    fun swipeDownFromTop(xFraction: Float): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * xFraction
        val path = Path().apply {
            moveTo(x, 1f)
            lineTo(x, metrics.heightPixels * SWIPE_DEPTH)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_MS))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Opens [panel] by its global action — right for a combined shade only; see this class. False when refused. */
    fun openPanel(panel: ShadePanel): Boolean = performGlobalAction(
        when (panel) {
            ShadePanel.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            ShadePanel.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
        },
    )

    /** Turns the screen off and locks it. False where [canLockScreen] is not, or when the system refuses. */
    fun lockScreen(): Boolean = canLockScreen && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    internal companion object {
        /** The bound service, or null while the user has it switched off. */
        @Volatile
        var connected: MorphicGestureService? = null
            private set

        /**
         * Whether this device has the lock action at all — API 28, where it arrived. One answer for the service that
         * performs it and the picker that offers it, so a row is never offered for an action that cannot run.
         */
        val canLockScreen: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** How long the replayed swipe takes. Fast enough to read as a flick, slow enough not to be dropped as a tap. */
        const val SWIPE_MS = 200L

        /** How far down the screen the swipe travels, as a fraction of its height — past every skin's open threshold. */
        const val SWIPE_DEPTH = 0.4f
    }
}
