package inkspire.morphic.feature.settings.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
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
 * way L1's preset application could, since each call is one atomic slice update.
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
     * same reason L1's four `setSideTop`/`setSideRight`/… were a smell rather than an API.
     */
    fun bindApps(edge: HomeEdge, layout: AppsLayout?) {
        val binding = layout?.let(SideBinding::Apps)
        viewModelScope.launch { settingsRepository.setSide(edge, binding) }
    }

    /**
     * Sets HOME's own pairing — its main area and the side zone that comes with it.
     *
     * One write and nothing else, which is the whole of switching layouts: each pairing's grids have their own stored
     * sizes and their own stored contents (`home_list_item` beside the placement tables), so the one it is *not*
     * drawing keeps everything it had. Switching back finds it as it was, which is exactly what L1's shared store
     * could not offer — its list and its grid were one arrangement, so reordering one flattened the other.
     */
    fun setHomeLayout(layout: HomeLayout) {
        viewModelScope.launch { settingsRepository.setHomeLayout(layout) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
