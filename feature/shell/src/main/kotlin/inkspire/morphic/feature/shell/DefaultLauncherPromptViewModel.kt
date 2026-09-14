package inkspire.morphic.feature.shell

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.apps.DefaultLauncherRole
import inkspire.morphic.data.settings.DefaultLauncherAsk
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for [DefaultLauncherPrompt]: whether to ask, as the launcher comes back, to be made the default home app.
 *
 * **It asks at most once per [DefaultLauncherAsk.Cooldown]**, and only while this launcher is not the home app and the
 * device offers a way to become it. The ask is stamped when the dialog is *shown*, not when it is answered, so coming
 * back from the system's own chooser — which resumes the launcher — never asks again, whatever the user chose there.
 *
 * **It holds back while HOME's edge hint is up.** The first home after setup carries one hint, and a dialog over it would
 * hide the only thing saying where the apps are. So the first ask comes on a resume after the hint has gone — which is
 * also always a resume after the one that finished setup.
 *
 * **Its own holder rather than members of `ShellViewModel`**, for [GestureServicePromptViewModel]'s reason: it shares
 * nothing with the shell's state, and lives in the shell only because the shell is above every surface.
 */
class DefaultLauncherPromptViewModel(
    private val defaultLauncherRole: DefaultLauncherRole,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val mutableRequest = MutableStateFlow<Intent?>(null)

    /** How to ask to become the home app, while the dialog is up; null while there is nothing to ask. */
    val request: StateFlow<Intent?> = mutableRequest.asStateFlow()

    private var checking: Job? = null

    /**
     * Puts the dialog up if an ask is due and still needed. Called on every resume — the stored cooldown, not the
     * caller, is what keeps it rare.
     *
     * The cooldown is read first because it is a local read, and the role only then, off the main thread, because both
     * of its halves are binder calls into the package manager.
     */
    fun askIfDue() {
        if (mutableRequest.value != null || checking?.isActive == true) return
        checking = viewModelScope.launch {
            if (!settingsRepository.onboarding.first().edgeHintDismissed) return@launch
            val now = System.currentTimeMillis()
            if (!settingsRepository.defaultLauncherAsk.first().isDue(now)) return@launch
            val intent = withContext(Dispatchers.Default) {
                if (defaultLauncherRole.isDefault()) null else defaultLauncherRole.requestIntent()
            } ?: return@launch
            settingsRepository.setDefaultLauncherAskedAt(now)
            mutableRequest.value = intent
        }
    }

    /** Closes the dialog, whichever way it was answered. The settings index keeps offering the request until it is done. */
    fun dismiss() {
        mutableRequest.value = null
    }
}
