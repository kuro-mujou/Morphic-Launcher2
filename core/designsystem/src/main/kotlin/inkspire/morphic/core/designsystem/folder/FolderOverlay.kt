package inkspire.morphic.core.designsystem.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.toGridConfig

/**
 * The opened-folder view: the folder [label] over a grid of its [apps]; tapping one calls [onLaunch]. A shared
 * launcher surface — the home opens it for a folder tile, and the APPS surfaces reuse it for pager folders and
 * for a category card opening its apps. It stays dumb: it takes the label + resolved [AppInfo]s and two
 * callbacks, so each caller decides what "launch" and "dismiss" mean.
 *
 * Modelled on L1's `FolderView` layout, minus the behaviours that need pieces not built yet — no in-folder
 * reorder, drag-out extract, "add" cell (needs an app picker), or multi-page pager. Column count comes from the
 * [FolderGrid] blueprint for the device, so it lines up with the rest of the grid taxonomy. Dismissed by Back
 * or by tapping outside an app icon.
 *
 * TODO(launcher frosted UI): replace the solid-black backdrop with the deferred blur/frosted backdrop when the
 *  wallpaper-adaptive launcher-UI subsystem lands.
 */
@Composable
fun FolderOverlay(
    label: String,
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val device = currentDeviceConfiguration()
    val cols = remember(device) { FolderGrid.toGridConfig(device).cols }

    // A tap anywhere on the scrim (outside an app icon) closes the folder; no ripple on the full-screen backdrop.
    val dismissInteraction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = apps, key = { it.componentKey.flatten() }) { app ->
                    AppCell(
                        app = app,
                        onClick = { onLaunch(app.componentKey) },
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }
    }
}
