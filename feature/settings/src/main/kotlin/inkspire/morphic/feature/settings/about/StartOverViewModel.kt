package inkspire.morphic.feature.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.settings.LookRepository
import kotlinx.coroutines.launch

/**
 * State holder for About's "Run first-time setup again" row.
 *
 * **Its own holder rather than a member of [AboutViewModel]**, which reads a package snapshot and has no store behind it
 * because nothing on its screens is a setting. This is the one thing in About that writes.
 */
internal class StartOverViewModel(private val lookRepository: LookRepository) : ViewModel() {

    /**
     * Re-opens the first-run screen with the current setup offered first — see [LookRepository.startOver]. The gate
     * follows the store, so this screen is replaced the moment the write lands.
     */
    fun startOver() {
        viewModelScope.launch { lookRepository.startOver() }
    }
}
