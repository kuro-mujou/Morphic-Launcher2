package inkspire.morphic.feature.settings.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.sideSlot
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Home hub shows.
 *
 * @property layout HOME's pairing — the one setting this screen writes, and what decides which two zones its rows
 *   lead to. A real default rather than a null, because the rows have to be drawn before the store answers.
 * @property sideExtentDp the side zone's thickness, for the mockup alone: it is what makes the strip in the picture
 *   the size the dock (or widget area) really is. **Null is "not yet"**, since it is keyed by a device configuration
 *   the screen has yet to report — the same convention every other section here uses. Nothing on this screen edits
 *   it; the zone's own pane does.
 */
data class HomeHubState(
    val layout: HomeLayout = HomeLayout.PAGER_WITH_DOCK,
    val sideExtentDp: Int? = null,
)

/**
 * Screen-level state holder for the Home hub: reads HOME's pairing, writes it, and reads the one number its mockup
 * needs.
 *
 * **A ViewModel even though the hub edits nothing but one enum.** The mockup is what forced it: drawing the two zones
 * at their *real* proportion means resolving the side zone's stored extent for this device, which is a keyed read over
 * two flows — a section's shape, not a shell's. The alternative was to draw the blueprint default, and a hub showing a
 * 96dp dock one tap away from a pane showing the 200dp one the user set is the exact contradiction a preview exists to
 * prevent.
 *
 * **The Icons hub reached the same place from the other direction**, which is worth knowing because this note used to
 * cite it as the counter-example: its preset list lived on `SettingsShellViewModel` on the reasoning that a hub holds
 * no state of its own, and it has since moved to `IconsViewModel`. Two hubs, two independent conclusions — a hub with
 * a real read of its own is a section, not a corner of the shell.
 *
 * **This is where the pairing is written, and it is the only place.** It lived on `SurfaceRegisterViewModel` while the
 * register's center card was the switch; leaving a second writer behind is how two controls for one setting grow back.
 * `SettingsShellViewModel` still *reads* it, for the list's row names and the app bar's title — one writer, several
 * readers, which is the ordinary shape.
 */
class HomeHubViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    private val layout: StateFlow<HomeLayout> =
        settingsRepository.surfaceRegister
            .map { it.homeLayout }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.PAGER_WITH_DOCK)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeHubState> =
        combine(device, layout) { configuration, current -> configuration to current }
            .flatMapLatest { (configuration, current) ->
                if (configuration == null) {
                    // The pairing is still real before the device reports — the rows and the switch are drawn from it,
                    // and only the mockup has to wait.
                    flowOf(HomeHubState(layout = current))
                } else {
                    settingsRepository.extent(current.sideSlot, configuration)
                        .map { HomeHubState(layout = current, sideExtentDp = it) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeHubState())

    /**
     * Reports the device configuration, which every keyed read below is resolved against.
     *
     * `setDevice` rather than a narrower input, matching every other surface here: pushing the configuration down
     * means the section derives what it needs from it rather than being handed a pre-chewed value that would have to
     * be re-derived the moment a second question arrived.
     */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /**
     * Sets HOME's pairing — its main area and the side zone that comes with it.
     *
     * One write and nothing else, which is the whole of switching pairings: each one's grids have their own stored
     * sizes and their own stored contents (`home_list_item` beside the placement tables), so the one it is *not*
     * drawing keeps everything it had. Switching back finds it as it was — exactly what L1's shared store could not
     * offer, since its list and its grid were one arrangement and reordering either flattened the other. That is what
     * makes this one tap with no confirm.
     */
    fun setLayout(layout: HomeLayout) {
        viewModelScope.launch { settingsRepository.setHomeLayout(layout) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
