package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.DragAutoScrollEffect
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.FloatingDragIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderDragDelegate
import inkspire.morphic.core.designsystem.folder.FolderOverlay
import inkspire.morphic.core.designsystem.folder.FolderPhase
import inkspire.morphic.core.designsystem.folder.rememberFolderHostState
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.feature.apps.AppsCategory
import kotlin.math.roundToInt

/**
 * How many columns of cards, and the card's own spacing/inset/corner/tint — **placeholders, not design choices**, in
 * the same sense as the other APPS layouts' cell heights: these are surface metrics bound for the settings layer, and
 * a flat constant says so where derived arithmetic would read as a decision.
 *
 * Two columns of square cards is the App-Library idiom this layout borrows, and it is the largest count at which a
 * four-slot preview stays legible on a phone — but it is still a value nothing owns yet, so it is deliberately *not* a
 * [inkspire.morphic.core.model.GridBlueprint]: that would claim it is per-device and user-editable, and neither is
 * decided. The visible consequence is a tablet, where two columns make each card huge (see [PreviewIconMetrics]).
 */
private const val CardColumns = 2
private val CardSpacing = 12.dp
private val CardPadding = 16.dp
private val CardInset = 12.dp
private val CardCorner = 24.dp
private val HeaderGap = 8.dp
private val SlotGap = 8.dp
private const val CardAlpha = 0.10f

/**
 * The preview's shape: [PreviewCols] × [PreviewCols] slots. The last one becomes the **overflow cluster** when the
 * category holds more than fits, so a fuller card shows [PreviewSlots] - 1 launchable icons plus a tile that opens
 * the rest.
 */
private const val PreviewCols = 2
private const val PreviewSlots = PreviewCols * PreviewCols

/**
 * The preview icons' metrics: **no labels**, and the icon filling its slot.
 *
 * A card is a thumbnail of a category, not a grid of it — four labels at this size would be four ellipsised words and
 * would eat the space the icons need to be recognisable. Recognising an icon *is* the preview's whole job, so the
 * label goes and [IconMetrics.iconPercent] goes to 1: the slot is already sized to be an icon.
 *
 * [IconMetrics.maxIconDp]'s default (72dp) is left alone even though it *will* bind on a tablet, where two columns of
 * cards give a slot far wider than that and the icon then floats in it. Raising it here would paper over the real
 * cause — the column count above being device-blind — with a number nothing owns either. Fix the columns, not this.
 */
private val PreviewIconMetrics = IconMetrics(iconPercent = 1f, showLabel = false)

/**
 * The expanded category's metrics — labels on, matching the other APPS layouts' density.
 *
 * Provided as the surface's [LocalIconMetrics] (rather than passed to the overlay) because it is what this surface
 * means by "an app cell": the card previews are the exception and say so at each call site.
 */
private val ExpandedIconMetrics = IconMetrics(iconPercent = 0.75f)

/** This surface's drop zone — the whole card grid, as each paged surface registers its viewport. */
private val CardGridZoneId = ZoneId("apps-category-cards")

/**
 * The plan reported for a hover over any card: **merge**, because that is what dropping on a card does — it folds the
 * app into that collection, exactly as a folder's centre ring does.
 *
 * The footprint is a token, unread: which card is being aimed at lives in this surface's own state
 * ([AppsCategoryCard]'s `hoveredCategoryId`), because a card is not a cell in a lattice anyone else could name. The
 * *intent* is the part that has to be true, since the drop shadow and the drag host both branch on it.
 */
private val CardMergePlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.MERGE)

/**
 * The floating proxy's size while a drag is over the card grid — **a placeholder**, and the one metric here with no
 * cell to inherit from: the app was lifted out of an expansion whose cell size this surface never sees, and a card
 * preview slot is not what the user is carrying. Replaced by the icon-size setting with the rest.
 */
private val DragProxySize = 72.dp

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
 * **The expansion is a [FolderOverlay]** — not a lookalike. That type's parameters are already a label and a list of
 * apps with no folder id anywhere in them, because what it actually renders is *an ordered collection of apps opened
 * over a surface*, which is exactly what an expanded category is; even the grid it sizes itself from is
 * [inkspire.morphic.core.model.FolderGrid], whose KDoc has always called itself the "folder / category-card grid".
 * Reusing it brings the paging, the dots, the MovingGap reorder, the scrim and the whole leave/enter dwell for free.
 *
 * ## Dragging between categories — the folder↔home gesture, on cards
 *
 * The lifecycle is the same `FolderHostState` home and the APPS pager run on, so every rule below is that machine's
 * rather than this file's: **tap a card to open it; hold a dragged app over another card (~1s) and that card expands
 * mid-drag so the app lands at a *chosen* slot; hold outside an open expansion (~1s) and it closes with the drag
 * carrying on over the cards beneath.** Both halves are repeatable, in any order, over any number of categories,
 * including re-entering one already visited — because neither half writes anything. Membership is decided **only at
 * the drop**. A drop straight onto a card needs no dwell at all and appends.
 *
 * Three things differ from home, and all three are properties of *this* surface rather than choices:
 * - **A drag starts inside an expansion, never on the card grid.** Home has loose apps on its grid to pick up; here
 *   every app is filed in exactly one category, so there is no such thing as an app sitting *on* the surface. The
 *   analogue of home's folder→folder move is expansion→card, and it is the whole gesture. Preview icons on a card stay
 *   tap-to-launch: a folder's preview tile isn't a set of draggable items on home either, and making these draggable
 *   would pin the source card in composition — which a lazy grid cannot honour while auto-scrolling to reach the
 *   target card (see the grid below).
 * - **There is no "empty cell" landing.** Off a card there is nowhere for an app to be, so the planner reports no plan
 *   at all and a release there is a cancel. That is why the only intent this surface ever reports is
 *   [DropIntent.MERGE]. It also means a *held* finger almost always has a target: where home's post-leave dwell only
 *   catches the small centre ring of a cell that happens to hold a folder, here nearly every point is a card, so
 *   holding still after leaving an expansion will open whatever is beneath. That is the shared machine's rule, not a
 *   local one — moving the finger to another card restarts the dwell, and moving off the cards cancels it.
 * - **Landing owes nothing to the category the app came from.** `AppsCategoryChange.Move` unfiles the app from every
 *   other category as part of filing it, so one op is the whole re-file — where the pager has to pair a
 *   `RemoveFromFolder` with a `Move` and commit both in one batch. Dropping an app back on the category it was lifted
 *   from is a **no-op** for the same reason home's is: it is still filed there and nothing was written on the way out.
 *
 * **Releasing outside an open expansion cancels** (it closes, nothing is written). Leaving is a deliberate dwell, so a
 * release out there reads as "never mind" — and the drag has no landing to honour anyway.
 *
 * **Two tap targets per card and no overlap between them**, which is the "an item's touch target is its visible
 * extent" rule applied to a container: the preview icons launch, and the **header row** plus the **overflow cluster**
 * open the category. Nothing else on the card responds — the empty slots and the padding stay free. That the header
 * opens the card is not a fallback for the cluster: a category holding four apps or fewer has no cluster at all, and
 * without a header target it could never be opened, reordered, or dropped into.
 *
 * @param onMove commits a re-file: the app, the category it landed in, and its slot within that category. Also how an
 *   app carried into an expansion commits, since placing it at a chosen slot *is* a move.
 * @param onReorder commits a reorder inside an expansion, when the app was already filed there: the category, and the
 *   order its overlay reported. That report covers only the apps the cache could resolve, and is reconciled against
 *   real membership **in the store** rather than here — see `AppsCategoryChange.Reorder`.
 */
@Composable
fun AppsCategoryCard(
    categories: List<AppsCategory>,
    onLaunch: (ComponentKey) -> Unit,
    onMove: (app: ComponentKey, toCategory: String, toSlot: Int) -> Unit,
    onReorder: (category: String, order: List<ComponentKey>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gestureConfig = rememberAppsGestureConfig()

    val gridState = rememberLazyGridState()
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    // Each card's bounds in root space, so a finger can be turned into a card. A per-card map rather than a grid
    // geometry, because there is no lattice here to compute from: cards are lazy, square, and separated by spacing
    // that is not part of any cell. Entries are added as cards are laid out and removed as they are scrolled away.
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }

    // The card under the finger, resolved by the planner and read back by the drop, the dwell and the drop shadow —
    // never re-derived from the plan's footprint, which is a token here. Safe because the planner runs on the very
    // move that produced the plan those readers are looking at, so it is always the current answer.
    var hoveredCategoryId by remember { mutableStateOf<String?>(null) }

    // The open expansion's drag hooks. Above the coordinator because the planner reads it and must be built first,
    // while the folder host is created after (its effects observe the drag) — the construction-order squeeze every
    // collection-hosting surface documents.
    val expansionDelegate = remember { mutableStateOf<FolderDragDelegate?>(null) }
    val planner = remember {
        DropPlanner { zone, item, fingerInRoot ->
            // Anything that isn't the card grid is the open expansion's zone, which plans its own reorder.
            if (zone.id != CardGridZoneId) return@DropPlanner expansionDelegate.value?.onHover(item, fingerInRoot)
            val card = cardBounds.entries.firstOrNull { it.value.contains(fingerInRoot) }?.key
            hoveredCategoryId = card
            // No card under the finger means no landing exists, not "land where you are": every app is filed in some
            // category, so the gap between cards is not a place. A null plan makes the release a no-op.
            if (card == null) null else CardMergePlan
        }
    }
    val coordinator = rememberDragCoordinator(planner)
    val session = coordinator.session

    LaunchedEffect(coordinator.isDragging) { if (!coordinator.isDragging) hoveredCategoryId = null }
    DisposableEffect(coordinator) { onDispose { coordinator.unregisterZone(CardGridZoneId) } }

    // Category hosting, on the same lifecycle home uses for folders — keyed by `String` here, which is the whole of
    // what the generic id on `FolderHostState` buys: the open/leave/enter machine is identical, so this surface gets
    // it rather than a near-copy of it. The one thing the host can't know is which collection a merge plan targets;
    // home compares placements, the pager compares slots, and a card grid compares card bounds (above).
    val folderHost = rememberFolderHostState<String>(coordinator) { zoneId, _ ->
        if (zoneId == CardGridZoneId) hoveredCategoryId else null
    }
    val openCategoryId = folderHost.openFolderId
    val expanded = categories.firstOrNull { it.category.id == openCategoryId }

    // A category can stop existing underneath an open expansion (a rebalance drops the ids it no longer defines, and
    // `dropUnknownCategories` unfiles their apps), which would otherwise leave an overlay with nothing to render and
    // an id that reappears if it is ever re-created.
    LaunchedEffect(categories, openCategoryId) {
        if (openCategoryId != null && categories.none { it.category.id == openCategoryId }) folderHost.close()
    }
    // Tell the host the presented category's persisted contents, so it knows a just-committed app has landed and can
    // stop handing it over as `incoming`.
    val openMembers = expanded?.apps?.map { it.componentKey }
    LaunchedEffect(openMembers) { folderHost.onMembersChanged(openMembers.orEmpty()) }

    fun handleDrop() {
        // Read before dropping: both are cleared when the drag ends, which the drop is.
        val sourceCategoryId = folderHost.dragSourceFolderId
        val presentedId = folderHost.openFolderId
        val target = hoveredCategoryId
        val outcome = coordinator.drop()

        // 1. Inside the open expansion → its own business: a reorder, which is also how an arriving app commits.
        if (outcome != null && outcome.zone != CardGridZoneId) {
            expansionDelegate.value?.commitReorder(outcome.item)
            return
        }
        // 2. Released outside the expansion that is on screen → "never mind": close it and write nothing. Leaving is
        //    a deliberate dwell, and there is no landing out here to honour anyway.
        if (presentedId != null) {
            folderHost.close()
            return
        }
        // 3. Released over no card at all.
        if (outcome == null || target == null) return
        val app = (outcome.item as? GridItem.App)?.component ?: return
        // 4. Dropped on a card: file it there, at the end. Back on the category it came from is a no-op — it is still
        //    filed there and nothing was written on the way out.
        if (target == sourceCategoryId) return
        // The slot is the target's *resolved* size, which can be short of its true membership by however many of its
        // apps the cache couldn't resolve. `moveCategoryItem` coerces, so the app lands at the end of what the user
        // can actually see — which is what "dropped on the card, no position chosen" should mean.
        onMove(app, target, categories.firstOrNull { it.category.id == target }?.apps?.size ?: 0)
    }

    // The app being carried into the open expansion, resolved once and *held*. Keyed on the component alone,
    // deliberately not on `categories`: it is still filed in the category it came from until the write lands, and the
    // commit re-files it — so re-deriving afterwards would lose it mid-hand-off and the icon would blink out.
    val incomingApp = remember(folderHost.incomingComponent) {
        folderHost.incomingComponent?.let { component -> appInCategories(categories, component) }
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
        scrollState = gridState,
        bounds = gridBounds,
        fingerInRoot = session
            ?.takeIf { it.activeZone == CardGridZoneId && openCategoryId == null }
            ?.fingerInRoot,
    )

    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    CompositionLocalProvider(LocalIconMetrics provides ExpandedIconMetrics) {
        Box(modifier.fillMaxSize()) {
            // Lazy, unlike the category *pager*'s pages, which use `LauncherGrid` in SCROLL_GRID mode. The card count
            // is small, but each card composes up to seven baked icons, so a screenful is already ~30 and the whole
            // list is hundreds — the same input-size argument that puts `AppsVerticalGrid` on a lazy grid. It costs
            // nothing here *because* no drag starts on this grid: nothing on it owns a live pointer stream, so a card
            // being disposed as the drag auto-scrolls past it can't kill the gesture.
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(CardColumns),
                userScrollEnabled = !coordinator.isDragging,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(safeInsets)
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        gridBounds = bounds
                        coordinator.registerZone(
                            DropZone(CardGridZoneId, bounds, z = 0) { item -> item is GridItem.App },
                        )
                    },
                contentPadding = PaddingValues(CardPadding),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing),
                verticalArrangement = Arrangement.spacedBy(CardSpacing),
            ) {
                // TODO(category management): long-press a card for rename / delete, and drag one to reorder the
                //  categories. Both write category *definitions*, which `AppsCategoryChange` deliberately has no ops
                //  for — the rewrite plan puts that editor in `feature:settings`, and its op set should be shaped by
                //  the screen that uses it rather than invented here.
                items(items = categories, key = { it.category.id }) { entry ->
                    val id = entry.category.id
                    CategoryCard(
                        category = entry,
                        gestureConfig = gestureConfig,
                        shadowed = id == shadowedCategoryId,
                        onLaunch = onLaunch,
                        onExpand = { folderHost.open(id) },
                        onBounds = { bounds ->
                            if (bounds == null) cardBounds.remove(id) else cardBounds[id] = bounds
                        },
                    )
                }
            }

            // The floating proxy, drawn by whichever surface is presenting the drag: while an expansion is on screen
            // that is the expansion (at its own cell size), so exactly one of the two paints.
            if (session != null && draggedApp != null && openCategoryId == null) {
                val finger = session.fingerInRoot
                val halfPx = with(density) { DragProxySize.toPx() } / 2f
                FloatingDragIcon(
                    rootOffset = IntOffset((finger.x - halfPx).roundToInt(), (finger.y - halfPx).roundToInt()),
                    size = DpSize(DragProxySize, DragProxySize),
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
            val holder = folderHost.dragSourceFolderId
                ?.takeIf { it != openCategoryId }
                ?.let { id -> categories.firstOrNull { it.category.id == id } }
            val overlays = listOfNotNull(holder?.let { it to false }, expanded?.let { it to true })
            overlays.forEach { (entry, presenting) ->
                key(entry.category.id) {
                    FolderOverlay(
                        label = entry.category.name,
                        apps = entry.apps,
                        coordinator = coordinator,
                        gestureConfig = gestureConfig,
                        incoming = if (presenting) incomingApp else null,
                        presenting = presenting,
                        onLaunch = { component -> onLaunch(component); folderHost.close() },
                        onReorder = { order ->
                            // An app still *arriving* is placed with a `Move` — one op that both files it here and
                            // unfiles it from wherever it was. Once committed (or if it was already a member) this is
                            // a plain re-sequence, which is the one thing `Move` cannot express for a whole list.
                            val incoming = (folderHost.phase as? FolderPhase.Injecting<*>)?.app
                            if (incoming != null && incoming in order) {
                                onMove(incoming, entry.category.id, order.indexOf(incoming))
                                folderHost.injectCommitted()
                            } else {
                                onReorder(entry.category.id, order)
                            }
                        },
                        onLeave = folderHost::leaveFolder,
                        onDrop = ::handleDrop,
                        onPublishDelegate = { expansionDelegate.value = it },
                        onDismiss = { folderHost.close() },
                    )
                }
            }
        }
    }
}

/**
 * One category's card: its name, then a [PreviewSlots]-slot thumbnail of the apps filed under it.
 *
 * The last slot is the **overflow cluster** when the category holds more than fits — an [IconPreviewPlate] of the next
 * four apps, i.e. the same tile a folder draws on a grid, which is the honest rendering of "and more in here". The
 * cluster opens the category; so does the header. A category with [PreviewSlots] apps or fewer shows them all and has
 * no cluster.
 *
 * @param shadowed true while a dragged app is aimed at this card — drawn as a [DropIntent.MERGE] drop shadow over the
 *   whole card, the same affordance a folder's merge ring gets, because dropping here does the same thing.
 * @param onBounds reports this card's root-space bounds as it is laid out, and **null when it leaves composition** so
 *   the surface's hit-test map doesn't keep testing a card that has scrolled away.
 */
@Composable
private fun CategoryCard(
    category: AppsCategory,
    gestureConfig: ItemGestureConfig,
    shadowed: Boolean,
    onLaunch: (ComponentKey) -> Unit,
    onExpand: () -> Unit,
    onBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    DisposableEffect(Unit) { onDispose { onBounds(null) } }
    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                // Square, so the four preview slots are square too and a card reads as one tile rather than a band.
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .clip(RoundedCornerShape(CardCorner))
                .background(colors.surface.copy(alpha = CardAlpha))
                .padding(CardInset),
        ) {
            // The header is a full-width strip rather than just the glyphs: the target has to be reachable for a
            // one-word category name, and a header row *is* its own visible extent, the same way a list row's is.
            Text(
                text = category.category.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().categoryOpenGestures(gestureConfig, onExpand),
            )
            Spacer(Modifier.height(HeaderGap))
            // Slots are sized from the smaller bound and centred, so they stay square whichever way the leftover area
            // happens to be shaped (the header's height varies with the type scale).
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // Floored at zero because `Modifier.size` rejects a negative: a card squeezed below the gap's own
                // width draws nothing rather than crashing.
                val slot = ((minOf(maxWidth, maxHeight) - SlotGap) / 2).coerceAtLeast(0.dp)
                // The cluster takes the last slot only when it would hold *more* than the single icon that slot could
                // show on its own — a category of exactly [PreviewSlots] apps shows all four, no cluster.
                val overflow = category.apps.drop(PreviewSlots - 1)
                val cluster = overflow.take(PreviewSlots).takeIf { overflow.size > 1 }
                Column(verticalArrangement = Arrangement.spacedBy(SlotGap)) {
                    repeat(PreviewCols) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(SlotGap)) {
                            repeat(PreviewCols) { col ->
                                val index = row * PreviewCols + col
                                Box(Modifier.size(slot)) {
                                    PreviewSlot(
                                        app = category.apps.getOrNull(index),
                                        cluster = cluster?.takeIf { index == PreviewSlots - 1 },
                                        slot = slot,
                                        gestureConfig = gestureConfig,
                                        onLaunch = onLaunch,
                                        onExpand = onExpand,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Over the card rather than inside its `Column`, so it covers the header and preview both, and shaped to the
        // card so the shadow reads as "this whole collection" instead of a cell.
        if (shadowed) {
            DropFootprint(
                intent = DropIntent.MERGE,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(CardCorner),
            )
        }
    }
}

/**
 * One preview slot: the overflow [cluster] if this is the last slot and there is an overflow, otherwise [app]'s icon,
 * otherwise nothing.
 *
 * An icon launches; the cluster opens the category. Both go through the launcher's one item-gesture contract rather
 * than a `clickable`, so this surface can't drift from the rest of the launcher on long-press timing or slop.
 */
@Composable
private fun PreviewSlot(
    app: AppInfo?,
    cluster: List<AppInfo>?,
    slot: Dp,
    gestureConfig: ItemGestureConfig,
    onLaunch: (ComponentKey) -> Unit,
    onExpand: () -> Unit,
) {
    when {
        cluster != null -> IconPreviewPlate(
            apps = cluster,
            size = slot,
            modifier = Modifier.categoryOpenGestures(gestureConfig, onExpand),
        )
        app != null -> AppCell(
            app = app,
            modifier = Modifier.fillMaxSize(),
            metrics = PreviewIconMetrics,
            itemGestures = Modifier.appsItemGestures(gestureConfig) { onLaunch(app.componentKey) },
        )
        else -> Unit
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
 * A **category's** tap contract — the launcher's one item-gesture recogniser with only "open" connected.
 *
 * Separate from [appsItemGestures] rather than reusing it, though the two do the same thing today: that one is the
 * *app* contract, and its empty slots are an app's unfinished behaviours (the app options menu, drag-out-to-home).
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
    onDismissMenu = {},
    onEdgeAction = {},
    // A card is not draggable: reordering the categories themselves is category management, deferred with the rest of
    // it. An app *inside* an expansion is what drags here.
    onBeginDrag = {},
    onDragTo = {},
    onDrop = {},
    onCancelDrag = {},
)
