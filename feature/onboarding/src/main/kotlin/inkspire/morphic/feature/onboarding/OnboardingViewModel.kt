package inkspire.morphic.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SideBinding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which side of the onboarding gate the launcher is on. */
enum class OnboardingGateState {
    /** The flag has not been read yet. */
    UNRESOLVED,

    /** Setup is unfinished, so the first-run screen stands in for the launcher. */
    OPEN,

    /** Setup is finished, so the launcher is shown. */
    CLOSED,
}

/**
 * State holder for [OnboardingGate]: whether first-run setup is finished, and the action that finishes it.
 *
 * **Scoped to the Activity, and that is its real lifetime.** The gate sits outside navigation, so `koinViewModel`
 * resolves against the Activity's store — and the gate lives exactly as long as the Activity does.
 */
class OnboardingViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Which side of the gate to compose. Follows the store, so a cleared flag re-opens the gate with no restart. */
    val gate: StateFlow<OnboardingGateState> = settingsRepository.onboarding
        .map { if (it.completed) OnboardingGateState.CLOSED else OnboardingGateState.OPEN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnboardingGateState.UNRESOLVED)

    /**
     * Applies the classic look — HOME's pager with a dock, and the paged app list behind the bottom edge — then
     * finishes setup.
     *
     * **The register is written before the flag.** A process killed between the writes leaves the gate open over a look
     * already applied, and applying it again is harmless; the other order could close the gate on a launcher with no
     * edge bound, which is the state the gate exists to rule out.
     */
    fun applyClassic() {
        viewModelScope.launch {
            settingsRepository.setHomeLayout(HomeLayout.PAGER_WITH_DOCK)
            settingsRepository.setSide(HomeEdge.BOTTOM, SideBinding.Apps(AppsLayout.PAGER))
            settingsRepository.completeOnboarding()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
