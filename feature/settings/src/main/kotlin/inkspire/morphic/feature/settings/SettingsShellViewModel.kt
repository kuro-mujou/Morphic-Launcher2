package inkspire.morphic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The **shell's** own state holder — the settings surface has exactly one thing to know that no section owns.
 *
 * HOME's pairing decides what two of the rows in the list are *called*: `HOME_GRID` is a grid on one pairing and a list
 * on the other, and the side-zone row is a dock or a widget area. A row whose title contradicted the pane it opens
 * would be worse than a generic one, so the list reads the pairing and names them accordingly ([SettingsSection.meta]).
 *
 * **A ViewModel rather than a `koinInject<SettingsRepository>()` in composition**, small as it is. That shortcut is
 * exactly what L1 did — 25 times across its settings module, with one ViewModel in the whole feature — and it is why
 * none of its screens had a unit-testable layer. One more of these is cheaper than the first exception to the rule.
 */
class SettingsShellViewModel(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** HOME's pairing. Defaults to the register's own default until the store's first emission. */
    val homeLayout: StateFlow<HomeLayout> = settingsRepository.surfaceRegister
        .map { it.homeLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeLayout.PAGER_WITH_DOCK)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
