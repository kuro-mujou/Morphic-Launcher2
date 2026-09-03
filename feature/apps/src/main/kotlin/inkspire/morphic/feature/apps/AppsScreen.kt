package inkspire.morphic.feature.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.backdrop.OnFilm
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.collection.AppAdditions
import inkspire.morphic.core.designsystem.grid.GridArea
import inkspire.morphic.core.designsystem.grid.cardMinCell
import inkspire.morphic.core.designsystem.grid.fitCols
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.menu.MenuAction
import inkspire.morphic.core.designsystem.menu.surfaceMenuGestures
import inkspire.morphic.core.designsystem.surface.AxisScroll
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.surface.ScrollAxes
import inkspire.morphic.core.model.AppsCardGrid
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.AppsListGrid
import inkspire.morphic.core.model.AppsPagerGrid
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.colsFor
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.feature.apps.layout.AppsVerticalGrid
import inkspire.morphic.feature.apps.layout.AppsVerticalList
import inkspire.morphic.feature.apps.layout.categorycard.AppsCategoryCard
import inkspire.morphic.feature.apps.layout.categorypager.AppsCategoryPager
import inkspire.morphic.feature.apps.layout.pager.AppsPager
import inkspire.morphic.feature.apps.layout.rememberAppsGestureConfig
import org.koin.androidx.compose.koinViewModel

/**
 * The APPS surface: the full app collection, rendered in whichever [AppsLayout] is selected.
 *
 * **This is the one place a layout is chosen**, and the reason this module exists as a single `feature:apps`
 * rather than a module per look. A "drawer" and a "library" are the same collection of the same apps with the same
 * launch behavior, differing only in arrangement — split across modules they duplicate the data wiring, and the
 * "drawer or library?" question has to be answered *before* the layout question, twice. The model
 * already collapsed that (`Surface.APPS` + [AppsLayout], "the layout alone decides the look"); this is the code
 * catching up. A new layout is a new file under `layout/` and a new arm below, with nothing else to touch.
 *
 * Everything above the `when` is shared by construction — the ViewModel, the ordering, the background, and the
 * **device configuration** every layout's sizing resolves against — so no layout can quietly disagree with another
 * about what the app list *is* or how big its icons are. The **theme** is shared from further out still:
 * `feature:shell` themes the whole launcher zone, so this surface cannot disagree with home either.
 *
 * **Icon sizing arrives resolved, and is handed down explicitly** rather than published as one ambient
 * `LocalIconMetrics` for the whole surface. Two layouts host a folder overlay, and a folder is *its own grid with its
 * own configuration* — one ambient value would silently give it the page's sizing. So each layout takes the metrics for
 * the grid it draws, and the two that open folders take the folder's as well.
 *
 * **Long-press on empty space opens this surface's settings**, which is the one thing the APPS surface menu offers.
 * A side surface with no empty-space menu is defensible where items fill their cells, and not here: touch targets
 * are narrower by design — an item's gestures cover its icon and its label, never its cell
 * or its row, so every one of these layouts has real free space between items and none of it did anything. What the
 * row goes to is the **arrangement being looked at** — the APPS settings section has a chip per layout, and without
 * this reaching the one you are using is a long-press on home, a section, and then a chip.
 *
 * @param layout which arrangement to render. A parameter with a default rather than a read of user preference,
 *   because nothing owns that preference yet: it belongs to `data:settings` (B7), per-binding, since the same
 *   surface can be reached from different home edges with different layouts. Wire it there, not here.
 * @param onOpenLayoutSettings goes to the settings for [layout]. **Nullable, and null means the row is absent** —
 *   not disabled: a host with no back stack for that destination would otherwise get a row that does nothing. The
 *   same nullable-lambda shape `WidgetPickerSheet` uses for a capability its host may not have.
 */
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    layout: AppsLayout = AppsLayout.VERTICAL_LIST,
    onOpenLayoutSettings: (() -> Unit)? = null,
) {
    val viewModel = koinViewModel<AppsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The one thing the UI knows and the ViewModel cannot: which window configuration this is. Everything
    // device-dependent — every grid's size, every grid's icon sizing — is resolved from it down there, against the
    // settings store, rather than being worked out up here and pushed down piecemeal — pushing a derivative leaves
    // the surface drawing its blueprint whatever the user had chosen.
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    // **The pager's page capacity, fitted here and reported down** — the one grid on this surface whose stored size
    // cannot simply be clamped where it is drawn, because it is also what the *store* is paginated against (see
    // `AppsViewModel.setPagerFit`). The area is the window minus the insets the pager itself pads its pages by, so the
    // capacity describes the space a page really has.
    //
    // **Reported from here rather than from the pager's arm**, so it does not depend on which layout is showing: the
    // pager's arrangement is kept in step with what is installed whatever the user is looking at, which is the
    // invariant that makes switching layout reload nothing.
    // **The pager's margin comes off before its capacity is fitted**, which is the one place on this surface where
    // padding is more than a visual inset: `rows × cols` is what the *store* is paginated against, so a capacity
    // computed against the full width would put entries on pages the drawn grid has no room for. Every other layout
    // clamps where it draws; this one reports.
    val pagerPadding = state.paddingFor(GridSlot.APPS_PAGER).dp
    val pagerArea = usableWindowArea(uiInsets).let {
        GridArea(widthDp = (it.widthDp - pagerPadding.value * 2).coerceAtLeast(1f), heightDp = it.heightDp)
    }
    // The card grid's own margin and the width left for its lanes, computed here beside the pager's because the
    // settings section bounds its lane buttons against exactly this expression. Two derivations of "how wide is the
    // grid" that could disagree is the thing `usableWindowArea` exists to prevent.
    val card = rememberCardGeometry(state, device)
    // The blueprint stands in for the frame before the store answers — the same fallback every other grid here uses —
    // but the *report* below is gated on the store having answered. Paginating against a placeholder would write pages
    // nobody chose and then rewrite them, which is the trap home's settle effects are guarded against too.
    val storedPager = state.pagerConfig ?: AppsPagerGrid.toGridConfig(device)
    val pagerFit = AppsPagerGrid.fitGridConfig(
        area = pagerArea,
        cols = storedPager.visualCols,
        rows = storedPager.visualRows,
        metrics = state.metricsFor(GridSlot.APPS_PAGER),
    )
    if (state.pagerConfig != null) LaunchedEffect(pagerFit) { viewModel.setPagerFit(pagerFit) }

    // No `LauncherTheme` here: the launcher **zone** is themed once by `feature:shell`'s `LauncherShell`, as home's
    // Settings keeps its own boundary, so the two can disagree about dark/light — the launcher follows wallpaper
    // brightness, settings follows the system.
    //
    // **Transparent, and read against the shell's frost.** This surface is hundreds of rows of plain text at
    // whatever density the user chose, so a busy wallpaper
    // directly behind it is unreadable; the opaque background was the honest stand-in until something better existed.
    // `SurfaceBackdropLayer` in `feature:shell` is that thing: a blurred sheet of the wallpaper between HOME and this,
    // fading in as the pan brings this surface on. It is opaque-by-scrim when there is no wallpaper to sample, so the
    // fresh-install look is unchanged.
    //
    // Painting nothing here is what lets the two move independently — the frost is not this composable's to carry.
    // **The surface menu**, on the one root every layout is drawn into — so all five get it identically and a new
    // layout cannot forget it. `surfaceMenuGestures` owns why a press that lands on an icon does not reach here, and
    // gating on presence is the floating proxy's rule: a surface panned off to one side must not answer a press meant
    // for the one in front of it.
    // **What a collection's Add cell offers**: every installed app, sorted, built once for the whole surface —
    // each overlay subtracts what it already holds. Two commits rather than one, because the two stores mean
    // different things by "add": a folder takes membership, where a category is a filing an app has exactly one of.
    val additions = rememberCollectionAdditions(state, viewModel)

    val menuHost = LocalMenuHost.current
    val gestureConfig = rememberAppsGestureConfig()
    val presented = LocalSurfacePresented.current
    // **This whole surface sits on the shell's film**, which is two things everything drawn here has to know: not to
    // frost itself again (a second sample over an already-blurred sheet is a sharper hole through it, not glass on
    // it), and to take its content color from the *film* rather than from the wallpaper the film is made of. `OnFilm`
    // is both. Declared once at the root rather than per layout, for the surface menu's reason — five arrangements,
    // and a new one must not be able to forget.
    OnFilm {
        Box(
            modifier
                .fillMaxSize()
                .surfaceMenuGestures(gestureConfig, enabled = presented) { position ->
                    menuHost?.showSurface(
                        position = position,
                        // The host appends the launcher-wide *Settings* row itself; this is the one verb the APPS
                        // surface owns. Empty when there is nowhere to send it, which collapses the menu to that row.
                        surfaceActions = onOpenLayoutSettings
                            ?.let { listOf(MenuAction("Apps settings", onClick = it)) }
                            .orEmpty(),
                        // The menu is composed at the shell, so it cannot read the local above for itself.
                        overFrost = true,
                    )
                },
        ) {
            when (layout) {
                AppsLayout.VERTICAL_LIST -> AppsVerticalList(
                    apps = state.apps,
                    onLaunch = viewModel::launch,
                    metrics = state.metricsFor(GridSlot.APPS_LIST),
                    rowHeight = state.rowHeight,
                    horizontalPadding = state.paddingFor(GridSlot.APPS_LIST).dp,
                )

                AppsLayout.VERTICAL_GRID -> AppsVerticalGrid(
                    apps = state.apps,
                    onLaunch = viewModel::launch,
                    metrics = state.metricsFor(GridSlot.APPS_SCROLL),
                    cols = state.colsFor(GridSlot.APPS_SCROLL, device),
                    horizontalPadding = state.paddingFor(GridSlot.APPS_SCROLL).dp,
                )

                AppsLayout.PAGER -> AppsPager(
                    pages = state.pagerPages,
                    onLaunch = viewModel::launch,
                    onMove = viewModel::movePagerItem,
                    onMerge = viewModel::mergePagerItem,
                    onReorderFolder = viewModel::reorderFolder,
                    onAddToFolder = viewModel::addToFolder,
                    onDropExtracted = viewModel::dropExtractedApp,
                    onMergeExtracted = viewModel::mergeExtractedApp,
                    metrics = state.metricsFor(GridSlot.APPS_PAGER),
                    // A folder opened over the pager is a different grid, so it takes its own sizing rather than
                    // inheriting the page's.
                    folderMetrics = state.metricsFor(GridSlot.FOLDER),
                    // The same grid the ViewModel paginates the store against — the fitted one computed above, passed down
                    // rather than re-resolved, so the page drawn and the page stored cannot be different sizes.
                    config = pagerFit,
                    horizontalPadding = pagerPadding,
                    wraps = state.wraps(GridSlot.APPS_PAGER),
                    rememberPage = state.remembersPage(GridSlot.APPS_PAGER),
                    folderAdditions = additions.forFolder,
                )

                AppsLayout.PAGER_WITH_CATEGORY -> AppsCategoryPager(
                    categories = state.categories,
                    onLaunch = viewModel::launch,
                    onMove = viewModel::moveCategoryItem,
                    metrics = state.metricsFor(GridSlot.APPS_CATEGORY),
                    cols = state.colsFor(GridSlot.APPS_CATEGORY, device),
                    horizontalPadding = state.paddingFor(GridSlot.APPS_CATEGORY).dp,
                    wraps = state.wraps(GridSlot.APPS_CATEGORY),
                    rememberPage = state.remembersPage(GridSlot.APPS_CATEGORY),
                    // The two writes a **tab** can make, beside the one a page can: its order in the strip, and its
                    // name. Both are the categories themselves rather than what is filed under them, which is why
                    // they are their own commits and not more `onMove`.
                    onReorderCategories = viewModel::reorderCategories,
                    onRenameCategory = viewModel::renameCategory,
                    // The one piece of chrome any of these five layouts reads: which edge its tab strip sits on.
                    tabEdge = state.categoryTabEdge,
                )
                // The fifth and last layout, sharing the category store the one above uses. Named rather than folded
                // into an `else`, like every arm here: adding a value to [AppsLayout] must fail to compile until it
                // is rendered.
                AppsLayout.CATEGORY_CARD -> AppsCategoryCard(
                    categories = state.categories,
                    onLaunch = viewModel::launch,
                    // The same `Move` the category pager commits: on both layouts a re-file and a reposition are one
                    // op, because the destination id carries the difference.
                    onMove = viewModel::moveCategoryItem,
                    onReorder = viewModel::reorderCategory,
                    // An expansion *is* a folder overlay on the folder grid, so it takes that slot's sizing; a card's
                    // preview slots take their own, which is `APPS_CARD`'s.
                    metrics = state.metricsFor(GridSlot.FOLDER),
                    slotMetrics = card.metrics,
                    chrome = card.chrome,
                    // **Clamped where it is drawn**, as every other scrolling grid here is, and against the same width
                    // the settings editor bounds its lane buttons by — so the editor cannot offer a lane the surface will
                    // not draw. The floor is a *card's*: two of its preview icons at their own guardrail, plus the
                    // paddings around and between them, which is `CellFit`'s ordinary inversion applied to a tile.
                    cardColumns = card.columns,
                    horizontalPadding = card.padding,
                    categoryAdditions = additions.forCategory,
                )
            }
        }
    }
}

/**
 * **What each APPS layout scrolls, per axis** — the fact the launcher shell turns into an edge's *close* policy,
 * since a swipe back to HOME crosses this surface's own content.
 *
 * The twin of `HomeLayout.scrollAxes`, and the reason both exist rather than one table in the shell: the shell owns
 * the question and each feature owns its answer, so a new layout declares its own scroll behavior in the module
 * that draws it. The `when` is exhaustive, so a sixth [AppsLayout] cannot be added without saying what it scrolls —
 * the same rule the render `when` above enforces.
 *
 * @param wraps whether this layout's pager loops — `SettingsRepository.pagerWraps` for [AppsLayout.pagerSlot], and
 *   ignored by the three layouts that do not page. A wrapping pager has no end to hand a one-finger swipe off at, so
 *   the axis becomes [AxisScroll.INFINITE] and that edge turns two-finger-only; see `HomeLayout.scrollAxes`, which
 *   says the same thing from the other side of the boundary.
 */
fun AppsLayout.scrollAxes(wraps: Boolean): ScrollAxes = when (this) {
    AppsLayout.VERTICAL_LIST, AppsLayout.VERTICAL_GRID, AppsLayout.CATEGORY_CARD ->
        ScrollAxes(vertical = AxisScroll.BOUNDED)

    AppsLayout.PAGER -> ScrollAxes(horizontal = AxisScroll.ofPager(wraps))
    // The only layout that scrolls on both: pages across, a category down. Only the pages can wrap — a category's
    // own scroll is a list of apps, which has a top and a bottom whatever the pager does.
    AppsLayout.PAGER_WITH_CATEGORY ->
        ScrollAxes(horizontal = AxisScroll.ofPager(wraps), vertical = AxisScroll.BOUNDED)
}

/**
 * The resolved [IconMetrics] for [slot], or the ambient default when the store has not answered yet.
 *
 * The fallback is the honest one rather than a guessed constant: `LocalIconMetrics`' own default is what every cell
 * would have used anyway, and it applies for at most the frame between this surface reporting its device and the first
 * emission arriving. A per-slot placeholder here would be a second source of truth for a value the blueprint owns.
 */
@Composable
private fun AppsState.metricsFor(slot: GridSlot): IconMetrics =
    iconSizing[slot]?.toIconMetrics() ?: LocalIconMetrics.current

/**
 * The resolved visual column count for scrolling grid [slot], or its blueprint's default until the store answers.
 *
 * The fallback is the blueprint rather than a constant for the reason [metricsFor]'s is `LocalIconMetrics`: it is the
 * same number the store itself would resolve for a user who has changed nothing, so the frame before the first
 * emission cannot show a size nothing owns. Reached through the slot's own blueprint, so a new scrolling grid needs no
 * change here.
 */
private fun AppsState.colsFor(slot: GridSlot, device: DeviceConfiguration): Int =
    gridCols[slot] ?: slot.blueprint.colsFor(device)

// The pager has no `pagerConfigFor` twin of [colsFor] any more: its stored grid needs *fitting* rather than merely
// resolving, and a fit needs the measured window — so it is computed in the composable above, next to the measurement,
// and reported to the ViewModel from there.

/**
 * The list's resolved row height, or its blueprint's until the store answers.
 *
 * Needs no device, unlike its neighbors: a row height is a physical size rather than a count, so the blueprint
 * declares one value and an override may differ it per configuration — the same shape the dock's height has.
 */
private val AppsState.rowHeight: Dp
    get() = (listRowHeightDp ?: checkNotNull(AppsListGrid.rowHeightDp)).dp

/**
 * Everything the **category card** layout is sized by, resolved together.
 *
 * Extracted because it is one measurement in four parts and only one of five layouts reads any of it — and because
 * `AppsScreen`'s job is choosing between arrangements, not doing each one's arithmetic in its body.
 *
 * @property columns clamped **where it is drawn**, as every other scrolling grid here is, and against the same width
 *   the settings editor bounds its lane buttons by — so the editor cannot offer a lane the surface will not draw. The
 *   floor is a *card's*: two preview icons at their own guardrail plus the paddings around and between them, which is
 *   `CellFit`'s ordinary inversion applied to a tile.
 */
private class CardGeometry(
    val padding: Dp,
    val chrome: CardChrome,
    val metrics: IconMetrics,
    val columns: Int,
)

@Composable
private fun rememberCardGeometry(state: AppsState, device: DeviceConfiguration): CardGeometry {
    val padding = state.paddingFor(GridSlot.APPS_CARD).dp
    // A card's own chrome and the sizing of one preview slot. Both stand in with the blueprint's own values until the
    // store answers, which for the chrome is all-zero — the same thing a fresh install draws, so the first frame is
    // not a different card.
    val chrome = state.cardChrome ?: CardChrome()
    val metrics = state.metricsFor(GridSlot.APPS_CARD)
    // The grid's own gutter comes off as well as the user's margin, so what is divided by the lane count is the width
    // the *lanes* share. `cardMinCell` folds in the spacing between them, which is the other half of the same sum.
    val area = usableWindowArea(uiInsets).let {
        val lanes = it.widthDp - padding.value * 2 - 16.dp.value * 2
        GridArea(widthDp = lanes.coerceAtLeast(1f), heightDp = it.heightDp)
    }
    return CardGeometry(
        padding = padding,
        chrome = chrome,
        metrics = metrics,
        columns = AppsCardGrid.fitCols(
            areaWidthDp = area.widthDp,
            cols = state.colsFor(GridSlot.APPS_CARD, device),
            min = cardMinCell(metrics, chrome),
        ),
    )
}

/** What a collection's Add cell offers on this surface, for each of the two kinds that have one. */
private class CollectionAdditions(
    val forFolder: (Long) -> AppAdditions,
    val forCategory: (String) -> AppAdditions,
)

/**
 * The **Add apps** offer for every collection on this surface: every installed app, sorted once.
 *
 * **Unfiltered on purpose** — each overlay subtracts what it already holds, which is the one thing it knows better
 * than this does (see `AppAdditions.offered`). Sorted here because `AppPicker` does not re-sort, and a picker over
 * every installed app is unusable in any other order.
 *
 * **Two commits rather than one**, because the two stores mean different things by "add": a folder takes membership,
 * where a category is a filing an app has exactly one of, so putting it in one takes it out of another.
 */
@Composable
private fun rememberCollectionAdditions(state: AppsState, viewModel: AppsViewModel): CollectionAdditions {
    val offered = remember(state.apps) { state.apps.sortedBy { it.label.lowercase() } }
    return CollectionAdditions(
        forFolder = { folderId -> AppAdditions(offered) { viewModel.addAppsToFolder(folderId, it) } },
        forCategory = { categoryId -> AppAdditions(offered) { viewModel.addAppsToCategory(categoryId, it) } },
    )
}
