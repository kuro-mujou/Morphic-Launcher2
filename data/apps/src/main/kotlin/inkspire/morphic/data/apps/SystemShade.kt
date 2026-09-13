package inkspire.morphic.data.apps

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import inkspire.morphic.core.model.ShadePanel
import inkspire.morphic.core.model.ShadeRequest
import inkspire.morphic.core.model.ShadeStyle
import timber.log.Timber

/**
 * Opens one of the system's pull-down panels — what [GestureActionRunner] calls for `GestureAction.OpenSystemPanel`.
 *
 * A type of its own, beside [AppInfoOpener] and for its reason: a fire-and-forget side effect on the platform, not
 * access to a store.
 */
interface SystemShade {

    /** Opens the panel [request] asks for. A platform that refuses is a no-op, logged — never a crash. */
    fun expand(request: ShadeRequest)

    /**
     * Whether [ShadeSwipeService] is switched on, so a separate shade's sides can be reached by a replayed swipe.
     *
     * Read on demand rather than observed: the user switches it on in the system's settings, which sends nothing back.
     */
    val swipeServiceOn: Boolean

    /** Opens the system's accessibility settings, where the user switches [ShadeSwipeService] on. */
    fun openSwipeServiceSettings()
}

/**
 * Default [SystemShade]: a replayed swipe for a separate shade while [ShadeSwipeService] is on, and otherwise
 * `StatusBarManager`'s hidden `expandNotificationsPanel` / `expandSettingsPanel`.
 *
 * **The swipe only for a separate shade**, because that is the one arrangement decided by where a finger lands. On a
 * combined shade a swipe from either side opens the same panel, so quick settings would be out of its reach, where the
 * call opens either directly.
 *
 * **The call is reflection over `EXPAND_STATUS_BAR`** — a normal permission granted at install, declared in this
 * module's manifest; both methods are hidden but not blocked. **It fails silently in two known ways**: a release that
 * blocks the methods (the lookup throws, and this logs), and a skin that answers both with one panel. RedMagic OS sends
 * every request to its control center while its split is on — which is the case the swipe exists for.
 *
 * `internal` so only Koin constructs it — consumers depend on [SystemShade].
 */
internal class PlatformSystemShade(private val context: Context) : SystemShade {

    override val swipeServiceOn: Boolean get() = ShadeSwipeService.connected != null

    override fun expand(request: ShadeRequest) {
        val service = ShadeSwipeService.connected
        if (request.style == ShadeStyle.SEPARATE && service != null) {
            service.swipeDownFromTop(if (request.panel == ShadePanel.NOTIFICATIONS) LEFT_SIDE else RIGHT_SIDE)
        } else {
            expandByCall(request.panel)
        }
    }

    override fun openSwipeServiceSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No accessibility settings screen to open")
        }
    }

    private fun expandByCall(panel: ShadePanel) {
        val method = when (panel) {
            ShadePanel.NOTIFICATIONS -> "expandNotificationsPanel"
            ShadePanel.QUICK_SETTINGS -> "expandSettingsPanel"
        }
        try {
            // `Context.STATUS_BAR_SERVICE`, which is only public API from 33; the name has not changed below that.
            @SuppressLint("WrongConstant")
            val manager = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod(method).invoke(manager)
        } catch (e: ReflectiveOperationException) {
            Timber.w(e, "Could not open the system %s panel", panel)
        } catch (e: SecurityException) {
            Timber.w(e, "Could not open the system %s panel", panel)
        }
    }

    private companion object {
        /** Where a replayed swipe lands for each side: a quarter in from the edge, clear of the middle and of One UI's
         *  70% line alike. */
        const val LEFT_SIDE = 0.25f

        /** See [LEFT_SIDE]. */
        const val RIGHT_SIDE = 0.75f
    }
}
