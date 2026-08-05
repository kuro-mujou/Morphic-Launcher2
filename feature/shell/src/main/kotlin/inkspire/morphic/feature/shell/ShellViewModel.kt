package inkspire.morphic.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SurfaceRegister
import inkspire.morphic.data.wallpaper.WallpaperBrightness
import inkspire.morphic.data.wallpaper.WallpaperRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What the launcher shell renders.
 *
 * Two fields now that the wallpaper-brightness signal has landed, which is the case the state object was kept for: the
 * remaining named input is the per-layout one-finger swipe policies, and it joins the same way.
 *
 * @property register HOME's layout, its per-edge bindings, and the crossing transition. [SurfaceRegister.Default]
 *   until the store's first emission, which is why the shell never renders "no settings" — it renders the defaults,
 *   and the defaults bind no edge.
 * @property brightness how bright the wallpaper behind the chrome is, which is the launcher's dark/light input.
 *   [WallpaperBrightness.DARK] until the first read, which is what the shell hardcoded before this existed — so a
 *   first frame looks exactly as it used to and then corrects if the wallpaper is bright.
 */
data class ShellState(
    val register: SurfaceRegister = SurfaceRegister.Default,
    val brightness: WallpaperBrightness = WallpaperBrightness.DARK,
)

/**
 * Screen-level state holder for the launcher shell: the surface register, and how bright the wallpaper is.
 *
 * **Why a ViewModel for what looked like one flow read.** It would have been one line to `koinInject` a repository in
 * the composable and collect it there — and that is precisely what L1's settings feature did, 25 times, which is why
 * it has no presentation layer to port. The rule is a ViewModel per screen, and the second input has now arrived: this
 * is the `combine` that was predicted, rather than a second `collectAsStateWithLifecycle` plus assembly logic in the
 * composable.
 *
 * **Two repositories, and they are unrelated on purpose.** The register is a preference and brightness is a reading of
 * the world; joining them is this holder's whole job, and neither store has to know the other exists.
 *
 * No write path: the shell *obeys* both. Editing the register is the settings surface's job, and brightness is not
 * anybody's to set — it is what the wallpaper happens to be.
 */
class ShellViewModel(
    settingsRepository: SettingsRepository,
    wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    val state: StateFlow<ShellState> =
        combine(settingsRepository.surfaceRegister, wallpaperRepository.brightness, ::ShellState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShellState())

    private companion object {
        /** Keeps the store subscription alive across a configuration change instead of tearing it down and back up. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
