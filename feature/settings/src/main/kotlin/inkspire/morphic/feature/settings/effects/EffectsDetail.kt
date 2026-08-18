package inkspire.morphic.feature.settings.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.Orientation
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
 * - **A live preview, which this section went without for a while** — see [BackdropPreview] for what changed and why
 *   the backdrop it samples is provided at the *pane* rather than at the settings zone's root. It pins above the
 *   controls in a `stickyHeader`, the arrangement `SurfaceDetail` uses for the same reason: the sliders are read
 *   *through* the picture, so scrolling to reach one must not scroll the picture away. That is also why this is a
 *   `LazyColumn` rather than the `Column` + `verticalScroll` it was. L1 had no preview here at all.
 *
 * **Liquid glass is hidden rather than disabled below API 33.** An effect that silently comes out as a plain blur is
 * worse than one that is not offered, so the chip goes and the reason is stated — L1's wording, kept.
 */
@Composable
internal fun EffectsDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<EffectsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    // Which way the device is held, for the rotating pair's sake — the pane is where the window is.
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(windowSize) {
        viewModel.setOrientation(
            if (windowSize.width > windowSize.height) Orientation.LANDSCAPE else Orientation.PORTRAIT,
        )
    }

    // **The dragged effect, which is what the preview draws.** Null means "nothing is being dragged", so the preview
    // follows the stored value; a slider's `onPreview` fills it per frame and its `onCommit` writes the real thing,
    // after which the stored value catches up and this can be dropped. Every parameter but the blur is a draw-time
    // read, so this is the whole of what makes them preview live.
    var dragged by remember { mutableStateOf<BackdropEffect?>(null) }
    val previewed = dragged ?: state.effect

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "chooser") {
            Column(modifier = Modifier.fillMaxWidth().padding(ScreenPadding)) {
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
                            // A chip is a whole new variant, so anything mid-drag is about the one being left.
                            onClick = {
                                dragged = null
                                viewModel.select(option)
                            },
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
            }
        }

        // Pinned, and opaque behind the heading so the controls do not scroll *through* it. The picture punches to the
        // wallpaper inside its own box, which is why that background does not defeat it.
        stickyHeader(key = "preview") { _ ->
            Column(modifier = Modifier.fillMaxWidth().background(colors.background)) {
                SettingsSectionHeader("Preview", Modifier.padding(horizontal = ScreenPadding))
                BackdropPreview(
                    effect = previewed,
                    image = state.backdropImage,
                    accent = state.backdropAccent,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }

        item(key = "controls") {
            Column(modifier = Modifier.fillMaxWidth().padding(ScreenPadding)) {
                // Exhaustive over the sealed type rather than over the chips, so a new variant fails to compile here
                // until it has controls — the same rule `AppsScreen` follows for an unbuilt layout.
                when (val effect = state.effect) {
                    is BackdropEffect.Plain -> PlainControls(effect, viewModel::set) { dragged = it }
                    is BackdropEffect.Blur -> BlurControls(effect, viewModel::set) { dragged = it }
                    is BackdropEffect.MaterialYou -> MaterialYouControls(effect, viewModel::set) { dragged = it }
                    is BackdropEffect.LiquidGlass -> LiquidGlassControls(effect, viewModel::set) { dragged = it }
                }
            }
        }
    }
}

/**
 * The unwashed blur's one control.
 *
 * **It has one now, which is the visible half of `None` becoming `Plain`.** Under the old model this variant sampled
 * nothing, so the section showed a note explaining why it was empty; every effect blurs now, and the amount is the
 * only thing left to choose once there is no wash over it.
 *
 * **Dormant, along with every other slider in this section**, and the whole section shares one reason: the frost
 * behind an arriving surface is fixed per variant (`BackdropEffect.fullScreenFilm`), and those two layers are the only
 * frosted surfaces there are. What these sliders are *for* is a frosted **panel** — a popup menu, the widget picker —
 * which is also where liquid glass's rim lives. Kept rather than cut, at the author's call, because they come back
 * with the first panel; the alternative reading is this section's own rule that a control which changes nothing is
 * worse than a missing one. No subtitle here claims otherwise, which is the least this can do meanwhile.
 */
@Composable
private fun PlainControls(
    effect: BackdropEffect.Plain,
    onSet: (BackdropEffect) -> Unit,
    onPreview: (BackdropEffect) -> Unit,
) {
    SettingsSectionHeader("Amount")
    SettingsCommitSlider(
        title = "Blur",
        value = effect.strength,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(strength = it)) },
        onPreview = { onPreview(effect.copy(strength = it)) },
    )
}

@Composable
private fun BlurControls(
    effect: BackdropEffect.Blur,
    onSet: (BackdropEffect) -> Unit,
    onPreview: (BackdropEffect) -> Unit,
) {
    SettingsSectionHeader("Amount")
    SettingsCommitSlider(
        title = "Blur",
        value = effect.strength,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(strength = it)) },
        onPreview = { onPreview(effect.copy(strength = it)) },
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
        onPreview = { onPreview(effect.copy(tint = it)) },
    )
}

@Composable
private fun MaterialYouControls(
    effect: BackdropEffect.MaterialYou,
    onSet: (BackdropEffect) -> Unit,
    onPreview: (BackdropEffect) -> Unit,
) {
    SettingsSectionHeader("Amount")
    SettingsCommitSlider(
        title = "Blur",
        value = effect.strength,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(strength = it)) },
        onPreview = { onPreview(effect.copy(strength = it)) },
    )
    SettingsCommitSlider(
        title = "Tint",
        subtitle = "Wallpaper-toned wash over the blur",
        value = effect.tint,
        // Higher than the blurs' ceiling, as in L1: this wash *is* the effect, where theirs is a correction to one.
        valueRange = 0f..MAX_MATERIAL_YOU_TINT,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(tint = it)) },
        onPreview = { onPreview(effect.copy(tint = it)) },
    )
}

@Composable
private fun LiquidGlassControls(
    effect: BackdropEffect.LiquidGlass,
    onSet: (BackdropEffect) -> Unit,
    onPreview: (BackdropEffect) -> Unit,
) {
    SettingsSectionHeader("Lens")
    SettingsCommitSlider(
        title = "Blur",
        subtitle = "Kept low — a heavy blur has no structure left to bend",
        value = effect.blur,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(blur = it)) },
        onPreview = { onPreview(effect.copy(blur = it)) },
    )
    SettingsCommitSlider(
        title = "Refraction",
        subtitle = "How far the rim bends what is behind it",
        value = effect.refraction,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(refraction = it)) },
        onPreview = { onPreview(effect.copy(refraction = it)) },
    )
    SettingsCommitSlider(
        title = "Depth",
        subtitle = "How far in from the edge the lens reaches",
        value = effect.depth,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(depth = it)) },
        onPreview = { onPreview(effect.copy(depth = it)) },
    )
    SettingsSectionHeader("Light")
    SettingsCommitSlider(
        title = "Vibrancy",
        value = effect.vibrancy,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(vibrancy = it)) },
        onPreview = { onPreview(effect.copy(vibrancy = it)) },
    )
    SettingsCommitSlider(
        title = "Sheen",
        subtitle = "Rim highlight",
        value = effect.sheen,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(sheen = it)) },
        onPreview = { onPreview(effect.copy(sheen = it)) },
    )
    SettingsCommitSlider(
        title = "Dispersion",
        subtitle = "Rainbow fringe at the corners",
        value = effect.dispersion,
        valueRange = 0f..1f,
        valueLabel = ::percent,
        onCommit = { onSet(effect.copy(dispersion = it)) },
        onPreview = { onPreview(effect.copy(dispersion = it)) },
    )
}

/** The chip's text. Kept beside the enum's use rather than on it — a label is this screen's, not the option's. */
private val BackdropOption.label: String
    get() = when (this) {
        BackdropOption.PLAIN -> "Plain"
        BackdropOption.LIGHT_BLUR -> "Light blur"
        BackdropOption.DARK_BLUR -> "Dark blur"
        BackdropOption.MATERIAL_YOU -> "Material You"
        BackdropOption.LIQUID_GLASS -> "Liquid glass"
    }

/** Every parameter here is a `0f..1f` strength, so every one of them reads as a percentage. */
private fun percent(value: Float): String = "${(value * 100).toInt()}%"

private const val MAX_TINT = 0.6f
private const val MAX_MATERIAL_YOU_TINT = 0.9f
