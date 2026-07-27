package inkspire.morphic.core.designsystem.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.cellLabelHeight
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.toGridConfig
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Padding between the folder title and the inner zone. */
private val TitleBottomPadding = 12.dp

/** Fixed height of the page-dots row below the inner zone (reserved even for a single page, so the card's
 *  size doesn't depend on how many pages the folder happens to have). */
private val FolderDotsHeight = 24.dp
private val DotSize = 6.dp
private val DotSpacing = 6.dp

/** This overlay's inner-grid drop zone, registered above the home zone (`z = 1`) on the shared coordinator. */
private val FolderZoneId = ZoneId("folder")

/** How long a dragged app must dwell over the outer zone before it's extracted out of the folder. */
private const val ExtractDwellMs = 300L

/**
 * The opened-folder view — two zones on the **shared** [DragCoordinator] the home owns (one coordinator over
 * both surfaces, per its design):
 * - the **outer zone** is the full-screen scrim: tapping it closes the folder, and holding a dragged app over it
 *   (~[ExtractDwellMs], i.e. the finger is off the inner grid) extracts the app — fires [onExtract]; and
 * - the **inner zone** (registered as [FolderZoneId] at a higher `z` than home) is a bounded card holding the
 *   folder's app grid ([label] above it), sized by [folderInnerSize] so every folder is the same size.
 *
 * The apps are a **dense flow** chunked into pages (dots below), swipeable. Long-press to reorder within the flow
 * ([MovingGap][movingGap]) — the folder's plan/commit are exposed to the home via a [FolderDragDelegate]
 * ([onPublishDelegate]) so the shared coordinator's zone-dispatching planner/drop route the folder zone here
 * without hoisting the folder's order/gap out. A tap launches ([onLaunch]); cells commit through the shared
 * [onDrop].
 *
 * Extract is still step B: [onExtract] ends the drag and the caller removes + re-places the app on home. The
 * seamless continue-onto-home (A-2) builds on this shared coordinator next. Reorder is within the current page.
 *
 * TODO(launcher frosted UI): replace the solid-black backdrop with the deferred blur/frosted backdrop.
 */
@Composable
fun FolderOverlay(
    label: String,
    apps: List<AppInfo>,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onLaunch: (ComponentKey) -> Unit,
    onReorder: (List<ComponentKey>) -> Unit,
    onExtract: (ComponentKey) -> Unit,
    onDrop: () -> Unit,
    onPublishDelegate: (FolderDragDelegate?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
) {
    BackHandler(onBack = onDismiss)

    val device = currentDeviceConfiguration()
    val grid = remember(device) { FolderGrid.toGridConfig(device) }
    val labelHeight = cellLabelHeight(metrics)
    val pageSize = (grid.cols * grid.rows).coerceAtLeast(1)

    // The title row + the dots row are what landscape sizing must leave room for above/below the grid.
    val titleStyle = MaterialTheme.typography.titleMedium
    val titleHeight = with(LocalDensity.current) {
        (if (titleStyle.lineHeight.isSpecified) titleStyle.lineHeight else titleStyle.fontSize * 1.2f).toDp()
    }
    val landscapeReserve = titleHeight + TitleBottomPadding + FolderDotsHeight

    // ── Reorder state ──
    val orderComponents = apps.map { it.componentKey }
    val appByComponent = remember(apps) { apps.associateBy { it.componentKey } }

    // Optimistic order: on drop we show the new order immediately, then clear the override once the persisted
    // [apps] catch up (same order) — or if membership changed underneath. Avoids the item snapping back.
    var orderOverride by remember { mutableStateOf<List<ComponentKey>?>(null) }
    LaunchedEffect(orderComponents) {
        val override = orderOverride ?: return@LaunchedEffect
        if (orderComponents == override || orderComponents.toSet() != override.toSet()) orderOverride = null
    }
    val effectiveOrder = orderOverride ?: orderComponents
    val liveOrder = rememberUpdatedState(effectiveOrder)

    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    var gap by remember { mutableStateOf(-1) }

    val pageCount = rememberUpdatedState((orderComponents.size + pageSize - 1) / pageSize)
    val pagerState = rememberLauncherPagerState(pageCount = { pageCount.value.coerceAtLeast(1) }, infiniteScroll = { false })

    // The folder's drag hooks for the shared coordinator, kept stable and reading live state. The plan migrates
    // the reorder gap; commitReorder densifies and persists (optimistically first).
    val gridState = rememberUpdatedState(grid)
    val onReorderState = rememberUpdatedState(onReorder)
    val delegate = remember {
        object : FolderDragDelegate {
            override fun plan(item: GridItem, fingerInRoot: Offset): PlacementPlan? {
                val geo = geometry ?: return null
                val dragged = (item as? GridItem.App)?.component ?: return null
                val g = gridState.value
                val ps = (g.cols * g.rows).coerceAtLeast(1)
                // Off the grid → hold the current gap; on a cell → migrate the gap toward it.
                val cell = geo.cellAt(fingerInRoot)
                    ?: return PlacementPlan(GridPlacement(0, 0, 0), DropIntent.PLACE)
                val flatSlot = pagerState.currentPage * ps + cell.row * g.cols + cell.col
                gap = movingGap(liveOrder.value, dragged, gap, flatSlot, geo.cellFractionX(fingerInRoot) < 0.5f)
                return PlacementPlan(GridPlacement(0, 0, 0), DropIntent.PLACE)
            }

            override fun commitReorder(item: GridItem) {
                val dragged = (item as? GridItem.App)?.component ?: return
                val newOrder = movingGapDisplayOrder(liveOrder.value, dragged, gap)
                orderOverride = newOrder // show it now; persist and let the store catch up
                onReorderState.value(newOrder)
                gap = -1
            }
        }
    }
    DisposableEffect(delegate) {
        onPublishDelegate(delegate)
        onDispose { onPublishDelegate(null) }
    }
    LaunchedEffect(coordinator.isDragging) { if (!coordinator.isDragging) gap = -1 }
    DisposableEffect(coordinator) { onDispose { coordinator.unregisterZone(FolderZoneId) } }

    val session = coordinator.session
    val draggedComponent = (session?.item as? GridItem.App)?.component

    // Dwell with the finger off the inner grid (over the outer zone / home behind it) extracts the app out of
    // the folder. B: end the drag and hand the component to the caller (remove + re-place on home).
    val overOuterZone = session != null && session.activeZone != FolderZoneId
    LaunchedEffect(overOuterZone) {
        if (!overOuterZone) return@LaunchedEffect
        delay(ExtractDwellMs)
        val component = (coordinator.session?.item as? GridItem.App)?.component ?: return@LaunchedEffect
        coordinator.cancel()
        onExtract(component)
    }

    val displayApps = movingGapDisplayOrder(effectiveOrder, draggedComponent, gap).mapNotNull(appByComponent::get)
    val pages = displayApps.chunked(pageSize)

    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val scrimInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }

    // Outer scrim fills the whole screen (black behind the bars); the content region is inset to the safe area.
    // The floating drag proxy is a sibling of the (inset) content so its root-space offset isn't shifted.
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(safeInsets),
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
                // Inner zone: the paged app grid. A tap on its background is consumed so it doesn't dismiss.
                Box(
                    Modifier
                        .size(innerSize)
                        .clickable(interactionSource = innerInteraction, indication = null, onClick = {}),
                ) {
                    LauncherPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                            .onGloballyPositioned {
                                val b = it.boundsInRoot()
                                geometry = GridGeometry(
                                    originInRoot = Offset(b.left, b.top),
                                    cellW = b.width / grid.cols,
                                    cellH = b.height / grid.rows,
                                    cols = grid.cols,
                                    rows = grid.rows,
                                )
                                coordinator.registerZone(DropZone(FolderZoneId, b, z = 1) { it is GridItem.App })
                            },
                    ) { pageIndex ->
                        LauncherGrid(config = grid, modifier = Modifier.fillMaxSize()) {
                            flowItems(
                                items = pages.getOrNull(pageIndex).orEmpty(),
                                itemKey = { it.componentKey.flatten() },
                            ) { app, cellModifier ->
                                LauncherDragCell(
                                    coordinator = coordinator,
                                    item = GridItem.App(app.componentKey),
                                    gestureConfig = gestureConfig,
                                    onDrop = onDrop,
                                    modifier = cellModifier,
                                    onOpen = { onLaunch(app.componentKey) },
                                ) {
                                    AppCell(app = app, onClick = {}, modifier = Modifier.fillMaxSize(), metrics = metrics)
                                }
                            }
                        }
                    }
                }
                // Page dots below the inner zone; the row's height is reserved even for a single page.
                Box(Modifier.height(FolderDotsHeight), contentAlignment = Alignment.Center) {
                    if (pages.size > 1) PageDots(count = pages.size, current = pagerState.currentPage)
                }
            }
        }

        // Floating proxy following the finger during a reorder drag (root space, above the content).
        val geo = geometry
        val dragApp = draggedComponent?.let(appByComponent::get)
        if (session != null && geo != null && dragApp != null) {
            val finger = session.fingerInRoot
            FloatingDragIcon(
                rootOffset = IntOffset(
                    (finger.x - geo.cellW / 2f).roundToInt(),
                    (finger.y - geo.cellH / 2f).roundToInt(),
                ),
                size = DpSize(with(LocalDensity.current) { geo.cellW.toDp() }, with(LocalDensity.current) { geo.cellH.toDp() }),
            ) {
                AppCell(app = dragApp, onClick = {}, modifier = Modifier.fillMaxSize(), metrics = metrics)
            }
        }
    }
}

/** A row of small dots marking the folder's pages, the [current] one filled. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(DotSpacing)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(if (index == current) Color.White else Color.White.copy(alpha = 0.3f)),
            )
        }
    }
}
