package inkspire.morphic.feature.settings.folder

import androidx.compose.foundation.layout.Column
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
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.feature.settings.icons.IconSizingControls
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp

/**
 * **Folders**: how the icons inside an opened folder are sized.
 *
 * The port of L1's `FolderSettingsDetail`, which is this exact shape — its layout group is literally empty and its icon
 * controls are the whole screen. That is a property of the grid rather than an omission: `FolderGrid` declares no
 * `editRange`, because a folder's card is sized to fit the screen and its rows and columns follow from that, so there
 * is no count for a user to pick and no `GridEditor` to draw. The one thing a folder still has to be told is how big
 * the icons in it are.
 *
 * **It is also where the *category card's expansion* is sized**, and the screen says so rather than leaving it to be
 * discovered: an expanded card is the same `FolderOverlay` on the same `FolderGrid`, so one set of controls governs
 * both. That sharing is stated on the blueprint too.
 *
 * **This section is why the icon-sizing screen is gone.** That screen was a waiting room for grids whose surface had no
 * section yet, and the folder grid was the last one in it — so the room is empty and has been removed rather than kept
 * as a heading with nothing under it. L1's own `Icons` section is a different thing entirely (shape, background, layers:
 * the **icon studio**, B9), and that is what will claim the name back.
 *
 * **It edits the device configuration you are holding**, like every section here, because overrides are keyed by it.
 */
@Composable
internal fun FolderDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<FolderViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    val colors = LocalMorphicColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text("Folders", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "Icons inside an opened folder, and inside an expanded category card — one grid, so one setting.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        // The grid a folder opens onto, stated as a fact rather than offered as a control: it is the blueprint's, and
        // this section deliberately has no rows/columns editor because a folder's card is sized to the screen. Saying
        // the number pre-empts "where are the − and + buttons?" without inventing an answer to it.
        val size = FolderGrid.defaults.getValue(device)
        Text(
            text = "A folder shows ${size.cols} × ${size.rows} icons a page on this screen, and adds pages as it fills.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = RowGap),
        )

        // Null only for the frame before the device is reported; there is no honest value to show until then, and a
        // placeholder would be a second source of truth for a number the blueprint owns.
        val icon = state.icon ?: return@Column
        IconSizingControls(
            slot = GridSlot.FOLDER,
            sizing = icon,
            onChange = viewModel.icons::change,
            onToggle = { label, showIcon -> viewModel.icons.toggle(label, showIcon) },
            onDpRange = viewModel.icons::changeDpRange,
        )
        MorphicButton(
            onClick = viewModel.icons::reset,
            style = MorphicButtonStyle.Text,
            modifier = Modifier.padding(top = RowGap * 2),
        ) {
            Text("Reset icons")
        }
    }
}
