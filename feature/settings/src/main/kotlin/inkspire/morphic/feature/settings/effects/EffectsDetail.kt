package inkspire.morphic.feature.settings.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.feature.settings.component.SettingsChip
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp

/**
 * **Effects**: how frosted surfaces render over the wallpaper — the one global choice, and its parameters.
 *
 * The port of L1's `EffectsTab`, and structurally the same screen: a chooser, then the sliders belonging to whatever
 * is chosen. Three differences, each following from a decision made before this:
 *
 * - **The sliders come from the *variant*, not from a ten-field bag.** L1 held every parameter of every effect at once
 *   and used a `when` to decide which subset to draw; here the selected `BackdropEffect` carries only its own, so the
 *   `when` is over the sealed type and the compiler checks the mapping is total. The bill is stated in
 *   `SettingsRepository.setBackdropEffect`: switching *between* variants discards the previous one's parameters.
 * - **Chips, not L1's `SettingsOptionRow`.** Five short mutually-exclusive labels are what `SettingsChip` in a
 *   `FlowRow` is for, and it is what the register and APPS sections already use for the same shape of choice.
 * - **No live preview**, unlike every surface section here. Those preview a *cell*, which is a self-contained thing a
 *   pane can draw; an effect previews the wallpaper behind a frosted surface, and the settings pane deliberately has
 *   no backdrop — that is provided at the launcher shell, one zone over. Faking one would mean a second provider,
 *   which is exactly the duplication L1 ended up with. L1 had no preview here either.
 *
 * **Liquid glass is hidden rather than disabled below API 33.** An effect that silently comes out as a plain blur is
 * worse than one that is not offered, so the chip goes and the reason is stated — L1's wording, kept.
 */
@Composable
internal fun EffectsDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<EffectsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text("Effects", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "How folders and other surfaces drawn over the wallpaper are frosted.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        SettingsSectionHeader("Effect")
        val options = BackdropOption.entries.filter {
            it != BackdropOption.LIQUID_GLASS || state.liquidGlassAvailable
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RowGap),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            val selected = state.effect.option
            options.forEach { option ->
                SettingsChip(
                    label = option.label,
                    selected = option == selected,
                    onClick = { viewModel.select(option) },
                )
            }
        }
        if (!state.liquidGlassAvailable) {
            Text(
                text = "Liquid glass is only available on Android 13 and above.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
                modifier = Modifier.padding(top = RowGap),
            )
        }

        // Exhaustive over the sealed type rather than over the chips, so a new variant fails to compile here until it
        // has controls — the same rule `AppsScreen` follows for an unbuilt layout.
        when (val effect = state.effect) {
            BackdropEffect.None -> NoneNote()
            is BackdropEffect.Blur -> BlurControls(effect, viewModel::set)
            is BackdropEffect.MaterialYou -> MaterialYouControls(effect, viewModel::set)
            is BackdropEffect.LiquidGlass -> LiquidGlassControls(effect, viewModel::set)
        }
    }
}

/** Why the rest of the screen is empty — a heading with nothing under it reads as a bug. */
@Composable
private fun NoneNote() {
    Text(
        text = "Frosted surfaces fall back to a flat colour, and the wallpaper is not sampled at all.",
        style = MaterialTheme.typography.bodySmall,
        color = LocalMorphicColors.current.contentMuted,
        modifier = Modifier.padding(top = RowGap * 2),
    )
}

@Composable
private fun BlurControls(effect: BackdropEffect.Blur, onSet: (BackdropEffect) -> Unit) {
    SettingsSectionHeader("Amount")
    SettingsCommitSlider(
        title = "Blur",
        value = effect.strength,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(strength = it)) },
    )
    SettingsCommitSlider(
        title = "Tint",
        subtitle = "How much of the wash sits over the blur",
        value = effect.tint,
        // L1's ceiling for the light/dark tints, and it is a legibility bound rather than a taste one: past ~60% the
        // wash stops being a frost and becomes an opaque sheet, which is the look the backdrop exists to replace.
        valueRange = 0f..MAX_TINT,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(tint = it)) },
    )
}

@Composable
private fun MaterialYouControls(effect: BackdropEffect.MaterialYou, onSet: (BackdropEffect) -> Unit) {
    SettingsSectionHeader("Amount")
    SettingsCommitSlider(
        title = "Blur",
        value = effect.strength,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(strength = it)) },
    )
    SettingsCommitSlider(
        title = "Tint",
        subtitle = "Wallpaper-toned wash over the blur",
        value = effect.tint,
        // Higher than the blurs' ceiling, as in L1: this wash *is* the effect, where theirs is a correction to one.
        valueRange = 0f..MAX_MATERIAL_YOU_TINT,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(tint = it)) },
    )
}

@Composable
private fun LiquidGlassControls(effect: BackdropEffect.LiquidGlass, onSet: (BackdropEffect) -> Unit) {
    SettingsSectionHeader("Lens")
    SettingsCommitSlider(
        title = "Blur",
        subtitle = "Kept low — a heavy blur has no structure left to bend",
        value = effect.blur,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(blur = it)) },
    )
    SettingsCommitSlider(
        title = "Refraction",
        subtitle = "How far the rim bends what is behind it",
        value = effect.refraction,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(refraction = it)) },
    )
    SettingsCommitSlider(
        title = "Depth",
        subtitle = "How far in from the edge the lens reaches",
        value = effect.depth,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(depth = it)) },
    )
    SettingsSectionHeader("Light")
    SettingsCommitSlider(
        title = "Vibrancy",
        value = effect.vibrancy,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(vibrancy = it)) },
    )
    SettingsCommitSlider(
        title = "Sheen",
        subtitle = "Rim highlight",
        value = effect.sheen,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(sheen = it)) },
    )
    SettingsCommitSlider(
        title = "Dispersion",
        subtitle = "Rainbow fringe at the corners",
        value = effect.dispersion,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(dispersion = it)) },
    )
}

/** The chip's text. Kept beside the enum's use rather than on it — a label is this screen's, not the option's. */
private val BackdropOption.label: String
    get() = when (this) {
        BackdropOption.NONE -> "None"
        BackdropOption.LIGHT_BLUR -> "Light blur"
        BackdropOption.DARK_BLUR -> "Dark blur"
        BackdropOption.MATERIAL_YOU -> "Material You"
        BackdropOption.LIQUID_GLASS -> "Liquid glass"
    }

/** Every parameter here is a `0f..1f` strength, so every one of them reads as a percentage. */
private fun percent(value: Float): String = "${(value * 100).toInt()}%"

private const val MAX_TINT = 0.6f
private const val MAX_MATERIAL_YOU_TINT = 0.9f
