package inkspire.morphic.data.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import inkspire.morphic.core.model.GestureAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Whether [MorphicGestureService] is switched on, the way to where the user switches it, and the action that last found
 * it off — what settings shows as status and the shell turns into a dialog.
 *
 * Apart from the commands that *use* the service because this is about the service itself: the shade and the screen
 * lock each need it for their own reason, and one question is asked for both.
 */
interface GestureServiceAccess {

    /**
     * Whether the service is bound right now.
     *
     * Read on demand rather than observed: the user switches it on in the system's settings, which sends nothing back.
     */
    val isOn: Boolean

    /**
     * An action that fired while the service was off, until [dismissBlocked]; null otherwise.
     *
     * **Held here rather than returned to the caller**, because actions fire from two modules — HOME's own gestures in
     * the shell, an item's in home — and the dialog is one, in the shell. A result handed back would have to be carried
     * there by each caller.
     */
    val blocked: StateFlow<GestureAction?>

    /** Records that [action] could not run for want of the service. A newer report replaces an unanswered one. */
    fun reportBlocked(action: GestureAction)

    /** Clears [blocked] — the dialog has been answered, either way. */
    fun dismissBlocked()

    /** Opens the system's accessibility settings, where the user switches the service on. */
    fun openSettings()
}

/** Default [GestureServiceAccess]. `internal` so only Koin constructs it, and a singleton, since it holds [blocked]. */
internal class PlatformGestureServiceAccess(private val context: Context) : GestureServiceAccess {

    override val isOn: Boolean get() = MorphicGestureService.connected != null

    private val blockedAction = MutableStateFlow<GestureAction?>(null)

    override val blocked: StateFlow<GestureAction?> = blockedAction

    override fun reportBlocked(action: GestureAction) {
        blockedAction.value = action
    }

    override fun dismissBlocked() {
        blockedAction.value = null
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No accessibility settings screen to open")
        }
    }
}
