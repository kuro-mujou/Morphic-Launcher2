package inkspire.morphic.feature.apps.layout.categorypager

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import inkspire.morphic.core.designsystem.drag.DropOutcome
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.grid.GridGeometry
import inkspire.morphic.core.designsystem.grid.derivedCell
import inkspire.morphic.core.designsystem.grid.fitCols
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.menu.MenuAction
import inkspire.morphic.core.designsystem.ordered.cellFractionX
import inkspire.morphic.core.designsystem.ordered.movingGap
import inkspire.morphic.core.designsystem.pager.EdgeFlipEffect
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.model.AppsCategoryGrid
import inkspire.morphic.core.model.Category
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.feature.apps.AppsCategory
import inkspire.morphic.feature.apps.layout.rememberAppsGestureConfig
import inkspire.morphic.feature.apps.layout.rememberAppsItemMenu
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** This surface's drop zone — the pager viewport, as on the other paged surfaces. */
private val CategoryZoneId = ZoneId("apps-category-pager")

/**
 * The **tab strip's** zone: a second target on this surface, disjoint from [CategoryZoneId] rather than stacked over
 * it, since the strip sits beside the pager — above it or below it — and takes its height out of the viewport.
 *
 * A zone of its own rather than a branch inside the pager's planner, because it answers a different question: the
 * pager's planner resolves a *slot* within the page under the finger, while this one resolves a *category* and files
 * at its end. Both come out as an `AppsCategoryChange.Move`, which is the point of that op.
 */
private val CategoryTabZoneId = ZoneId("apps-category-tabs")

/**
 * The plan this surface reports for every hover it accepts: droppable, painting the gap and nothing else.
 *
 * [DropIntent.REORDER], as on every ordered surface — the preview is the reflow, and the footprint is drawn from
 * the surface's own gap rather than from this plan, whose footprint goes unread.
 *
 * **The tab strip answers with it too**, because a tab is the same op with a different destination: filing an app at
 * the end of another category's list is the `Move` a drop between two icons on that category's page is. Its preview
 * is the tab lighting up rather than a footprint, drawn from this surface's hover state — so the footprint goes
 * unread there as well.
 */
private val CategoryReorderPlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)

/**
 * A tab drop waiting for its write to come back: the order it asked for, and the category to keep on screen when it
 * arrives.
 *
 * Both halves are needed. [keep] is the answer, and [ids] is how the surface knows the answer is safe to use — the
 * emission carrying that exact order is the one where the category has moved.
 *
 * @property keep null when the pager was resting on nothing (no categories yet), which simply means there is nothing
 *   to hold in view.
 */
private class PendingReorder(val ids: List<String>, val keep: String?)

/**
 * The **category pager** layout of the APPS surface
 * ([inkspire.morphic.core.model.AppsLayout.PAGER_WITH_CATEGORY]): one page per category, each page a
 * vertically-scrolling grid of the apps filed under it, rearranged by dragging.
 *
 * **A page is a category, so dragging across pages re-files the app.** Reordering within a page and moving to
 * another are one operation with a different destination — which is why the store needs a single `Move` op and this
 * file needs no separate "change category" path. Carrying an app to the next page *is* changing its category.
 *
 * **No folders live here**, so cells split into **halves** (insert before / after) with no center merge ring. A
 * category is already the grouping; a folder inside one would be a second, redundant one, and with nothing to merge
 * into a center third would be dead space where the user's aim does nothing. That is the one structural difference
 * from the APPS pager's drag — this is that, minus the merge branch and minus the page-capacity cascade.
 *
 * **SCROLL_GRID, unlike `AppsVerticalGrid`** — which renders every installed app and therefore uses a
 * `LazyVerticalGrid`. A category page holds a bounded subset (tens, not hundreds), which is exactly the case
 * `LauncherGrid`'s scroll mode names as its own. Same "right tool per surface" rule, opposite answer, because the
 * input size is different.
 *
 * **Because the pages scroll, geometry comes from each page's grid, not from the viewport.** The other paged
 * surfaces can publish one viewport geometry because a page *is* the viewport there; here the grid slides under it,
 * so a finger→cell read against viewport bounds would name whatever cell used to be at that height. Each page
 * reports its own grid bounds (which move with the scroll) and the planner reads the current page's.
 *
 * **Scrolling is gated off while an item is in flight**, like page-swipe — two vertical gestures competing for one
 * finger otherwise. Content past the fold is reached instead by holding the dragged app near the top or bottom
 * edge, which scrolls it (`DragAutoScrollEffect`): the vertical counterpart of the edge dwell that flips pages.
 *
 * Empty categories still get a page: a category the user emptied keeps its definition, and a page that vanished
 * with its last app could never be dragged back into.
 *
 * **The strip beside the pages is how a category is reached without walking to it** ([CategoryTabStrip]): one tab
 * per category, tapped or slid across, and a drop target of its own — dragging an app onto a tab files it under that
 * category. It sits on whichever edge [tabEdge] names. The two zones are disjoint: the strip takes its height out of
 * the pager, so the viewport this surface measures is what is left, and nothing overlaps.
 *
 * A page itself is [CategoryPage], beside this file — a leaf that takes everything it draws as a parameter. What
 * stays here is the drag state, the planner that writes it, and the drop that reads it.
 *
 * @param onMove commits a drop — the app, the category it landed in, and its slot within that category.
 * @param metrics a page's icon sizing, resolved from `GridSlot.APPS_CATEGORY`'s blueprint and the user's overrides.
 * @param cols how many columns a page is across, resolved from the same slot — passed rather than read here for the
 *   reason [metrics] is: the surface resolves every grid's configuration in one place.
 * @param onReorderCategories commits a dropped tab: the category ids in the order the strip now shows them.
 * @param onRenameCategory commits a rename from a tab's menu.
 * @param header drawn **outside the strip, on the strip's own edge** — the search field, when
 *   `SearchPlacement.InHeader` puts it here. Null draws nothing. A slot rather than a `SearchPlacement` parameter
 *   because this surface has no business knowing what the thing is: it knows only that the tabs share their edge
 *   with it, and that the order is field-then-tabs reading inward from the screen edge.
 * @param tabEdge which edge the tab strip sits on, from `AppsChrome.categoryTabEdge`. **Honored here and previewed in
 *   the APPS settings section**, which is the pair the shared-derivation rule is about: the editor's mockup draws a
 *   row on the edge this reads, so a surface ignoring it would make that control a lie.
 */
@Composable
fun AppsCategoryPager(
    categories: List<AppsCategory>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (app: ComponentKey, toCategory: String, toSlot: Int) -> Unit,
    onReorderCategories: (orderedIds: List<String>) -> Unit,
    onRenameCategory: (id: String, name: String) -> Unit,
    metrics: IconMetrics,
    cols: Int,
    horizontalPadding: Dp,
    wraps: Boolean,
    rememberPage: Boolean,
    tabEdge: VerticalEdge,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // The launcher's one menu host — null outside the launcher shell, which is what makes a tab's menu absent in a
    // preview rather than a second host quietly appearing.
    val menuHost = LocalMenuHost.current
    val gestureConfig = rememberAppsGestureConfig()
    // One menu handler for every app on this surface — see `rememberAppsItemMenu`.
    val showItemMenu = rememberAppsItemMenu()

    // Held in a state so the count lambda reads the current list: `rememberLauncherPagerState` remembers the lambda
    // once, so capturing the parameter would freeze the pager at however many categories existed on the first
    // composition — which is none, before the store's first emission.
    val liveCategories = rememberUpdatedState(categories)
    // Held live for the reason `liveCategories` is, stated above: the factory remembers its lambdas once.
    val liveWraps = rememberUpdatedState(wraps)
    val pagerState = rememberLauncherPagerState(
        pageCount = { liveCategories.value.size.coerceAtLeast(1) },
        infiniteScroll = { liveWraps.value },
        rememberPage = rememberPage,
    )

    // Which tab the strip marks. **Through `derivedStateOf`, which is the whole reason this is a val rather than a
    // read at the call site**: `currentPage` is computed from the pager's animatable, so a raw read in composition
    // subscribes this surface to every frame of a swipe, where the derivation only invalidates when the *page* it
    // rounds to changes.
    val selectedTab by remember { derivedStateOf { pagerState.currentPage } }

    // One geometry per page rather than one for the surface, because each page's grid scrolls independently — see
    // the class KDoc. Only the current page's is ever read, but every page reports, since `keepAllPagesPlaced` keeps
    // them all laid out during a drag.
    //
    // A page republishes this on **every scroll frame** (the origin moves — that is the whole point of publishing from
    // the grid rather than the viewport), so nothing may read it during composition: one composition-time read turns a
    // scroll into a recomposition per frame of this entire surface. The only reader is the planner, which runs inside a
    // pointer callback. That is a constraint on the *readers*, not on the map — snapshot writes invalidate nobody when
    // nobody is subscribed — which is why the proxy below is sized from `viewport` instead.
    val geometries = remember { mutableStateMapOf<Int, GridGeometry>() }
    var viewport by remember { mutableStateOf<Rect?>(null) }

    // Each page's scroller, so the report below can name the *current* page's. A plain map rather than snapshot
    // state, deliberately: its only reader is the report lambda, which runs from a pointer callback, so nothing
    // should ever be invalidated by a page re-publishing. Entries are never removed — the map is bounded by the
    // page count and an index past the end is never asked for.
    val pageScrolls = remember { mutableMapOf<Int, ScrollState>() }

    // **The only surface that owns both axes**, which is why the report is one value rather than one per scroller:
    // a second `ReportScrollEdges` in the same slot would replace this one instead of combining with it. Horizontal
    // is the pager's, vertical is whichever page is showing — and neither is read in composition, so a swipe across
    // this surface recomposes nothing.
    ReportScrollEdges {
        val page = pageScrolls[pagerState.currentPage]
        ScrollEdges(
            atLeft = pagerState.atFirstPage,
            atRight = pagerState.atLastPage,
            atTop = page?.canScrollBackward != true,
            atBottom = page?.canScrollForward != true,
        )
    }

    // Where the dragged app would land, and on which page. The page matters as much as the index here: it names the
    // *category* the drop writes, which is what makes a cross-page drag a re-file.
    var gap by remember { mutableIntStateOf(-1) }
    var gapPage by remember { mutableIntStateOf(-1) }

    // The tab strip's two halves of state: where it is (for the zone published over it) and which tab a dragged app
    // is over (for the highlight, and for what a release there files). -1 rather than null for the tab, matching
    // `gapPage` beside it — this surface's "no answer yet" is a negative index throughout.
    var stripBounds by remember { mutableStateOf<Rect?>(null) }
    var hoveredTab by remember { mutableIntStateOf(-1) }

    // The category whose tab menu asked to be renamed, or null. Held here rather than in the strip because the
    // dialog outlives the gesture that opened it — the finger is long gone by the time a name is typed.
    var renaming by remember { mutableStateOf<Category?>(null) }
    // A dropped tab, until its write comes back. A page is identified by its *index*, so re-sequencing the categories
    // leaves the pager showing whatever moved into the index it was resting on — which reads as the reorder having
    // also jumped the user somewhere else.
    var pendingReorder by remember { mutableStateOf<PendingReorder?>(null) }
    LaunchedEffect(categories, pendingReorder) {
        val pending = pendingReorder ?: return@LaunchedEffect
        // **Waits for the order it asked for, not merely for an emission.** Checking that the kept category is
        // somewhere in the list is not enough: it is in the *old* list too, at its old index, so the effect would
        // fire on the frame of the drop, snap to where the category already was, and be spent by the time the write
        // landed — leaving the pager on the index whose category had changed underneath it, which is exactly the
        // jump this exists to prevent.
        if (categories.map { it.category.id } != pending.ids) return@LaunchedEffect
        val index = categories.indexOfFirst { it.category.id == pending.keep }
        if (index >= 0) pagerState.snapToPage(index)
        pendingReorder = null
    }

    val planner = remember(cols) {
        DropPlanner { item, fingerInRoot, _ ->
            val page = pagerState.currentPage
            val geo = geometries[page] ?: return@DropPlanner null
            val stored = liveCategories.value.getOrNull(page)?.apps.orEmpty().map { GridItem.App(it.componentKey) }
            val others = stored.filterNot { it == item }
            val cell = geo.cellAt(fingerInRoot)
            if (cell == null) {
                // **Off the grid is an answer, not silence.** A page's grid is SCROLL_GRID, so it is only as tall as
                // its content — a category of three apps in a four-column grid is *one row* — and the rest of the
                // page is slack that is still squarely inside this zone. Treating that as unanswerable left most of
                // the screen dead for any short category: no gap, no drop shadow, and a release silently discarded,
                // because `commitLanding` needs a `gapPage` the planner never set while still reporting the drop as
                // droppable.
                //
                // **Which end the slack means depends on the alignment, so it is read off the geometry rather than
                // told**: a bottom-aligned page (the strip on the bottom) leaves its slack *above* the block, where a
                // top-aligned one leaves it below, and the answer is the nearer end of the list either way — the end
                // whose cell the footprint appears in is the one the finger is on the side of.
                //
                // Only on **arrival**, though. Once a cell on this page has answered, the slack holds that answer
                // instead of snapping to an end: the finger is between targets there, and a preview that jumped every
                // time it crossed the block's edge would strobe — which is what carrying an app up to the eject band
                // over a bottom-aligned page does on every drag.
                if (page != gapPage) {
                    gap = if (fingerInRoot.y < geo.originInRoot.y) 0 else others.size
                    gapPage = page
                }
                return@DropPlanner CategoryReorderPlan
            }
            val slot = cell.row * cols + cell.col
            gap = if (page != gapPage) {
                // Arriving on a page seeds the gap at the slot under the finger; `movingGap` refines it from there.
                // Seeding through `movingGap` would start at index 0, since the app isn't in this page's list.
                slot.coerceIn(0, others.size)
            } else {
                // Two zones: halves, not thirds. There is nothing to merge into on this surface.
                movingGap(others, item, gap, slot, geo.cellFractionX(fingerInRoot) < 0.5f)
            }
            gapPage = page
            CategoryReorderPlan
        }
    }
    // The launcher's one coordinator (`feature:shell`'s), which is what lets an app lifted on one of these pages be
    // carried out through the eject band and dropped onto home.
    val coordinator = requireDragCoordinator()
    val presented = LocalSurfacePresented.current
    val session = coordinator.session

    // What a hover over the **tab strip** resolves: a category, from the finger's x across equal-width tabs. The
    // strip's own scrub gesture divides that same width the same way (`tabIndexAt`), so the tab that lights up and
    // the tab a release files into cannot come out one apart.
    //
    // Reads `stripBounds` and the live category list out of state, which is safe for the reason the pager's planner
    // reading `geometries` is: a planner runs from a pointer callback, never from composition, so nothing here
    // subscribes to anything.
    val tabPlanner = remember {
        DropPlanner { _, fingerInRoot, _ ->
            val bounds = stripBounds ?: return@DropPlanner null
            hoveredTab = tabIndexAt(fingerInRoot.x - bounds.left, bounds.width, liveCategories.value.size)
            CategoryReorderPlan
        }
    }

    LaunchedEffect(coordinator.isDragging) {
        if (!coordinator.isDragging) {
            gap = -1; gapPage = -1; hoveredTab = -1
        }
    }

    EdgeFlipEffect(
        pagerState = pagerState,
        viewport = viewport,
        fingerInRoot = session?.takeIf { it.activeZone == CategoryZoneId }?.fingerInRoot,
    )

    // What a landing **on one of these pages** means: the page names the category, so a reorder and a re-file are
    // the same write. The zone's handler rather than the releasing cell's, so it runs whoever lifted the app — and
    // conversely, an app lifted here and ejected onto home is committed by *home's* zone and never reaches this.
    fun commitLanding(outcome: DropOutcome) {
        val app = (outcome.item as? GridItem.App)?.component ?: return
        // A gap of -1 means the finger never rested on a cell, so there is nothing to write.
        val category = gapPage.takeIf { it >= 0 }?.let { categories.getOrNull(it) } ?: return
        onMove(app, category.category.id, gap.coerceAtLeast(0))
    }

    /**
     * What a landing **on a tab** means: file the app at the end of that category, wherever it came from.
     *
     * The end rather than the front, and rather than a slot the strip would have to invent: a tab is one target for a
     * whole category, so there is no position in it for the finger to be pointing at. The store clamps the slot, so
     * a list that grew under the drag cannot put it out of range.
     */
    fun commitFiling(outcome: DropOutcome) {
        val app = (outcome.item as? GridItem.App)?.component ?: return
        val category = hoveredTab.takeIf { it >= 0 }?.let { categories.getOrNull(it) } ?: return
        onMove(app, category.category.id, category.apps.size)
    }

    // One drop zone, the whole viewport. Registered from state rather than from the layout callback, because being
    // registered also depends on this surface being on screen — see [RegisterDropZone].
    RegisterDropZone(
        coordinator = coordinator,
        zone = viewport?.let {
            DropZone(
                id = CategoryZoneId,
                bounds = it,
                z = 0,
                planner = planner,
                accepts = { item -> item is GridItem.App },
                onDrop = ::commitLanding,
            )
        },
    )

    // The strip's zone, published on the same terms and gated on the strip being drawn at all: with one category
    // there is no strip and nothing to file into that the app is not already in.
    RegisterDropZone(
        coordinator = coordinator,
        zone = stripBounds?.takeIf { categories.size > 1 }?.let {
            DropZone(
                id = CategoryTabZoneId,
                bounds = it,
                z = 0,
                planner = tabPlanner,
                accepts = { item -> item is GridItem.App },
                onDrop = ::commitFiling,
            )
        },
    )

    val draggedApp = session?.item?.let { item ->
        (item as? GridItem.App)?.component?.let { component ->
            categories.firstNotNullOfOrNull { it.apps.firstOrNull { app -> app.componentKey == component } }
        }
    }

    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        Box(modifier.fillMaxSize()) {
            // **The insets are the column's, the horizontal margin stays the pager's.** The strip has to be inset too
            // — it is the topmost thing on the surface, so a notch would sit on it — while the margin belongs to the
            // pager for the reason spelled out on it below: `viewport` has to be the box the cells are divided out
            // of. The strip is given the same margin so its first tab lines up with the first column beneath it.
            // What the strip's gestures commit. Assembled here rather than inside the strip because two of the four
            // are this surface's business rather than the strip's: the menu is the launcher's one menu host, and a
            // reorder has to keep the page you were looking at on screen (see `keepInView`).
            val tabActions = remember(categories, menuHost) {
                CategoryTabActions(
                    goToPage = { page -> scope.launch { pagerState.animateToPage(page) } },
                    showMenu = { category, anchor ->
                        menuHost?.show(
                            title = category.category.name,
                            anchor = anchor,
                            // One verb, because one is what the store can honor — see [CategoryRenameDialog].
                            actions = listOf(MenuAction("Rename") { renaming = category.category }),
                            // The menu is composed at the shell, so it cannot read `OnFilm` for itself.
                            overFrost = true,
                        )
                    },
                    dismissMenu = { menuHost?.dismiss() },
                    reorder = { ids ->
                        pendingReorder = PendingReorder(
                            ids = ids,
                            keep = categories.getOrNull(pagerState.settledPage)?.category?.id,
                        )
                        onReorderCategories(ids)
                    },
                )
            }

            // A slot rather than two call sites, because the strip is drawn above *or* below the pages and the two
            // placements differ in nothing else — a near-copy per edge is how they would drift apart.
            //
            // **Absent with one category, not merely inert**: there is nowhere else to go, and nothing to file into
            // that an app is not already in. See "absent, not disabled".
            val strip: @Composable () -> Unit = {
                if (categories.size > 1) {
                    CategoryTabStrip(
                        categories = categories,
                        selected = selectedTab,
                        // Lit only while the finger is actually over the strip. `hoveredTab` is written by the
                        // planner and the planner stops being asked the moment another zone wins the finger, so
                        // without the active-zone test the last tab touched would stay lit for the rest of the drag.
                        hovered = hoveredTab.takeIf { it >= 0 && session?.activeZone == CategoryTabZoneId },
                        gestures = gestureConfig,
                        // Paging off while an item is in flight, for the reason the page swipe and the page scroll
                        // are: the strip is a drop target then, and a second finger paging under a carried app would
                        // move the pages out from under the drop it is aiming at.
                        enabled = !coordinator.isDragging,
                        actions = tabActions,
                        onBounds = { stripBounds = it },
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(uiInsets),
            ) {
                if (tabEdge == VerticalEdge.TOP) {
                    header?.invoke()
                    strip()
                }
                LauncherPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // **One padding, above everything that measures.** The `viewport` read below becomes the padded
                        // box, and every other number on this surface is divided out of it — the drop zone, the proxy's
                        // cell width, and each page's own `maxWidth` (a page fills this box, so `fitCols` and
                        // `derivedCell` see the reduced width without being told). Applying it per page instead would
                        // leave the drop zone and the proxy describing a width the cells no longer have.
                        .padding(horizontal = horizontalPadding)
                        .launcherPagerSwipe(pagerState, enabled = { !coordinator.isDragging })
                        .onGloballyPositioned {
                            val bounds = it.boundsInRoot()
                            viewport = bounds
                        },
                    // Keep off-screen pages placed while dragging, so the lifted cell's pointer stream survives a flip.
                    keepAllPagesPlaced = coordinator.isDragging,
                ) { pageIndex ->
                    categories.getOrNull(pageIndex)?.let { category ->
                        CategoryPage(
                            category = category,
                            cols = cols,
                            coordinator = coordinator,
                            gestures = gestureConfig,
                            dragged = if (pageIndex == gapPage) draggedApp else null,
                            gap = gap,
                            // Only the page being dragged over scrolls itself; the others must sit still.
                            fingerInRoot = if (pageIndex == gapPage) session?.fingerInRoot else null,
                            metrics = metrics,
                            // The strip's edge reaches a page as the one thing it changes there: which end its
                            // content hugs, so a bottom strip has the grid against it rather than a gap.
                            alignBottom = tabEdge == VerticalEdge.BOTTOM,
                            onLaunch = onLaunch,
                            showItemMenu = showItemMenu,
                            onRelease = { coordinator.drop() },
                            onGeometry = { geometries[pageIndex] = it },
                            onScrollState = { pageScrolls[pageIndex] = it },
                        )
                    }
                }
                if (tabEdge == VerticalEdge.BOTTOM) {
                    strip()
                    header?.invoke()
                }
            }

            // The floating proxy — the only thing the user sees moving, since `LauncherDragCell` draws the lifted cell
            // invisible.
            //
            // **Sized from this surface's own measurements, never from a page's published geometry.** A cell is
            // `viewport.width / cols` wide — a page's grid fills the viewport, and the grid's margin was applied
            // *above* the measurement, so it is already out of `viewport.width` — and its height follows from that width — the same `cellHeight` derivation a page lays its
            // grid out with, applied to the same width, so the proxy and the cell it lifted cannot come out different
            // sizes. Reading the geometry map here instead — as this did — makes the proxy depend on a page having
            // reported *up* through `onGeometry` before it can draw anything, so a drag whose first frame beats that
            // report renders nothing at all. `viewport` comes from the pager's own `onGloballyPositioned`, a direct
            // child, which is the same place `AppsPager` gets its geometry and is why that surface never had the
            // problem.
            // The column count is clamped here too, by the same `fitCols` a page applies to the same width — a page's
            // grid fills the viewport, so the two divisions are identical and the proxy cannot come out a different
            // size from the cell it lifted just because the icons outgrew the stored count.
            val cellWidth = viewport?.let {
                with(density) { (it.width / AppsCategoryGrid.fitCols(it.width.toDp().value, cols, metrics)).toDp() }
            }
            // The same pair a page lays its grid out with, so the proxy cannot be a different size *or* draw a
            // different icon from the cell it lifted.
            val cell = derivedCell(cellWidth = cellWidth ?: 0.dp, metrics = metrics)
            val cellHeight = cell.height
            // Gated on this being the surface on screen: the coordinator is the launcher's, so a drag that has
            // been ejected onto home is still live here, and two proxies under one finger is what that would look
            // like.
            if (presented && session != null && cellWidth != null && draggedApp != null) {
                val cellW = with(density) { cellWidth.toPx() }
                val cellH = with(density) { cellHeight.toPx() }
                val finger = session.fingerInRoot
                FloatingDragIcon(
                    rootOffset = IntOffset(
                        (finger.x - cellW / 2f).roundToInt(),
                        (finger.y - cellH / 2f).roundToInt(),
                    ),
                    size = DpSize(cellWidth, cellHeight),
                ) {
                    AppCell(app = draggedApp, metrics = cell.metrics, modifier = Modifier.fillMaxSize())
                }
            }

            renaming?.let { category ->
                CategoryRenameDialog(
                    category = category,
                    onRename = { name -> onRenameCategory(category.id, name) },
                    onDismiss = { renaming = null },
                )
            }
        }
    }
}
