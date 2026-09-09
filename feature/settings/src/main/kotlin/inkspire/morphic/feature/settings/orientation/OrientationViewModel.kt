package inkspire.morphic.feature.settings.orientation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.core.model.authoredArrangement
import inkspire.morphic.core.model.oppositeOrientation
import inkspire.morphic.core.model.portraitOfFormFactor
import inkspire.morphic.core.model.referenceSnapshot
import inkspire.morphic.data.layout.AppsOrderRepository
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.copyArrangement
import inkspire.morphic.data.layout.snapshotArrangement
import inkspire.morphic.data.settings.OrientationSettings
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.homeZoneGrids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which arrangement survives when the two postures stop being edited separately.
 *
 * Asked rather than assumed because by then there are genuinely two layouts and no way to tell which one the user
 * considers theirs — they made both.
 */
enum class IndependenceMerge {
    /** Portrait's arrangement becomes the shared one; landscape re-derives from it. */
    KEEP_PORTRAIT,

    /** Landscape's arrangement is carried back into portrait and becomes the shared one. */
    KEEP_LANDSCAPE,

    /** Neither: the layout as it stood when independence was switched on comes back. */
    RESTORE_SNAPSHOT,
}

/**
 * What the Orientation section shows.
 *
 * @property settings the stored answers. The reported device is deliberately **not** here: nothing in this pane is
 *   drawn differently because of it, and the merge actions that do need it read it from the holder. A field the UI
 *   never reads is one a later reader has to check for a use that does not exist.
 */
data class OrientationState(val settings: OrientationSettings = OrientationSettings.Default)

/**
 * Screen-level state holder for **Orientation**: what the launcher does when the device turns.
 *
 * **It is the one settings section that writes *layout* as well as settings.** Turning independence on or off is not
 * a flag on its own — it decides what happens to two arrangements — and the alternative was for the home surface to
 * notice the flag had changed and react, which makes a write a side effect of reading state. That is the shape this
 * codebase has already been bitten by; here the button press owns the whole consequence, exactly as the grid
 * editor's `±` owns the placements it displaces.
 */
class OrientationViewModel(
    private val settingsRepository: SettingsRepository,
    private val layoutRepository: LayoutRepository,
    private val appsOrderRepository: AppsOrderRepository,
) : ViewModel() {

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    val state: StateFlow<OrientationState> = settingsRepository.orientationSettings
        .map(::OrientationState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OrientationState())

    /** Reports the window configuration the pane is drawn on — the merge actions resolve their grids from it. */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /** Locks the launcher to one orientation, or lets it follow the device again. */
    fun setRotationMode(mode: RotationMode) {
        viewModelScope.launch { settingsRepository.setRotationMode(mode) }
    }

    /**
     * Gives landscape a layout of its own, starting from the one it is showing.
     *
     * **The snapshot is the whole of the work.** Both postures already hold correct rows — that is what being kept
     * in step means — so nothing has to be materialized for them. What is taken is a copy of the reference *as it
     * stands*, parked in the form factor's `*_SHARED` key, so that switching independence back off later can offer
     * "neither" as a real answer rather than a euphemism for "portrait".
     *
     * Verbatim rather than projected: source and target describe the same grid, and re-laying would close the gaps
     * the snapshot exists to preserve.
     */
    fun enableIndependentLayout() {
        val configuration = device.value ?: return
        viewModelScope.launch {
            val reference = configuration.authoredArrangement.portraitOfFormFactor
            layoutRepository.snapshotArrangement(reference, reference.referenceSnapshot)
            appsOrderRepository.copyPager(reference, reference.referenceSnapshot, PAGER_SNAPSHOT_CAPACITY)
            settingsRepository.setIndependentLayout(true)
        }
    }

    /**
     * Puts the two postures back in step, with [merge] deciding which layout they agree on.
     *
     * **Only the reference posture is written.** Landscape is rebuilt from portrait the next time it is drawn — the
     * home surface does that unconditionally while the two are shared — so setting portrait right and clearing the
     * flag is the entire operation. Writing landscape here as well would be a second answer to the same question,
     * and the two could disagree.
     *
     * The flag is cleared **last**, so nothing re-derives landscape from a portrait that is still mid-merge.
     */
    fun disableIndependentLayout(merge: IndependenceMerge) {
        val configuration = device.value ?: return
        viewModelScope.launch {
            val reference = configuration.authoredArrangement.portraitOfFormFactor
            when (merge) {
                // Portrait already holds what it should; landscape's divergence is simply dropped.
                IndependenceMerge.KEEP_PORTRAIT -> Unit

                IndependenceMerge.KEEP_LANDSCAPE -> reference.oppositeOrientation?.let { landscape ->
                    // Laid out against **portrait's** grids, not the ones on screen: this is writing the reference,
                    // and a landscape arrangement stored against a landscape lattice would be positions for a screen
                    // the reference is never drawn on.
                    val grids = settingsRepository.homeZoneGrids(configuration.portrait).first()
                    layoutRepository.copyArrangement(landscape, reference, grids)
                    appsOrderRepository.copyPager(landscape, reference, PAGER_SNAPSHOT_CAPACITY)
                }

                IndependenceMerge.RESTORE_SNAPSHOT -> {
                    layoutRepository.snapshotArrangement(reference.referenceSnapshot, reference)
                    appsOrderRepository.copyPager(reference.referenceSnapshot, reference, PAGER_SNAPSHOT_CAPACITY)
                }
            }
            settingsRepository.setIndependentLayout(false)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The page size a pager copy is written at.
         *
         * Arbitrary on purpose, and safe because page boundaries in that store are advisory — every read
         * re-paginates at the reader's own capacity, so what crosses over is the order. See
         * `AppsOrderRepository.copyPager`.
         */
        const val PAGER_SNAPSHOT_CAPACITY = 20
    }
}
