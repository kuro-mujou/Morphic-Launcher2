package inkspire.morphic.core.designsystem.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.cellLabelHeight
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.toGridConfig

/** Padding between the folder title and the inner zone. */
private val TitleBottomPadding = 12.dp

/**
 * The opened-folder view — two zones:
 * - the **outer zone** is the full-screen scrim: tapping it (outside the inner zone) closes the folder, and it
 *   will later be the drop target for dragging an app *out* of the folder;
 * - the **inner zone** is a bounded card holding the folder's app grid ([label] above it), sized by
 *   [folderInnerSize] so every folder is the same, consistent size on a given device.
 *
 * A shared launcher surface: the home opens it for a folder tile, and the APPS surfaces reuse it for pager
 * folders and category cards. It stays dumb — label + resolved [apps] + [onLaunch]/[onDismiss] callbacks — so
 * each caller decides what launch/dismiss mean.
 *
 * First cut of the fuller view: single page (dense-flow pager is next), launch-only (in-folder reorder and
 * dwell-to-extract come after). Dismissed by Back or a tap on the outer zone.
 *
 * TODO(launcher frosted UI): replace the solid-black backdrop with the deferred blur/frosted backdrop.
 */
@Composable
fun FolderOverlay(
    label: String,
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
) {
    BackHandler(onBack = onDismiss)

    val device = currentDeviceConfiguration()
    val grid = remember(device) { FolderGrid.toGridConfig(device) }
    val labelHeight = cellLabelHeight(metrics)

    // The title row's height (+ its bottom padding) is what landscape sizing must leave room for above the grid.
    val titleStyle = MaterialTheme.typography.titleMedium
    val titleHeight = with(LocalDensity.current) {
        (if (titleStyle.lineHeight.isSpecified) titleStyle.lineHeight else titleStyle.fontSize * 1.2f).toDp()
    }
    val landscapeReserve = titleHeight + TitleBottomPadding

    // Inset by the system bars + display cutout (not `safeDrawing` — that would also inset the IME, which is
    // irrelevant here) so the folder is sized against, and laid out within, the safe area — never under a bar.
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    // Outer zone: tap anywhere on the scrim (outside the inner zone) closes it; no ripple on the full-screen
    // backdrop. The black fills the whole screen (behind the bars); only the content region is inset.
    val scrimInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
            .windowInsetsPadding(safeInsets),
        contentAlignment = Alignment.Center,
    ) {
        val innerSize: DpSize = folderInnerSize(DpSize(maxWidth, maxHeight), device, grid, labelHeight, landscapeReserve)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = titleStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = TitleBottomPadding),
            )
            // Inner zone: the bounded app grid. A tap on its background is consumed so it doesn't close the folder.
            Box(
                Modifier
                    .size(innerSize)
                    .clickable(interactionSource = innerInteraction, indication = null, onClick = {}),
            ) {
                LauncherGrid(config = grid, modifier = Modifier.fillMaxSize()) {
                    // TODO(dense-flow pager): page the ordered list; for now show the first page only.
                    flowItems(items = apps.take(grid.cols * grid.rows), itemKey = { it.componentKey.flatten() }) { app, cellModifier ->
                        AppCell(
                            app = app,
                            onClick = { onLaunch(app.componentKey) },
                            modifier = cellModifier,
                            metrics = metrics,
                        )
                    }
                }
            }
        }
    }
}
