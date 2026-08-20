package inkspire.morphic.feature.settings.iconstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.settings.IconPreset
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.settings.icons.SamplePreviewApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the **Icons** hub shows: the preset library, which of them is currently in force, and an app to draw them on.
 *
 * **Its own ViewModel rather than the shell's**, which every other section already has — this pane was the last one
 * borrowing `SettingsShellViewModel`, and it stopped being tenable the moment the library needed to *render*: a
 * preview needs an app, an app means `AppRepository`, and the shell has no business holding one for one pane.
 */
internal class IconsViewModel(
    private val settingsRepository: SettingsRepository,
    appRepository: AppRepository,
) : ViewModel() {

    /**
     * The app every tile borrows artwork from — the same helper the four sizing sections use, for the same reason it
     * gives: **which** app it is changes what a recipe looks like, since a legacy icon with a flat plate and an
     * adaptive one with a transparent foreground answer the same layer differently.
     */
    private val sample = SamplePreviewApp(appRepository, viewModelScope)

    val state: StateFlow<IconsState> =
        combine(
            settingsRepository.iconPresets,
            settingsRepository.iconAppearance,
            sample.app,
        ) { presets, applied, app ->
            IconsState(presets = presets, applied = applied, sample = app?.componentKey)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IconsState())

    /**
     * Makes [preset] the recipe every inheriting icon uses, at once.
     *
     * **A tap on a tile writes this, with no confirm, and that is a deliberate call rather than an oversight.** What
     * makes it defensible is that the tile *is* the result — a real icon under this exact recipe — and that
     * [IconsState.appliedPreset] then marks it, so the press has a visible consequence rather than appearing to do
     * nothing. What it costs is real and worth stating: there is no undo here, the presets slice keeping no history,
     * so the way back is another preset or the studio. The library is the safety net — a look worth returning to is
     * a look worth saving first.
     */
    fun apply(preset: IconPreset) = viewModelScope.launch {
        settingsRepository.setIconAppearance(preset.appearance)
    }

    /** Removes a saved preset. Touches nothing it was applied to — a preset is a copy, not a link. */
    fun delete(name: String) = viewModelScope.launch {
        settingsRepository.deleteIconPreset(name)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * @property presets the saved library, in the order it is stored.
 * @property applied the recipe in force for every app that has not been given its own — what a tile is compared
 *   against to know whether it is the one being used.
 * @property sample the app whose artwork every tile draws, or null until the app cache answers.
 */
internal data class IconsState(
    val presets: List<IconPreset> = emptyList(),
    val applied: IconAppearance = IconAppearance.Base,
    val sample: ComponentKey? = null,
) {

    /**
     * The name of the preset currently in force, or null when the default matches none of them.
     *
     * **Compared by value, not remembered as a pointer.** A recipe is a data class all the way down, so equality
     * *is* "these produce the same icon" — which is also what `IconId` keys the bake cache on. So editing the
     * default in the studio until it happens to match a preset marks that preset, correctly: it says which look is
     * on, not which tile was last pressed.
     */
    val appliedPreset: String? get() = presets.firstOrNull { it.appearance == applied }?.name
}
