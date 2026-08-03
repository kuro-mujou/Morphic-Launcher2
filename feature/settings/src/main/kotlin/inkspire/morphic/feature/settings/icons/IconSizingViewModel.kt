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
 * Every grid whose icons a user can size — each [GridSlot] whose blueprint declares icon sizing.
 *
 * Derived from the registry rather than listed by hand, which has a useful consequence: adding a grid that draws icons
 * makes it editable here with no change to this screen, and adding one that draws *tiles* (the category card, whose
 * blueprint declares no icon sizing) correctly does not.
 */
internal val EditableSlots: List<GridSlot> = GridSlot.entries.filter { it.blueprint.icon != null }

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
     * Commits a numeric field.
     *
     * `internal`, like [IconSizingField]: this is the vocabulary the section's own controls speak, not API for anyone
     * outside the feature.
     */
    internal fun change(field: IconSizingField, value: Float) {
        edit {
            when (field) {
                IconSizingField.IconPercent -> copy(iconPercent = value)
                IconSizingField.LabelScale -> copy(labelScale = value)
            }
        }
    }

    /**
     * Commits both icon-size guardrails together, in whole dp.
     *
     * **No clamp here, and that is the point of using a range slider.** The two bounds used to be independent sliders,
     * which meant this had to stop them crossing — against the *resolved* values, since a sparse override may hold null
     * for the other half — and had to avoid pinning a field that was still following the blueprint. A two-thumb control
     * cannot produce a crossed pair at all, so the whole clamp and its subtlety are gone rather than relocated.
     */
    fun changeIconDp(range: IntRange) {
        edit { copy(minIconDp = range.first, maxIconDp = range.last) }
    }

    /**
     * Commits a boolean field; pass null for the one not being changed.
     *
     * Parameters are named for the thing rather than the field (`label`, not `showLabel`) so the receiver's own
     * properties are readable inside the transform without `this.` disambiguation.
     */
    fun toggle(label: Boolean? = null, icon: Boolean? = null) {
        edit { copy(showLabel = label ?: showLabel, showIcon = icon ?: showIcon) }
    }

    /**
     * Clears every override for this grid on this device, returning it to its blueprint's defaults.
     *
     * A plain write of an empty override rather than a dedicated operation — and the store then *removes* the entry
     * instead of storing an empty one, so resetting leaves storage exactly as it started. That is the payoff of sparse
     * overrides: "back to default" needs no separate concept, and no default is copied into storage to express it.
     */
    fun reset() {
        edit { IconOverride() }
    }

    private fun edit(transform: IconOverride.() -> IconOverride) {
        val configuration = device.value ?: return
        val current = slot.value
        viewModelScope.launch { settingsRepository.updateIcon(current, configuration, transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
