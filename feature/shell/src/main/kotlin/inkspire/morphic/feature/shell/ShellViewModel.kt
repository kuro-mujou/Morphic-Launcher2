package inkspire.morphic.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SurfaceRegister
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the launcher shell renders.
 *
 * One field today, and still a state object rather than a bare [SurfaceRegister]: the shell's next two inputs are
 * already named — the **wallpaper-brightness** signal that replaces its hardcoded `darkTheme` (B7b), and the
 * per-layout one-finger swipe policies. Both join here, and a screen whose state is one value has nowhere to put the
 * second without changing every reader.
 *
 * @property register HOME's layout, its per-edge bindings, and the crossing transition. [SurfaceRegister.Default]
 *   until the store's first emission, which is why the shell never renders "no settings" — it renders the defaults,
 *   and the defaults bind no edge.
 */
data class ShellState(
    val register: SurfaceRegister = SurfaceRegister.Default,
)

/**
 * Screen-level state holder for the launcher shell: reads the surface register and nothing else.
 *
 * **Why a ViewModel for what looks like one flow read.** It would be one line to `koinInject<SettingsRepository>()` in
 * the composable and collect it there — and that is precisely what L1's settings feature did, 25 times, which is why
 * it has no presentation layer to port. The rule is a ViewModel per screen, and the value shows up the moment the
 * second input arrives: joining wallpaper brightness to the register is a `combine` here, or a second
 * `collectAsStateWithLifecycle` plus assembly logic in the composable there.
 *
 * No write path: the shell *obeys* the register, it doesn't edit it. Editing is the settings surface's job, and the
 * shell finds out through the same flow as everything else.
 */
class ShellViewModel(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ShellState> = settingsRepository.surfaceRegister
        .map(::ShellState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShellState())

    private companion object {
        /** Keeps the store subscription alive across a configuration change instead of tearing it down and back up. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
