package inkspire.morphic.core.designsystem.collection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Rect
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
import inkspire.morphic.core.designsystem.backdrop.SurfaceBackdropLayer
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.LauncherDragCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.ordered.cellFractionX
import inkspire.morphic.core.designsystem.ordered.flatSlotOf
import inkspire.morphic.core.designsystem.ordered.movingGap
import inkspire.morphic.core.designsystem.ordered.movingGapDisplayOrder
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.LockSurfaceGesture
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
import kotlin.time.Duration.Companion.milliseconds

/** Width of the outline drawn round the inner zone while a drag is in flight (its drop-target affordance). */
private val InnerZoneOutline = 1.dp

private val DotSize = 6.dp
private val DotSpacing = 6.dp

/** This overlay's inner-grid drop zone, registered above the surface's zone (`z = 1`) on the shared coordinator. */
private val CollectionZoneId = ZoneId("collection")

/**
 * How long a dragged app must dwell over the outer zone before the collection closes and the drag carries on beneath
 * it.
 *
 * Deliberately long — and **exactly** the dwell that *opens* a collection mid-drag (`OPEN_COLLECTION_DWELL_MS`),
 * because the two are opposite halves of the same gesture: one hold takes the app in, the same hold takes it out
 * again, and a user who has learned one has learned both. A short dwell here made the card feel like it was ejecting
 * apps by accident: the outer zone is most of the screen, so merely traveling across the card's edge on the way to
 * another cell was enough to trigger it.
 */
private const val LeaveDwellMs = 1000L

/**
 * An **ordered collection of apps opened over a surface** — a home folder, a folder on the APPS pager, or an APPS
 * category card's expansion. Two zones on the **shared** [DragCoordinator] the host surface owns:
 * - the **outer zone** is the full-screen scrim: tapping it closes the collection, and holding a dragged app over it
 *   (~[LeaveDwellMs], i.e. the finger is off the inner grid — and only once it has *been* on the inner grid, see the
 *   arming note at that dwell) tells the caller the drag has **left** ([onLeave]). The caller closes this one, so
 *   the shared coordinator targets the surface again and the same uninterrupted drag continues there — free to enter
 *   the next collection, or this one again. Nothing is written on the way out; the drop decides where the app ends up.
 * - the **inner zone** (registered as [CollectionZoneId] at a higher `z` than the surface) is a bounded card holding
 *   the collection's app grid ([label] above it), sized by [appCollectionInnerSize] so every one is the same size.
 *
 * **What it renders is the same thing in all three cases**, which is why it is named for that rather than for the
 * folder it was written for: an ordered list of apps, paged, reorderable, opened over whatever was underneath. The
 * grid it sizes itself from is `FolderGrid`, whose own KDoc has always called itself the "folder / category-card
 * grid" — the vocabulary was mixed here long before the name was fixed.
 *
 * The apps are a **dense flow** chunked into pages (dots below), swipeable. Long-press to reorder within the flow
 * — a *moving gap*: the dragged app's slot travels through the ordered list and the flow densifies on drop (see
 * `AppCollectionReorder.kt`). The hover and drop belong to this overlay's **own zone**, registered below, so its
 * order and gap never have to be hoisted into the host. A tap launches ([onLaunch]); a release in here merely ends
 * the drag ([onRelease]), and what the *host* has to write — an app injected from the surface joining this
 * collection — reaches it through [onReorder], which is the only report that crosses the boundary at all.
 *
 * Leaving is a **continuous hand-off**: the drag started here carries on as the *same* session onto the surface, no
 * lift. Reorder is within the current page.
 *
 * **Two roles, selected by [presenting].** Normally an overlay *is* the open collection. But an app can be carried
 * out of one and into another in a single drag, and the cell driving that drag lives in the **first** one's grid —
 * an in-flight pointer stream can't be moved to another node, so that one has to stay composed or the gesture dies
 * (the toolkit's "keep a source surface composed while a drag from it is in flight" rule). So the host composes it
 * alongside the newly opened one with `presenting = false`: same overlay, reduced to a pointer holder — invisible,
 * no back handler, no drop zone, no proxy. See [AppCollectionHostState.dragSourceCollectionId].
 *
 * The flag is a *role*, not a one-way door: a drag that leaves the source collection and later comes back flips it
 * false → true again, and the overlay must resume completely (visible, zone re-registered, leave-dwell re-armed from
 * scratch). Anything latched for the duration of a drag rather than the duration of a *visit* breaks that, which is
 * why nothing here is.
 *
 * @param presenting true when this overlay is the collection the user is looking at and interacting with; false when
 *   it is composed only to keep an in-flight drag's pointer stream alive. A host must emit both from **one keyed call
 *   site** — a second call site is a different composition position, which disposes it and defeats the whole point.
 * @param onShowMenu a long-press on one of the contained apps, with that app's visible extent in root coordinates.
 *   The host decides what the menu offers, because what an app inside a collection can be asked to do is not the same
 *   as what one on a grid can — it has no placement, so there is nothing to remove it from.
 *
 * TODO(launcher frosted UI): replace the solid-black backdrop with the deferred blur/frosted backdrop.
 */
@Composable
fun AppCollectionOverlay(
    label: String,
    apps: List<AppInfo>,
    coordinator: DragCoordinator,
    gestureConfig: ItemGestureConfig,
    onLaunch: (ComponentKey) -> Unit,
    onReorder: (List<ComponentKey>) -> Unit,
    onLeave: () -> Unit,
    onRelease: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    incoming: AppInfo? = null,
    presenting: Boolean = true,
    onShowMenu: (AppInfo, Rect) -> Unit = { _, _ -> },
) {
    // Only the presented collection answers back; a pointer holder is invisible and must not intercept it.
    if (presenting) BackHandler(onBack = onDismiss)

    val device = currentDeviceConfiguration()
    val grid = remember(device) { FolderGrid.toGridConfig(device) }
    val pageSize = (grid.cols * grid.rows).coerceAtLeast(1)
    val titleStyle = MaterialTheme.typography.titleMedium

    // ── Reorder state ──
    // An app dragged in from home (not yet a member) is appended so the same MovingGap machinery positions it:
    // its cell is the dragged one → drawn invisible (the gap), the proxy floats, and the drop reports an order
    // that includes it (the caller adds it to the collection + removes it from home).
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
    // The inner grid's bounds in root space, kept as state so zone registration is driven by [presenting] rather than
    // by layout: the overlay is laid out once and then only changes alpha, so a re-presented collection would never get
    // another `onGloballyPositioned` to register itself from.
    var innerBounds by remember { mutableStateOf<Rect?>(null) }
    var gap by remember { mutableStateOf(-1) }

    val pageCount = rememberUpdatedState((orderComponents.size + pageSize - 1) / pageSize)
    val pagerState = rememberLauncherPagerState(pageCount = { pageCount.value.coerceAtLeast(1) }, infiniteScroll = { false })

    // The collection's drag hooks for the shared coordinator, kept stable and reading live state. onHover migrates
    // the reorder gap; commitReorder densifies and persists (optimistically first).
    val gridState = rememberUpdatedState(grid)
    val onReorderState = rememberUpdatedState(onReorder)
    val delegate = remember {
        object : AppCollectionDragDelegate {
            override fun onHover(item: GridItem, fingerInRoot: Offset): PlacementPlan? {
                val geo = geometry ?: return null
                val dragged = (item as? GridItem.App)?.component ?: return null
                val g = gridState.value
                val ps = (g.cols * g.rows).coerceAtLeast(1)
                // Off the grid → hold the current gap; on a cell → migrate the gap toward it. Two-zone: a collection
                // holds no collections, so a cell splits into halves with no center merge third to read.
                val cell = geo.cellAt(fingerInRoot) ?: return AppCollectionReorderPlan
                val flatSlot = flatSlotOf(cell.row, cell.col, g.cols, pagerState.currentPage, ps)
                gap = movingGap(liveOrder.value, dragged, gap, flatSlot, geo.cellFractionX(fingerInRoot) < 0.5f)
                return AppCollectionReorderPlan
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
    // The collection drop zone is owned by the *presented* overlay alone, registered and torn down with that role rather
    // than with composition. A pointer holder must not register (the coordinator would route drops into a collection
    // nobody is looking at) and must not unregister either: [CollectionZoneId] is one shared id, so an unguarded
    // teardown would pull the zone out from under whichever collection is actually on screen — which is exactly what
    // [RegisterDropZone]'s `enabled` gate expresses, with `presenting` as the extra condition on top of the
    // surface-level one it already applies.
    //
    // **The zone carries the collection's own hover and drop.** They used to be routed here by the host surface, whose
    // planner had a `zone.id != mine` branch handing over to the published delegate and whose drop had a matching
    // one; both were the same statement — *this zone's behavior is the collection's* — said in the surface instead of
    // in the zone. The delegate stays for what genuinely crosses the boundary: the host still needs `commitReorder`
    // to be reachable, because an app injected from outside commits through the host's own write path.
    val bounds = innerBounds
    RegisterDropZone(
        coordinator = coordinator,
        zone = bounds?.let {
            DropZone(
                id = CollectionZoneId,
                bounds = it,
                z = 1,
                planner = { item, finger -> delegate.onHover(item, finger) },
                accepts = { item -> item is GridItem.App },
                onDrop = { outcome -> delegate.commitReorder(outcome.item) },
            )
        },
        // Both conditions, and the surface-level one written out rather than left to the default: passing `enabled`
        // at all replaces that default, and a collection is only ever the user's business while the surface holding it
        // is the one on screen.
        enabled = presenting && LocalSurfacePresented.current,
    )
    // Whether the finger has been over the inner grid during **this visit** — see the arming note below. Reset when
    // the collection stops being presented, not when the drag ends: a single drag can leave and re-enter, and each entry
    // has to earn its own arming or the hold that opened the collection would immediately close it again.
    var enteredInnerZone by remember { mutableStateOf(false) }
    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) {
            gap = -1; enteredInnerZone = false
        }
    }
    LaunchedEffect(presenting) { if (!presenting) enteredInnerZone = false }

    val session = coordinator.session
    val draggedComponent = (session?.item as? GridItem.App)?.component

    val overInnerZone = presenting && session != null && session.activeZone == CollectionZoneId
    LaunchedEffect(overInnerZone) { if (overInnerZone) enteredInnerZone = true }

    // Dwell with the finger off the inner grid (over the outer zone / home behind it) hands the drag back to the
    // surface: the caller closes this collection, the coordinator targets home again, and the same drag continues —
    // onto a cell, into the next collection, or back into this one. Nothing is written here; the drop decides.
    //
    // **Armed only after the finger has been inside.** A collection can *open mid-drag* to receive an app (the inject
    // dwell), and when it does the finger is wherever the collection's cell happened to be — for a collection near a screen
    // edge, that is already over this overlay's outer zone. Leaving from there would eject the app the instant it
    // arrived: the user held still to put it *in*, and the same held finger would immediately take it back out. So a
    // drag becomes ejectable only once it has been over the inner grid, which is the user showing they are done
    // arriving and are now choosing to leave.
    //
    // For a collection that opens under the middle of the screen the finger is already inside, so it arms at once and
    // dragging out ejects as expected. And a drag that *starts* in the collection (reordering a member) begins on a cell
    // of the inner grid, so it is armed from the first frame — one rule covers all three cases, which is why this
    // replaced a narrower "never extract the incoming app" guard that also made case 2 impossible.
    val overOuterZone = presenting && session != null && !overInnerZone
    LaunchedEffect(overOuterZone, enteredInnerZone) {
        if (!overOuterZone || !enteredInnerZone) return@LaunchedEffect
        delay(LeaveDwellMs.milliseconds)
        if (coordinator.session == null) return@LaunchedEffect // released while we waited
        onLeave()
    }

    val displayApps = movingGapDisplayOrder(effectiveOrder, draggedComponent, gap).mapNotNull(appByComponent::get)
    val pages = displayApps.chunked(pageSize)

    val scrimInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }

    // **How present this overlay is, `0f..1f`** — one driver, two nodes, animated rather than flipped.
    //
    // A pointer holder is faded to nothing but kept composed, so the dragged cell keeps its pointer stream (the
    // proven "closing surface fades but stays in the tree" rule). What changed is that the flip is now a *fade*: the
    // frost arrives and leaves over a few frames instead of appearing whole, which is the same motion the shell gives
    // a side surface. Nothing about the hand-off depends on the timing — dismiss-on-tap is gated on `presenting`
    // itself, not on the alpha, and the drop shadow and proxy below flip instantly because they belong to the drag
    // rather than to the presentation.
    // **An `Animatable` seeded at zero, not `animateFloatAsState`** — and the difference is the whole feature. That
    // helper initializes to its *target*, so an overlay composed with `presenting = true` would snap in at full
    // strength and only ever animate on a later change: a collection would fade out but never fade in. Starting at zero
    // and animating toward the flag gives the entrance as well, and costs a pointer holder nothing (composed at
    // `false`, it starts at zero and stays there).
    //
    // Read inside `graphicsLayer`'s lambda below, so a frame of the fade re-draws without recomposing the card.
    val presence = remember { Animatable(0f) }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val presenceSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(presenting) { presence.animateTo(if (presenting) 1f else 0f, presenceSpec) }

    // **An open collection claims the surface swipe.** A swipe inside a collection is its pager's or nothing; panning to
    // another surface out from under an overlay leaves the user somewhere they did not ask to be, with the collection
    // still on screen. Claimed here rather than by each host, so home, the APPS pager and the category card's
    // expansion are covered at once — `presenting` is exactly the right window, since a pointer holder is invisible
    // and owns no gestures of its own.
    LockSurfaceGesture(presenting)

    // Root spans the screen; the floating proxy is a sibling of the content so its root-space offset isn't
    // shifted by the content's inset.
    Box(modifier.fillMaxSize()) {
        // **The frost, as its own node under the content** — the same `SurfaceBackdropLayer` the shell puts under a
        // side surface, and here for the same two reasons. It fixes a real fault: this used to call
        // `wallpaperBackdrop` directly, which meant liquid glass tried to draw its *refraction rim* across the whole
        // screen, where the band falls under the system bars and costs a shader for almost nothing. The layer passes
        // `refracts = false`, so glass renders here as its blur plus its saturation — see `SurfaceBackdropLayer`.
        //
        // And it separates the two motions. They move together today, but the frost and the card are no longer one
        // node, so giving the card its own entrance later needs no restructuring.
        //
        // `Color.Black` is the scrim rather than the theme's background, unlike the shell's: this overlay draws its
        // title and labels in white by construction, so black is what they are legible against on a device the
        // launcher has never been given a wallpaper for. That is exactly what this drew before the backdrop existed.
        SurfaceBackdropLayer(alpha = presence::value, scrimColor = Color.Black)

        // The card and everything in it. Structurally stable across the presenting flip — only `alpha` and the
        // clickable's `enabled` change — because the cells inside own live pointer streams.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = presence.value }
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    enabled = presenting,
                    onClick = onDismiss,
                ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(uiInsets),
                contentAlignment = Alignment.Center,
            ) {
                val innerSize: DpSize = appCollectionInnerSize(DpSize(maxWidth, maxHeight), device, grid, metrics)
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
                        modifier = Modifier
                            .size(innerSize)
                            // The outline is always in the chain and switches *color*, so this chain stays
                            // structurally stable across the drag flip — the same rule the backdrop above follows,
                            // and the cells inside here own live pointer streams. Note `border(0.dp, …)` would not be
                            // the off switch it looks like: 0.dp *is* Dp.Hairline, which still draws a 1px line.
                            .border(InnerZoneOutline, if (session != null) Color.White else Color.Transparent)
                            // Consumes a tap on the card's background so it doesn't reach the scrim and dismiss —
                            // and **gated on `presenting` for the same reason the scrim's is**. A pointer holder is
                            // invisible but still laid out over the middle of the screen, and Compose stops
                            // hit-testing at the topmost sibling it hits: an always-enabled clickable up here takes
                            // every press aimed at the surface beneath, so items behind an invisible collection could
                            // neither be launched nor lifted. The cells inside are deliberately *not* gated — one of
                            // them owns the in-flight pointer stream this overlay is being kept alive for.
                            .clickable(
                                interactionSource = innerInteraction,
                                indication = null,
                                enabled = presenting,
                                onClick = {},
                            ),
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
                                    // Measuring only; whether these bounds are a live drop zone is the presented
                                    // role's business, decided by the effect above.
                                    innerBounds = b
                                },
                        ) { pageIndex ->
                            LauncherGrid(
                                config = grid,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // The drop shadow for the slot the gap has opened, behind the cells (declared
                                // first) and inside the page grid so it travels with the page. `gap` is an index
                                // into the *display* order, which is the flat list these pages were chunked from —
                                // so it names a page as well as a cell, and only the page holding it paints.
                                if (presenting && session != null && gap >= 0 && gap / pageSize == pageIndex) {
                                    val slot = gap % pageSize
                                    Box(
                                        Modifier.gridPlacement(
                                            GridPlacement(0, slot / grid.cols, slot % grid.cols),
                                        ),
                                    ) {
                                        DropFootprint(DropIntent.REORDER, Modifier.fillMaxSize())
                                    }
                                }
                                flowItems(
                                    items = pages.getOrNull(pageIndex).orEmpty(),
                                    itemKey = { it.componentKey.flatten() },
                                ) { app, cellModifier ->
                                    LauncherDragCell(
                                        coordinator = coordinator,
                                        item = GridItem.App(app.componentKey),
                                        gestureConfig = gestureConfig,
                                        onRelease = onRelease,
                                        modifier = cellModifier,
                                        onOpen = { onLaunch(app.componentKey) },
                                        onShowMenu = { anchor -> onShowMenu(app, anchor) },
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
                    Box(
                        modifier = Modifier.height(CollectionDotsHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pages.size > 1) PageDots(count = pages.size, current = pagerState.currentPage)
                    }
                }
            }
        }

        // Floating proxy following the finger during a reorder drag (root space, above the content).
        //
        // Resolved once per dragged component and *held*, deliberately not re-derived from [appByComponent]: an app
        // carried in from the surface is only in that map while the host still calls it `incoming`, and committing the
        // drop ends exactly that — so re-deriving would lose the app mid-gesture and the icon under the finger would
        // blink out. Same resolve-once-and-hold reasoning as the caller's own `incoming` lookup.
        // A pointer holder draws nothing: whoever is presenting owns the proxy (the surface, once the drag has left
        // every collection), so two overlays drawing one would put two icons under a single finger.
        val geo = geometry
        val dragApp = remember(draggedComponent) { draggedComponent?.let(appByComponent::get) }
        if (presenting && session != null && geo != null && dragApp != null) {
            val finger = session.fingerInRoot
            FloatingDragIcon(
                rootOffset = IntOffset(
                    (finger.x - geo.cellW / 2f).roundToInt(),
                    (finger.y - geo.cellH / 2f).roundToInt(),
                ),
                size = DpSize(
                    with(LocalDensity.current) { geo.cellW.toDp() },
                    with(LocalDensity.current) { geo.cellH.toDp() }),
            ) {
                // No `itemGestures`: the proxy is a rendering that follows the finger, not a touch target.
                AppCell(app = dragApp, modifier = Modifier.fillMaxSize(), metrics = metrics)
            }
        }
    }
}

/** A row of small dots marking the collection's pages, the [current] one filled. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(DotSpacing)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(if (index == current) Color.White else Color.White.copy(alpha = 0.3f)),
            )
        }
    }
}
