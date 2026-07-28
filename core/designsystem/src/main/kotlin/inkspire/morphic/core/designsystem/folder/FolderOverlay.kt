package inkspire.morphic.core.designsystem.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
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
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.toGridConfig
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Padding between the folder title and the inner zone. */
/** Width of the outline drawn round the inner zone while a drag is in flight (its drop-target affordance). */
private val InnerZoneOutline = 1.dp

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
 *   (~[ExtractDwellMs], i.e. the finger is off the inner grid) hands the drag off to home — the overlay hides
 *   itself but stays composed (so the dragged cell keeps its pointer stream) and drops its zone, so the shared
 *   coordinator now targets home and the drag continues there; the caller ([onExtractStart]) tracks it and the
 *   drop places the app on home + removes it from the folder; and
 * - the **inner zone** (registered as [FolderZoneId] at a higher `z` than home) is a bounded card holding the
 *   folder's app grid ([label] above it), sized by [folderInnerSize] so every folder is the same size.
 *
 * The apps are a **dense flow** chunked into pages (dots below), swipeable. Long-press to reorder within the flow
 * — a *moving gap*: the dragged app's slot travels through the ordered list and the flow densifies on drop (see
 * `FolderReorder.kt`). The folder's hover/commit hooks are exposed to the home via a [FolderDragDelegate]
 * ([onPublishDelegate]) so the shared coordinator's zone-dispatching planner/drop route the folder zone here
 * without hoisting the folder's order/gap out. A tap launches ([onLaunch]); cells commit through the shared
 * [onDrop].
 *
 * Extract is a **continuous hand-off**: the drag started here carries on as the *same* session onto home, no
 * lift. Reorder is within the current page.
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
    onExtractStart: (ComponentKey) -> Unit,
    onDrop: () -> Unit,
    onPublishDelegate: (FolderDragDelegate?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    incoming: AppInfo? = null,
) {
    BackHandler(onBack = onDismiss)

    val device = currentDeviceConfiguration()
    val grid = remember(device) { FolderGrid.toGridConfig(device) }
    val pageSize = (grid.cols * grid.rows).coerceAtLeast(1)
    val titleStyle = MaterialTheme.typography.titleMedium

    // ── Reorder state ──
    // An app dragged in from home (not yet a member) is appended so the same MovingGap machinery positions it:
    // its cell is the dragged one → drawn invisible (the gap), the proxy floats, and the drop reports an order
    // that includes it (the caller adds it to the folder + removes it from home).
    val allApps = remember(apps, incoming) {
        if (incoming != null && apps.none { it.componentKey == incoming.componentKey }) apps + incoming else apps
    }
    val orderComponents = allApps.map { it.componentKey }
    val appByComponent = remember(allApps) { allApps.associateBy { it.componentKey } }

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

    // The folder's drag hooks for the shared coordinator, kept stable and reading live state. onHover migrates
    // the reorder gap; commitReorder densifies and persists (optimistically first).
    val gridState = rememberUpdatedState(grid)
    val onReorderState = rememberUpdatedState(onReorder)
    val delegate = remember {
        object : FolderDragDelegate {
            override fun onHover(item: GridItem, fingerInRoot: Offset): PlacementPlan? {
                val geo = geometry ?: return null
                val dragged = (item as? GridItem.App)?.component ?: return null
                val g = gridState.value
                val ps = (g.cols * g.rows).coerceAtLeast(1)
                // Off the grid → hold the current gap; on a cell → migrate the gap toward it.
                val cell = geo.cellAt(fingerInRoot) ?: return FolderReorderPlan
                val flatSlot = pagerState.currentPage * ps + cell.row * g.cols + cell.col
                gap = movingGap(liveOrder.value, dragged, gap, flatSlot, geo.cellFractionX(fingerInRoot) < 0.5f)
                return FolderReorderPlan
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
    // True once a drag has dwelled off the grid and been handed off to home: the overlay hides but stays
    // composed (the dragged cell keeps its pointer), and its zone is dropped so the coordinator targets home.
    var extracting by remember { mutableStateOf(false) }
    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) { gap = -1; extracting = false }
    }
    DisposableEffect(coordinator) { onDispose { coordinator.unregisterZone(FolderZoneId) } }

    val session = coordinator.session
    val draggedComponent = (session?.item as? GridItem.App)?.component

    // Dwell with the finger off the inner grid (over the outer zone / home behind it) hands the drag off to
    // home: hide + drop our zone so the shared coordinator targets home, and tell the caller which app is
    // leaving. The drag continues; the drop (on home) does the actual move + folder removal. An app being
    // dragged *in* (the incoming) is never extracted — it isn't a member to remove.
    val draggedIsIncoming = draggedComponent != null && draggedComponent == incoming?.componentKey
    val overOuterZone = session != null && session.activeZone != FolderZoneId && !draggedIsIncoming
    LaunchedEffect(overOuterZone, extracting) {
        if (!overOuterZone || extracting) return@LaunchedEffect
        delay(ExtractDwellMs)
        val component = (coordinator.session?.item as? GridItem.App)?.component ?: return@LaunchedEffect
        extracting = true
        coordinator.unregisterZone(FolderZoneId)
        onExtractStart(component)
    }

    val displayApps = movingGapDisplayOrder(effectiveOrder, draggedComponent, gap).mapNotNull(appByComponent::get)
    val pages = displayApps.chunked(pageSize)

    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val scrimInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }

    // Root spans the screen; the floating proxy is a sibling of the content so its root-space offset isn't
    // shifted by the content's inset.
    Box(modifier.fillMaxSize()) {
        // Backdrop (black behind the bars) + card. While extracting it's faded to nothing but kept composed —
        // so the dragged cell keeps its pointer stream (the proven "closing surface fades but stays in the
        // tree" rule). The modifier chain is kept structurally stable across that flip (only `alpha` and the
        // clickable's `enabled` change), so the drag isn't disturbed; at alpha 0 the black vanishes and home
        // shows through, and dismiss-on-tap is off.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (extracting) 0f else 1f }
                .background(Color.Black)
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    enabled = !extracting,
                    onClick = onDismiss,
                ),
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(safeInsets),
                contentAlignment = Alignment.Center,
            ) {
            val innerSize: DpSize = folderInnerSize(DpSize(maxWidth, maxHeight), device, grid, metrics)
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
                        // The outline is always in the chain and switches *colour*, so this chain stays
                        // structurally stable across the drag flip — the same rule the backdrop above follows,
                        // and the cells inside here own live pointer streams. Note `border(0.dp, …)` would not be
                        // the off switch it looks like: 0.dp *is* Dp.Hairline, which still draws a 1px line.
                        .border(InnerZoneOutline, if (session != null) Color.White else Color.Transparent)
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
                                // Don't re-register once we've handed off to home (the hidden grid may re-lay-out).
                                if (!extracting) {
                                    coordinator.registerZone(DropZone(FolderZoneId, b, z = 1) { it is GridItem.App })
                                }
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
                                ) { itemGestures ->
                                    AppCell(
                                        app = app,
                                        modifier = Modifier.fillMaxSize(),
                                        metrics = metrics,
                                        itemGestures = itemGestures,
                                    )
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
                // No `itemGestures`: the proxy is a rendering that follows the finger, not a touch target.
                AppCell(app = dragApp, modifier = Modifier.fillMaxSize(), metrics = metrics)
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
