package inkspire.morphic.feature.apps.layout.categorypager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.drag.DragAutoScrollEffect
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.LauncherGridScope
import inkspire.morphic.core.designsystem.grid.derivedCell
import inkspire.morphic.core.designsystem.grid.fitCols
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.ordered.movingGapDisplayOrder
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.AppsCategoryGrid
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.feature.apps.AppsCategory

/**
 * Provisional header spacing — **a placeholder, not a design choice**, for the reason the other layouts' metrics are:
 * a surface metric bound for the settings layer, and a flat constant says so where derived arithmetic would look like
 * a decision.
 *
 * It lives here rather than with [AppsCategoryPager] because a page is the only thing that reads it. The cell *height*
 * is no longer a constant at all: it is derived from the cell width by `derivedCell`, and the surface derives its own
 * from the same rule rather than waiting on a page to report geometry.
 */
private val HeaderPadding = 16.dp

/**
 * One category's page: its name, then its apps in a scrolling, draggable grid.
 *
 * Split from [AppsCategoryPager] because it is a leaf — everything it needs arrives as a parameter, and it reads
 * none of the surface's drag state directly. That is also what makes its two odd wirings legible in isolation: the
 * scroll it owns is switched off while a drag is in flight, and the geometry it publishes is reconstructed from the
 * scroll offset rather than measured off the grid (see the derivation in the body — the measured version goes stale
 * mid-scroll, and silently).
 *
 * @param cols the configured column count. Clamped in the body to what this page's measured width can hold at the
 *   current icon size — a stored count outlives the icon settings it was chosen under, and only a laid-out page knows
 *   how wide it is.
 * @param dragged the app being carried, **only when this page is the one holding the gap** — otherwise null, so a
 *   page the finger has left keeps drawing its stored order.
 * @param fingerInRoot the dragged finger, again only when the drag is this page's business; null keeps the page
 *   still, since a page nobody is dragging over must not auto-scroll itself — and it is what tells this page whether
 *   a re-plan is its business.
 * @param metrics this page's icon sizing, which is also what decides how tall its cells come out — passed rather than
 *   read from [inkspire.morphic.core.designsystem.cell.LocalIconMetrics] because a height is arithmetic on it, and
 *   arithmetic on an ambient value is where a surface stops being able to say what it drew.
 * @param onGeometry reports where this page's cells currently are, republished on every scroll frame because the
 *   content moves under the finger while the finger itself may not move at all.
 */
@Composable
internal fun CategoryPage(
    category: AppsCategory,
    cols: Int,
    coordinator: DragCoordinator,
    gestures: ItemGestureConfig,
    dragged: AppInfo?,
    gap: Int,
    fingerInRoot: Offset?,
    metrics: IconMetrics,
    onLaunch: (ComponentKey) -> Unit,
    onDrop: () -> Unit,
    onGeometry: (GridGeometry) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val colors = LocalMorphicColors.current
    // **The stored column count, clamped to what this page's width can draw at this icon size** — and everything below
    // reads the clamped one, because a lattice, a cell height and a published geometry that disagreed about how many
    // columns there are would put the drop footprint in a different place from the icons. The clamp needs the measured
    // width, which is why it is here rather than where the count is resolved (`AppsScreen`); it is a read, so nothing
    // is written and the column returns when the icons shrink.
    val drawnCols = AppsCategoryGrid.fitCols(maxWidth.value, cols, metrics)
    // **The cell height is derived here, from the page's own width.** A page is a SCROLL_GRID: the columns fix the
    // cell width and nothing fixes its height, so the height is whatever the icon and label need — the inverse of the
    // arithmetic `IconLabelCell` lays a cell out by. Measured rather than read from the published geometry, so the very
    // first frame draws at the right height instead of correcting itself once a viewport lands.
    //
    // `derivedCell` answers with the metrics to draw it with as well, and they must be used: the height was derived by
    // spending `iconPercent`, so a cell handed the original metrics would apply the fraction twice.
    val cell = derivedCell(cellWidth = maxWidth / drawnCols, metrics = metrics)
    val cellHeight = cell.height
    val cellHeightPx = with(LocalDensity.current) { cellHeight.toPx() }
    // The shared MovingGap render, told to compare apps by component: `AppInfo` carries a label and an icon, and the
    // question is only *which app is this*. No truncation, unlike the APPS pager's — a category has no capacity, so
    // nothing can overflow it, and the dragged app may legitimately appear on two pages at once (its source page keeps
    // the cell that owns the pointer stream; both copies draw invisible).
    val display = movingGapDisplayOrder(category.apps, dragged, gap) { it.componentKey }
    val rows = ((display.size + drawnCols - 1) / drawnCols).coerceAtLeast(1)

    val scrollState = rememberScrollState()
    var scrollViewport by remember { mutableStateOf<Rect?>(null) }
    // Reaching apps past the fold while dragging. The manual scroll below is off for the duration, so without this
    // a long category could only be rearranged as far as one screenful. Programmatic scrolling still works with the
    // gesture disabled — `enabled` gates pointer input, not the state.
    DragAutoScrollEffect(scrollState = scrollState, bounds = scrollViewport, fingerInRoot = fingerInRoot)

    // **Geometry comes from the scroll viewport and the scroll offset — never from the grid's own
    // `onGloballyPositioned`.** Scrolling moves the grid's global position through its *parent's* placement without
    // re-running the grid's own layout, so that callback does not reliably re-fire and the origin it published goes
    // stale. The failure is quiet and very recognisable: with a stale origin at offset `S0` and a real offset `S`, a
    // finger at `y` resolves to a cell that draws at `y - (S - S0)` — so the drop footprint sits a *fixed* distance
    // from the finger, tracks it row for row while dragging, and snaps into place the moment the content returns
    // to `S0`.
    //
    // The viewport is the honest anchor: `onGloballyPositioned` sits *outside* `verticalScroll` in the chain below, so
    // `scrollViewport` is the window rather than the content and its position genuinely doesn't move. Subtracting
    // `scrollState.value` reconstructs the content origin exactly, and because that is snapshot state this composable
    // recomposes — and so republishes — on every scroll frame.
    val geometry = scrollViewport?.let { viewport ->
        GridGeometry(
            originInRoot = Offset(viewport.left, viewport.top - scrollState.value),
            cellW = viewport.width / drawnCols,
            cellH = cellHeightPx,
            cols = drawnCols,
            rows = rows,
        )
    }

    // Publish it, then **re-plan against what was just published**, as one ordered step.
    //
    // The re-plan is the other half of the auto-scroll fix: the coordinator resolves a plan in `moveTo`, i.e. only
    // when the *finger* moves, while auto-scroll slides rows past a finger held deliberately still at the edge. A
    // corrected geometry that nothing re-reads changes nothing, so the same position is re-sent to recompute the plan.
    //
    // Both live in one `SideEffect` because the order matters and a separate `snapshotFlow` on the scroll offset gets
    // it wrong: that emits on the snapshot commit, *before* the recomposition that derives the new geometry, so the
    // re-plan would run against the previous frame's origin and stay one scroll step behind. Here the write precedes
    // the read by construction. A `SideEffect` also keeps the publish out of composition, where a state write belongs
    // least.
    //
    // This settles rather than looping: `moveTo` only rewrites `session` when the resolved zone or plan actually
    // differs (an equal `copy` is not a snapshot change), and the gap it may move cannot feed back into the geometry —
    // `movingGapDisplayOrder` permutes the list without resizing it, so `rows` is the same whatever the gap is.
    // `moveTo` is a no-op when no drag is in flight, and `fingerInRoot` is null unless this page is the one being
    // dragged over, so an idle page does nothing at all.
    SideEffect {
        geometry?.let(onGeometry)
        fingerInRoot?.let(coordinator::moveTo)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = category.category.name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = HeaderPadding, vertical = HeaderPadding / 2),
        )
        // The scroll host: the grid inside reports its *content* height, which grows past this box and scrolls. Its
        // own `remember` sits inside the pager's per-page `key`, so each category keeps its own scroll position.
        // Scrolling is disabled during a drag — otherwise the scroll and the drag fight over the same vertical
        // finger; the auto-scroll above is how content past the fold is reached instead.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { scrollViewport = it.boundsInRoot() }
                .verticalScroll(scrollState, enabled = !coordinator.isDragging),
        ) {
            LauncherGrid(
                // `rows` is unused in scroll mode (height comes from cellHeight × content) but GridConfig requires a
                // positive value, so it is set to what the content actually reaches rather than to a lie.
                config = GridConfig(rows = rows, cols = drawnCols),
                cellHeight = cellHeight,
                // No geometry published from here — see the derivation above for why the grid's own position is not a
                // trustworthy source once it is inside a scroller.
                modifier = Modifier.fillMaxWidth(),
            ) {
                dropFootprintCell(dragged != null, gap, drawnCols)
                flowItems(items = display, itemKey = { it.componentKey.flatten() }) { app, cellModifier ->
                    LauncherDragCell(
                        coordinator = coordinator,
                        item = GridItem.App(app.componentKey),
                        gestureConfig = gestures,
                        onDrop = onDrop,
                        modifier = cellModifier,
                        onOpen = { onLaunch(app.componentKey) },
                    ) { itemGestures ->
                        // `cell.metrics`, not the ambient `LocalIconMetrics` the pager provides: this page's height was
                        // derived by spending `iconPercent`, so the cell must not spend it again.
                        AppCell(
                            app = app,
                            metrics = cell.metrics,
                            modifier = Modifier.fillMaxSize(),
                            itemGestures = itemGestures,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Paints the gap's cell, if this page holds it — the slot the app would land in.
 *
 * Declared before the cells so it sits behind them, and inside the page grid so it scrolls with the content. Only a
 * reorder can happen here, so there is no merge case to prefer over it.
 */
@Suppress("ComposableNaming") // an emitter, named for what it paints rather than as a component
@Composable
private fun LauncherGridScope.dropFootprintCell(draggingHere: Boolean, gap: Int, cols: Int) {
    if (!draggingHere || gap < 0) return
    Box(Modifier.gridPlacement(GridPlacement(0, gap / cols, gap % cols))) {
        DropFootprint(DropIntent.REORDER, Modifier.fillMaxSize())
    }
}
