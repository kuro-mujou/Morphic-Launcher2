package inkspire.morphic.feature.settings.orientation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.core.model.SyncMode
import inkspire.morphic.data.settings.OrientationSettings
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Orientation section shows.
 *
 * @property settings the stored answers, which are the whole of this pane. Nothing here is drawn differently per
 *   posture, so there is no device to report.
 */
data class OrientationState(val settings: OrientationSettings = OrientationSettings.Default)

/**
 * Screen-level state holder for **Orientation**: what the launcher does when the device turns.
 *
 * **It writes settings and nothing else.** Each mode owns its own arrangement rows, so the layout toggle selects a
 * pair rather than reconciling two — which is what leaves this holder with no layout work to do and no question to
 * put to the user.
 */
class OrientationViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<OrientationState> = settingsRepository.orientationSettings
        .map(::OrientationState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OrientationState())

    /** Sets how a layout is carried between the two orientations while they are kept in step. */
    fun setSyncMode(mode: SyncMode) {
        viewModelScope.launch { settingsRepository.setSyncMode(mode) }
    }

    /** Locks the launcher to one orientation, or lets it follow the device again. */
    fun setRotationMode(mode: RotationMode) {
        viewModelScope.launch { settingsRepository.setRotationMode(mode) }
    }

    /**
     * Switches which pair of arrangements every surface reads.
     *
     * **No copy, no question, either direction.** Both pairs keep their rows, so turning this off and on again shows
     * each layout exactly as it was left. The one copy that remains is a *seed*, and it belongs to the surfaces:
     * a pair first used with nothing in it is mirrored from its linked twin, where a write here could only guess at
     * which postures were about to be drawn.
     */
    fun setIndependentLayout(independent: Boolean) {
        viewModelScope.launch { settingsRepository.setIndependentLayout(independent) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
