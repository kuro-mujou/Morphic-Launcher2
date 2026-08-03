package inkspire.morphic.feature.settings.icons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.data.settings.IconOverride
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
 * What the icon-sizing screen shows.
 *
 * @property slot the grid being edited.
 * @property sizing its **resolved** sizing, or null until the screen reports its device — resolution is per device
 *   configuration, so there is no honest value before then.
 */
data class IconSizingState(
    val slot: GridSlot = EditableSlots.first(),
    val sizing: IconSizing? = null,
)

/**
 * Grids whose icon sizing has **moved into their surface's own section**, and so no longer appears here.
 *
 * The list shrinks this one as sections arrive — the direction L1 already pointed: its home, drawer, dock and folder
 * details each embedded `IconLayoutControls`, and it had no separate icons section at all for grid sizing. This
 * screen is the waiting room for grids whose surface has no section yet, not a second place to edit the ones that do.
 */
private val Relocated = setOf(
    GridSlot.HOME_MAIN,
    GridSlot.HOME_DOCK,
    GridSlot.APPS_LIST,
    GridSlot.APPS_SCROLL,
    GridSlot.APPS_PAGER,
    GridSlot.APPS_CATEGORY,
)

/**
 * Every grid whose icons this screen still sizes — each [GridSlot] whose blueprint declares icon sizing, less those
 * that have moved out.
 *
 * Derived from the registry rather than listed by hand, which has a useful consequence: adding a grid that draws icons
 * makes it editable here with no change to this screen, and adding one that draws *tiles* (the category card, whose
 * blueprint declares no icon sizing) correctly does not.
 */
internal val EditableSlots: List<GridSlot> =
    GridSlot.entries.filter { it.blueprint.icon != null && it !in Relocated }

/**
 * Screen-level state holder for the icon-sizing section: reads one grid's resolved sizing, writes sparse overrides.
 *
 * **No `min`/`max` clamp anywhere.** L1 needed one, written inline at each of its two slider call sites where the pair
 * could disagree; here the guardrails are one range slider whose thumbs cannot cross, so their ordering is a property of
 * the control rather than a rule this holder enforces. Choosing the right control removed the invariant instead of
 * moving it.
 */
class IconSizingViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val slot = MutableStateFlow(EditableSlots.first())
    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<IconSizingState> = combine(slot, device) { current, configuration -> current to configuration }
        .flatMapLatest { (current, configuration) ->
            if (configuration == null) {
                flowOf(IconSizingState(current, sizing = null))
            } else {
                settingsRepository.iconSizing(current, configuration).map { IconSizingState(current, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IconSizingState())

    /** Reports the device configuration being edited — the UI's one job, as on every other surface. */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /** Switches which grid is being edited. */
    fun selectSlot(value: GridSlot) {
        slot.value = value
    }

    /**
     * The controls' commands, shared with every section that embeds the same controls.
     *
     * Exposed as an object rather than re-declared as methods here: this screen and the per-surface sections issue
     * *identical* writes differing only in which grid they name, and the grid is already a parameter.
     */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        slot = { slot.value },
        device = { device.value },
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
