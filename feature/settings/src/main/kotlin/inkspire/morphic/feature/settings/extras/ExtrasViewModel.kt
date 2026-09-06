package inkspire.morphic.feature.settings.extras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AlphabetStripStyle
import inkspire.morphic.data.settings.AlphabetStrip
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Extras section shows.
 *
 * @property alphabetStrip the stored strip settings. Not nullable, unlike the sections that resolve per device: this
 *   one has no device dimension, so the slice's own default is a real answer rather than a stand-in for one.
 */
data class ExtrasState(val alphabetStrip: AlphabetStrip = AlphabetStrip.Default)

/**
 * Screen-level state holder for **Extras**: the launcher-wide additions that belong to no single surface.
 *
 * **One setting today, and the section exists anyway** — which is a claim about where the A–Z strip belongs rather
 * than a placeholder. The strip attaches to *A–Z-ordered content*, and two surfaces have that: the APPS derived
 * layouts now, HOME's vertical list next. Filed under APPS it would have to be read by HOME from a record named for
 * another surface, or renamed later — and a renamed settings key is a setting the user loses.
 *
 * No device configuration and no preview, so there is no `setDevice` here and no `SamplePreviewApp`. Every other
 * section has both because every other section sizes something; this one turns things on.
 */
class ExtrasViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val state: StateFlow<ExtrasState> = settingsRepository.alphabetStrip
        .map(::ExtrasState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExtrasState())

    /** Draws the A–Z strip on the surfaces whose content is ordered by it, or stops drawing it. */
    fun setAlphabetStripEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAlphabetStripEnabled(enabled) }
    }

    /** Switches the strip's look. Writable while the strip is off, and the control is hidden then — see the pane. */
    fun setAlphabetStripStyle(style: AlphabetStripStyle) {
        viewModelScope.launch { settingsRepository.setAlphabetStripStyle(style) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
