package inkspire.morphic.feature.apps.layout.categorypager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import inkspire.morphic.core.designsystem.drag.DragAutoScrollEffect
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.LauncherGridScope
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.feature.apps.AppsCategory

/**
 * Provisional cell height and header spacing — **placeholders, not design choices**, for the reason the other
 * layouts' are: these are surface metrics bound for the settings layer, and a flat constant says so where derived
 * arithmetic would look like a decision.
 *
 * They live beside [CategoryPage] rather than with [AppsCategoryPager] because a page is the only thing that reads
 * them; the surface-wide metrics (icon proportion, column count) stay with the surface that provides them.
 */
private val CellHeight = 96.dp
private val HeaderPadding = 16.dp

/**
 * One category's page: its name, then its apps in a scrolling, draggable grid.
 *
 * Split from [AppsCategoryPager] because it is a leaf — everything it needs arrives as a parameter, and it reads
 * none of the surface's drag state directly. That is also what makes its two odd wirings legible in isolation: the
 * geometry it publishes is the *grid's* (so it travels with the scroll), and the scroll it owns is switched off
 * while a drag is in flight.
 *
 * @param dragged the app being carried, **only when this page is the one holding the gap** — otherwise null, so a
 *   page the finger has left keeps drawing its stored order.
 * @param fingerInRoot the dragged finger, again only when the drag is this page's business; null keeps the page
 *   still, since a page nobody is dragging over must not auto-scroll itself.
 * @param onGeometry reports this page's grid bounds every time they move, which during a scroll is every frame.
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
    onLaunch: (ComponentKey) -> Unit,
    onDrop: () -> Unit,
    onGeometry: (GridGeometry) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val cellHeightPx = with(LocalDensity.current) { CellHeight.toPx() }
    val display = displayOrder(category.apps, dragged, gap)
    val rows = ((display.size + cols - 1) / cols).coerceAtLeast(1)

    val scrollState = rememberScrollState()
    var scrollViewport by remember { mutableStateOf<Rect?>(null) }
    // Reaching apps past the fold while dragging. The manual scroll below is off for the duration, so without this
    // a long category could only be rearranged as far as one screenful. Programmatic scrolling still works with the
    // gesture disabled — `enabled` gates pointer input, not the state.
    DragAutoScrollEffect(scrollState = scrollState, bounds = scrollViewport, fingerInRoot = fingerInRoot)

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
                config = GridConfig(rows = rows, cols = cols),
                cellHeight = CellHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    // Published from the *grid*, so it travels with the scroll: cell hit-testing has to name the
                    // cells actually drawn under the finger, not the ones that were there before scrolling.
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        onGeometry(
                            GridGeometry(
                                originInRoot = Offset(bounds.left, bounds.top),
                                cellW = bounds.width / cols,
                                cellH = cellHeightPx,
                                cols = cols,
                                rows = rows,
                            ),
                        )
                    },
            ) {
                dropFootprintCell(dragged != null, gap, cols)
                flowItems(items = display, itemKey = { it.componentKey.flatten() }) { app, cellModifier ->
                    LauncherDragCell(
                        coordinator = coordinator,
                        item = GridItem.App(app.componentKey),
                        gestureConfig = gestures,
                        onDrop = onDrop,
                        modifier = cellModifier,
                        onOpen = { onLaunch(app.componentKey) },
                    ) { itemGestures ->
                        AppCell(app = app, modifier = Modifier.fillMaxSize(), itemGestures = itemGestures)
                    }
                }
            }
        }
    }
}

/**
 * What one page draws while a drag is over it: its apps with the dragged one lifted to the gap.
 *
 * **The dragged cell stays composed on its source page even once the finger has carried it to another**, so this
 * can return the same app on two pages at once. Not a glitch to tidy: the cell on the source page owns the
 * gesture's pointer stream, and disposing it mid-drag kills the drag. Both copies are drawn invisible, so the user
 * sees only the floating proxy — the far one exists to occupy the gap so the other icons flow around it.
 *
 * Apps are compared by [inkspire.morphic.core.model.ComponentKey] rather than by value: [AppInfo] carries a label
 * and an icon, and the question here is only *which app is this*.
 *
 * No truncation, unlike the APPS pager's equivalent: a category has no capacity, so nothing can overflow it.
 *
 * `internal` rather than file-private so it is reachable from a unit test, as the store-side arithmetic in
 * `data:layout` is.
 */
internal fun displayOrder(apps: List<AppInfo>, dragged: AppInfo?, gap: Int): List<AppInfo> {
    if (dragged == null) return apps
    val others = apps.filterNot { it.componentKey == dragged.componentKey }
    val at = gap.coerceIn(0, others.size)
    return others.take(at) + dragged + others.drop(at)
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
