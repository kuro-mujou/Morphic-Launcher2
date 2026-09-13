package inkspire.morphic.data.apps

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Replays a real swipe down the status bar — the one way to reach the **notification side of a separate shade** on
 * skins that route every programmatic request to the other side.
 *
 * **Why a swipe and not a call.** RedMagic OS (and the ZTE SystemUI under it) answers `expandNotificationsPanel` with
 * its control center whenever the split is on; the only thing it routes to notifications is a touch that comes down on
 * the left half of the status bar. An accessibility gesture is injected as exactly that touch, so it is decided the way
 * the user's own finger is.
 *
 * **Optional, and nothing else.** It reads no window content and receives no events; the user switches it on in the
 * system's accessibility settings, and until then [PlatformSystemShade] falls back to the platform call.
 *
 * [connected] is the live instance while the system has the service bound, and null otherwise — which is also the
 * honest answer to "can a swipe be replayed right now".
 */
class ShadeSwipeService : AccessibilityService() {

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
     * Swipes down from the very top of the screen at [xFraction] of its width, 0 at the physical left.
     *
     * It starts on the first pixel row so the touch lands in the status bar window, which is the only window SystemUI
     * reads a pull-down from.
     */
    fun swipeDownFromTop(xFraction: Float) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * xFraction
        val path = Path().apply {
            moveTo(x, 1f)
            lineTo(x, metrics.heightPixels * SWIPE_DEPTH)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_MS))
            .build()
        dispatchGesture(gesture, null, null)
    }

    internal companion object {
        /** The bound service, or null while the user has it switched off. */
        @Volatile
        var connected: ShadeSwipeService? = null
            private set

        /** How long the replayed swipe takes. Fast enough to read as a flick, slow enough not to be dropped as a tap. */
        const val SWIPE_MS = 200L

        /** How far down the screen the swipe travels, as a fraction of its height — past every skin's open threshold. */
        const val SWIPE_DEPTH = 0.4f
    }
}
