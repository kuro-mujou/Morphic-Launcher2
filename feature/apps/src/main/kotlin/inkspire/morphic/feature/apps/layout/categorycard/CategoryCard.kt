package inkspire.morphic.feature.apps.layout.categorycard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import inkspire.morphic.core.designsystem.cell.CategoryCardFace
import inkspire.morphic.core.designsystem.cell.CategoryClusterTile
import inkspire.morphic.core.designsystem.cell.CategoryPreviewIcon
import inkspire.morphic.core.designsystem.cell.CategoryPreviewSlots
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.cell.categoryOverflowCluster
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.feature.apps.AppsCategory

/**
 * One category's card: its name, then a [CategoryPreviewSlots]-slot thumbnail of the apps filed under it.
 *
 * The last slot is the **overflow cluster** when the category holds more than fits — an [IconPreviewPlate] of the next
 * four apps, i.e. the same tile a folder draws on a grid, which is the honest rendering of "and more in here". The
 * cluster opens the category; so does the header. A category with [CategoryPreviewSlots] apps or fewer shows them all and has
 * no cluster.
 *
 * Split from [AppsCategoryCard] because it is a leaf: it takes what it draws as parameters and reports its bounds
 * back. The surface keeps the drag state, the planner and the drop.
 *
 * **Its preview icons are draggable**, which is the second way an app is re-filed — the first being from inside an
 * expansion. A preview is the only part of a card that names one app, so it is the only part that can start a drag;
 * the header and the overflow cluster open the category, and the empty slots and padding stay free. Note what this
 * costs the surface: a lifted cell owns the pointer stream driving the drag, so the card grid cannot be lazy any
 * more (see [AppsCategoryCard]).
 *
 * @param chrome the card's resolved tile chrome — its corner, its title's scale, and the two paddings around and
 *   between the preview slots. Every one of them starts at zero; see `CardChrome`.
 * @param metrics the resolved icon sizing of a **preview slot**, which is `GridSlot.APPS_CARD`'s own. A slot is sized
 *   to be an icon, so `iconPercent` means it literally here, and the blueprint declares `showLabel = false` because
 *   four ellipsized words at this size would eat the room the icons need to be recognizable.
 * @param shadowed true while a dragged app is aimed at this card — drawn as a [DropIntent.MERGE] drop shadow over the
 *   whole card, the same affordance a folder's merge ring gets, because dropping here does the same thing.
 * @param onRelease a preview icon released the finger; it only ends the drag, since the landing belongs to whichever
 *   zone it fell in — another card, or one of home's grids if the drag was ejected.
 * @param onBounds reports this card's root-space rectangle as it is laid out, and **null when it leaves composition**
 *   so the surface's hit-test map doesn't keep testing a card that no longer exists. The rectangle is deliberately
 *   *unclipped* — see the call site; a clipped one makes every off-screen card undroppable.
 */
@Composable
internal fun CategoryCard(
    category: AppsCategory,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    shadowed: Boolean,
    chrome: CardChrome,
    metrics: IconMetrics,
    onLaunch: (ComponentKey) -> Unit,
    showItemMenu: (AppInfo, Rect) -> Unit,
    onExpand: () -> Unit,
    onRelease: () -> Unit,
    onBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) { onDispose { onBounds(null) } }
    Box(modifier.fillMaxWidth()) {
        CategoryCardFace(
            title = category.category.name,
            chrome = chrome,
            // The header is a full-width strip rather than just the glyphs: the target has to be reachable for a
            // one-word category name, and a header row *is* its own visible extent, the same way a list row's is.
            titleGestures = Modifier.categoryOpenGestures(gestureConfig, onExpand),
            // **Position + size, never `boundsInRoot()`** - that one is *clipped by ancestor clipping*, and this card
            // sits inside a `verticalScroll`, which clips. A card below the fold therefore reports an empty rectangle
            // and can never contain a finger, so a drop onto it is silently ignored; a card half off the top reports
            // half of itself. `positionInRoot()` does no clipping, so this is the card's real rectangle whether it is
            // on screen or not.
            //
            // It could not bite while the grid was lazy - an item that did not exist reported nothing at all, and
            // every item that did exist was visible. Making the grid non-lazy (so a drag can start on a card preview)
            // is what turned "not composed" into "composed and lying".
            modifier = Modifier.onGloballyPositioned { onBounds(Rect(it.positionInRoot(), it.size.toSize())) },
        ) { index, slotSize ->
            // The split is shared with the settings preview of a card, so the two cannot disagree about which apps
            // are shown and when the last slot becomes a cluster.
            val cluster = categoryOverflowCluster(category.apps)
            PreviewSlot(
                app = category.apps.getOrNull(index),
                cluster = cluster?.takeIf { index == CategoryPreviewSlots - 1 },
                slot = slotSize,
                metrics = metrics,
                coordinator = coordinator,
                gestureConfig = gestureConfig,
                onLaunch = onLaunch,
                showItemMenu = showItemMenu,
                onExpand = onExpand,
                onRelease = onRelease,
            )
        }
        // Over the card rather than inside its face, so it covers the title and preview both, and shaped to the card
        // so the shadow reads as "this whole collection" instead of a cell.
        if (shadowed) {
            DropFootprint(
                intent = DropIntent.MERGE,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(chrome.cornerRadiusDp.dp),
            )
        }
    }
}

/**
 * One preview slot: the overflow [cluster] if this is the last slot and there is an overflow, otherwise [app]'s icon,
 * otherwise nothing.
 *
 * An icon launches on a tap and **lifts on a long-press-and-move**; the cluster opens the category. Both go through
 * the launcher's one item-gesture contract rather than a `clickable`, so this surface can't drift from the rest of the
 * launcher on long-press timing or slop.
 *
 * The icon goes through [LauncherDragCell] specifically, rather than wiring the coordinator by hand, so it gets the
 * same three things every other draggable cell has: the gesture contract, the lifted cell drawn invisible while the
 * proxy stands in for it, and `animatePlacement`. Handing the gestures down to the icon keeps the target the icon
 * itself, leaving the slack in the slot free for the card beneath.
 */
@Composable
private fun PreviewSlot(
    app: AppInfo?,
    cluster: List<AppInfo>?,
    slot: Dp,
    metrics: IconMetrics,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onLaunch: (ComponentKey) -> Unit,
    showItemMenu: (AppInfo, Rect) -> Unit,
    onExpand: () -> Unit,
    onRelease: () -> Unit,
) {
    when {
        cluster != null -> CategoryClusterTile(
            apps = cluster,
            slotSize = slot,
            metrics = metrics,
            itemGestures = Modifier.categoryOpenGestures(gestureConfig, onExpand),
        )
        app != null -> LauncherDragCell(
            coordinator = coordinator,
            item = GridItem.App(app.componentKey),
            gestureConfig = gestureConfig,
            onRelease = onRelease,
            modifier = Modifier.fillMaxSize(),
            onOpen = { onLaunch(app.componentKey) },
            onShowMenu = { anchor -> showItemMenu(app, anchor) },
        ) { itemGestures ->
            // **The icon alone, not an `AppCell`.** A card slot has no label and no chrome of its own, and a cell's
            // own 4dp inset is what stopped the spacing slider ever reaching zero — see `CategoryPreviewIcon`.
            CategoryPreviewIcon(
                app = app,
                slotSize = slot,
                metrics = metrics,
                itemGestures = itemGestures,
            )
        }
        else -> Unit
    }
}

/**
 * A **category's** tap contract — the launcher's one item-gesture recognizer with only "open" connected.
 *
 * Separate from [appsItemGestures] rather than reusing it, though the two do the same thing today: that one is the
 * *app* contract, and its empty slots are an app's unfinished behaviors (the app options menu, drag-out-to-home).
 * Neither means anything for a category, whose long-press belongs to a different unbuilt feature. Sharing them would
 * make one of the two wrong the moment either is filled in.
 */
private fun Modifier.categoryOpenGestures(
    config: ItemGestureConfig,
    onOpen: () -> Unit,
): Modifier = launcherItemGestures(
    config = config,
    onOpen = onOpen,
    // TODO(category management): the category's menu — rename, delete, choose an icon. A `feature:settings` concern
    //  (see the card grid's TODO), so this stays empty until that op set exists.
    onShowMenu = {},
    onEdgeAction = {},
    // A card is not draggable: reordering the categories themselves is category management, deferred with the rest of
    // it. An app *inside* an expansion is what drags here.
    onBeginDrag = {},
    onDragTo = {},
    onDrop = {},
    onCancelDrag = {},
)
