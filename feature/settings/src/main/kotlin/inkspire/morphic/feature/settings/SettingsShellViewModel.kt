package inkspire.morphic.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.settings.setup.DefaultLauncherRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The **shell's** own state holder — the settings surface has exactly one thing to know that no section owns.
 *
 * HOME's pairing decides what its two **zone** panes are called — a grid or a list, a dock or a widget area — and so
 * what the app bar titles them. A title that contradicted the pane under it would be worse than a generic one, so the
 * shell reads the pairing and names them accordingly ([SettingsSection.meta]).
 *
 * **The list itself renames nothing.** HOME's two zones as top-level rows would make the *index* change under the
 * user as a setting in another section moved; it carries one "Home" row instead, and the renaming happens a level
 * down, where the pairing is also chosen. What is left here is the app bar, which spans
 * every pane and so belongs to nothing else — and `SettingsList` still takes the pairing, because the hub is reached
 * through the same row component.
 *
 * **A ViewModel rather than a `koinInject<SettingsRepository>()` in composition**, small as it is. That shortcut is
 * how a settings module ends up with one ViewModel and no unit-testable layer anywhere. One more of these is cheaper
 * than the first exception to the rule.
 */
internal class SettingsShellViewModel(
    settingsRepository: SettingsRepository,
    private val defaultLauncherRole: DefaultLauncherRole,
) : ViewModel() {

    /** HOME's pairing. Defaults to the register's own default until the store's first emission. */
    val homeLayout: StateFlow<HomeLayout> = settingsRepository.surfaceRegister
        .map { it.homeLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeLayout.PAGER_WITH_DOCK)

    private val mutableDefaultLauncherRequest = MutableStateFlow<Intent?>(null)

    /**
     * How to ask to become the home app, or **null when there is nothing to ask** — because this launcher already is
     * one, or because the device offers no way to choose.
     *
     * **One nullable field rather than a boolean beside an intent**, and that is what makes the row it draws unable
     * to be wrong: a visible row with nothing to launch and a launchable intent with no row are both unrepresentable.
     * The first of the setup hub's steps (`O6`), and the reason it is a *flow* rather than a value read once is
     * [refreshDefaultLauncher].
     */
    val defaultLauncherRequest: StateFlow<Intent?> = mutableDefaultLauncherRequest.asStateFlow()

    /**
     * Re-derives whether the ask is still needed.
     *
     * **Called on every resume, which is not belt-and-braces.** The step is completed in a *system* dialog that
     * reports nothing back to us, so the only moment we can learn the answer changed is the moment we are shown
     * again — and a row still offering to do what the user has just done is the exact "control that changes nothing"
     * this codebase refuses to draw.
     *
     * Off the main thread: both halves are binder calls into the package manager.
     */
    fun refreshDefaultLauncher() {
        viewModelScope.launch {
            mutableDefaultLauncherRequest.value = withContext(Dispatchers.Default) {
                if (defaultLauncherRole.isDefault()) null else defaultLauncherRole.requestIntent()
            }
        }
    }

    // **The pairing is read only, and written by `HomeHubViewModel`** — the section whose one control changes it;
    // this holder reads it because the *app bar* is named from it, which spans every pane and so belongs to nothing
    // else. The icon presets belong to `IconsViewModel` rather than here — the same conclusion the Home hub reached
    // independently: a hub with a real read of its own is a section, not a corner of the shell. The setup step above
    // is the shell's for that same test read the other way: it belongs to no section, and it is drawn on the index.

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
