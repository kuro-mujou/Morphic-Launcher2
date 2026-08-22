package inkspire.morphic.feature.apps.layout.categorycard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.collection.AppCollectionOverlay
import inkspire.morphic.core.designsystem.collection.AppCollectionPhase
import inkspire.morphic.core.designsystem.collection.rememberAppCollectionHostState
import inkspire.morphic.core.designsystem.drag.DragAutoScrollEffect
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.feature.apps.AppsCategory
import inkspire.morphic.feature.apps.layout.rememberAppsGestureConfig
import inkspire.morphic.feature.apps.layout.rememberAppsItemMenu
import kotlin.math.roundToInt

/** This surface's drop zone — the whole card grid, as each paged surface registers its viewport. */
private val CardGridZoneId = ZoneId("apps-category-cards")

/**
 * The plan reported for a hover over any card: **merge**, because that is what dropping on a card does — it folds the
 * app into that collection, exactly as a folder's center ring does.
 *
 * The footprint is a token, unread: which card is being aimed at lives in this surface's own state
 * ([AppsCategoryCard]'s `hoveredCategoryId`), because a card is not a cell in a lattice anyone else could name. The
 * *intent* is the part that has to be true, since the drop shadow and the drag host both branch on it.
 */
private val CardMergePlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.MERGE)

/**
 * The **category card** layout of the APPS surface
 * ([inkspire.morphic.core.model.AppsLayout.CATEGORY_CARD]): a scrolling grid of one square card per category, each
 * previewing a few of its apps, tapped open to reach the rest — and **dragged between to re-file**. The iOS
 * App-Library shape, and the last of the five APPS layouts.
 *
 * **It shares the category pager's store** (`category` + `category_item`, via `AppsOrderRepository`) — the same
 * arrangement, drawn as cards instead of pages. So there is nothing to seed, classify or migrate here: a user who
 * switches between the two layouts sees the same categories in the same order with the same apps in them.
 *
 * **No folders live on a card, and none ever will.** There are two independent reasons and either would be enough:
 * a category *is* the grouping (the category pager's reason), and a card is *already* a folder in everything but name
 * — a titled tile previewing a collection, which opens into a bounded grid — so a folder on a card would nest a
 * grouping inside a grouping that looks identical to it, with a 2×2 preview tile to draw inside a 2×2 preview slot at
 * roughly 20dp a side. `category_item` therefore stays keyed on `component`; no reshape, no migration.
 *
 * **The expansion is an [AppCollectionOverlay]** — not a lookalike. That type's parameters are already a label and a
 * list of apps with no folder id anywhere in them, because what it renders is *an ordered collection of apps opened
 * over a surface*, which is exactly what an expanded category is; even the grid it sizes itself from is
 * [inkspire.morphic.core.model.FolderGrid], whose KDoc has always called itself the "folder / category-card grid".
 * Reusing it brings the paging, the dots, the MovingGap reorder, the scrim and the whole leave/enter dwell for free.
 *
 * ## Dragging between categories — the folder↔home gesture, on cards
 *
 * The lifecycle is the same `AppCollectionHostState` home and the APPS pager run on, so every rule below is that
 * machine's rather than this file's: **tap a card to open it; hold a dragged app over another card (~1s) and it expands
 * mid-drag so the app lands at a *chosen* slot; hold outside an open expansion (~1s) and it closes with the drag
 * carrying on over the cards beneath.** Both halves are repeatable, in any order, over any number of categories,
 * including re-entering one already visited — because neither half writes anything. Membership is decided **only at
 * the drop**. A drop straight onto a card needs no dwell at all and appends.
 *
 * Three things differ from home, and all three are properties of *this* surface rather than choices:
 * - **A drag starts inside an expansion, or on a card's own preview icons.** The second half reverses this file's
 *   first cut, which kept previews tap-only on the grounds that making them draggable "would pin the source card in
 *   composition — which a lazy grid cannot honor". That was true, and the answer was to stop being lazy (see the
 *   grid below) rather than to withhold the gesture: a preview icon is the only part of a card that names one app, so
 *   it is the only place a re-file can start without opening the category first. The header and the overflow cluster
 *   still open; the empty slots and padding still stay free.
 * - **There is no "empty cell" landing.** Off a card there is nowhere for an app to be, so the planner reports no plan
 *   at all and a release there is a cancel. That is why the only intent this surface ever reports is
 *   [DropIntent.MERGE]. It also means a *held* finger almost always has a target: where home's post-leave dwell only
 *   catches the small center ring of a cell that happens to hold a folder, here nearly every point is a card, so
 *   holding still after leaving an expansion will open whatever is beneath. That is the shared machine's rule, not a
 *   local one — moving the finger to another card restarts the dwell, and moving off the cards cancels it.
 * - **Landing owes nothing to the category the app came from.** `AppsCategoryChange.Move` unfiles the app from every
 *   other category as part of filing it, so one op is the whole re-file — where the pager has to pair a
 *   `RemoveFromFolder` with a `Move` and commit both in one batch. Dropping an app back on the category it was lifted
 *   from is a **no-op** for the same reason home's is: it is still filed there and nothing was written on the way out.
 *
 * **Dragging out to HOME** works as it does on the other APPS layouts: carry the app into the eject band at the top
 * (`TopActionZone`, registered by `feature:shell`) and the surface closes with the drag still live, so it lands on
 * home's grid. The app keeps its category — being on home and being filed here are independent.
 *
 * **Releasing outside an open expansion cancels** (it closes, nothing is written). Leaving is a deliberate dwell, so a
 * release out there reads as "never mind" — and the drag has no landing to honor anyway.
 *
 * **Two tap targets per card and no overlap between them**, which is the "an item's touch target is its visible
 * extent" rule applied to a container: the preview icons launch, and the **header row** plus the **overflow cluster**
 * open the category. Nothing else on the card responds — the empty slots and the padding stay free. That the header
 * opens the card is not a fallback for the cluster: a category holding four apps or fewer has no cluster at all, and
 * without a header target it could never be opened, reordered, or dropped into. A card itself is [CategoryCard],
 * beside this file; what stays here is the drag state, the planner that writes it, and the drop that reads it.
 *
 * @param onMove commits a re-file: the app, the category it landed in, and its slot within that category. Also how an
 *   app carried into an expansion commits, since placing it at a chosen slot *is* a move.
 * @param onReorder commits a reorder inside an expansion, when the app was already filed there: the category, and the
 *   order its overlay reported. That report covers only the apps the cache could resolve, and is reconciled against
 *   real membership **in the store** rather than here — see `AppsCategoryChange.Reorder`.
 * @param slotMetrics the icon sizing of one **preview slot** on a card (`GridSlot.APPS_CARD`), which is a different
 *   grid from the expansion below and so a different setting. A slot is sized to be an icon, and the blueprint turns
 *   labels off, so what a user changes here is how large the four thumbnails are and where the lane ceiling sits.
 * @param chrome the resolved [CardChrome] every card on this grid is drawn with.
 * @param metrics an *expansion's* icon sizing (`GridSlot.FOLDER`, since an expansion is that same overlay and grid).
 *   The card previews are the exception and pass their own at each call site, because a preview icon is derived from
 *   the card's square rather than configured.
 * @param cardColumns how many lanes of cards across, resolved from `GridSlot.APPS_CARD`'s blueprint and the user's
 *   overrides. **The one dimension this layout has**, and it decides everything else: a card is square, so the lane
 *   count *is* the card size, and the preview icons inside are derived from that square.
 */
@Composable
fun AppsCategoryCard(
    categories: List<AppsCategory>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (app: ComponentKey, toCategory: String, toSlot: Int) -> Unit,
    onReorder: (category: String, order: List<ComponentKey>) -> Unit,
    metrics: IconMetrics,
    slotMetrics: IconMetrics,
    chrome: CardChrome,
    cardColumns: Int,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gestureConfig = rememberAppsGestureConfig()
    // One menu handler for every app on this surface — see `rememberAppsItemMenu`.
    val showItemMenu = rememberAppsItemMenu()

    val scrollState = rememberScrollState()
    // **Where the card grid is scrolled, for the surface swipe** — the vertical grid's report, over cards rather
    // than apps. An open expansion does not change it: the expansion is an `AppCollectionOverlay` with its own paging, and
    // while it is up the surface swipe is locked out entirely by `SurfaceGestureLock`.
    ReportScrollEdges {
        ScrollEdges(atTop = !scrollState.canScrollBackward, atBottom = !scrollState.canScrollForward)
    }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    // Each card's rectangle, so a finger can be turned into a card. A per-card map rather than a grid geometry,
    // because there is no lattice here to compute from: cards are square and separated by spacing that belongs to
    // no cell.
    //
    // **Each entry remembers the scroll offset it was measured at**, and [cardAt] subtracts the difference. This is
    // the "a measured position is not trustworthy inside a scroller" rule, in the one form that works whether or not
    // `onGloballyPositioned` re-fires as the content moves: if it does, the recorded offset is the current one and
    // the correction is zero; if it does not, the correction is exactly the scroll that happened since. The
    // alternative — republishing from a stable anchor — needs the card to know the viewport's origin, which only
    // this surface has.
    //
    // That rule has a **second half a scroller imposes, and it is the one that actually bit**: the rectangle a card
    // reports has to be *unclipped*, or a card below the fold reports nothing and silently refuses every drop. See
    // the `onGloballyPositioned` in [CategoryCard]. Nothing here needs to clip it back — the grid's own drop zone is
    // the viewport, so a finger can only ever be inside one card that is actually on screen.
    val cardBounds = remember { mutableStateMapOf<String, MeasuredCard>() }
    fun cardAt(finger: Offset): String? {
        val scrolled = scrollState.value
        return cardBounds.entries
            .firstOrNull { (_, card) -> card.boundsAt(scrolled).contains(finger) }
            ?.key
    }

    // The card under the finger, resolved by the planner and read back by the drop, the dwell and the drop shadow —
    // never re-derived from the plan's footprint, which is a token here. Safe because the planner runs on the very
    // move that produced the plan those readers are looking at, so it is always the current answer.
    var hoveredCategoryId by remember { mutableStateOf<String?>(null) }

    val planner = remember {
        DropPlanner { _, fingerInRoot ->
            val card = cardAt(fingerInRoot)
            hoveredCategoryId = card
            // No card under the finger means no landing exists, not "land where you are": every app is filed in some
            // category, so the gap between cards is not a place. A null plan makes the release a no-op.
            if (card == null) null else CardMergePlan
        }
    }
    // The launcher's one coordinator (`feature:shell`'s), which is what lets an app lifted from a card or an
    // expansion be carried out through the eject band and dropped onto home.
    val coordinator = requireDragCoordinator()
    val presented = LocalSurfacePresented.current
    val session = coordinator.session

    LaunchedEffect(coordinator.isDragging) { if (!coordinator.isDragging) hoveredCategoryId = null }

    // Category hosting, on the same lifecycle home uses for folders — keyed by `String` here, which is the whole of
    // what the generic id on `AppCollectionHostState` buys: the open/leave/enter machine is identical, so this surface gets
    // it rather than a near-copy of it. The one thing the host can't know is which collection a merge plan targets;
    // home compares placements, the pager compares slots, and a card grid compares card bounds (above).
    val categoryHost = rememberAppCollectionHostState<String>(coordinator) { zoneId, _ ->
        if (zoneId == CardGridZoneId) hoveredCategoryId else null
    }
    val openCategoryId = categoryHost.openCollectionId
    val expanded = categories.firstOrNull { it.category.id == openCategoryId }

    // A category can stop existing underneath an open expansion (a rebalance drops the ids it no longer defines, and
    // `dropUnknownCategories` unfiles their apps), which would otherwise leave an overlay with nothing to render and
    // an id that reappears if it is ever re-created.
    LaunchedEffect(categories, openCategoryId) {
        if (openCategoryId != null && categories.none { it.category.id == openCategoryId }) categoryHost.close()
    }
    // Tell the host the presented category's persisted contents, so it knows a just-committed app has landed and can
    // stop handing it over as `incoming`.
    val openMembers = expanded?.apps?.map { it.componentKey }
    LaunchedEffect(openMembers) { categoryHost.onMembersChanged(openMembers.orEmpty()) }

    // What a landing **on a card** means: file the app there, at the end. The zone's handler rather than the
    // releasing cell's, so it runs whether the app was lifted from an expansion, from a card's own preview, or —
    // once the reverse direction exists — from anywhere else on the same coordinator.
    fun commitLanding(outcome: DropOutcome) {
        // Released while an expansion is on screen → "never mind": close it and write nothing. Leaving is a
        // deliberate dwell, and a drop out here was aimed at a card the scrim is covering.
        if (categoryHost.openCollectionId != null) {
            categoryHost.close()
            return
        }
        val target = hoveredCategoryId ?: return
        val app = (outcome.item as? GridItem.App)?.component ?: return
        // Back on the category it came from is a no-op — it is still filed there and nothing was written on the way
        // out. The source is the expansion the drag started in when there was one, and otherwise simply wherever the
        // app is filed: a drag lifted from a card's preview icon opens nothing, so the host has no answer for it.
        val sourceCategoryId = categoryHost.dragSourceCollectionId ?: categoryOf(categories, app)
        if (target == sourceCategoryId) return
        // The slot is the target's *resolved* size, which can be short of its true membership by however many of its
        // apps the cache couldn't resolve. `moveCategoryItem` coerces, so the app lands at the end of what the user
        // can actually see — which is what "dropped on the card, no position chosen" should mean.
        onMove(app, target, categories.firstOrNull { it.category.id == target }?.apps?.size ?: 0)
    }

    // A cell of this surface released the finger — that only ends the drag; the landing belongs to whichever zone it
    // fell in. What is left is the source-side bookkeeping: an expansion on screen with nothing landed under it.
    fun handleRelease() {
        val presentedId = categoryHost.openCollectionId
        val outcome = coordinator.drop()
        if (presentedId != null && outcome == null) categoryHost.close()
    }

    // The card grid's own drop zone — the whole scroller. Registered from state rather than from the layout callback
    // because it also depends on this surface being on screen; see [RegisterDropZone].
    RegisterDropZone(
        coordinator = coordinator,
        zone = gridBounds?.let {
            DropZone(
                id = CardGridZoneId,
                bounds = it,
                z = 0,
                planner = planner,
                accepts = { item -> item is GridItem.App },
                onDrop = ::commitLanding,
            )
        },
    )

    // The app being carried into the open expansion, resolved once and *held*. Keyed on the component alone,
    // deliberately not on `categories`: it is still filed in the category it came from until the write lands, and the
    // commit re-files it — so re-deriving afterwards would lose it mid-hand-off and the icon would blink out.
    val incomingApp = remember(categoryHost.incomingComponent) {
        categoryHost.incomingComponent?.let { component -> appInCategories(categories, component) }
    }
    val draggedComponent = (session?.item as? GridItem.App)?.component
    val draggedApp = remember(draggedComponent) { draggedComponent?.let { appInCategories(categories, it) } }

    // Which card to shadow. Gated on the finger actually being over the card grid — `hoveredCategoryId` is only
    // rewritten while the planner is running, so a finger carried off every zone would otherwise leave the last card
    // highlighted — and on no expansion being presented, since a shadow behind the scrim promises nothing.
    val shadowedCategoryId = hoveredCategoryId
        ?.takeIf { session?.activeZone == CardGridZoneId && openCategoryId == null }

    // Reaching a card past the fold: the grid's own scroll gesture is off for the duration of a drag (two vertical
    // gestures would fight over one finger), so holding the app near the top or bottom edge scrolls it instead. Off
    // while an expansion is presented — the grid is behind the scrim, and that dwell means "leave", not "scroll".
    DragAutoScrollEffect(
        scrollState = scrollState,
        bounds = gridBounds,
        fingerInRoot = session
            ?.takeIf { it.activeZone == CardGridZoneId && openCategoryId == null }
            ?.fingerInRoot,
    )

    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        Box(modifier.fillMaxSize()) {
            // **Not lazy**, and this reverses the call the first cut made. It was a `LazyVerticalGrid` on the
            // vertical grid's argument: a card composes up to seven baked icons, so the *card* count is small but
            // the icon count is not. What paid for that was the note beside it — "it costs nothing *because* no drag
            // starts on this grid". A drag does start on it now (a card's preview icons are draggable), and the
            // lifted cell owns the pointer stream driving it, so a card disposed while auto-scrolling toward the
            // target would kill the gesture mid-flight. `HomeListSurface` made the same trade for the same reason.
            //
            // The `onGloballyPositioned` sits **outside** `verticalScroll`, so the rectangle it reports is the
            // viewport's and stays still while the content moves under it — the drop zone and the auto-scroll band
            // both want exactly that.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(uiInsets)
                    .onGloballyPositioned { gridBounds = it.boundsInRoot() }
                    .verticalScroll(scrollState, enabled = !coordinator.isDragging)
                    // The grid's own margin adds to the card gutter rather than replacing it: `CategoryCardGutter` is the
                    // gap between a card and the screen edge that the *tile* needs to read as a tile, and the
                    // setting is the user's inset on top. Inside the scroller, so cards still travel under the bars.
                    .padding(
                        start = 16.dp + horizontalPadding,
                        top = 16.dp,
                        end = 16.dp + horizontalPadding,
                        bottom = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // TODO(category management): long-press a card for rename / delete, and drag one to reorder the
                //  categories. Both write category *definitions*, which `AppsCategoryChange` deliberately has no ops
                //  for — the rewrite plan puts that editor in `feature:settings`, and its op set should be shaped by
                //  the screen that uses it rather than invented here.
                //
                // Rows of `cardColumns`, padded out with weighted spacers so a short last row leaves its cards the
                // same width as every other row's rather than stretching them.
                categories.chunked(cardColumns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { entry ->
                            key(entry.category.id) {
                                val id = entry.category.id
                                CategoryCard(
                                    category = entry,
                                    coordinator = coordinator,
                                    gestureConfig = gestureConfig,
                                    shadowed = id == shadowedCategoryId,
                                    chrome = chrome,
                                    metrics = slotMetrics,
                                    onLaunch = onLaunch,
                                    showItemMenu = showItemMenu,
                                    onExpand = { categoryHost.open(id) },
                                    onRelease = ::handleRelease,
                                    onBounds = { bounds ->
                                        if (bounds == null) {
                                            cardBounds.remove(id)
                                        } else {
                                            cardBounds[id] = MeasuredCard(bounds, scrollState.value)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(cardColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // The floating proxy, drawn by whichever surface is presenting the drag: while an expansion is on screen
            // that is the expansion (at its own cell size), so exactly one of the two paints.
            // Gated on this being the surface on screen as well: the coordinator is the launcher's, so a drag
            // ejected onto home is still live here and would otherwise paint a second icon under the same finger.
            if (presented && session != null && draggedApp != null && openCategoryId == null) {
                val finger = session.fingerInRoot
                val halfPx = with(density) { 72.dp.toPx() } / 2f
                FloatingDragIcon(
                    rootOffset = IntOffset((finger.x - halfPx).roundToInt(), (finger.y - halfPx).roundToInt()),
                    size = DpSize(72.dp, 72.dp),
                ) {
                    // No `itemGestures`: the proxy follows the finger, it is not a touch target.
                    AppCell(app = draggedApp, modifier = Modifier.fillMaxSize())
                }
            }

            // The expansions, above the cards. Usually one — but a drag that started inside a category keeps *that*
            // expansion composed for its whole life even once another is on screen, because the cell driving the drag
            // is in its grid and an in-flight pointer stream cannot be handed to another node. That one is the
            // **pointer holder** (`presenting = false`): invisible, zone-less, no delegate, no proxy.
            //
            // Holder first so it sits below the presented one, and both from this **one** keyed call site: an
            // expansion moving between the two roles has to keep its composition, and a second call site is a
            // different composition position, which disposes it and kills the drag it exists to preserve.
            val holder = categoryHost.dragSourceCollectionId
                ?.takeIf { it != openCategoryId }
                ?.let { id -> categories.firstOrNull { it.category.id == id } }
            val overlays = listOfNotNull(holder?.let { it to false }, expanded?.let { it to true })
            overlays.forEach { (entry, presenting) ->
                key(entry.category.id) {
                    AppCollectionOverlay(
                        label = entry.category.name,
                        apps = entry.apps,
                        coordinator = coordinator,
                        gestureConfig = gestureConfig,
                        incoming = if (presenting) incomingApp else null,
                        presenting = presenting,
                        onLaunch = { component -> onLaunch(component); categoryHost.close() },
                        onReorder = { order ->
                            // An app still *arriving* is placed with a `Move` — one op that both files it here and
                            // unfiles it from wherever it was. Once committed (or if it was already a member) this is
                            // a plain re-sequence, which is the one thing `Move` cannot express for a whole list.
                            val incoming = (categoryHost.phase as? AppCollectionPhase.Injecting<*>)?.app
                            if (incoming != null && incoming in order) {
                                onMove(incoming, entry.category.id, order.indexOf(incoming))
                                categoryHost.injectCommitted()
                            } else {
                                onReorder(entry.category.id, order)
                            }
                        },
                        onLeave = categoryHost::leaveCollection,
                        onRelease = ::handleRelease,
                        onDismiss = { categoryHost.close() },
                        onShowMenu = showItemMenu,
                    )
                }
            }
        }
    }
}

/**
 * The app for [component] wherever it is filed, for the floating proxy and for an app arriving in an expansion.
 *
 * Searched across *every* category rather than the one being rendered, because a drag detaches an app from nothing
 * until it lands: an app on its way from one category to another is still filed in the first, and a lookup scoped to
 * the destination would find nothing and draw nothing.
 */
private fun appInCategories(categories: List<AppsCategory>, component: ComponentKey): AppInfo? =
    categories.firstNotNullOfOrNull { entry -> entry.apps.firstOrNull { it.componentKey == component } }

/**
 * The id of the category [component] is currently filed under, or null if the cache resolves it nowhere.
 *
 * The fallback source of a drag that did **not** start in an expansion — a card's own preview icon — so that
 * dropping such an app back on the card it came from is the same no-op that dropping it back into its expansion is.
 */
private fun categoryOf(categories: List<AppsCategory>, component: ComponentKey): String? =
    categories.firstOrNull { entry -> entry.apps.any { it.componentKey == component } }?.category?.id

/**
 * A card's measured rectangle together with the scroll offset it was measured at.
 *
 * The pair exists because [Rect] alone stops being true the moment the content scrolls: `onGloballyPositioned` does
 * not reliably re-fire when a scroller moves a node through its parent's placement. Carrying the offset makes the
 * correction self-contained and costs nothing when the callback *does* re-fire — the difference is then zero.
 */
private data class MeasuredCard(val bounds: Rect, val scrollOffset: Int) {
    /** Where this card really is, given the scroller is now at [scrolled]. */
    fun boundsAt(scrolled: Int): Rect = bounds.translate(0f, (scrollOffset - scrolled).toFloat())
}
