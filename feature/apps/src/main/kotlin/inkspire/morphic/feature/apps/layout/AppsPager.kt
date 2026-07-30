package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.model.AppsPagerGrid
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.feature.apps.AppsItem

/**
 * The pager's own icon proportion.
 *
 * Denser than home's 0.88 for the same reason the vertical grid's is — a page packs four to eight columns where a
 * home cell is a 2×2 slot around one icon — and a placeholder in the same sense: the value is a starting point,
 * the *mechanism* (a per-surface [IconMetrics] through [LocalIconMetrics]) is the answer. Matches the vertical
 * grid, since the two draw the same kind of cell.
 */
private val PagerIconMetrics = IconMetrics(iconPercent = 0.75f)

/**
 * The **pager** layout of the APPS surface ([inkspire.morphic.core.model.AppsLayout.PAGER]): the app collection
 * laid out across swipeable pages, in an order the **user** owns.
 *
 * **The first APPS layout that stores anything.** The list and grid are derived — re-computed A–Z from the app
 * cache on every emission — so they take a flat list and own nothing. This one takes [pages] already arranged,
 * because the arrangement is data (`apps_pager_item`, via `AppsOrderRepository`), not a function of the cache.
 * That difference is the whole reason it needed the store built first.
 *
 * **Fixed pages, not a scrolling grid.** Each page is a [LauncherGrid] in FIXED_PAGER mode at
 * [AppsPagerGrid]'s size for the device, so every page holds exactly `rows × cols` entries and a page boundary is
 * a real boundary — which is what the store's page + slot means. It is also why this uses `LauncherGrid` where
 * the vertical grid deliberately does not: a page is bounded and composes a couple of dozen cells, not hundreds.
 *
 * First cut: renders and launches. Dragging to rearrange, folders opening, and the page indicator are P5 — this
 * part exists to be looked at on a device before any of that is wired.
 *
 * @param pages the arrangement to draw: pages in order, each dense from its first slot.
 * @param onGridResolved reports the resolved page grid up to the ViewModel, which needs the capacity to page the
 *   store. The device is a `@Composable` read, so the UI is the only place that can resolve it — the same
 *   arrangement `HomeScreen` uses for its own grid.
 */
@Composable
fun AppsPager(
    pages: List<List<AppsItem>>,
    onLaunch: (ComponentKey) -> Unit,
    onGridResolved: (GridConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = currentDeviceConfiguration()
    val config = remember(device) { AppsPagerGrid.toGridConfig(device) }
    LaunchedEffect(config) { onGridResolved(config) }

    val gestureConfig = rememberAppsGestureConfig()
    // Held in a state so the count lambda reads the *current* pages: `rememberLauncherPagerState` remembers the
    // lambda once, so capturing the parameter directly would freeze the pager at however many pages existed on the
    // first composition — which is none, since the store hasn't been paged until `onGridResolved` lands.
    val livePages = rememberUpdatedState(pages)
    val pagerState = rememberLauncherPagerState(
        // At least one page, so an empty store (first run, before the sync lands) is a blank page rather than a
        // pager with nothing to lay out.
        pageCount = { livePages.value.size.coerceAtLeast(1) },
        infiniteScroll = { false },
    )

    // Padded, not just inset as content: a page is a fixed grid rather than a scrolling list, so there is nothing
    // to scroll under the bars — an icon there would simply be unreachable. A system constraint, not styling.
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    CompositionLocalProvider(LocalIconMetrics provides PagerIconMetrics) {
        LauncherPager(
            state = pagerState,
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(safeInsets)
                .launcherPagerSwipe(pagerState),
        ) { pageIndex ->
            LauncherGrid(config = config, modifier = Modifier.fillMaxSize()) {
                flowItems(
                    items = pages.getOrNull(pageIndex).orEmpty(),
                    itemKey = { it.key() },
                ) { item, cellModifier ->
                    AppsPagerCell(item = item, onLaunch = onLaunch, modifier = cellModifier, gestures = gestureConfig)
                }
            }
        }
    }
}

/**
 * One entry on a page — an app or a folder, drawn the same way they are on home.
 *
 * A folder is drawn but not yet openable: opening it means hosting a `FolderOverlay`, which is P5's work along
 * with the drag that can create one in the first place. Until then no folder can exist here to tap.
 */
@Composable
private fun AppsPagerCell(
    item: AppsItem,
    onLaunch: (ComponentKey) -> Unit,
    modifier: Modifier,
    gestures: ItemGestureConfig,
) {
    when (item) {
        is AppsItem.App -> AppCell(
            app = item.info,
            modifier = modifier,
            itemGestures = Modifier.appsItemGestures(gestures) { onLaunch(item.info.componentKey) },
        )
        // TODO(P5): tapping opens the folder, once this surface hosts a FolderOverlay via FolderHostState.
        is AppsItem.Folder -> FolderCell(
            label = item.folder.label,
            apps = item.apps,
            modifier = modifier,
        )
    }
}

/** A stable identity for the cell, so a reorder animates the same cell rather than recycling it. */
private fun AppsItem.key(): Any = when (this) {
    is AppsItem.App -> info.componentKey.flatten()
    is AppsItem.Folder -> "folder:${folder.id}"
}
