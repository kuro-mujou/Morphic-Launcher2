package inkspire.morphic.feature.settings.icons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.feature.settings.component.SettingsChip
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val ChipGap = 8.dp

/**
 * **Icon sizing**: how big icons and labels are in each of the launcher's grids.
 *
 * The port of L1's per-surface icon controls, which lived inside five separate settings details (home, drawer, library,
 * dock, folder) each embedding the same `IconLayoutControls`. Here there is one section with a grid picker, because L2
 * has no per-surface sections yet — and when they arrive, each embeds [IconSizingControls] rather than copying it, which
 * is the arrangement L1 had and the part worth keeping.
 *
 * **Every grid that draws icons appears, derived from the blueprint registry** rather than listed here: `EditableSlots`
 * is every slot whose blueprint declares icon sizing. So a new icon-drawing grid becomes editable with no change to
 * this screen, and the category card — a grid of *tiles*, whose blueprint declares none — correctly does not appear.
 *
 * **It edits the device configuration you are holding.** Overrides are stored per configuration, so the values here are
 * the ones for this posture; rotating a tablet edits a different set. That is deliberate rather than incidental — the
 * blueprint's own defaults differ per configuration too, so an override that spanned them would be coarser than what it
 * replaces.
 *
 * **Its own theme boundary**, following [isSystemInDarkTheme] — settings is our own surface, where the launcher shell
 * follows wallpaper brightness. One palette, two "is-dark" inputs.
 *
 * @param onBack leaves the section. Wired to the navigator by the host, and to system back here so the two agree.
 */
@Composable
fun IconSizingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<IconSizingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reported rather than read in the ViewModel, as on every other surface: the configuration is a `@Composable` read
    // of the window, and overrides are keyed by it.
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    BackHandler(onBack = onBack)

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(safeInsets)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
        ) {
            Text("Icon sizing", style = MaterialTheme.typography.headlineSmall, color = colors.content)
            Text(
                text = "Per grid, for this screen orientation.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ChipGap),
                verticalArrangement = Arrangement.spacedBy(ChipGap),
                modifier = Modifier.padding(top = ChipGap * 2),
            ) {
                EditableSlots.forEach { slot ->
                    SettingsChip(
                        label = slot.label,
                        selected = slot == state.slot,
                        onClick = { viewModel.selectSlot(slot) },
                    )
                }
            }

            // Null only for the frame before the device is reported; there is no honest sizing to show until then, and
            // a placeholder would be a second source of truth for a number the blueprint owns.
            val sizing = state.sizing
            if (sizing != null) {
                IconSizingControls(
                    slot = state.slot,
                    sizing = sizing,
                    onChange = viewModel::change,
                    onToggle = { label, icon -> viewModel.toggle(label, icon) },
                )
                MorphicButton(
                    onClick = viewModel::reset,
                    style = MorphicButtonStyle.Text,
                    modifier = Modifier.padding(top = ChipGap * 2),
                ) {
                    Text("Reset to default")
                }
            }
        }
    }
}

/**
 * A human label for a grid.
 *
 * Local to this screen rather than on the enum: `core:model` stays free of display strings and of localisation, the same
 * reason `Category` carries an id and the UI resolves its name.
 */
private val GridSlot.label: String
    get() = when (this) {
        GridSlot.HOME_MAIN -> "Home"
        GridSlot.HOME_DOCK -> "Dock"
        GridSlot.APPS_LIST -> "Apps list"
        GridSlot.APPS_SCROLL -> "Apps grid"
        GridSlot.APPS_PAGER -> "Apps pager"
        GridSlot.APPS_CATEGORY -> "Category page"
        GridSlot.APPS_CARD -> "Category cards"
        GridSlot.FOLDER -> "Folders"
    }
