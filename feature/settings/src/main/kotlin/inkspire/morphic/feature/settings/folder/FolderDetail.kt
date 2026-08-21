package inkspire.morphic.feature.settings.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.collection.appCollectionInnerSize
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.feature.settings.component.SurfaceDetail
import inkspire.morphic.feature.settings.icons.IconSizingGroup
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel

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
 * discovered: an expanded card is the same `AppCollectionOverlay` on the same `FolderGrid`, so one set of controls governs
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
    val sampleApp by viewModel.sample.app.collectAsStateWithLifecycle()

    // Null only for the frame before the device is reported; there is no honest value to show until then, and a
    // placeholder would be a second source of truth for a number the blueprint owns.
    val icon = state.icon ?: return
    var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
    val shownIcon = previewIcon ?: icon

    // **The cell size comes from a *sizer* rather than from a division**: `appCollectionInnerSize` is what the
    // overlay itself lays out with, so the only way this preview can be the size a folder really draws — and it is why
    // the folder grid needs no editor (that function decides the card, and the card decides the cells).
    val grid = FolderGrid.toGridConfig(device)
    val window = usableWindowArea(uiInsets)
    val inner = appCollectionInnerSize(
        window = DpSize(window.widthDp.dp, window.heightDp.dp),
        device = device,
        grid = grid,
        metrics = shownIcon.toIconMetrics(),
    )

    SurfaceDetail(
        onReroll = viewModel.sample::reroll,
        modifier = modifier,
        // **Empty, and that is the section.** A folder's card is sized to the screen, so its rows and columns follow
        // from that and there is no count to pick — `FolderGrid` declares no `editRange` and this pane draws no
        // editor. What sat here was a sentence stating the count instead; it went because nothing on this pane can
        // change it, which makes it a fact about a screen the user can simply open and look at.
        layout = { },
        preview = { previewModifier ->
            IconSizingPreview(
                app = sampleApp,
                metrics = shownIcon.toIconMetrics(),
                cellWidth = inner.width / grid.visualCols,
                cellHeight = inner.height / grid.visualRows,
                modifier = previewModifier,
            )
        },
        icons = {
            IconSizingGroup(
                slot = GridSlot.FOLDER,
                sizing = icon,
                edits = viewModel.icons,
                onPreview = { previewIcon = it },
            )
        },
    )
}
