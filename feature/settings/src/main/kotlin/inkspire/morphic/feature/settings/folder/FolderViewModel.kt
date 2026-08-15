package inkspire.morphic.feature.settings.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.settings.icons.IconSizingEdits
import inkspire.morphic.feature.settings.icons.SamplePreviewApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the folder section shows.
 *
 * @property icon the folder grid's **resolved** icon sizing, or null until the screen reports its device — resolution
 *   is per device configuration, so there is no honest value before then. The same "null is not yet" convention every
 *   other section here uses.
 */
data class FolderState(val icon: IconSizing? = null)

/**
 * Screen-level state holder for the **folder** section: the sizing of the icons inside an opened folder.
 *
 * **One grid, no chip row, and no grid editor** — which is what distinguishes it from the icon-sizing screen it
 * replaced. That screen switched between grids because it was a waiting room for several; this section belongs to one
 * thing, so the slot is a constant. And `FolderGrid.editRange` is null — a folder's rows and columns are the
 * blueprint's, since its card is sized to fit the screen rather than chosen — so there is nothing here for
 * `GridEditor` to edit, exactly as L1's `FolderSettingsDetail` had an empty layout section and its icon controls only.
 *
 * **It writes through the shared [IconSizingEdits]**, like every other section, so a folder's icons are committed by
 * the same code path as home's or the dock's. That sharing is the whole reason moving these controls between sections
 * costs nothing.
 */
class FolderViewModel(
    private val settingsRepository: SettingsRepository,
    appRepository: AppRepository,
) : ViewModel() {

    /** The app the icon preview draws, and the dice that changes it. Shared by every section that has a preview. */
    internal val sample = SamplePreviewApp(appRepository, viewModelScope)

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FolderState> = device
        .flatMapLatest { configuration ->
            if (configuration == null) {
                flowOf(FolderState())
            } else {
                settingsRepository.iconSizing(SLOT, configuration).map { FolderState(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FolderState())

    /** Reports the device configuration being edited — the UI's one job, as on every other surface. */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /** The folder grid's icon sizing commands, shared with every other section that offers the same controls. */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        slot = { SLOT },
        device = { device.value },
    )

    private companion object {
        /**
         * The folder grid — **and the category card's expansion**, which is the same overlay on the same grid
         * (`FolderGrid`'s KDoc says so, and one `AppCollectionOverlay` renders both). So these controls size the icons in
         * both, which the screen states rather than leaving a user to discover.
         */
        val SLOT = GridSlot.FOLDER
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
