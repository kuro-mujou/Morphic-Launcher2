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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.drag.DropPlanner
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.folder.FolderDragDelegate
import inkspire.morphic.core.designsystem.folder.FolderOverlay
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.feature.apps.AppsCategory

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

/**
 * The **category card** layout of the APPS surface
 * ([inkspire.morphic.core.model.AppsLayout.CATEGORY_CARD]): a scrolling grid of one square card per category, each
 * previewing a few of its apps, tapped open to reach the rest. The iOS App-Library shape, and the last of the five
 * APPS layouts.
 *
 * **It shares the category pager's store** (`category` + `category_item`, via `AppsOrderRepository`) — the same
 * arrangement, drawn as cards instead of pages. So there is nothing to seed, classify or migrate here: a user who
 * switches between the two layouts sees the same categories in the same order with the same apps in them.
 *
 * **No folders live on a card, and none ever will.** That answers the last open question in the arrangement model, so
 * it is worth being explicit about why — there are two independent reasons and either would be enough:
 * - The category pager's reason, unchanged: a category *is* the grouping, so a folder inside one is a second,
 *   redundant grouping of the same apps.
 * - A reason peculiar to this layout: a card is *already* a folder in everything but name — a titled tile previewing a
 *   collection, which opens into a bounded grid. A folder on a card would nest a grouping inside a grouping that looks
 *   identical to it, and its preview tile (a 2×2 of icons) would have to render inside one of four preview slots that
 *   are themselves 2×2 — a 2×2 inside a 2×2, at roughly 20dp a side.
 *
 * The consequence is a schema question closed rather than answered: `category_item` stays keyed on `component`, and
 * the "exactly one of app-or-folder" reshape that `apps_pager_item` needed is **not** owed here. No migration.
 *
 * **The expansion is a [FolderOverlay]** — not a lookalike. That type's parameters are already a label and a list of
 * apps with no folder id anywhere in them, because what it actually renders is *an ordered collection of apps opened
 * over a surface*, which is exactly what an expanded category is; even the grid it sizes itself from is
 * [inkspire.morphic.core.model.FolderGrid], whose KDoc has always called itself the "folder / category-card grid".
 * Reusing it brings the paging, the dots, the MovingGap reorder and the scrim for free. It leaves the type carrying a
 * name that now covers one case too many; renaming it (to something like `IconCollectionOverlay`) would touch home,
 * the APPS pager and the whole `folder/` package, so it is better done — if at all — when a *third* consumer says what
 * the honest name is, rather than guessed at from two.
 *
 * **Nothing on a card is dragged yet.** A drag inside the expansion reorders that category; a drag out of one, and a
 * drop onto another card (which is how a card layout re-files), come next — see the TODOs below. The two live
 * together because they are one gesture, and both need this surface to register a drop zone over the card grid.
 *
 * **Two tap targets per card and no overlap between them**, which is the "an item's touch target is its visible
 * extent" rule applied to a container: the preview icons launch, and the **header row** plus the **overflow cluster**
 * open the category. Nothing else on the card responds — the empty slots and the padding stay free. That the header
 * opens the card is not a fallback for the cluster: a category holding four apps or fewer has no cluster at all, and
 * without a header target it could never be opened, reordered, or dropped into.
 *
 * @param onReorder commits a reorder inside the expansion: the category, and the order its overlay reported. The
 *   report covers only the apps the cache could resolve, and is reconciled against real membership **in the store**
 *   rather than here — see `AppsCategoryChange.Reorder`.
 */
@Composable
fun AppsCategoryCard(
    categories: List<AppsCategory>,
    onLaunch: (ComponentKey) -> Unit,
    onReorder: (category: String, order: List<ComponentKey>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestureConfig = rememberAppsGestureConfig()

    // Which category is expanded. A plain id, deliberately not a `FolderHostState`: that state machine exists for the
    // phases an app passes through while its *membership* changes mid-drag (injecting, injected), and nothing here
    // changes membership — an expansion is opened by a tap and reorders within one category. It arrives with the
    // drop-onto-a-card gesture, which is what can move an app between categories.
    var openCategoryId by remember { mutableStateOf<String?>(null) }

    // The expanded overlay's own drag hooks. It has to sit above the coordinator because the planner reads it and is
    // built first — the same construction-order squeeze the other folder-hosting surfaces document.
    val expansionDelegate = remember { mutableStateOf<FolderDragDelegate?>(null) }
    // Every zone that can exist right now belongs to the expansion (the card grid registers none yet), so every hover
    // is its business. When the card grid becomes a drop target this grows the zone check the other surfaces have.
    val planner = remember { DropPlanner { _, item, finger -> expansionDelegate.value?.onHover(item, finger) } }
    val coordinator = rememberDragCoordinator(planner)

    fun handleDrop() {
        // Null means the finger was outside the expansion's grid when it was released. Nothing was written on the way,
        // so that is a cancel — the same "released outside the card" outcome a folder reaches.
        val outcome = coordinator.drop() ?: return
        expansionDelegate.value?.commitReorder(outcome.item)
    }

    // A category can stop existing underneath an open expansion (a rebalance drops the ids it no longer defines, and
    // `dropUnknownCategories` unfiles their apps). Clearing the id keeps that from leaving a card layout with an
    // overlay that would reappear if the id were ever re-created.
    LaunchedEffect(categories) {
        if (openCategoryId != null && categories.none { it.category.id == openCategoryId }) openCategoryId = null
    }

    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    CompositionLocalProvider(LocalIconMetrics provides ExpandedIconMetrics) {
        Box(modifier.fillMaxSize()) {
            // Lazy, unlike the category *pager*'s pages, which use `LauncherGrid` in SCROLL_GRID mode. The card count
            // is small, but each card composes up to seven baked icons, so a screenful is already ~30 and the whole
            // list is hundreds — the same input-size argument that puts `AppsVerticalGrid` on a lazy grid. Nothing is
            // lost by it here: this part publishes no `GridGeometry` because it registers no drop zone.
            LazyVerticalGrid(
                columns = GridCells.Fixed(CardColumns),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(safeInsets),
                contentPadding = PaddingValues(CardPadding),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing),
                verticalArrangement = Arrangement.spacedBy(CardSpacing),
            ) {
                // TODO(category management): long-press a card for rename / delete, and drag one to reorder the
                //  categories. Both write category *definitions*, which `AppsCategoryChange` deliberately has no ops
                //  for — the rewrite plan puts that editor in `feature:settings`, and its op set should be shaped by
                //  the screen that uses it rather than invented here.
                items(items = categories, key = { it.category.id }) { entry ->
                    CategoryCard(
                        category = entry,
                        gestureConfig = gestureConfig,
                        onLaunch = onLaunch,
                        onExpand = { openCategoryId = entry.category.id },
                    )
                }
            }

            val expanded = categories.firstOrNull { it.category.id == openCategoryId }
            if (expanded != null) {
                // Keyed so switching categories doesn't inherit the previous one's pager position or reorder gap —
                // the same guard the folder-hosting surfaces put round their overlay.
                key(expanded.category.id) {
                    FolderOverlay(
                        label = expanded.category.name,
                        apps = expanded.apps,
                        coordinator = coordinator,
                        gestureConfig = gestureConfig,
                        onLaunch = { component -> onLaunch(component); openCategoryId = null },
                        onReorder = { order -> onReorder(expanded.category.id, order) },
                        // TODO(drop onto a card): holding a dragged app outside the expansion should close it and hand
                        //  the drag to the card grid beneath, so an app can be carried from one category into another
                        //  in a single gesture — the folder subsystem's "leave" half. Ignored until the card grid is a
                        //  drop zone: closing the expansion now would strand the drag over a surface with nowhere to
                        //  land, where holding still simply does nothing instead.
                        onLeave = {},
                        onDrop = ::handleDrop,
                        onPublishDelegate = { expansionDelegate.value = it },
                        onDismiss = { openCategoryId = null },
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
 */
@Composable
private fun CategoryCard(
    category: AppsCategory,
    gestureConfig: ItemGestureConfig,
    onLaunch: (ComponentKey) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Square, so the four preview slots are square too and a card reads as one tile rather than as a band.
            .aspectRatio(1f)
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
            // Floored at zero because `Modifier.size` rejects a negative: a card squeezed below the gap's own width
            // draws nothing rather than crashing.
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
    // A category is not draggable: reordering the categories themselves is category management, deferred with the
    // rest of it.
    onBeginDrag = {},
    onDragTo = {},
    onDrop = {},
    onCancelDrag = {},
)
