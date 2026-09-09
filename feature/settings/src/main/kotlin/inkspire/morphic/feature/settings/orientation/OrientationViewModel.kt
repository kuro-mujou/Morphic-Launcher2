package inkspire.morphic.feature.settings.orientation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.RotationMode
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
 * @property settings the stored answers. Not nullable, for `ExtrasState`'s reason: nothing here is resolved per
 *   device, so the slice's own default is a real answer rather than a stand-in for one.
 */
data class OrientationState(val settings: OrientationSettings = OrientationSettings.Default)

/**
 * Screen-level state holder for **Orientation**: what the launcher does when the device turns or folds.
 *
 * No `setDevice` and no preview, as in `ExtrasViewModel` and for the same reason — this section sizes nothing. It is
 * the one section whose settings describe the *window* rather than something drawn in it, which is also why applying
 * them is not this holder's job: a `requestedOrientation` belongs to an Activity, and reaching for one from here
 * would put a `Context` in a state holder.
 */
class OrientationViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val state: StateFlow<OrientationState> = settingsRepository.orientationSettings
        .map(::OrientationState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OrientationState())

    /** Locks the launcher to one orientation, or lets it follow the device again. */
    fun setRotationMode(mode: RotationMode) {
        viewModelScope.launch { settingsRepository.setRotationMode(mode) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
