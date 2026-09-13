package inkspire.morphic.feature.shell

import androidx.lifecycle.ViewModel
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.data.apps.GestureServiceAccess
import kotlinx.coroutines.flow.StateFlow

/**
 * State holder for [GestureServicePrompt]: the action that found Morphic gestures off, and the dialog's two answers.
 *
 * **Its own holder rather than members of `ShellViewModel`**, which it shares nothing with: the shell's state is the
 * register and the wallpaper, and this is one command's report. It lives in the shell only because the shell is above
 * every surface a gesture can fire on.
 */
class GestureServicePromptViewModel(private val gestureService: GestureServiceAccess) : ViewModel() {

    /** The action that last fired while the service was off, until answered; null while there is nothing to ask. */
    val blocked: StateFlow<GestureAction?> = gestureService.blocked

    /** "Turn on": closes the dialog and opens the system's accessibility settings. */
    fun turnOn() {
        gestureService.dismissBlocked()
        gestureService.openSettings()
    }

    /** "Cancel". The assignment stays, so the next gesture that needs the service asks again. */
    fun dismiss() = gestureService.dismissBlocked()
}
