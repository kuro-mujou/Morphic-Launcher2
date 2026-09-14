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
 * State holder for [OnboardingGate]: whether first-run setup is finished, and the two writes that finish it.
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
     * Applies the classic look — HOME's pager with a dock, and the paged app list behind the bottom edge.
     *
     * **Applied for real, before the user has chosen it**, because the preview *is* the launcher and shows only what
     * the store holds. Nothing is final until [finish]; applying again is harmless.
     *
     * **The flag is stamped unfinished first**, and the gate depends on the order: an absent flag beside a stored
     * register reads as an install set up before the flag existed, so writing the register first closes the gate.
     */
    fun applyClassic() {
        viewModelScope.launch {
            settingsRepository.beginOnboarding()
            settingsRepository.setHomeLayout(HomeLayout.PAGER_WITH_DOCK)
            settingsRepository.setSide(HomeEdge.BOTTOM, SideBinding.Apps(AppsLayout.PAGER))
        }
    }

    /**
     * Finishes setup with the look already applied, closing the gate.
     *
     * Only reachable from a screen that applied a look on being shown, which is what keeps the gate from closing on a
     * launcher with no edge bound.
     */
    fun finish() {
        viewModelScope.launch { settingsRepository.completeOnboarding() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
