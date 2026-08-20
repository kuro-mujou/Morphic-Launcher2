package inkspire.morphic.feature.settings.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.settings.SurfaceRegister
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the surface-register screen shows.
 *
 * @property register the stored register. [SurfaceRegister.Default] until the store's first emission — which binds
 *   nothing, and is exactly what a fresh install looks like.
 */
data class SurfaceRegisterState(
    val register: SurfaceRegister = SurfaceRegister.Default,
)

/**
 * Screen-level state holder for the surface-register section: reads the register, writes edge bindings.
 *
 * **The first settings ViewModel, so it sets the pattern** for every section that follows: one immutable
 * [StateFlow] out, plain typed methods in, and no `Intent`/reducer ceremony. It matters that it exists at all — L1's
 * settings screens read `koinInject<SettingsRepository>()` straight from composition, 25 times across the module, with
 * exactly one ViewModel in the whole feature. That is why its screens had no unit-testable layer and why every one of
 * them mixed assembly with rendering.
 *
 * Writes are fire-and-forget on `viewModelScope`: the repository is the source of truth, so there is no optimistic
 * local copy to keep in step — the flow re-emits and the screen redraws. That also means a write cannot half-apply the
 * way, since each call is one atomic slice update.
 */
class SurfaceRegisterViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SurfaceRegisterState> = settingsRepository.surfaceRegister
        .map(::SurfaceRegisterState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SurfaceRegisterState())

    /**
     * Binds [edge] to the APPS surface rendered in [layout], or **unbinds** it when [layout] is null.
     *
     * Takes the edge as data rather than offering four methods, mirroring `SettingsRepository.setSide` — and for the
     * same reason a `setSideTop`/`setSideRight`/… quartet would be a smell rather than an API.
     */
    fun bindApps(edge: HomeEdge, layout: AppsLayout?) {
        val binding = layout?.let(SideBinding::Apps)
        viewModelScope.launch { settingsRepository.setSide(edge, binding) }
    }

    // **No `setHomeLayout` here.** HOME's pairing is written by `SettingsShellViewModel`, beside the read the Home
    // hub's segmented control is driven from. It lived here while the register's center card was the switch; leaving
    // it behind would leave a second writer for a setting this screen no longer offers, which is how two controls
    // for one setting grow back.

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
