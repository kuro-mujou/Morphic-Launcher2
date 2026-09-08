package inkspire.morphic.feature.settings.iconstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.ComponentKey
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
            settingsRepository.appliedIconPreset,
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
        // The name goes with the recipe, and it is what the ring is resolved from — see [IconsState.appliedPreset].
        settingsRepository.setIconAppearance(preset.appearance, preset.name)
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
 * @property applied the name the last apply stamped, or null when the recipe in force came from anywhere else. Not
 *   yet an answer about the library — see [appliedPreset].
 * @property sample the app whose artwork every tile draws, or null until the app cache answers.
 */
internal data class IconsState(
    val presets: List<IconPreset> = emptyList(),
    val applied: String? = null,
    val sample: ComponentKey? = null,
) {

    /**
     * The name of the preset currently in force, or null when none is.
     *
     * **Resolved by name, never by comparing recipes, because a preset's recipe is not its identity.** Duplicating a
     * look under a second name is a supported thing to do — it is how a variation is kept for later adjustment — so
     * two presets can be value-equal by design, and a `firstOrNull` over recipes then rings whichever was saved
     * first no matter which tile was pressed. That was the bug, and comparing looks *at all* is what caused it: the
     * name is the only thing that tells clones apart, and it can be, since `IconPresets.with` replaces rather than
     * duplicating and so keeps at most one preset per name.
     *
     * The one thing checked is that the name still names something — a preset can be deleted after being applied,
     * and a dangling stamp would make this claim a preset is in force when the library no longer holds it.
     *
     * The cost, and it is deliberate: **a look re-created by hand in the studio marks nothing.** Value comparison
     * used to catch that, and it is genuinely a small loss — but a recipe arrived at by editing did not come from a
     * preset, and paying for that with an unreliable ring on every clone is the wrong trade.
     */
    val appliedPreset: String? get() = applied?.takeIf { name -> presets.any { it.name == name } }
}
