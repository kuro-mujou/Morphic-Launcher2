package inkspire.morphic.feature.settings.icons

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
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
 * **A waiting room, and it is meant to empty.** L1 had no section like this: its icon controls lived inside each
 * surface's own detail (home, drawer, library, dock, folder), which is the right place — a grid's icon size and its
 * row count decide each other. L2 is moving the same way one section at a time, and home and the dock have already
 * gone; what is left here is the grids whose surface has no section yet.
 *
 * The `Icons` section itself stays, because it is where the **icon studio** will live (shape, background, layers) —
 * a per-app concern rather than a per-grid one, and deferred with B9. These sizing controls are its lodger until then.
 *
 * **Which grids appear is derived from the blueprint registry**, less those that have moved out: `EditableSlots` is
 * every slot whose blueprint declares icon sizing and has no section of its own. So a new icon-drawing grid becomes
 * editable here with no change to this screen, and the category card — a grid of *tiles*, whose blueprint declares
 * none — correctly does not appear.
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
internal fun IconSizingDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<IconSizingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reported rather than read in the ViewModel, as on every other surface: the configuration is a `@Composable` read
    // of the window, and overrides are keyed by it.
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }


    val colors = LocalMorphicColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
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
                onChange = viewModel.icons::change,
                onToggle = { label, icon -> viewModel.icons.toggle(label, icon) },
                onDpRange = viewModel.icons::changeDpRange,
            )
            MorphicButton(
                onClick = viewModel.icons::reset,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = ChipGap * 2),
            ) {
                Text("Reset to default")
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
