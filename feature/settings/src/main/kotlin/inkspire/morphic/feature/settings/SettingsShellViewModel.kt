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
 * HOME's pairing decides what its two **zone** panes are called — a grid or a list, a dock or a widget area — and so
 * what the app bar titles them. A title that contradicted the pane under it would be worse than a generic one, so the
 * shell reads the pairing and names them accordingly ([SettingsSection.meta]).
 *
 * **The list itself no longer renames anything**, which is the Home hub's doing: those two zones used to be top-level
 * rows, so the *index* changed under the user as a setting in another section moved. It carries one "Home" row now and
 * the renaming happens a level down, where the pairing is also chosen. What is left here is the app bar, which spans
 * every pane and so belongs to nothing else — and `SettingsList` still takes the pairing, because the hub is reached
 * through the same row component.
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

    // **Read only, and one field.** The pairing is written by `HomeHubViewModel`, in the section whose one control
    // changes it; this holder reads it because the *app bar* is named from it, which spans every pane and so belongs
    // to nothing else. The icon presets used to sit here too, on the reasoning that the Icons pane is a hub holding no
    // state of its own — they have since moved to `IconsViewModel`, which is the same conclusion the Home hub reached
    // independently: a hub with a real read of its own is a section, not a corner of the shell.

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
