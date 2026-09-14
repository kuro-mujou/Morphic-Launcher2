package inkspire.morphic.feature.settings.setup

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.settings.SetupStep
import inkspire.morphic.data.setup.SetupSteps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the setup hub draws.
 *
 * @property steps the unfinished steps, in order; the hub is absent while this is empty.
 * @property defaultLauncherRequest how to ask for the home role — non-null exactly while that step is in [steps].
 */
internal data class SetupHubState(
    val steps: List<SetupStep> = emptyList(),
    val defaultLauncherRequest: Intent? = null,
)

/** State holder for [SetupHub]: the unfinished setup steps, and putting one away. */
internal class SetupHubViewModel(private val setupSteps: SetupSteps) : ViewModel() {

    val state: StateFlow<SetupHubState> = combine(setupSteps.pending, setupSteps.defaultLauncherRequest) { steps, request ->
        SetupHubState(steps = steps, defaultLauncherRequest = request)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SetupHubState())

    /** Re-reads the home role — see [SetupSteps.refresh]; called on every resume of the settings list. */
    fun refresh() {
        viewModelScope.launch { setupSteps.refresh() }
    }

    /** Puts [step] away for good. */
    fun dismiss(step: SetupStep) {
        viewModelScope.launch { setupSteps.dismiss(step) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
