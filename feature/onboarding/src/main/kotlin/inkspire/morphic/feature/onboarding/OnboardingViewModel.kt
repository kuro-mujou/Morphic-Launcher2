package inkspire.morphic.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.data.settings.LookRepository
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The id of the row offering back the setup the user had when they started over. */
private const val CurrentSetupId = "current"

/**
 * State holder for [OnboardingGate] and the first-run screen: whether setup is finished, the offered looks, and the
 * writes that choose one.
 *
 * **Scoped to the Activity, and that is its real lifetime.** The gate sits outside navigation, so `koinViewModel`
 * resolves against the Activity's store — and the gate lives exactly as long as the Activity does.
 */
internal class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val lookRepository: LookRepository,
    private val builtInLooks: BuiltInLooks,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val options = MutableStateFlow<List<LookOption>>(emptyList())
    private val selected = MutableStateFlow<String?>(null)
    private var applying: Job? = null

    val state: StateFlow<OnboardingState> = combine(
        settingsRepository.onboarding,
        settingsRepository.surfaceRegister,
        options,
        selected,
    ) { onboarding, register, looks, selectedId ->
        OnboardingState(
            gate = if (onboarding.completed) OnboardingGateState.CLOSED else OnboardingGateState.OPEN,
            looks = looks.map { LookRow(id = it.id, name = it.title, summary = it.summary) },
            selected = selectedId,
            previewEdge = register.sides.keys.firstOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnboardingState())

    /**
     * Reads the offered looks and applies the first, once — called as the first-run screen is shown, so a launcher whose
     * setup is long finished never reads them.
     *
     * **After starting over, the first is the setup the user already had**, so the screen opens on exactly what they
     * left and applying it changes nothing. Only a fresh install opens on a shipped look.
     */
    fun onScreenShown() {
        if (selected.value != null) return
        viewModelScope.launch {
            if (options.value.isEmpty()) {
                val current = lookRepository.restartLook.first()?.let { look ->
                    LookOption(
                        id = CurrentSetupId,
                        title = "Your current setup",
                        look = look,
                        summary = "Everything as it is now.",
                    )
                }
                options.value = listOfNotNull(current) + withContext(dispatchers.io) { builtInLooks.load() }
            }
            options.value.firstOrNull()?.let { select(it.id) }
        }
    }

    /**
     * Applies the look [id] for real, since the preview *is* the launcher and shows only what the store holds.
     *
     * **The flag is stamped unfinished first**: an absent flag beside a stored register reads as an install set up before
     * the flag existed, so applying without the stamp would close the gate. **A newer choice cancels an older apply**, so
     * taps in quick succession end on the last one rather than on whichever write finished last.
     */
    fun select(id: String) {
        val option = options.value.firstOrNull { it.id == id } ?: return
        selected.value = id
        applying?.cancel()
        applying = viewModelScope.launch {
            settingsRepository.beginOnboarding()
            lookRepository.apply(option.look)
        }
    }

    /**
     * Finishes setup with the look on screen — after its apply has landed, so the gate never closes on half of one — and
     * then forgets the setup kept for starting over. **Finished first, forgotten second**: the other order, interrupted,
     * would re-open this screen with nothing to offer back but a shipped look.
     */
    fun use() {
        viewModelScope.launch {
            applying?.join()
            settingsRepository.completeOnboarding()
            lookRepository.clearRestartLook()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
