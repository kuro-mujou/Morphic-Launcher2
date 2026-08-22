package inkspire.morphic.feature.home

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.fitRowHeight
import inkspire.morphic.core.designsystem.drag.DragAutoScrollEffect
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.grid.CoordinateDragGrid
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.GridSpan
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.WidgetMinCell
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.splitForSideZone
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.menu.MenuAction
import inkspire.morphic.core.designsystem.menu.surfaceMenuGestures
import inkspire.morphic.core.designsystem.ordered.cellFractionY
import inkspire.morphic.core.designsystem.ordered.movingGap
import inkspire.morphic.core.designsystem.ordered.movingGapDisplayOrder
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomeListGrid
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.core.model.WidgetAreaGrid
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.WidgetSpan
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.feature.home.widgetpicker.WidgetPickerSheet
import inkspire.morphic.feature.home.widgetpicker.rememberWidgetAddFlow
import org.koin.compose.koinInject
import kotlin.math.floor
import kotlin.math.roundToInt

/** The drag identities of this layout's two zones — named for the zone, as the pager surface's are. */
private val ListZoneId = ZoneId("home-list")
private val WidgetAreaZoneId = ZoneId("home-widget-area")

/**
 * The plan the list reports for **every** hover it accepts: droppable, and explicitly *painting nothing*.
 *
 * An ordered surface previews a reorder by reflowing its own rows around the gap ([movingGapDisplayOrder]), so there
 * is no target cell for a drop shadow to name — `DropIntent.REORDER` says exactly that and `DropFootprint` returns
 * early on it. The APPS pager and the category pager each declare the identical constant for the identical reason;
 * three copies of one `PlacementPlan` is a smell worth a shared name once something needs to *vary*, and so far
 * nothing does.
 */
private val ListReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)

/**
 * **`HomeLayout.LIST_WITH_WIDGET_AREA`** — a **widget area** at the leading edge and a hand-ordered **vertical list**
 * of apps filling the rest.
 *
 * The other of HOME's two arrangements, chosen by [HomeScreen]; the pager one is [HomePagerSurface]. They share this
 * module's ViewModel and state and nothing else, and that is not an omission — a coordinate surface asks "which
 * cell, and who gets shoved aside?" while an ordered one asks "which index?", so there is no planner, no store and
 * no gesture the two could share. What *is* shared is [HomeZoneScaffold], because "a zone of fixed extent at one
 * edge, the main area taking the rest" is the same arrangement either way.
 *
 * **The widget area is the dock's mirror**, and reads as one everywhere it matters: same fixed extent, same free
 * placement, same "the extent bounds the count divided out of it" rule, same settings section — placed at the *top*
 * (or the leading rail on a phone in landscape) because it is the thing you look at rather than the thing you reach
 * for. See `SideZoneEdge`.
 *
 * **It holds widgets, and only widgets.** `DropZone.accepts` refuses everything else, which makes the rule
 * structural rather than a check at drop time: a zone that refuses the dragged item is skipped by the hit
 * test, so an app carried over the area falls through to the list beneath instead of being rejected on release.
 * Adding one is the surface menu's *Widgets* row; moving one within the area is the same shared planner the pager
 * pairing's zones use.
 *
 * **The list is ordered, and dragging it reorders it — nothing else.** There is no merge ring (a list of apps has no
 * folders in it), no page to carry an item onto, and no coordinate to write: a drop is an
 * index, committed through [HomeViewModel.reorderList]. The preview is MovingGap, the same model the APPS pager and
 * every folder use.
 *
 * **An app dragged in from the APPS surface lands at the end**, and that is the one place the reorder model does not
 * apply: MovingGap migrates the gap from where an item *already is*, and a stranger is nowhere. Appending is also the
 * honest reading of the gesture — this list has no cell to aim at, so a drop into it says "put this in my list", not
 * "put it at row seven". A chosen position stays reachable the way it always was, by dragging the row afterwards.
 *
 * **A `Column` in a `verticalScroll`, deliberately not a `LazyColumn`** — the opposite call from `AppsVerticalList`,
 * and for two reasons that are both properties of this list rather than preferences. A lazy list disposes rows that
 * scroll away, and the lifted row **owns the pointer stream driving the drag**, so auto-scrolling far enough would
 * kill the gesture (the APPS pager needs `keepAllPagesPlaced` for exactly this). And this list is curated — the
 * handful of apps the user chose — where the drawer is every app installed, so composing it whole costs nothing.
 * It is also what makes the drag geometry the documented one: `scrollState.value` is snapshot state, so a stable
 * viewport anchor minus the scroll offset republishes the content origin every frame for free.
 *
 * Not built: the "Add apps" row (a picker), the long-press item menu, and removing an app from the list. Without a
 * picker, the list's contents are what [HomeViewModel] seeded from the grid.
 *
 * @param device reported by [HomeScreen], which is the layer that can read the window.
 */
@Composable
internal fun HomeListSurface(
    viewModel: HomeViewModel,
    state: HomeState,
    device: DeviceConfiguration,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Where the widget area sits, and therefore whether its extent is a height or a width — the same one expression
    // the pager surface and both settings sections read, differing only in the layout handed to it.
    val edge = device.sideZoneEdge(HomeLayout.LIST_WITH_WIDGET_AREA)
    val window = usableWindowArea(uiInsets)

    val sideSizing = state.side
    val extent = (sideSizing?.extentDp ?: checkNotNull(WidgetAreaGrid.extentDp)).dp
    val split = window.splitForSideZone(extent.value, edge)

    val listPadding = state.paddingFor(GridSlot.HOME_LIST).dp
    val areaPadding = state.paddingFor(GridSlot.HOME_WIDGET_AREA).dp
    val areaArea = split.side.copy(widthDp = (split.side.widthDp - areaPadding.value * 2).coerceAtLeast(1f))

    // **Fitted by a widget's floor, not an icon's.** `WidgetAreaGrid.icon` is null — a widget is not an icon in a
    // cell — so there are no guardrails to invert and `CellFit` is given `WidgetMinCell` instead. Everything else is
    // the dock's arithmetic exactly: the stored counts clamped to what the extent and
    // the area can actually hold, never written back. The blueprint stands in until the store answers.
    val areaConfig = if (sideSizing == null) {
        remember(device) { WidgetAreaGrid.toGridConfig(device) }
    } else {
        WidgetAreaGrid.fitGridConfig(areaArea, sideSizing.cols, sideSizing.rows, WidgetMinCell)
    }

    // The list's row height is the *setting* and the icon is a fraction of it — the reverse of a grid, and the whole
    // of what a one-lane layout has to be told. Clamped on read to what the current guardrails can honor
    // (`fitRowHeight`), never written back, exactly as the grids clamp their counts.
    val listMetrics = state.metricsFor(GridSlot.HOME_LIST)
    val storedRowHeight = (state.main as? HomeMainSizing.List)?.rowHeightDp
    val rowHeight = fitRowHeight((storedRowHeight ?: checkNotNull(HomeListGrid.rowHeightDp)).dp, listMetrics)
    val rowHeightPx = with(density) { rowHeight.toPx() }

    val gestureConfig = remember {
        ItemGestureConfig(touchSlopPx = with(density) { 20.dp.toPx() }, longPressTimeoutMillis = 400L)
    }

    // The order the store reports, and the gap the finger is currently holding open in it. `gap` is an index into the
    // list *without* the dragged app (see `movingGap`), and `-1` means "not seeded yet" — the first hover derives it
    // from where the app already sits, so a drag that goes nowhere is a no-op rather than an off-by-one.
    val order = remember(state.listApps) { state.listApps.map { it.componentKey } }
    val liveOrder = rememberUpdatedState(order)
    var gap by remember { mutableIntStateOf(-1) }

    // **The list's geometry, reconstructed rather than measured.** `onGloballyPositioned` does not reliably re-fire
    // when scrolling moves a node — the scroll moves it through its parent's placement — so the viewport's own
    // position (which genuinely does not move) minus `scrollState.value` (snapshot state) is the content origin, and
    // it republishes every frame for free. `CategoryPage` is the worked example of the same rule.
    val scrollState = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }

    // The app whose Gestures sheet is open, and where its placeholder lands. Held by the surface rather than the
    // menu, which is gone by the time the sheet is up.
    var gesturesFor by remember { mutableStateOf<AppInfo?>(null) }
    val context = LocalContext.current
    val listGeometry = if (viewport == Rect.Zero || rowHeightPx <= 0f) {
        null
    } else {
        GridGeometry(
            originInRoot = Offset(viewport.left, viewport.top - scrollState.value),
            cellW = viewport.width,
            cellH = rowHeightPx,
            cols = 1,
            rows = order.size.coerceAtLeast(1),
        )
    }
    val liveGeometry = rememberUpdatedState(listGeometry)

    // **Where HOME is scrolled, for the surface swipe** — the vertical mirror of the pager pairing's report. A
    // one-finger swipe onto a TOP or BOTTOM surface crosses this list, so it may only leave HOME once the list has
    // nothing further to scroll in that direction; horizontally nothing moves, so LEFT and RIGHT stay free.
    //
    // A flat "no vertical swipe" cannot express this — it forbids the crossing outright rather than handing it off
    // at the end of the list.
    ReportScrollEdges {
        ScrollEdges(atTop = !scrollState.canScrollBackward, atBottom = !scrollState.canScrollForward)
    }

    var areaGeometry by remember { mutableStateOf<GridGeometry?>(null) }

    // The list's own planner. The widget area plans nothing today because nothing it accepts can be lifted yet
    // (`CoordinateDragGrid` still registers its zone, so an app carried over it falls through to the list beneath
    // rather than being refused on release) — hence the constant `null` one zone over.
    val planner = remember {
        DropPlanner { item, fingerInRoot ->
            val geo = liveGeometry.value ?: return@DropPlanner null
            val app = (item as? GridItem.App)?.component ?: return@DropPlanner null
            // **An app arriving from the APPS surface is not in this list, so there is no gap to migrate** — it
            // lands at the end, which is what was specified for it. `movingGap` reasons about an item's *current*
            // index, so asking it about a stranger would answer from index 0.
            if (app !in liveOrder.value) {
                gap = liveOrder.value.size
                return@DropPlanner ListReorderPlan
            }
            // The row under the finger, unclamped at the bottom so `movingGap` can read "past the last item" as
            // append; floored at zero because a finger above the first row still means the first row.
            val slot = floor((fingerInRoot.y - geo.originInRoot.y) / geo.cellH).toInt().coerceAtLeast(0)
            gap = movingGap(
                order = liveOrder.value,
                dragged = app,
                currentGap = gap,
                flatSlot = slot,
                // A list flows down, so the half that decides "before or after" is the top half of a row rather than
                // the left half of a cell.
                insertBefore = geo.cellFractionY(fingerInRoot) < 0.5f,
            )
            ListReorderPlan
        }
    }
    // The launcher's one coordinator — the shell's, not this screen's, which is what lets an app dragged out of the
    // APPS drawer be dropped onto this list at all.
    val coordinator = requireDragCoordinator()
    // The launcher's one menu host, for the same reason as the coordinator: the verbs belong to the item.
    val menuHost = LocalMenuHost.current
    // The add flow, reporting into the **widget area** — this pairing's home for widgets. Same shape as the pager
    // pairing's; what differs is the zone and the grid, which is the whole reason each surface owns its own.
    val widgetHost = koinInject<AppWidgetHostController>()
    val addWidget = rememberWidgetAddFlow(widgetHost) { bound ->
        val geo = areaGeometry
        val span = geo?.let {
            WidgetSpan.forMinSize(bound.minWidthPx, bound.minHeightPx, it.cellW, it.cellH, areaConfig)
        }
        span != null && viewModel.placeWidget(
            widget = WidgetInfo(
                appWidgetId = bound.appWidgetId,
                providerPackage = bound.provider.packageName,
                providerClass = bound.provider.className,
                label = bound.label,
            ),
            span = span,
            zone = HomeZone.WIDGET_AREA,
            config = areaConfig,
        )
    }

    // Whether the widget picker is up — surface-local, see the menu row below.
    var widgetPickerOpen by remember { mutableStateOf(false) }

    // A widget in the area offers the one verb a widget has, exactly as it does on the pager pairing's grids —
    // removing it also releases its `appWidgetId`, which is why it goes through the ViewModel rather than being a
    // plain `RemoveFromGrid`.
    val showAreaMenu: (HomeItem, Rect) -> Unit = { item, anchor ->
        (item as? HomeItem.Widget)?.let { widget ->
            menuHost?.show(
                title = widget.info.label.ifBlank { "Widget" },
                anchor = anchor,
                actions = listOf(
                    MenuAction("Remove widget") { viewModel.removeWidget(widget.info.appWidgetId) },
                ),
            )
        }
    }
    val presented = LocalSurfacePresented.current
    val session = coordinator.session
    val draggedApp = (session?.item as? GridItem.App)?.component

    // **The gap is cleared when the drag ends, never by the code that reads it.** An earlier cut reset it at the top
    // of `commitLanding` and then computed the committed order from it two lines later, so every drop wrote the app to
    // index 0. Resetting on `isDragging` is the APPS pager's shape and it has the property that matters: the only
    // writer of `-1` runs *after* the drop has been read, and it covers a cancel as well as a release.
    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) gap = -1
    }

    // What the user sees: the order with the dragged app lifted to the gap, everything else densified around it. The
    // same function produces the committed order on drop, so the preview and the write cannot disagree.
    val displayed = remember(order, draggedApp, gap) { movingGapDisplayOrder(order, draggedApp, gap) }
    // Resolved through [appInfo] rather than from the list's own apps, so the row standing in for the gap can be an
    // app that is not in the list yet — one being carried in from the APPS drawer, which is in nothing home owns
    // until the drop. It renders invisible (it *is* the dragged item), which is exactly what the gap should look
    // like; a lookup scoped to `listApps` would drop it and the gap would never open.
    val displayedApps = remember(displayed, state) { displayed.mapNotNull(state::appInfo) }

    // Hold the dragged app past the drop: the write is asynchronous, so re-deriving the proxy's icon from the state
    // would lose it for a frame at exactly the moment the finger lifts.
    val proxyApp = remember(draggedApp) { draggedApp?.let(state::appInfo) }

    // What a landing **in this list** means, whether the app was lifted from a row of it or carried in from the APPS
    // drawer. Both are the same write, because `movingGapDisplayOrder` inserts a stranger and moves a member with the
    // one expression — and the gap the planner left is what the user has been *looking at*, the same order the rows
    // were drawn from, so the committed order is by construction the one on screen.
    fun commitLanding(outcome: DropOutcome) {
        if (gap < 0) return // the finger never rested on a row; nothing to write
        val app = (outcome.item as? GridItem.App)?.component ?: return
        viewModel.reorderList(movingGapDisplayOrder(liveOrder.value, app, gap))
    }

    // The list's own drop zone. `CoordinateDragGrid` registers the widget area's for itself; the list is a plain
    // scroller, so it registers from the one measurement it already publishes — the viewport, whose rectangle is
    // exactly the area a finger may drop in. **It accepts apps only**, which is the mirror of the widget area's rule
    // one zone over: neither zone can be handed something it has nowhere to put.
    RegisterDropZone(
        coordinator = coordinator,
        zone = viewport.takeIf { it != Rect.Zero }?.let {
            DropZone(
                id = ListZoneId,
                bounds = it,
                z = 0,
                planner = planner,
                accepts = { item -> item is GridItem.App },
                onDrop = ::commitLanding,
            )
        },
    )

    Box(
        modifier
            .fillMaxSize()
            // **Long-press on empty space → the surface menu.** On the root, so it covers both zones and the margins
            // between them; `surfaceMenuGestures` owns why a press that lands on an icon does not reach it. Gated on
            // being the surface on screen for the floating proxy's reason — a surface panned off to one side must not
            // answer a press meant for the one in front of it.
            //
            // **One detector rather than one per zone** (home, dock, widget area). Three would be worth it only if
            // each offered a *different* action set; ours all resolve to the same single row today, so splitting them
            // would be three ways to say one thing. The split returns with the first verb that is not launcher-wide.
            .surfaceMenuGestures(gestureConfig, enabled = presented) { position ->
                menuHost?.showSurface(
                    position = position,
                    // The same one verb the pager pairing offers, and the same reason the sheet is hosted here
                    // rather than above both arms: it is sized against the grid a widget would land on, which
                    // differs between the two.
                    surfaceActions = listOf(MenuAction("Widgets") { widgetPickerOpen = true }),
                )
            },
    ) {
        HomeZoneScaffold(
            edge = edge,
            extent = extent,
            mainPadding = listPadding,
            sidePadding = areaPadding,
            // The widget area, on the same coordinator as the list. It **accepts widgets only**, made structural:
            // a zone that refuses the dragged item is skipped by the hit test, so
            // an app carried over it falls through to the list beneath instead of being rejected at drop time.
            side = { zoneModifier ->
                CoordinateDragGrid(
                    items = state.inZone(HomeZone.WIDGET_AREA),
                    config = areaConfig,
                    coordinator = coordinator,
                    zoneId = WidgetAreaZoneId,
                    gestureConfig = gestureConfig,
                    dragItem = { it.gridItem },
                    placement = { it.placement },
                    acceptsItem = { it is GridItem.Widget || it is GridItem.WidgetContainer },
                    // The same shared planner the pager pairing's two zones use — a zone is described by its
                    // geometry, its dimensions and its occupants, not by an algorithm of its own.
                    planner = { item, finger ->
                        areaGeometry?.let { geo ->
                            planCoordinateDrop(
                                geo = geo,
                                config = areaConfig,
                                page = 0,
                                occupants = state.inZone(HomeZone.WIDGET_AREA)
                                    .filter { it.gridItem != item }
                                    .associate { it.gridItem to it.placement },
                                item = item,
                                // A widget keeps its own footprint here too — the area holds nothing else, so
                                // the one-visual-cell fallback is only ever the first frame of a drag from
                                // somewhere that has no placement.
                                span = state.inZone(HomeZone.WIDGET_AREA)
                                    .firstOrNull { it.gridItem == item }?.placement
                                    ?.let { GridSpan(colSpan = it.colSpan, rowSpan = it.rowSpan) }
                                    ?: GridSpan(areaConfig.cellMultiplier, areaConfig.cellMultiplier),
                                fingerInRoot = finger,
                            )
                        }
                    },
                    onLand = { outcome ->
                        val plan = outcome.plan ?: return@CoordinateDragGrid
                        viewModel.applyChanges(
                            plan.moves.map { (item, to) ->
                                LayoutChange.Move(item, to, HomeZone.WIDGET_AREA)
                            } + LayoutChange.Move(outcome.item, plan.footprint, HomeZone.WIDGET_AREA),
                        )
                    },
                    onRelease = { coordinator.drop() },
                    modifier = zoneModifier,
                    onGeometryChange = { areaGeometry = it },
                    onOpen = {},
                    onShowMenu = { item, anchor -> showAreaMenu(item, anchor) },
                ) { item, cellModifier, itemGestures ->
                    // Only a widget can be here — `acceptsItem` refuses everything else, and nothing seeds it — so
                    // anything the state reports for this zone that is not one is a row we cannot draw.
                    (item as? HomeItem.Widget)?.let {
                        WidgetCell(
                            appWidgetId = it.info.appWidgetId,
                            label = it.info.label.ifBlank { "Widget" },
                            modifier = cellModifier,
                            itemGestures = itemGestures,
                        )
                    }
                }
            },
            main = { zoneModifier ->
                ListZone(
                    apps = displayedApps,
                    rowHeight = rowHeight,
                    scrollState = scrollState,
                    dragging = coordinator.isDragging,
                    modifier = zoneModifier,
                    onViewportChange = { bounds -> viewport = bounds },
                    coordinator = coordinator,
                    gestureConfig = gestureConfig,
                    // A release here only ends the drag; the landing is committed by whichever zone it fell in —
                    // this list's, or one of the pager pairing's if the user has switched layouts mid-gesture.
                    onRelease = { coordinator.drop() },
                    onLaunch = { viewModel.launch(it) },
                    // **"Remove" here is the list's own verb**, and it is neither `RemoveFromGrid` nor a reorder:
                    // this list is an order store of its own, not a view of the pager's placements, so taking an
                    // app off it is a *membership* write. Writing the order without that app looks equivalent and
                    // is not — the store reconciles a reported order against real membership and would put the app
                    // straight back at the end. See [HomeViewModel.removeFromList].
                    onShowMenu = { app, anchor ->
                        menuHost?.showApp(
                            component = app.componentKey,
                            label = app.label,
                            anchor = anchor,
                            surfaceActions = listOf(
                                MenuAction("Gestures") { gesturesFor = app },
                                MenuAction("Remove") { viewModel.removeFromList(app.componentKey) },
                            ),
                        )
                    },
                    gesturesOn = { state.itemGestures.gesturesOn(it) },
                    onGesture = { app, what ->
                        Toast.makeText(context, "${app.label}: $what", Toast.LENGTH_SHORT).show()
                    },
                    metrics = listMetrics,
                )
            },
        )

        // Auto-scroll while a drag is held near the viewport's top or bottom, so an app can be carried to a part of
        // the list that is off screen. The re-send afterwards is the second half of that rule: the coordinator only
        // re-plans when the *finger* moves, and auto-scroll exists precisely to move content under a finger held
        // still — so the same position must be pushed again once the origin has republished, in the same
        // `SideEffect` (a `snapshotFlow` on the offset fires before the recomposition that derives the new geometry,
        // and so stays a step behind).
        val finger = session?.fingerInRoot
        DragAutoScrollEffect(
            scrollState = scrollState,
            bounds = if (finger == null) null else viewport.takeIf { it != Rect.Zero },
            fingerInRoot = finger,
        )
        // Reading the offset here is what subscribes this composition to it, so a scroll under a *still* finger
        // produces a recomposition — and the `SideEffect` then re-sends the same position against the origin that
        // recomposition derived. It has to be a `SideEffect` rather than a `LaunchedEffect(offset)` because it must
        // run *after* that recomposition; a `snapshotFlow` on the offset fires before it and stays a step behind.
        // Re-sending on the drag's other recompositions costs nothing: the same finger resolves the same plan.
        if (finger != null) {
            @Suppress("UNUSED_VARIABLE") val scrolled = scrollState.value
            SideEffect { coordinator.moveTo(finger) }
        }

        // The floating proxy: one row-sized icon under the finger. Its counterpart in the list stays composed and
        // merely invisible — disposing it would kill the pointer stream driving the drag.
        // Gated on this being the surface on screen: the coordinator is the launcher's, so `session` is non-null
        // while the user drags inside the APPS drawer too, and home must not paint a second icon under that finger.
        if (presented && proxyApp != null && finger != null && viewport != Rect.Zero) {
            // **Pinned to the list's own left edge, following the finger only in y.** Every other surface centers its
            // proxy on the finger because its proxy is one cell — roughly square, and smaller than the finger's
            // travel. A row is the full width of the list, so centering it horizontally would swing the whole row
            // sideways with the thumb and leave it hanging off one edge. What a row can meaningfully be dragged
            // *along* is the one axis it has.
            FloatingDragIcon(
                rootOffset = IntOffset(
                    viewport.left.roundToInt(),
                    (finger.y - rowHeightPx / 2f).roundToInt(),
                ),
                size = DpSize(with(density) { viewport.width.toDp() }, rowHeight),
            ) {
                CompositionLocalProvider(LocalIconMetrics provides listMetrics) {
                    AppRowCell(app = proxyApp, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // **Sized against the widget area, not the list.** This pairing puts widgets in its side zone — the list
        // holds apps in one lane and has no cells to describe — so the "3 × 2" a user reads here is the widget
        // area's grid. That the two pairings answer this differently is exactly why the sheet takes the grid as a
        // parameter rather than deriving one.
        if (widgetPickerOpen) {
            val geo = areaGeometry
            WidgetPickerSheet(
                grid = areaConfig,
                cellWidthPx = geo?.cellW ?: 0f,
                cellHeightPx = geo?.cellH ?: 0f,
                onDismiss = { widgetPickerOpen = false },
                onAddWidget = { provider ->
                    widgetPickerOpen = false
                    addWidget.start(provider.component)
                },
            )
        }

        // Last in the stack, as on the pager pairing: a sheet declared before the zones would be painted over by
        // them.
        gesturesFor?.let { app ->
            val item = GridItem.App(app.componentKey)
            val taken = state.itemGestures.gesturesOn(item)
            HomeItemGestureSheet(
                label = app.label,
                assigned = taken,
                onToggle = { gesture ->
                    viewModel.setItemGestures(
                        item = item,
                        gestures = if (gesture in taken) taken - gesture else taken + gesture,
                    )
                },
                onDismiss = { gesturesFor = null },
                // **The two the list cannot honor**, still assignable because the same key is live on the pager
                // pairing — see the sheet's own note.
                unavailable = VerticalGestures,
                unavailableNote = VerticalUnavailable,
            )
        }
    }
}

/** The pair a scrolling list cannot take, since it owns that axis. */
private val VerticalGestures = setOf(ItemGesture.SWIPE_UP, ItemGesture.SWIPE_DOWN)

/**
 * Said where a user would otherwise only find out by trying it.
 *
 * **Plain over explanatory.** An earlier version named the cause — that the list scrolls this way — on the grounds
 * that a reason is more actionable than a refusal. It reads as an apology in a row of four short statuses, and the
 * cause is one a user can see for themselves by looking at the screen. What they cannot see is that the gesture is
 * stored and simply dormant here, which is what this says.
 */
private const val VerticalUnavailable = "Not supported on this layout"


/**
 * The list itself: one scrolling lane of [AppRowCell], each row [rowHeight] tall.
 *
 * Split out so the surface above reads as "two zones and a drag" rather than as a scroller; it owns nothing but its
 * own layout, and every piece of drag state is passed in.
 *
 * **Each row is a [LauncherDragCell]**, which is the same per-item wiring every coordinate surface and the APPS pager
 * use — and taking it rather than hand-rolling the three parts is what fixed the drag feeling wrong. It brings
 * [inkspire.morphic.core.designsystem.grid.animatePlacement] (rows *glide* as the gap migrates instead of jumping
 * between positions, and the lifted row drops the modifier so it lands on release without a spurious return flight),
 * the `alpha = 0` on the lifted row, and the gesture contract, in one copy. A `Column` places its children in order,
 * so reordering [apps] moves them and `animatePlacement` springs the difference — exactly as a grid's parent-data
 * placement change does.
 *
 * **A row's touch target is its icon and its label**, which `AppRowCell` arranges by hanging the gestures it is given
 * on a wrap-content group rather than on the row's root — the same "visible extent" rule an icon cell follows. So the
 * width past a short label falls through to this surface, which is what will make a press-and-hold on the *list*
 * reach home's own options menu when that lands.
 *
 * **The viewport publishes its bounds, not the content.** `onGloballyPositioned` sits *outside* `verticalScroll`,
 * which is what makes the reported rectangle stable while the content moves under it — see the surface's geometry
 * note. It doubles as the auto-scroll band and (with the scroll offset) as the finger→row origin, so there is one
 * measurement rather than three that could drift.
 *
 * **Manual scrolling is disabled while a drag is in flight**, as on every other dragging surface here: two vertical
 * gestures over one finger otherwise fight, and the drag's own auto-scroll is how content past the fold is reached.
 *
 * @param apps the display order — the stored order with the dragged app lifted to the gap.
 */
@Composable
private fun ListZone(
    apps: List<AppInfo>,
    rowHeight: Dp,
    scrollState: ScrollState,
    dragging: Boolean,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onRelease: () -> Unit,
    onLaunch: (ComponentKey) -> Unit,
    onShowMenu: (AppInfo, Rect) -> Unit,
    gesturesOn: (GridItem) -> Set<ItemGesture>,
    onGesture: (AppInfo, String) -> Unit,
    metrics: IconMetrics,
    modifier: Modifier,
    onViewportChange: (Rect) -> Unit,
) {
    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        Box(modifier = modifier.onGloballyPositioned { onViewportChange(it.boundsInRoot()) }) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState, enabled = !dragging)) {
                apps.forEach { app ->
                    key(app.componentKey.flatten()) {
                        val item = GridItem.App(app.componentKey)
                        LauncherDragCell(
                            coordinator = coordinator,
                            item = item,
                            gestureConfig = gestureConfig,
                            onRelease = onRelease,
                            modifier = Modifier.fillMaxWidth().height(rowHeight),
                            // **Horizontal only, and the vertical pair is not a gap.** This list scrolls, and
                            // a row that took a vertical swipe would take scrolling away from wherever the
                            // user had assigned one — on a surface that is almost entirely rows. Nothing here
                            // wants horizontal, so those cost nothing; the sheet says why the others are
                            // absent rather than leaving a stored gesture silently inert.
                            edgeActions = gesturesOn(item).mapNotNullTo(mutableSetOf()) {
                                it.swipe?.takeIf(SwipeDirection::isHorizontal)
                            },
                            doubleTap = ItemGesture.DOUBLE_TAP in gesturesOn(item),
                            onOpen = { onLaunch(app.componentKey) },
                            onShowMenu = { anchor -> onShowMenu(app, anchor) },
                            onEdgeAction = { direction -> onGesture(app, direction.name.lowercase()) },
                            onDoubleTap = { onGesture(app, "double tap") },
                        ) { itemGestures ->
                            AppRowCell(
                                app = app,
                                modifier = Modifier.fillMaxSize(),
                                itemGestures = itemGestures,
                            )
                        }
                    }
                }
            }
        }
    }
}
