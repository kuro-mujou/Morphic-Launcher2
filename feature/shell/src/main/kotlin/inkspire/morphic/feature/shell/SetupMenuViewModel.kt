package inkspire.morphic.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.setup.SetupSteps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for [OfferSetupOnMenu]: whether any setup step is still open, so HOME's menu can offer "Finish setup".
 *
 * **Its own holder rather than members of `ShellViewModel`**, for `DefaultLauncherPromptViewModel`'s reason: it shares
 * nothing with the shell's state. It asks the same [SetupSteps] the settings hub lists, so the row is offered exactly
 * while the hub has something in it.
 */
internal class SetupMenuViewModel(private val setupSteps: SetupSteps) : ViewModel() {

    /** True while the setup hub has at least one step. */
    val hasPending: StateFlow<Boolean> = setupSteps.pending
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Re-reads the home role — see [SetupSteps.refresh]. */
    fun refresh() {
        viewModelScope.launch { setupSteps.refresh() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
