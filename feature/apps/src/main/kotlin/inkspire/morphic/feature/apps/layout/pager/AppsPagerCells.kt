package inkspire.morphic.feature.apps.layout.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.drag.DragSession
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.grid.LauncherGridScope
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.feature.apps.AppsItem

/**
 * What the APPS pager emits into a page's `LauncherGrid` — one entry, and the drop shadow behind them.
 *
 * Split from [AppsPager] because both are leaves: they take what they draw as parameters and read none of the
 * surface's drag state, so they are the part of that file that could be read on its own. The state, the planner and
 * the drop resolution stay together over there, where they are genuinely one machine.
 */

/** One entry on a page — an app or a folder, drawn as home draws them. */
@Composable
internal fun AppsPagerCell(item: AppsItem, modifier: Modifier, itemGestures: Modifier) {
    when (item) {
        is AppsItem.App -> AppCell(app = item.info, modifier = modifier, itemGestures = itemGestures)
        is AppsItem.Folder -> FolderCell(
            label = item.folder.label,
            apps = item.apps,
            modifier = modifier,
            itemGestures = itemGestures,
        )
    }
}

/**
 * Paints this page's drop shadow, if it has one: the **merge ring's cell** when releasing would fold two entries
 * together, otherwise the **gap** the reorder has opened.
 *
 * The two never coexist — the planner returns a merge plan *before* touching the gap, so a merge leaves the gap
 * where it was, and painting both would show two promises at once. Merge wins for the same reason it short-circuits
 * there: it is the more specific answer to where the finger is.
 *
 * Only the merge target comes from the plan. A reorder's does not, and cannot: the plan's footprint is a token for
 * that intent (see [PagerReorderPlan]), because the landing is an index in this surface's own state rather than a
 * cell anyone else could name.
 */
@Suppress("ComposableNaming") // an emitter, named for what it paints rather than as a component
@Composable
internal fun LauncherGridScope.dropFootprintCell(
    session: DragSession?,
    pageIndex: Int,
    gap: Int,
    gapPage: Int,
    config: GridConfig,
) {
    if (session == null || session.activeZone != PagerZoneId) return
    val merge = session.plan?.takeIf { it.intent == DropIntent.MERGE }
    val cell = when {
        merge != null -> merge.footprint.takeIf { it.page == pageIndex }?.let { it.row to it.col }
        // A gap past the last cell means the item is being appended to a page that is already full: it will
        // cascade onto the next page, so this page has no slot to promise and paints nothing. Without the bound
        // the footprint would be placed off the grid — invisible either way, but silently so.
        gap in 0 until config.rows * config.cols && gapPage == pageIndex ->
            gap / config.cols to gap % config.cols

        else -> null
    } ?: return
    val (row, col) = cell
    Box(Modifier.gridPlacement(GridPlacement(0, row, col))) {
        DropFootprint(
            intent = if (merge != null) DropIntent.MERGE else DropIntent.REORDER,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
